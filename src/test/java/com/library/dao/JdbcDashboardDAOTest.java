package com.library.dao;

import com.library.dao.jdbc.JdbcDashboardDAO;
import com.library.dao.jdbc.JdbcLoanDAO;
import com.library.dao.support.TestDatabase;
import com.library.dao.support.TestFixtures;
import com.library.model.Book;
import com.library.model.BookCopy;
import com.library.model.DashboardStats;
import com.library.model.Loan;
import com.library.model.Member;
import com.library.model.enums.CopyStatus;
import com.library.model.enums.LoanStatus;
import com.library.model.enums.MemberStatus;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DAO tests for {@link JdbcDashboardDAO} against {@code library_test}.
 *
 * <p>The dashboard is the single owner of aggregate-count behavior: empty-database counts,
 * mutation-by-mutation synchronization, and the caller-supplied {@code today} for overdue.
 */
class JdbcDashboardDAOTest {

    private final DashboardDAO dao = new JdbcDashboardDAO();
    private final LoanDAO loanDao = new JdbcLoanDAO();

    @Test
    void emptyDatabaseCountsMatchRawTables() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            LocalDate today = LocalDate.of(2026, 10, 25);
            DashboardStats stats = dao.getStats(c, today);

            assertAll(
                    () -> assertEquals(rawCount(c, "SELECT COUNT(*) FROM books"), stats.getTotalBooks()),
                    () -> assertEquals(rawCount(c, "SELECT COUNT(*) FROM members"), stats.getTotalMembers()),
                    () -> assertEquals(rawCount(c, "SELECT COUNT(*) FROM book_copies"), stats.getTotalCopies()),
                    () -> assertEquals(rawCount(c,
                            "SELECT COUNT(*) FROM loans WHERE return_date IS NULL"), stats.getActiveLoans()),
                    () -> assertEquals(rawCount(c,
                            "SELECT COUNT(*) FROM book_copies WHERE status = 'AVAILABLE'"),
                            stats.getAvailableCopies()),
                    () -> assertEquals(rawCount(c,
                            "SELECT COUNT(*) FROM loans WHERE return_date IS NULL AND due_date < ?", today),
                            stats.getOverdueLoans()));

            assertAll(
                    () -> assertEquals(0, stats.getTotalBooks()),
                    () -> assertEquals(0, stats.getTotalMembers()),
                    () -> assertEquals(0, stats.getTotalCopies()),
                    () -> assertEquals(0, stats.getActiveLoans()),
                    () -> assertEquals(0, stats.getOverdueLoans()),
                    () -> assertEquals(0, stats.getAvailableCopies()));
        }
    }

    @Test
    void countsTrackMutationsAndFixedToday() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            LocalDate today = LocalDate.of(2026, 10, 25);

            DashboardStats empty = dao.getStats(c, today);
            assertAll(
                    () -> assertEquals(0, empty.getTotalBooks()),
                    () -> assertEquals(0, empty.getTotalMembers()),
                    () -> assertEquals(0, empty.getTotalCopies()),
                    () -> assertEquals(0, empty.getActiveLoans()),
                    () -> assertEquals(0, empty.getOverdueLoans()),
                    () -> assertEquals(0, empty.getAvailableCopies()));

            Book book = TestFixtures.insertBook(c, "978-DASH-000-001", "Dash Book", 2023);
            BookCopy available = TestFixtures.insertCopy(c, book, "DASH-CP-001", CopyStatus.AVAILABLE);
            BookCopy borrowedOne = TestFixtures.insertCopy(c, book, "DASH-CP-002", CopyStatus.BORROWED);
            BookCopy borrowedTwo = TestFixtures.insertCopy(c, book, "DASH-CP-003", CopyStatus.BORROWED);

            DashboardStats afterCopies = dao.getStats(c, today);
            assertAll(
                    () -> assertEquals(1, afterCopies.getTotalBooks()),
                    () -> assertEquals(3, afterCopies.getTotalCopies()),
                    () -> assertEquals(1, afterCopies.getAvailableCopies()),
                    () -> assertEquals(0, afterCopies.getActiveLoans(),
                            "A BORROWED copy does not imply an active loan"));

            Member active = TestFixtures.insertMember(c, "M-DASH-001", "Dash Member", "dash@test.com");
            TestFixtures.insertMember(c, "M-DASH-002", "Dash Inactive", "dash2@test.com",
                    MemberStatus.INACTIVE);

            DashboardStats afterMembers = dao.getStats(c, today);
            assertEquals(2, afterMembers.getTotalMembers(),
                    "totalMembers includes both ACTIVE and INACTIVE members");

            Loan overdue = new Loan(null, borrowedOne.getCopyId(), active.getMemberId(),
                    today.minusDays(24), today.minusDays(10), null, LoanStatus.ACTIVE);
            Loan current = new Loan(null, borrowedTwo.getCopyId(), active.getMemberId(),
                    today.minusDays(3), today.plusDays(11), null, LoanStatus.ACTIVE);
            loanDao.insert(c, overdue);
            loanDao.insert(c, current);

            DashboardStats afterLoans = dao.getStats(c, today);
            assertAll(
                    () -> assertEquals(1, afterLoans.getTotalBooks()),
                    () -> assertEquals(2, afterLoans.getTotalMembers()),
                    () -> assertEquals(3, afterLoans.getTotalCopies()),
                    () -> assertEquals(2, afterLoans.getActiveLoans()),
                    () -> assertEquals(1, afterLoans.getOverdueLoans(),
                            "due_date < today is overdue; due_date == today is not"),
                    () -> assertEquals(1, afterLoans.getAvailableCopies()));

            assertAll(
                    () -> assertEquals(rawCount(c, "SELECT COUNT(*) FROM books"),
                            afterLoans.getTotalBooks()),
                    () -> assertEquals(rawCount(c, "SELECT COUNT(*) FROM members"),
                            afterLoans.getTotalMembers()),
                    () -> assertEquals(rawCount(c, "SELECT COUNT(*) FROM book_copies"),
                            afterLoans.getTotalCopies()),
                    () -> assertEquals(rawCount(c,
                            "SELECT COUNT(*) FROM loans WHERE return_date IS NULL"),
                            afterLoans.getActiveLoans()),
                    () -> assertEquals(rawCount(c,
                            "SELECT COUNT(*) FROM loans WHERE return_date IS NULL AND due_date < ?", today),
                            afterLoans.getOverdueLoans()),
                    () -> assertEquals(rawCount(c,
                            "SELECT COUNT(*) FROM book_copies WHERE status = 'AVAILABLE'"),
                            afterLoans.getAvailableCopies()));

            loanDao.markReturned(c, overdue.getLoanId(), today);

            DashboardStats afterReturn = dao.getStats(c, today);
            assertAll(
                    () -> assertEquals(1, afterReturn.getActiveLoans()),
                    () -> assertEquals(0, afterReturn.getOverdueLoans()),
                    () -> assertEquals(1, afterReturn.getAvailableCopies(),
                            "Returning a loan does not change book_copies.status"));

            // Keep the copy variable referenced so the fixture intent stays explicit.
            assertEquals(CopyStatus.AVAILABLE, available.getStatus());
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
}
