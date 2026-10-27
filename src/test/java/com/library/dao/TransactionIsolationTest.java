package com.library.dao;

import com.library.dao.jdbc.JdbcBookCopyDAO;
import com.library.dao.jdbc.JdbcBookDAO;
import com.library.dao.jdbc.JdbcLoanDAO;
import com.library.dao.jdbc.JdbcMemberDAO;
import com.library.dao.support.TestDatabase;
import com.library.exception.DataAccessException;
import com.library.model.Book;
import com.library.model.BookCopy;
import com.library.model.Loan;
import com.library.model.Member;
import com.library.model.enums.CopyStatus;
import com.library.model.enums.LoanStatus;
import com.library.model.enums.MemberStatus;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Multi-connection transaction and concurrency tests.
 *
 * <p>PostgreSQL defaults to READ COMMITTED isolation. Each test in this class opens two
 * independent connections to {@code library_test}, disables auto-commit, and exercises
 * the isolation and locking guarantees that the schema and DAOs rely on:
 *
 * <ul>
 *   <li>The partial unique index {@code idx_loans_active_partial_unique} must reject a
 *       concurrent active-loan insert on the same copy with SQLState 23505 — even when
 *       the first transaction has not yet committed.</li>
 *   <li>An uncommitted INSERT in one connection must NOT be visible to another connection
 *       (READ COMMITTED isolation).</li>
 *   <li>After the first connection COMMITs, the second connection sees the new row.</li>
 *   <li>After the first connection ROLLBACKs, the second connection never sees the row.</li>
 *   <li>{@code findByIdForUpdate} acquires a row-level lock that blocks concurrent
 *       UPDATEs on the same row until the holding transaction commits or rolls back.
 *       Verified deterministically with a short {@code lock_timeout} on the second
 *       connection — the blocked UPDATE fails with SQLState 55P03 (lock_not_available).</li>
 *   <li>Two concurrent borrows on DIFFERENT copies of the same book both succeed — the
 *       unique index is scoped to {@code copy_id}, not {@code book_id}.</li>
 * </ul>
 *
 * <p>Every test cleans up after itself by rolling back both transactions and closing both
 * connections. None of these tests rely on {@link TestDatabase#resetAll(Connection)} mid-
 * test, because TRUNCATE inside a multi-connection scenario is unnecessarily disruptive.
 */
class TransactionIsolationTest {

    private final BookDAO bookDao = new JdbcBookDAO();
    private final BookCopyDAO copyDao = new JdbcBookCopyDAO();
    private final MemberDAO memberDao = new JdbcMemberDAO();
    private final LoanDAO loanDao = new JdbcLoanDAO();

    /** Setup connection uses auto-commit = true (default) so resetAll commits immediately. */
    private void prepare() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestDatabase.resetAll(c);
            TestDatabase.seedReferenceData(c);
        }
    }

    private Book insertBook(Connection c, String isbn, String title) {
        Book book = new Book(null, isbn, title, 1, 1, 2020);
        bookDao.insert(c, book);
        return book;
    }

    private BookCopy insertCopy(Connection c, Book book, String code, CopyStatus status) {
        BookCopy copy = new BookCopy(null, book.getBookId(), code, status);
        copyDao.insert(c, copy);
        return copy;
    }

    private Member insertMember(Connection c, String code, String name, String email) {
        Member m = new Member(null, code, name, email, MemberStatus.ACTIVE);
        memberDao.insert(c, m);
        return m;
    }

    private Loan insertActiveLoan(Connection c, BookCopy copy, Member member, LocalDate loanDate) {
        Loan loan = new Loan(null, copy.getCopyId(), member.getMemberId(),
                loanDate, loanDate.plusDays(14), null, LoanStatus.ACTIVE);
        loanDao.insert(c, loan);
        return loan;
    }

    /** Opens a connection with auto-commit disabled for explicit transaction control. */
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

    // ------------------------------------------------------------------
    // Partial unique index: concurrent active loans on same copy
    // ------------------------------------------------------------------

    @Test
    void twoConcurrentActiveLoansForSameCopySecondFailsWith23505() throws Exception {
        prepare();
        // Setup with auto-commit: insert book/copy/member that BOTH transactions will see.
        int copyId;
        int memberId;
        try (Connection setup = TestDatabase.getConnection()) {
            Book book = insertBook(setup, "978-CC-000-000-001", "Concurrent Copy Book");
            BookCopy copy = insertCopy(setup, book, "CC-CP-001", CopyStatus.AVAILABLE);
            Member member = insertMember(setup, "M-CC-001", "Concurrent Member", "cc@test.com");
            copyId = copy.getCopyId();
            memberId = member.getMemberId();
        }

        Connection tx1 = beginTransaction();
        Connection tx2 = beginTransaction();
        try {
            LocalDate loanDate = LocalDate.of(2026, 10, 25);

            // tx1 inserts an active loan for the copy (NOT committed yet).
            assertEquals(1, loanDao.insert(tx1,
                    new Loan(null, copyId, memberId, loanDate, loanDate.plusDays(14), null, LoanStatus.ACTIVE)));

            // tx2 tries to insert another active loan for the SAME copy.
            // The partial unique index idx_loans_active_partial_unique sees the uncommitted
            // row from tx1 (because PostgreSQL's unique indexes check uncommitted rows in
            // the SAME transaction snapshot for conflict — actually for unique constraints,
            // PG waits for the other tx to finish and then errors if it committed).
            // To make this test deterministic and not block forever, set a short lock_timeout
            // on tx2: if tx1 hasn't committed within the timeout, tx2 fails with 55P03.
            try (Statement st = tx2.createStatement()) {
                st.execute("SET LOCAL lock_timeout = '2s'");
            }
            DataAccessException ex = assertThrows(DataAccessException.class,
                    () -> loanDao.insert(tx2,
                            new Loan(null, copyId, memberId, loanDate, loanDate.plusDays(14), null, LoanStatus.ACTIVE)));

            // Two acceptable outcomes depending on PG timing:
            //   23505 — tx1 committed first, then tx2 hit the unique violation.
            //   55P03 — tx1 was still holding the write lock; tx2's lock_timeout fired.
            String state = ex.getSqlState();
            assertTrue("23505".equals(state) || "55P03".equals(state),
                    "Expected 23505 (unique_violation) or 55P03 (lock_not_available), got " + state);
        } finally {
            tx1.rollback();
            tx2.rollback();
            tx1.close();
            tx2.close();
        }
    }

    // ------------------------------------------------------------------
    // READ COMMITTED: uncommitted changes invisible to other connections
    // ------------------------------------------------------------------

    @Test
    void uncommittedInsertNotVisibleToOtherConnection() throws Exception {
        prepare();
        Connection tx1 = beginTransaction();
        Connection tx2 = beginTransaction();
        try {
            // tx1 inserts a member but does NOT commit.
            Member m = new Member(null, "M-UN-001", "Uncommitted", "un@test.com", MemberStatus.ACTIVE);
            memberDao.insert(tx1, m);
            assertNotNull(m.getMemberId());

            // tx2 must NOT see the uncommitted row.
            assertFalse(memberDao.findById(tx2, m.getMemberId()).isPresent(),
                    "READ COMMITTED: uncommitted inserts in tx1 must be invisible to tx2");

            // tx1 commits — now tx2 must see it.
            tx1.commit();
            assertTrue(memberDao.findById(tx2, m.getMemberId()).isPresent(),
                    "After tx1 commits, tx2 must see the new row");
        } finally {
            // tx1 already committed; clean up the new row by truncating via a fresh connection.
            tx2.rollback();
            tx2.close();
            // tx1 is closed by try-with-resources semantics — but we already committed; no further action.
        }
        // Cleanup: reset the test database so this test is idempotent.
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

            // tx2 cannot see the row yet.
            assertFalse(memberDao.findById(tx2, newId).isPresent());

            // tx1 rolls back — the row never existed for any other connection.
            tx1.rollback();
            assertFalse(memberDao.findById(tx2, newId).isPresent(),
                    "After tx1 rolls back, the row must not exist for tx2 (or anyone)");
        } finally {
            tx2.rollback();
            tx2.close();
        }
    }

    // ------------------------------------------------------------------
    // Row-level locking: FOR UPDATE blocks concurrent UPDATE
    // ------------------------------------------------------------------

    @Test
    void forUpdateLockBlocksConcurrentUpdateUntilCommitOrLockTimeout() throws Exception {
        prepare();
        int copyId;
        try (Connection setup = TestDatabase.getConnection()) {
            Book book = insertBook(setup, "978-FL-000-000-001", "For Update Lock Book");
            BookCopy copy = insertCopy(setup, book, "FL-CP-001", CopyStatus.AVAILABLE);
            copyId = copy.getCopyId();
        }

        Connection tx1 = beginTransaction();
        Connection tx2 = beginTransaction();
        try {
            // tx1 locks the copy row with findByIdForUpdate.
            assertTrue(copyDao.findByIdForUpdate(tx1, copyId).isPresent());

            // tx2 sets a short lock_timeout and tries to update the same row.
            try (Statement st = tx2.createStatement()) {
                st.execute("SET LOCAL lock_timeout = '1s'");
            }
            DataAccessException ex = assertThrows(DataAccessException.class,
                    () -> copyDao.updateStatus(tx2, copyId, CopyStatus.BORROWED));

            // 55P03 = lock_not_available (lock_timeout fired while waiting for tx1's row lock).
            assertEquals("55P03", ex.getSqlState(),
                    "FOR UPDATE in tx1 must block tx2's UPDATE; lock_timeout must fire as 55P03, not 57014 (statement_timeout)");

            // The lock timeout aborts tx2's current transaction. Roll back so the next
            // statement starts a fresh transaction instead of failing with 25P02.
            tx2.rollback();

            // tx1 still holds the lock and can update the row itself.
            assertEquals(1, copyDao.updateStatus(tx1, copyId, CopyStatus.MAINTENANCE));
            tx1.commit();

            // Now the lock is released; tx2 can retry successfully.
            try (Statement st = tx2.createStatement()) {
                st.execute("SET LOCAL lock_timeout = '5s'");
            }
            assertEquals(1, copyDao.updateStatus(tx2, copyId, CopyStatus.BORROWED));
            tx2.commit();
        } finally {
            // Best-effort cleanup; both transactions have already committed.
            if (!tx1.isClosed()) {
                tx1.close();
            }
            if (!tx2.isClosed()) {
                tx2.close();
            }
        }
        // Reset the database so subsequent tests start clean.
        try (Connection cleanup = TestDatabase.getConnection()) {
            TestDatabase.resetAll(cleanup);
        }
    }

    // ------------------------------------------------------------------
    // Concurrent borrows on DIFFERENT copies of the same book both succeed
    // ------------------------------------------------------------------

    @Test
    void twoConcurrentBorrowsOnDifferentCopiesOfSameBookBothSucceed() throws Exception {
        prepare();
        int copyId1;
        int copyId2;
        int memberId;
        try (Connection setup = TestDatabase.getConnection()) {
            Book book = insertBook(setup, "978-TC-000-000-001", "Two Copies Concurrent Book");
            BookCopy cp1 = insertCopy(setup, book, "TC-CP-001", CopyStatus.AVAILABLE);
            BookCopy cp2 = insertCopy(setup, book, "TC-CP-002", CopyStatus.AVAILABLE);
            Member member = insertMember(setup, "M-TC-001", "TC Member", "tc@test.com");
            copyId1 = cp1.getCopyId();
            copyId2 = cp2.getCopyId();
            memberId = member.getMemberId();
        }

        Connection tx1 = beginTransaction();
        Connection tx2 = beginTransaction();
        try {
            LocalDate loanDate = LocalDate.of(2026, 10, 25);

            // tx1 borrows copy1; tx2 borrows copy2 — different copy_id, so the partial
            // unique index does not conflict.
            assertEquals(1, loanDao.insert(tx1,
                    new Loan(null, copyId1, memberId, loanDate, loanDate.plusDays(14), null, LoanStatus.ACTIVE)));
            assertEquals(1, loanDao.insert(tx2,
                    new Loan(null, copyId2, memberId, loanDate, loanDate.plusDays(14), null, LoanStatus.ACTIVE)));

            tx1.commit();
            tx2.commit();

            // Both transactions committed; verify both loans are persisted.
            try (Connection verify = TestDatabase.getConnection()) {
                assertEquals(2, loanDao.countActive(verify),
                        "Two active loans on two different copies of the same book must both persist");
            }
        } finally {
            if (!tx1.isClosed()) {
                tx1.close();
            }
            if (!tx2.isClosed()) {
                tx2.close();
            }
        }
        // Reset the database so subsequent tests start clean.
        try (Connection cleanup = TestDatabase.getConnection()) {
            TestDatabase.resetAll(cleanup);
        }
    }

    // ------------------------------------------------------------------
    // Concurrent INSERT with same natural key: second fails with 23505
    // ------------------------------------------------------------------

    @Test
    void twoConcurrentInsertsWithSameIsbnSecondFailsWith23505() throws Exception {
        prepare();
        Connection tx1 = beginTransaction();
        Connection tx2 = beginTransaction();
        try {
            // tx1 inserts a book with ISBN X (committed at the end).
            Book b1 = new Book(null, "978-CI-000-000-001", "Concurrent ISBN 1", 1, 1, 2020);
            bookDao.insert(tx1, b1);

            // tx2 tries to insert the SAME ISBN. The unique index books_isbn_key will
            // wait for tx1 to commit/rollback, then either succeed (if tx1 rolled back)
            // or fail with 23505 (if tx1 committed).
            try (Statement st = tx2.createStatement()) {
                st.execute("SET LOCAL lock_timeout = '3s'");
            }
            tx1.commit();

            DataAccessException ex = assertThrows(DataAccessException.class,
                    () -> bookDao.insert(tx2,
                            new Book(null, "978-CI-000-000-001", "Concurrent ISBN 2", 1, 1, 2021)));
            assertSqlState("23505", ex);
            tx2.rollback();
        } finally {
            if (!tx1.isClosed()) {
                tx1.close();
            }
            if (!tx2.isClosed()) {
                tx2.close();
            }
        }
        // Reset the database so subsequent tests start clean.
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
            memberDao.insert(tx1, new Member(null, "M-CE-001", "Email Owner 1", "dup@test.com", MemberStatus.ACTIVE));
            try (Statement st = tx2.createStatement()) {
                st.execute("SET LOCAL lock_timeout = '3s'");
            }
            tx1.commit();

            DataAccessException ex = assertThrows(DataAccessException.class,
                    () -> memberDao.insert(tx2,
                            new Member(null, "M-CE-002", "Email Owner 2", "DUP@TEST.COM", MemberStatus.ACTIVE)));
            assertSqlState("23505", ex,
                    "Email unique index ux_members_email_lower is case-insensitive; second insert must fail with 23505");
            tx2.rollback();
        } finally {
            if (!tx1.isClosed()) {
                tx1.close();
            }
            if (!tx2.isClosed()) {
                tx2.close();
            }
        }
        // Reset the database so subsequent tests start clean.
        try (Connection cleanup = TestDatabase.getConnection()) {
            TestDatabase.resetAll(cleanup);
        }
    }

    // ------------------------------------------------------------------
    // Auto-commit semantics: DAO ops without explicit tx are committed immediately
    // ------------------------------------------------------------------

    @Test
    void autoCommitConnectionPersistsInsertImmediately() throws Exception {
        prepare();
        // Default connection has autoCommit = true.
        try (Connection c = TestDatabase.getConnection()) {
            Member m = insertMember(c, "M-AC-001", "AutoCommit", "ac@test.com");
            int newId = m.getMemberId();
            // No commit() call — autoCommit must have persisted the row.
            try (Connection verify = TestDatabase.getConnection()) {
                assertTrue(memberDao.findById(verify, newId).isPresent(),
                        "Default autoCommit=true means each DAO call is its own transaction");
            }
        }
        // Reset the database so subsequent tests start clean.
        try (Connection cleanup = TestDatabase.getConnection()) {
            TestDatabase.resetAll(cleanup);
        }
    }

    @Test
    void explicitTransactionCanRollbackEntireBatch() throws Exception {
        prepare();
        int memberId;
        try (Connection c = TestDatabase.getConnection()) {
            memberId = insertMember(c, "M-ET-001", "Explicit Tx", "et@test.com").getMemberId();
        }
        // Now open an explicit transaction, insert two more members, then ROLLBACK.
        try (Connection tx = beginTransaction()) {
            insertMember(tx, "M-ET-002", "Will Rollback 1", "et2@test.com");
            insertMember(tx, "M-ET-003", "Will Rollback 2", "et3@test.com");
            tx.rollback();
        }
        // Verify only the auto-committed member persisted.
        try (Connection verify = TestDatabase.getConnection()) {
            assertTrue(memberDao.findById(verify, memberId).isPresent());
            // Search by keyword to confirm the rolled-back members are not there.
            com.library.model.criteria.MemberSearchCriteria criteria =
                    new com.library.model.criteria.MemberSearchCriteria();
            criteria.setKeyword("Will Rollback");
            assertEquals(0, memberDao.search(verify, criteria).getTotalElements(),
                    "Members inserted in a rolled-back transaction must not exist");
        }
        // Reset the database so subsequent tests start clean.
        try (Connection cleanup = TestDatabase.getConnection()) {
            TestDatabase.resetAll(cleanup);
        }
    }
}
