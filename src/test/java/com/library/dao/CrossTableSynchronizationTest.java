package com.library.dao;

import com.library.dao.jdbc.JdbcBookAuthorDAO;
import com.library.dao.jdbc.JdbcBookCopyDAO;
import com.library.dao.jdbc.JdbcBookDAO;
import com.library.dao.jdbc.JdbcDashboardDAO;
import com.library.dao.jdbc.JdbcLoanDAO;
import com.library.dao.jdbc.JdbcMemberDAO;
import com.library.dao.support.TestDatabase;
import com.library.dao.support.TestFixtures;
import com.library.exception.DataAccessException;
import com.library.model.Book;
import com.library.model.BookCopy;
import com.library.model.DashboardStats;
import com.library.model.Loan;
import com.library.model.Member;
import com.library.model.enums.CopyStatus;
import com.library.config.AppConfig;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cross-table synchronization tests.
 *
 * <p>This class only owns invariants that span tables: manual copy-status synchronization,
 * CASCADE/RESTRICT behavior, and orphan detection. Per-DAO projections (dashboard counts,
 * active-loan counts, available copies, author replacement, loan history) are tested in the
 * DAO class that owns them.
 */
class CrossTableSynchronizationTest {

    private final BookDAO bookDao = new JdbcBookDAO();
    private final BookCopyDAO copyDao = new JdbcBookCopyDAO();
    private final BookAuthorDAO bookAuthorDao = new JdbcBookAuthorDAO();
    private final MemberDAO memberDao = new JdbcMemberDAO();
    private final LoanDAO loanDao = new JdbcLoanDAO();
    private final DashboardDAO dashboardDao = new JdbcDashboardDAO();

    /**
     * E01 gap: the DAO layer does not automatically flip {@code book_copies.status} on loan
     * insert or return. The Service layer must do it explicitly. The test also proves the
     * manual pattern keeps the tables consistent.
     */
    @Test
    void copyStatusSyncIsManualNotAutomatic() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            Book book = TestFixtures.insertBook(c, "978-SYNC-000-001", "Sync Book");
            BookCopy copy = TestFixtures.insertCopy(c, book, "SYNC-CP-001", CopyStatus.AVAILABLE);
            Member member = TestFixtures.insertMember(c, "M-SYNC-001", "Member Sync", "sync@test.com");

            LocalDate loanDate = LocalDate.of(2026, 10, 1);
            Loan loan = TestFixtures.insertActiveLoan(c, copy, member, loanDate);
            assertEquals(CopyStatus.AVAILABLE,
                    copyDao.findByIdForUpdate(c, copy.getCopyId()).orElseThrow().getStatus(),
                    "E01 gap: copy.status stays AVAILABLE after loan insert");

            assertEquals(1, copyDao.updateStatus(c, copy.getCopyId(), CopyStatus.BORROWED));
            assertEquals(CopyStatus.BORROWED,
                    copyDao.findByIdForUpdate(c, copy.getCopyId()).orElseThrow().getStatus(),
                    "Manual sync after borrow must set BORROWED");

            loanDao.markReturned(c, loan.getLoanId(), loanDate.plusDays(5));
            assertEquals(CopyStatus.BORROWED,
                    copyDao.findByIdForUpdate(c, copy.getCopyId()).orElseThrow().getStatus(),
                    "E01 gap: copy.status stays BORROWED after markReturned");

            assertEquals(1, copyDao.updateStatus(c, copy.getCopyId(), CopyStatus.AVAILABLE));
            assertEquals(CopyStatus.AVAILABLE,
                    copyDao.findByIdForUpdate(c, copy.getCopyId()).orElseThrow().getStatus(),
                    "Manual sync after return must set AVAILABLE");

            DashboardStats stats = dashboardDao.getStats(c, LocalDate.of(2026, 10, 25));
            assertEquals(1, stats.getAvailableCopies());
        }
    }

    /** Deleting a book removes its author links by CASCADE but preserves author rows. */
    @Test
    void cascadeDeleteBookRemovesLinksAndPreservesAuthors() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            Book book = TestFixtures.insertBook(c, "978-CD-000-000-001", "Cascade Book");
            bookAuthorDao.replaceForBook(c, book.getBookId(), List.of(1, 2, 3));
            assertEquals(3, bookAuthorDao.findByBookId(c, book.getBookId()).size());

            long authorsBefore = rawCount(c, "SELECT COUNT(*) FROM authors");

            bookDao.delete(c, book.getBookId());

            assertAll(
                    () -> assertEquals(0,
                            rawCount(c, "SELECT COUNT(*) FROM book_authors WHERE book_id = ?",
                                    book.getBookId()),
                            "ON DELETE CASCADE must remove every book_authors link row"),
                    () -> assertFalse(bookDao.findById(c, book.getBookId()).isPresent()),
                    () -> assertEquals(authorsBefore,
                            rawCount(c, "SELECT COUNT(*) FROM authors"),
                            "Author rows themselves must be preserved"));
        }
    }

    /** Publisher, category, and author rows referenced by a book are protected by RESTRICT. */
    @Test
    void restrictDeleteReferenceRowsWithBooksFails() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            Book book = TestFixtures.insertBook(c, "978-REST-000-001", "Referenced Book");
            bookAuthorDao.replaceForBook(c, book.getBookId(), List.of(1));

            assertAll(
                    () -> assertEquals(AppConfig.SQLSTATE_FOREIGN_KEY_VIOLATION,
                            sqlStateOf(c, "DELETE FROM publishers WHERE publisher_id = 1")),
                    () -> assertEquals(AppConfig.SQLSTATE_FOREIGN_KEY_VIOLATION,
                            sqlStateOf(c, "DELETE FROM categories WHERE category_id = 1")),
                    () -> assertEquals(AppConfig.SQLSTATE_FOREIGN_KEY_VIOLATION,
                            sqlStateOf(c, "DELETE FROM authors WHERE author_id = 1")));
        }
    }

    /**
     * A book with copies, a copy with loan history, and a member with loan history are all
     * protected. RESTRICT applies to returned history too, not only active loans.
     */
    @Test
    void restrictDeleteRowsWithDependentChildrenFails() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            Book book = TestFixtures.insertBook(c, "978-RL-000-000-001", "Copy With Loan Book");
            BookCopy copy = TestFixtures.insertCopy(c, book, "RL-CP-001", CopyStatus.AVAILABLE);
            Member member = TestFixtures.insertMember(c, "M-RL-001", "Member RL", "rl@test.com");

            LocalDate loanDate = LocalDate.of(2026, 7, 1);
            Loan loan = TestFixtures.insertActiveLoan(c, copy, member, loanDate);

            DataAccessException bookError = assertThrows(DataAccessException.class,
                    () -> bookDao.delete(c, book.getBookId()));
            assertEquals(AppConfig.SQLSTATE_FOREIGN_KEY_VIOLATION, bookError.getSqlState());
            assertTrue(bookDao.findById(c, book.getBookId()).isPresent());

            assertAll(
                    () -> assertEquals(AppConfig.SQLSTATE_FOREIGN_KEY_VIOLATION,
                            sqlStateOf(c, "DELETE FROM book_copies WHERE copy_id = " + copy.getCopyId())),
                    () -> assertEquals(AppConfig.SQLSTATE_FOREIGN_KEY_VIOLATION,
                            sqlStateOf(c, "DELETE FROM members WHERE member_id = " + member.getMemberId())));

            loanDao.markReturned(c, loan.getLoanId(), loanDate.plusDays(3));

            assertAll(
                    () -> assertEquals(AppConfig.SQLSTATE_FOREIGN_KEY_VIOLATION,
                            sqlStateOf(c, "DELETE FROM book_copies WHERE copy_id = " + copy.getCopyId()),
                            "Loan history still protects the copy after return"),
                    () -> assertEquals(AppConfig.SQLSTATE_FOREIGN_KEY_VIOLATION,
                            sqlStateOf(c, "DELETE FROM members WHERE member_id = " + member.getMemberId()),
                            "Loan history still protects the member after return"));
        }
    }

    /** No orphan rows after a mix of inserts, returns, and re-borrows. */
    @Test
    void noOrphanRowsAfterMixedOperations() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            Book book = TestFixtures.insertBook(c, "978-OR-000-000-001", "Orphan Test Book");
            BookCopy copy = TestFixtures.insertCopy(c, book, "OR-CP-001", CopyStatus.AVAILABLE);
            Member member = TestFixtures.insertMember(c, "M-OR-001", "Orphan Member", "or@test.com");

            LocalDate today = LocalDate.of(2026, 10, 25);
            Loan loan = TestFixtures.insertActiveLoan(c, copy, member, today.minusDays(2));
            loanDao.markReturned(c, loan.getLoanId(), today);
            TestFixtures.insertActiveLoan(c, copy, member, today);

            String orphanSql =
                    "SELECT "
                            + "(SELECT COUNT(*) FROM books b LEFT JOIN publishers p ON p.publisher_id = b.publisher_id WHERE p.publisher_id IS NULL) + "
                            + "(SELECT COUNT(*) FROM books b LEFT JOIN categories c ON c.category_id = b.category_id WHERE c.category_id IS NULL) + "
                            + "(SELECT COUNT(*) FROM book_copies bc LEFT JOIN books b ON b.book_id = bc.book_id WHERE b.book_id IS NULL) + "
                            + "(SELECT COUNT(*) FROM loans l LEFT JOIN book_copies bc ON bc.copy_id = l.copy_id WHERE bc.copy_id IS NULL) + "
                            + "(SELECT COUNT(*) FROM loans l LEFT JOIN members m ON m.member_id = l.member_id WHERE m.member_id IS NULL) + "
                            + "(SELECT COUNT(*) FROM book_authors ba LEFT JOIN books b ON b.book_id = ba.book_id WHERE b.book_id IS NULL) + "
                            + "(SELECT COUNT(*) FROM book_authors ba LEFT JOIN authors a ON a.author_id = ba.author_id WHERE a.author_id IS NULL)";
            assertEquals(0, rawCount(c, orphanSql),
                    "No orphan rows may exist after a mix of inserts, returns, and re-borrows");
        }
    }

    private static String sqlStateOf(Connection c, String sql) {
        SQLException error = assertThrows(SQLException.class, () -> {
            try (Statement st = c.createStatement()) {
                st.execute(sql);
            }
        });
        return error.getSQLState();
    }

    private long rawCount(Connection c, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        }
    }
}
