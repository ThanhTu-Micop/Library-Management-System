package com.library.dao;

import com.library.dao.jdbc.JdbcBookCopyDAO;
import com.library.dao.jdbc.JdbcBookDAO;
import com.library.dao.jdbc.JdbcLoanDAO;
import com.library.dao.jdbc.JdbcMemberDAO;
import com.library.dao.support.TestDatabase;
import com.library.dao.support.TestFixtures;
import com.library.exception.DataAccessException;
import com.library.model.Book;
import com.library.model.BookCopy;
import com.library.model.Loan;
import com.library.model.Member;
import com.library.model.criteria.MemberSearchCriteria;
import com.library.model.enums.CopyStatus;
import com.library.model.enums.LoanStatus;
import com.library.model.enums.MemberStatus;
import com.library.config.AppConfig;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Multi-connection transaction and concurrency tests.
 *
 * <p>Each test opens independent connections against {@code library_test} and verifies the
 * isolation, locking, and unique-index behavior the DAOs rely on. Concurrency checks remain
 * separate from single-connection business-rule tests.
 */
class TransactionIsolationTest {

    private final BookDAO bookDao = new JdbcBookDAO();
    private final BookCopyDAO copyDao = new JdbcBookCopyDAO();
    private final MemberDAO memberDao = new JdbcMemberDAO();
    private final LoanDAO loanDao = new JdbcLoanDAO();

    private void prepare() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
        }
    }

    private Connection beginTransaction() throws SQLException {
        Connection c = TestDatabase.getConnection();
        c.setAutoCommit(false);
        return c;
    }

    private static void assertSqlState(String expected, DataAccessException ex) {
        assertEquals(expected, ex.getSqlState(),
                "Expected SQLState " + expected + " but got " + ex.getSqlState()
                        + " (message: " + ex.getMessage() + ")");
    }

    private static void assertSqlState(String expected, DataAccessException ex, String message) {
        assertEquals(expected, ex.getSqlState(), message);
    }

    @Test
    void twoConcurrentActiveLoansForSameCopySecondFailsWith23505() throws Exception {
        prepare();
        int copyId;
        int memberId;
        try (Connection setup = TestDatabase.getConnection()) {
            Book book = TestFixtures.insertBook(setup, "978-CC-000-000-001", "Concurrent Copy Book");
            BookCopy copy = TestFixtures.insertCopy(setup, book, "CC-CP-001", CopyStatus.AVAILABLE);
            Member member = TestFixtures.insertMember(setup, "M-CC-001", "Concurrent Member", "cc@test.com");
            copyId = copy.getCopyId();
            memberId = member.getMemberId();
        }

        Connection tx1 = beginTransaction();
        Connection tx2 = beginTransaction();
        try {
            LocalDate loanDate = LocalDate.of(2026, 10, 25);
            assertEquals(1, loanDao.insert(tx1,
                    new Loan(null, copyId, memberId, loanDate, loanDate.plusDays(14), null,
                            LoanStatus.ACTIVE)));

            try (Statement st = tx2.createStatement()) {
                st.execute("SET LOCAL lock_timeout = '2s'");
            }
            DataAccessException ex = assertThrows(DataAccessException.class,
                    () -> loanDao.insert(tx2,
                            new Loan(null, copyId, memberId, loanDate, loanDate.plusDays(14), null,
                                    LoanStatus.ACTIVE)));

            String state = ex.getSqlState();
            assertTrue(AppConfig.SQLSTATE_UNIQUE_VIOLATION.equals(state) || "55P03".equals(state),
                    "Expected 23505 (unique_violation) or 55P03 (lock_not_available), got " + state);
        } finally {
            tx1.rollback();
            tx2.rollback();
            tx1.close();
            tx2.close();
        }
    }

    @Test
    void uncommittedInsertNotVisibleToOtherConnection() throws Exception {
        prepare();
        Connection tx1 = beginTransaction();
        Connection tx2 = beginTransaction();
        try {
            Member m = new Member(null, "M-UN-001", "Uncommitted", "un@test.com", MemberStatus.ACTIVE);
            memberDao.insert(tx1, m);
            assertNotNull(m.getMemberId());

            assertFalse(memberDao.findById(tx2, m.getMemberId()).isPresent(),
                    "READ COMMITTED: uncommitted inserts in tx1 must be invisible to tx2");

            tx1.commit();
            assertTrue(memberDao.findById(tx2, m.getMemberId()).isPresent(),
                    "After tx1 commits, tx2 must see the new row");
        } finally {
            tx1.close();
            tx2.rollback();
            tx2.close();
        }
        try (Connection cleanup = TestDatabase.getConnection()) {
            TestDatabase.resetAll(cleanup);
        }
    }

    @Test
    void rollbackHidesChangesFromOtherConnection() throws Exception {
        prepare();
        Connection tx1 = beginTransaction();
        Connection tx2 = beginTransaction();
        try {
            Member m = new Member(null, "M-RB-001", "Rollback", "rb@test.com", MemberStatus.ACTIVE);
            memberDao.insert(tx1, m);
            int newId = m.getMemberId();

            assertFalse(memberDao.findById(tx2, newId).isPresent());

            tx1.rollback();
            assertFalse(memberDao.findById(tx2, newId).isPresent(),
                    "After tx1 rolls back, the row must not exist for tx2");
        } finally {
            tx1.close();
            tx2.rollback();
            tx2.close();
        }
    }

    @Test
    void forUpdateLockBlocksConcurrentUpdateUntilCommitOrLockTimeout() throws Exception {
        prepare();
        int copyId;
        try (Connection setup = TestDatabase.getConnection()) {
            Book book = TestFixtures.insertBook(setup, "978-FL-000-000-001", "For Update Lock Book");
            BookCopy copy = TestFixtures.insertCopy(setup, book, "FL-CP-001", CopyStatus.AVAILABLE);
            copyId = copy.getCopyId();
        }

        Connection tx1 = beginTransaction();
        Connection tx2 = beginTransaction();
        try {
            assertTrue(copyDao.findByIdForUpdate(tx1, copyId).isPresent());

            try (Statement st = tx2.createStatement()) {
                st.execute("SET LOCAL lock_timeout = '1s'");
            }
            DataAccessException ex = assertThrows(DataAccessException.class,
                    () -> copyDao.updateStatus(tx2, copyId, CopyStatus.BORROWED));
            assertEquals("55P03", ex.getSqlState(),
                    "FOR UPDATE must block the concurrent UPDATE until lock_timeout");

            tx2.rollback();
            assertEquals(1, copyDao.updateStatus(tx1, copyId, CopyStatus.MAINTENANCE));
            tx1.commit();

            try (Statement st = tx2.createStatement()) {
                st.execute("SET LOCAL lock_timeout = '5s'");
            }
            assertEquals(1, copyDao.updateStatus(tx2, copyId, CopyStatus.BORROWED));
            tx2.commit();
        } finally {
            if (!tx1.isClosed()) {
                tx1.close();
            }
            if (!tx2.isClosed()) {
                tx2.close();
            }
        }
        try (Connection cleanup = TestDatabase.getConnection()) {
            TestDatabase.resetAll(cleanup);
        }
    }

    @Test
    void twoConcurrentBorrowsOnDifferentCopiesOfSameBookBothSucceed() throws Exception {
        prepare();
        int copyId1;
        int copyId2;
        int memberId;
        try (Connection setup = TestDatabase.getConnection()) {
            Book book = TestFixtures.insertBook(setup, "978-TC-000-000-001", "Two Copies Concurrent Book");
            BookCopy cp1 = TestFixtures.insertCopy(setup, book, "TC-CP-001", CopyStatus.AVAILABLE);
            BookCopy cp2 = TestFixtures.insertCopy(setup, book, "TC-CP-002", CopyStatus.AVAILABLE);
            Member member = TestFixtures.insertMember(setup, "M-TC-001", "TC Member", "tc@test.com");
            copyId1 = cp1.getCopyId();
            copyId2 = cp2.getCopyId();
            memberId = member.getMemberId();
        }

        Connection tx1 = beginTransaction();
        Connection tx2 = beginTransaction();
        try {
            LocalDate loanDate = LocalDate.of(2026, 10, 25);
            assertEquals(1, loanDao.insert(tx1,
                    new Loan(null, copyId1, memberId, loanDate, loanDate.plusDays(14), null,
                            LoanStatus.ACTIVE)));
            assertEquals(1, loanDao.insert(tx2,
                    new Loan(null, copyId2, memberId, loanDate, loanDate.plusDays(14), null,
                            LoanStatus.ACTIVE)));

            tx1.commit();
            tx2.commit();

            try (Connection verify = TestDatabase.getConnection()) {
                assertEquals(2, loanDao.countActive(verify),
                        "Two active loans on different copies must both persist");
            }
        } finally {
            if (!tx1.isClosed()) {
                tx1.close();
            }
            if (!tx2.isClosed()) {
                tx2.close();
            }
        }
        try (Connection cleanup = TestDatabase.getConnection()) {
            TestDatabase.resetAll(cleanup);
        }
    }

    @Test
    void twoConcurrentInsertsWithSameIsbnSecondFailsWith23505() throws Exception {
        prepare();
        Connection tx1 = beginTransaction();
        Connection tx2 = beginTransaction();
        try {
            Book b1 = new Book(null, "978-CI-000-000-001", "Concurrent ISBN 1", 1, 1, 2020);
            bookDao.insert(tx1, b1);

            try (Statement st = tx2.createStatement()) {
                st.execute("SET LOCAL lock_timeout = '3s'");
            }
            tx1.commit();

            DataAccessException ex = assertThrows(DataAccessException.class,
                    () -> bookDao.insert(tx2,
                            new Book(null, "978-CI-000-000-001", "Concurrent ISBN 2", 1, 1, 2021)));
            assertSqlState(AppConfig.SQLSTATE_UNIQUE_VIOLATION, ex);
            tx2.rollback();
        } finally {
            if (!tx1.isClosed()) {
                tx1.close();
            }
            if (!tx2.isClosed()) {
                tx2.close();
            }
        }
        try (Connection cleanup = TestDatabase.getConnection()) {
            TestDatabase.resetAll(cleanup);
        }
    }

    @Test
    void twoConcurrentInsertsWithSameEmailSecondFailsWith23505() throws Exception {
        prepare();
        Connection tx1 = beginTransaction();
        Connection tx2 = beginTransaction();
        try {
            memberDao.insert(tx1,
                    new Member(null, "M-CE-001", "Email Owner 1", "dup@test.com", MemberStatus.ACTIVE));
            try (Statement st = tx2.createStatement()) {
                st.execute("SET LOCAL lock_timeout = '3s'");
            }
            tx1.commit();

            DataAccessException ex = assertThrows(DataAccessException.class,
                    () -> memberDao.insert(tx2,
                            new Member(null, "M-CE-002", "Email Owner 2", "DUP@TEST.COM",
                                    MemberStatus.ACTIVE)));
            assertSqlState(AppConfig.SQLSTATE_UNIQUE_VIOLATION, ex,
                    "Email unique index ux_members_email_lower is case-insensitive");
            tx2.rollback();
        } finally {
            if (!tx1.isClosed()) {
                tx1.close();
            }
            if (!tx2.isClosed()) {
                tx2.close();
            }
        }
        try (Connection cleanup = TestDatabase.getConnection()) {
            TestDatabase.resetAll(cleanup);
        }
    }

    /** Auto-commit persists immediately; an explicit transaction can discard a whole batch. */
    @Test
    void autoCommitPersistsInsertAndExplicitTransactionRollsBackBatch() throws Exception {
        prepare();
        int memberId;
        int rollbackId1 = -1;
        int rollbackId2 = -1;

        try (Connection c = TestDatabase.getConnection()) {
            Member m = TestFixtures.insertMember(c, "M-AC-001", "AutoCommit", "ac@test.com");
            memberId = m.getMemberId();
            try (Connection verify = TestDatabase.getConnection()) {
                assertTrue(memberDao.findById(verify, memberId).isPresent(),
                        "Default autoCommit=true means each DAO call is its own transaction");
            }
        }

        try (Connection tx = beginTransaction()) {
            rollbackId1 = TestFixtures.insertMember(tx, "M-ET-002", "Will Rollback 1", "et2@test.com")
                    .getMemberId();
            rollbackId2 = TestFixtures.insertMember(tx, "M-ET-003", "Will Rollback 2", "et3@test.com")
                    .getMemberId();
            tx.rollback();
        }

        final int expectedRollbackId1 = rollbackId1;
        final int expectedRollbackId2 = rollbackId2;
        try (Connection verify = TestDatabase.getConnection()) {
            MemberSearchCriteria criteria = new MemberSearchCriteria();
            criteria.setKeyword("Will Rollback");
            assertAll(
                    () -> assertTrue(memberDao.findById(verify, memberId).isPresent()),
                    () -> assertFalse(memberDao.findById(verify, expectedRollbackId1).isPresent()),
                    () -> assertFalse(memberDao.findById(verify, expectedRollbackId2).isPresent()),
                    () -> assertEquals(0, memberDao.search(verify, criteria).getTotalElements(),
                            "Members inserted in a rolled-back transaction must not exist"));
        }

        try (Connection cleanup = TestDatabase.getConnection()) {
            TestDatabase.resetAll(cleanup);
        }
    }
}
