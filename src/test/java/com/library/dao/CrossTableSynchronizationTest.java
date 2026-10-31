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
import com.library.model.Author;
import com.library.model.Book;
import com.library.model.BookCopy;
import com.library.model.DashboardStats;
import com.library.model.Loan;
import com.library.model.Member;
import com.library.model.Page;
import com.library.model.criteria.MemberSearchCriteria;
import com.library.model.enums.CopyStatus;
import com.library.model.enums.LoanStatus;
import com.library.model.enums.MemberStatus;
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
 * <p>Where {@link DataConsistencyTest} verifies schema-level constraints on a single table,
 * this class verifies that <em>derived</em> state stays in sync with <em>authoritative</em>
 * state across tables:
 *
 * <ul>
 *   <li>{@link JdbcDashboardDAO} counts must equal direct {@code SELECT COUNT(*)} on each
 *       underlying table after every kind of mutation.</li>
 *   <li>{@code Member.activeLoanCount} (subquery in JdbcMemberDAO) must equal the actual
 *       number of {@code loans} rows with {@code return_date IS NULL} for that member,
 *       and must update as loans are returned.</li>
 *   <li>{@code Book.availableCopies} (subquery in JdbcBookDAO) must equal the actual number
 *       of {@code book_copies} rows with {@code status = 'AVAILABLE'} for that book.</li>
 *   <li>{@link LoanDAO#existsByBookId} must remain true for any book that has ever had a
 *       loan, including fully-returned history.</li>
 *   <li>{@link JdbcBookAuthorDAO#replaceForBook} must atomically remove old links and insert
 *       the new set (no leftovers, no partial state).</li>
 *   <li>ON DELETE CASCADE on {@code book_authors(book_id)} and ON DELETE RESTRICT on every
 *       other FK must behave as declared.</li>
 * </ul>
 *
 * <p>This class also documents the <strong>E01 synchronization gap</strong> noted in
 * README Â§6: {@code JdbcLoanDAO} does NOT automatically update {@code book_copies.status}
 * when a loan is created or returned. The schema does not enforce it either. The two
 * related tests below assert the <em>current</em> (gap) behavior so that any future fix
 * â€” whether a trigger, an explicit DAO call, or a service-layer orchestration â€” will
 * surface as a failing test that needs updating rather than a silent change.
 */
class CrossTableSynchronizationTest {

    private final BookDAO bookDao = new JdbcBookDAO();
    private final BookCopyDAO copyDao = new JdbcBookCopyDAO();
    private final BookAuthorDAO bookAuthorDao = new JdbcBookAuthorDAO();
    private final MemberDAO memberDao = new JdbcMemberDAO();
    private final LoanDAO loanDao = new JdbcLoanDAO();
    private final DashboardDAO dashboardDao = new JdbcDashboardDAO();

    private long rawCount(Connection c, String sql) throws SQLException {
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getLong(1) : 0;
        }
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

    // ------------------------------------------------------------------
    // E01 gap documentation: book_copies.status is NOT auto-synced with loans
    // ------------------------------------------------------------------

    @Test
    void bookCopyStatusIsNotAutomaticallySyncedOnLoanInsert() throws Exception {
        // E01 gap (README Â§6): The DAO layer does NOT automatically flip book_copies.status
        // to BORROWED when a loan is created. The schema has no trigger to do it either.
        // The Service layer (Person 2, not yet implemented) is expected to call
        // copyDao.updateStatus(BORROWED) explicitly alongside loanDao.insert.
        // This test asserts the CURRENT gap behavior; if a future fix adds auto-sync,
        // the final assertion must change to CopyStatus.BORROWED.
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            Book book = TestFixtures.insertBook(c, "978-G1-000-000-001", "Gap Sync Insert");
            BookCopy copy = TestFixtures.insertCopy(c, book, "G1-CP-001", CopyStatus.AVAILABLE);
            Member member = TestFixtures.insertMember(c, "M-G1-001", "Member G1", "g1@test.com");

            TestFixtures.insertActiveLoan(c, copy, member, LocalDate.of(2026, 10, 25));

            // Reload the copy directly from the table to bypass any DAO caching.
            BookCopy reloaded = copyDao.findByIdForUpdate(c, copy.getCopyId()).orElseThrow();
            assertEquals(CopyStatus.AVAILABLE, reloaded.getStatus(),
                    "E01 gap: copy.status stays AVAILABLE after loan is inserted â€” DAO does not auto-sync");
        }
    }

    @Test
    void bookCopyStatusIsNotAutomaticallySyncedOnMarkReturned() throws Exception {
        // E01 gap, second half: markReturned also does NOT flip status back to AVAILABLE.
        // If a copy starts as BORROWED (because the Service layer set it) and the loan is
        // returned, the copy will STAY BORROWED until the Service explicitly calls
        // updateStatus(AVAILABLE).
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            Book book = TestFixtures.insertBook(c, "978-G2-000-000-001", "Gap Sync Return");
            BookCopy copy = TestFixtures.insertCopy(c, book, "G2-CP-001", CopyStatus.BORROWED);
            Member member = TestFixtures.insertMember(c, "M-G2-001", "Member G2", "g2@test.com");

            LocalDate loanDate = LocalDate.of(2026, 10, 1);
            Loan loan = TestFixtures.insertActiveLoan(c, copy, member, loanDate);
            loanDao.markReturned(c, loan.getLoanId(), loanDate.plusDays(5));

            BookCopy reloaded = copyDao.findByIdForUpdate(c, copy.getCopyId()).orElseThrow();
            assertEquals(CopyStatus.BORROWED, reloaded.getStatus(),
                    "E01 gap: copy.status stays BORROWED after markReturned â€” DAO does not auto-sync");
        }
    }

    @Test
    void manualSyncKeepsCopyStatusConsistentWithLoanState() throws Exception {
        // The intended usage pattern: the Service layer (Person 2) is responsible for
        // calling both DAOs in the right order inside one transaction. This test shows
        // that the manual sync DOES keep the two tables consistent â€” the gap is the lack
        // of automatic enforcement, not a fundamental bug in the DAOs.
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            Book book = TestFixtures.insertBook(c, "978-MS-000-000-001", "Manual Sync");
            BookCopy copy = TestFixtures.insertCopy(c, book, "MS-CP-001", CopyStatus.AVAILABLE);
            Member member = TestFixtures.insertMember(c, "M-MS-001", "Member MS", "ms@test.com");

            // Borrow: insert loan + flip status to BORROWED.
            LocalDate loanDate = LocalDate.of(2026, 10, 1);
            Loan loan = TestFixtures.insertActiveLoan(c, copy, member, loanDate);
            assertEquals(1, copyDao.updateStatus(c, copy.getCopyId(), CopyStatus.BORROWED));
            assertEquals(CopyStatus.BORROWED,
                    copyDao.findByIdForUpdate(c, copy.getCopyId()).orElseThrow().getStatus());

            // Return: markReturned + flip status back to AVAILABLE.
            loanDao.markReturned(c, loan.getLoanId(), loanDate.plusDays(5));
            assertEquals(1, copyDao.updateStatus(c, copy.getCopyId(), CopyStatus.AVAILABLE));
            assertEquals(CopyStatus.AVAILABLE,
                    copyDao.findByIdForUpdate(c, copy.getCopyId()).orElseThrow().getStatus());

            // Now the dashboard "available copies" count reflects reality.
            DashboardStats stats = dashboardDao.getStats(c, LocalDate.of(2026, 10, 25));
            assertEquals(1, stats.getAvailableCopies());
        }
    }

    // ------------------------------------------------------------------
    // Dashboard synchronization: counts match underlying tables after mutations
    // ------------------------------------------------------------------

    @Test
    void dashboardCountsMatchUnderlyingTablesOnEmptyDatabase() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            // seedReferenceData adds 3 publishers, 4 categories, 4 authors â€” no books/copies/members/loans.
            DashboardStats stats = dashboardDao.getStats(c, LocalDate.of(2026, 10, 25));
            assertEquals(rawCount(c, "SELECT COUNT(*) FROM books"), stats.getTotalBooks());
            assertEquals(rawCount(c, "SELECT COUNT(*) FROM members"), stats.getTotalMembers());
            assertEquals(rawCount(c, "SELECT COUNT(*) FROM book_copies"), stats.getTotalCopies());
            assertEquals(rawCount(c, "SELECT COUNT(*) FROM loans WHERE return_date IS NULL"),
                    stats.getActiveLoans());
            assertEquals(rawCount(c, "SELECT COUNT(*) FROM book_copies WHERE status = 'AVAILABLE'"),
                    stats.getAvailableCopies());
            assertEquals(rawCount(c, "SELECT COUNT(*) FROM loans WHERE return_date IS NULL AND due_date < ?",
                    LocalDate.of(2026, 10, 25)), stats.getOverdueLoans());

            assertEquals(0, stats.getTotalBooks());
            assertEquals(0, stats.getTotalMembers());
            assertEquals(0, stats.getTotalCopies());
            assertEquals(0, stats.getActiveLoans());
            assertEquals(0, stats.getOverdueLoans());
            assertEquals(0, stats.getAvailableCopies());
        }
    }

    @Test
    void dashboardCountsStayInSyncAfterEachMutation() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            LocalDate today = LocalDate.of(2026, 10, 25);

            // Initial: empty.
            DashboardStats s0 = dashboardDao.getStats(c, today);
            assertEquals(0, s0.getTotalBooks());
            assertEquals(0, s0.getTotalMembers());
            assertEquals(0, s0.getTotalCopies());
            assertEquals(0, s0.getActiveLoans());
            assertEquals(0, s0.getAvailableCopies());

            // Insert 1 book + 2 copies (1 AVAILABLE, 1 BORROWED).
            Book book = TestFixtures.insertBook(c, "978-DS-000-000-001", "Dashboard Sync Book");
            BookCopy avail = TestFixtures.insertCopy(c, book, "DS-CP-001", CopyStatus.AVAILABLE);
            BookCopy borrowed = TestFixtures.insertCopy(c, book, "DS-CP-002", CopyStatus.BORROWED);

            DashboardStats s1 = dashboardDao.getStats(c, today);
            assertEquals(1, s1.getTotalBooks());
            assertEquals(2, s1.getTotalCopies());
            assertEquals(1, s1.getAvailableCopies());
            assertEquals(0, s1.getActiveLoans(),
                    "A copy with status=BORROWED does NOT imply an active loan â€” the dashboard reads loans.return_date, not book_copies.status");

            // Insert 2 members (1 ACTIVE, 1 INACTIVE).
            Member active = TestFixtures.insertMember(c, "M-DS-001", "Active", "ds1@test.com");
            TestFixtures.insertMember(c, "M-DS-002", "Inactive", "ds2@test.com", MemberStatus.INACTIVE);

            DashboardStats s2 = dashboardDao.getStats(c, today);
            assertEquals(2, s2.getTotalMembers(),
                    "Dashboard totalMembers includes both ACTIVE and INACTIVE members");

            // Create a loan against the BORROWED-tagged copy.
            Loan loan = TestFixtures.insertActiveLoan(c, borrowed, active, today.minusDays(20));

            DashboardStats s3 = dashboardDao.getStats(c, today);
            assertEquals(1, s3.getActiveLoans());
            assertEquals(1, s3.getOverdueLoans(),
                    "Loan with due_date < today must be counted as overdue");
            assertEquals(1, s3.getAvailableCopies(),
                    "availableCopies counts book_copies.status='AVAILABLE', independent of loans");

            // Return the loan â€” active and overdue counts must drop.
            loanDao.markReturned(c, loan.getLoanId(), today);

            DashboardStats s4 = dashboardDao.getStats(c, today);
            assertEquals(0, s4.getActiveLoans());
            assertEquals(0, s4.getOverdueLoans());
            assertEquals(1, s4.getAvailableCopies(),
                    "Returning a loan does not change book_copies.status â€” only an explicit updateStatus call does");
        }
    }

    // ------------------------------------------------------------------
    // Member.activeLoanCount synchronization
    // ------------------------------------------------------------------

    @Test
    void memberActiveLoanCountMatchesActualCount() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            Member member = TestFixtures.insertMember(c, "M-AC-001", "Active Count Member", "ac@test.com");
            Book book = TestFixtures.insertBook(c, "978-AC-000-000-001", "AC Book");
            BookCopy cp1 = TestFixtures.insertCopy(c, book, "AC-CP-001", CopyStatus.AVAILABLE);
            BookCopy cp2 = TestFixtures.insertCopy(c, book, "AC-CP-002", CopyStatus.AVAILABLE);
            BookCopy cp3 = TestFixtures.insertCopy(c, book, "AC-CP-003", CopyStatus.AVAILABLE);

            LocalDate today = LocalDate.of(2026, 10, 25);
            Loan l1 = TestFixtures.insertActiveLoan(c, cp1, member, today);
            Loan l2 = TestFixtures.insertActiveLoan(c, cp2, member, today);
            TestFixtures.insertActiveLoan(c, cp3, member, today);

            // 3 active loans via search projection.
            MemberSearchCriteria criteria = new MemberSearchCriteria();
            criteria.setKeyword("Active Count Member");
            Member found = memberDao.search(c, criteria).getContent().get(0);
            assertEquals(3, found.getActiveLoanCount());

            // Cross-check with raw COUNT(*) on the loans table.
            long raw = rawCount(c,
                    "SELECT COUNT(*) FROM loans WHERE member_id = ? AND return_date IS NULL",
                    member.getMemberId());
            assertEquals(raw, found.getActiveLoanCount());

            // Return one â€” count must drop to 2.
            loanDao.markReturned(c, l1.getLoanId(), today);
            Member afterOne = memberDao.findById(c, member.getMemberId()).orElseThrow();
            assertEquals(2, afterOne.getActiveLoanCount());

            // Return another â€” count must drop to 1.
            loanDao.markReturned(c, l2.getLoanId(), today);
            Member afterTwo = memberDao.findById(c, member.getMemberId()).orElseThrow();
            assertEquals(1, afterTwo.getActiveLoanCount());
        }
    }

    @Test
    void memberActiveLoanCountExcludesReturnedHistory() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            Member member = TestFixtures.insertMember(c, "M-RH-001", "Returned History Member", "rh@test.com");
            Book book = TestFixtures.insertBook(c, "978-RH-000-000-001", "RH Book");
            BookCopy copy = TestFixtures.insertCopy(c, book, "RH-CP-001", CopyStatus.AVAILABLE);

            // Borrow and return twice on the same copy.
            LocalDate d1 = LocalDate.of(2026, 1, 1);
            Loan l1 = TestFixtures.insertActiveLoan(c, copy, member, d1);
            loanDao.markReturned(c, l1.getLoanId(), d1.plusDays(5));

            LocalDate d2 = LocalDate.of(2026, 2, 1);
            Loan l2 = TestFixtures.insertActiveLoan(c, copy, member, d2);
            loanDao.markReturned(c, l2.getLoanId(), d2.plusDays(5));

            // 2 loans in history, 0 active.
            Member found = memberDao.findById(c, member.getMemberId()).orElseThrow();
            assertEquals(0, found.getActiveLoanCount(),
                    "activeLoanCount must reflect only return_date IS NULL, not total loan history");
        }
    }

    // ------------------------------------------------------------------
    // Book.availableCopies synchronization
    // ------------------------------------------------------------------

    @Test
    void bookAvailableCopiesMatchesActualCount() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            Book book = TestFixtures.insertBook(c, "978-AB-000-000-001", "Available Book");

            // 0 copies initially.
            assertEquals(0, bookDao.findById(c, book.getBookId()).orElseThrow().getAvailableCopies());

            BookCopy a1 = TestFixtures.insertCopy(c, book, "AB-CP-001", CopyStatus.AVAILABLE);
            BookCopy a2 = TestFixtures.insertCopy(c, book, "AB-CP-002", CopyStatus.AVAILABLE);
            TestFixtures.insertCopy(c, book, "AB-CP-003", CopyStatus.BORROWED);
            TestFixtures.insertCopy(c, book, "AB-CP-004", CopyStatus.MAINTENANCE);

            // 2 AVAILABLE copies.
            Book found = bookDao.findById(c, book.getBookId()).orElseThrow();
            assertEquals(2, found.getAvailableCopies());

            // Cross-check with raw COUNT(*).
            long raw = rawCount(c,
                    "SELECT COUNT(*) FROM book_copies WHERE book_id = ? AND status = 'AVAILABLE'",
                    book.getBookId());
            assertEquals(raw, (long) found.getAvailableCopies());

            // Flip one AVAILABLE â†’ BORROWED; count must drop.
            copyDao.updateStatus(c, a1.getCopyId(), CopyStatus.BORROWED);
            assertEquals(1, bookDao.findById(c, book.getBookId()).orElseThrow().getAvailableCopies());

            // Flip another AVAILABLE â†’ MAINTENANCE; count drops again.
            copyDao.updateStatus(c, a2.getCopyId(), CopyStatus.MAINTENANCE);
            assertEquals(0, bookDao.findById(c, book.getBookId()).orElseThrow().getAvailableCopies());

            // Restore one to AVAILABLE; count rises.
            copyDao.updateStatus(c, a1.getCopyId(), CopyStatus.AVAILABLE);
            assertEquals(1, bookDao.findById(c, book.getBookId()).orElseThrow().getAvailableCopies());
        }
    }

    // ------------------------------------------------------------------
    // existsByBookId: history preserved after return
    // ------------------------------------------------------------------

    @Test
    void existsByBookIdTrueAfterAllLoansReturned() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            Book book = TestFixtures.insertBook(c, "978-EB-000-000-001", "Exists Book");
            BookCopy copy = TestFixtures.insertCopy(c, book, "EB-CP-001", CopyStatus.AVAILABLE);
            Member member = TestFixtures.insertMember(c, "M-EB-001", "EB Member", "eb@test.com");

            LocalDate loanDate = LocalDate.of(2026, 9, 1);
            Loan loan = TestFixtures.insertActiveLoan(c, copy, member, loanDate);

            // Active loan â€” existsByBookId is true.
            assertTrue(loanDao.existsByBookId(c, book.getBookId()));

            // After return â€” still true (history is preserved).
            loanDao.markReturned(c, loan.getLoanId(), loanDate.plusDays(5));
            assertTrue(loanDao.existsByBookId(c, book.getBookId()),
                    "existsByBookId checks ANY loan history, including returned loans");
        }
    }

    @Test
    void existsByBookIdFalseForBookWithNoLoans() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            Book book = TestFixtures.insertBook(c, "978-NL-000-000-001", "No Loans Book");
            TestFixtures.insertCopy(c, book, "NL-CP-001", CopyStatus.AVAILABLE);

            assertFalse(loanDao.existsByBookId(c, book.getBookId()));
        }
    }

    // ------------------------------------------------------------------
    // book_authors replace semantics: no leftovers
    // ------------------------------------------------------------------

    @Test
    void replaceForBookRemovesOldLinksAndInsertsNewSet() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            Book book = TestFixtures.insertBook(c, "978-RP-000-000-001", "Replace Authors Book");

            // Initial: 2 authors (IDs 1 and 2 from seedReferenceData).
            bookAuthorDao.replaceForBook(c, book.getBookId(), List.of(1, 2));
            List<Author> first = bookAuthorDao.findByBookId(c, book.getBookId());
            assertEquals(2, first.size());

            // Replace with a different set of 1.
            bookAuthorDao.replaceForBook(c, book.getBookId(), List.of(3));
            List<Author> second = bookAuthorDao.findByBookId(c, book.getBookId());
            assertEquals(1, second.size());
            assertEquals("Robert C. Martin", second.get(0).getName(),
                    "replaceForBook must remove authors 1 and 2, not append 3 to the existing set");

            // Cross-check with raw COUNT(*).
            long raw = rawCount(c, "SELECT COUNT(*) FROM book_authors WHERE book_id = ?",
                    book.getBookId());
            assertEquals(1, raw);
        }
    }

    @Test
    void replaceForBookIsOrderIndependent() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            Book book1 = TestFixtures.insertBook(c, "978-O1-000-000-001", "Order Book 1");
            Book book2 = TestFixtures.insertBook(c, "978-O2-000-000-002", "Order Book 2");

            // Same author set, different order â€” result must be the same.
            bookAuthorDao.replaceForBook(c, book1.getBookId(), List.of(1, 2, 3));
            bookAuthorDao.replaceForBook(c, book2.getBookId(), List.of(3, 2, 1));

            List<Author> a1 = bookAuthorDao.findByBookId(c, book1.getBookId());
            List<Author> a2 = bookAuthorDao.findByBookId(c, book2.getBookId());
            assertEquals(3, a1.size());
            assertEquals(3, a2.size());
            // findByBookId orders by author_id, so both lists are identical.
            assertEquals(a1.get(0).getAuthorId(), a2.get(0).getAuthorId());
            assertEquals(a1.get(1).getAuthorId(), a2.get(1).getAuthorId());
            assertEquals(a1.get(2).getAuthorId(), a2.get(2).getAuthorId());
        }
    }

    // ------------------------------------------------------------------
    // CASCADE / RESTRICT delete behavior across tables
    // ------------------------------------------------------------------

    @Test
    void cascadeDeleteBookRemovesBookAuthorsLinks() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            Book book = TestFixtures.insertBook(c, "978-CD-000-000-001", "Cascade Book");
            bookAuthorDao.replaceForBook(c, book.getBookId(), List.of(1, 2, 3));
            assertEquals(3, bookAuthorDao.findByBookId(c, book.getBookId()).size());

            // Book has no copies â†’ delete succeeds â†’ book_authors rows are CASCADE-deleted.
            bookDao.delete(c, book.getBookId());

            long remaining = rawCount(c, "SELECT COUNT(*) FROM book_authors WHERE book_id = ?",
                    book.getBookId());
            assertEquals(0, remaining,
                    "ON DELETE CASCADE on book_authors(book_id) must remove all link rows when the book is deleted");
            assertFalse(bookDao.findById(c, book.getBookId()).isPresent());
        }
    }

    @Test
    void restrictDeletePublisherWithBooksFails() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            TestFixtures.insertBook(c, "978-RP-000-000-001", "Referenced Book");
            // Publisher 1 (Literature Publisher) is referenced by the book.
            // Raw JDBC bypasses the DAO wrapper, so the driver's SQLException is expected here.
            SQLException error = assertThrows(SQLException.class, () -> {
                try (Statement st = c.createStatement()) {
                    st.execute("DELETE FROM publishers WHERE publisher_id = 1");
                }
            });
            assertEquals(AppConfig.SQLSTATE_FOREIGN_KEY_VIOLATION, error.getSQLState());
        }
    }

    @Test
    void restrictDeleteCategoryWithBooksFails() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            TestFixtures.insertBook(c, "978-RC-000-000-001", "Referenced Cat Book");
            SQLException error = assertThrows(SQLException.class, () -> {
                try (Statement st = c.createStatement()) {
                    st.execute("DELETE FROM categories WHERE category_id = 1");
                }
            });
            assertEquals(AppConfig.SQLSTATE_FOREIGN_KEY_VIOLATION, error.getSQLState());
        }
    }

    @Test
    void restrictDeleteBookWithCopiesFails() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            Book book = TestFixtures.insertBook(c, "978-RB-000-000-001", "Book With Copies");
            TestFixtures.insertCopy(c, book, "RB-CP-001", CopyStatus.AVAILABLE);

            DataAccessException error = assertThrows(DataAccessException.class,
                    () -> bookDao.delete(c, book.getBookId()));
            assertEquals(AppConfig.SQLSTATE_FOREIGN_KEY_VIOLATION, error.getSqlState());
            // Book is still there.
            assertTrue(bookDao.findById(c, book.getBookId()).isPresent());
        }
    }

    @Test
    void restrictDeleteBookCopyWithLoanHistoryFails() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            Book book = TestFixtures.insertBook(c, "978-RL-000-000-001", "Copy With Loan Book");
            BookCopy copy = TestFixtures.insertCopy(c, book, "RL-CP-001", CopyStatus.AVAILABLE);
            Member member = TestFixtures.insertMember(c, "M-RL-001", "Member RL", "rl@test.com");

            LocalDate loanDate = LocalDate.of(2026, 7, 1);
            Loan loan = TestFixtures.insertActiveLoan(c, copy, member, loanDate);

            // Deleting the copy directly must fail because loan references it.
            // Raw JDBC bypasses the DAO wrapper, so the driver's SQLException is expected here.
            SQLException error = assertThrows(SQLException.class, () -> {
                try (Statement st = c.createStatement()) {
                    st.execute("DELETE FROM book_copies WHERE copy_id = " + copy.getCopyId());
                }
            });
            assertEquals(AppConfig.SQLSTATE_FOREIGN_KEY_VIOLATION, error.getSQLState());

            // Even after the loan is returned, the loan ROW still exists (history), so the
            // copy is still protected from deletion.
            loanDao.markReturned(c, loan.getLoanId(), loanDate.plusDays(3));
            SQLException stillRestricted = assertThrows(SQLException.class, () -> {
                try (Statement st = c.createStatement()) {
                    st.execute("DELETE FROM book_copies WHERE copy_id = " + copy.getCopyId());
                }
            });
            assertEquals(AppConfig.SQLSTATE_FOREIGN_KEY_VIOLATION, stillRestricted.getSQLState(),
                    "RESTRICT applies to ALL loans, not just active ones â€” loan history is preserved");
        }
    }

    @Test
    void restrictDeleteMemberWithLoanHistoryFails() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            Book book = TestFixtures.insertBook(c, "978-RM-000-000-001", "Member Restrict Book");
            BookCopy copy = TestFixtures.insertCopy(c, book, "RM-CP-001", CopyStatus.AVAILABLE);
            Member member = TestFixtures.insertMember(c, "M-RM-001", "Member RM", "rm@test.com");

            LocalDate loanDate = LocalDate.of(2026, 5, 1);
            Loan loan = TestFixtures.insertActiveLoan(c, copy, member, loanDate);
            loanDao.markReturned(c, loan.getLoanId(), loanDate.plusDays(4));

            // Member still has loan history â†’ RESTRICT prevents deletion.
            SQLException error = assertThrows(SQLException.class, () -> {
                try (Statement st = c.createStatement()) {
                    st.execute("DELETE FROM members WHERE member_id = " + member.getMemberId());
                }
            });
            assertEquals(AppConfig.SQLSTATE_FOREIGN_KEY_VIOLATION, error.getSQLState());
        }
    }

    @Test
    void restrictDeleteAuthorWithBookLinksFails() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            Book book = TestFixtures.insertBook(c, "978-RA-000-000-001", "Author Restrict Book");
            bookAuthorDao.replaceForBook(c, book.getBookId(), List.of(1));

            SQLException error = assertThrows(SQLException.class, () -> {
                try (Statement st = c.createStatement()) {
                    st.execute("DELETE FROM authors WHERE author_id = 1");
                }
            });
            assertEquals(AppConfig.SQLSTATE_FOREIGN_KEY_VIOLATION, error.getSQLState());
        }
    }

    @Test
    void cascadeDeleteBookAlsoRemovesCascadeLinkedAuthorsWhenNoOtherReferences() throws Exception {
        // Verify the CASCADE direction: book_authors.book_id â†’ books.book_id ON DELETE CASCADE.
        // When a book is deleted (and it has no copies to trip RESTRICT), the book_authors
        // rows for that book are removed. The author rows themselves are preserved.
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            Book book = TestFixtures.insertBook(c, "978-CA-000-000-001", "Cascade No Copy Book");
            bookAuthorDao.replaceForBook(c, book.getBookId(), List.of(1, 2));

            long authorsBefore = rawCount(c, "SELECT COUNT(*) FROM authors");
            long linksBefore = rawCount(c, "SELECT COUNT(*) FROM book_authors WHERE book_id = ?",
                    book.getBookId());
            assertEquals(2, linksBefore);

            bookDao.delete(c, book.getBookId());

            long linksAfter = rawCount(c, "SELECT COUNT(*) FROM book_authors WHERE book_id = ?",
                    book.getBookId());
            assertEquals(0, linksAfter, "CASCADE removed the book_authors link rows");

            long authorsAfter = rawCount(c, "SELECT COUNT(*) FROM authors");
            assertEquals(authorsBefore, authorsAfter,
                    "Author rows themselves are preserved â€” only the link table is CASCADE-deleted");
        }
    }

    // ------------------------------------------------------------------
    // Orphan check after a mix of operations
    // ------------------------------------------------------------------

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
            // Re-borrow the same copy.
            TestFixtures.insertActiveLoan(c, copy, member, today);

            // Run the same orphan-detection query used in manual-relation-test.sql.
            String orphanSql =
                    "SELECT "
                            + "(SELECT COUNT(*) FROM books b LEFT JOIN publishers p ON p.publisher_id = b.publisher_id WHERE p.publisher_id IS NULL) + "
                            + "(SELECT COUNT(*) FROM books b LEFT JOIN categories c ON c.category_id = b.category_id WHERE c.category_id IS NULL) + "
                            + "(SELECT COUNT(*) FROM book_copies bc LEFT JOIN books b ON b.book_id = bc.book_id WHERE b.book_id IS NULL) + "
                            + "(SELECT COUNT(*) FROM loans l LEFT JOIN book_copies bc ON bc.copy_id = l.copy_id WHERE bc.copy_id IS NULL) + "
                            + "(SELECT COUNT(*) FROM loans l LEFT JOIN members m ON m.member_id = l.member_id WHERE m.member_id IS NULL) + "
                            + "(SELECT COUNT(*) FROM book_authors ba LEFT JOIN books b ON b.book_id = ba.book_id WHERE b.book_id IS NULL) + "
                            + "(SELECT COUNT(*) FROM book_authors ba LEFT JOIN authors a ON a.author_id = ba.author_id WHERE a.author_id IS NULL)";
            long orphans = rawCount(c, orphanSql);
            assertEquals(0, orphans, "No orphan rows may exist after a mix of inserts, returns, and re-borrows");
        }
    }
}

