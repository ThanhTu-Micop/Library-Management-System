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
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DAO tests for {@link JdbcDashboardDAO} against {@code library_test}.
 */
class JdbcDashboardDAOTest {

    private final DashboardDAO dao = new JdbcDashboardDAO();

    @Test
    void getStatsWithFixedToday() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);

            Book book = TestFixtures.insertBook(c, "978-DASH-000-001", "Dash Book", 2023);
            TestFixtures.insertCopy(c, book, "DASH-CP-001", CopyStatus.AVAILABLE);
            BookCopy borrowedOne = TestFixtures.insertCopy(c, book, "DASH-CP-002", CopyStatus.BORROWED);
            BookCopy borrowedTwo = TestFixtures.insertCopy(c, book, "DASH-CP-003", CopyStatus.BORROWED);

            Member active = TestFixtures.insertMember(c, "M-DASH-001", "Dash Member", "dash@test.com");
            TestFixtures.insertMember(c, "M-DASH-002", "Dash Inactive", "dash2@test.com",
                    MemberStatus.INACTIVE);

            LoanDAO loanDao = new JdbcLoanDAO();
            LocalDate today = LocalDate.of(2026, 10, 25);
            loanDao.insert(c, new Loan(null, borrowedOne.getCopyId(), active.getMemberId(),
                    today.minusDays(24), today.minusDays(10), null, LoanStatus.ACTIVE));
            loanDao.insert(c, new Loan(null, borrowedTwo.getCopyId(), active.getMemberId(),
                    today.minusDays(3), today.plusDays(11), null, LoanStatus.ACTIVE));

            DashboardStats stats = dao.getStats(c, today);

            assertEquals(1, stats.getTotalBooks());
            assertEquals(2, stats.getTotalMembers());
            assertEquals(3, stats.getTotalCopies());
            assertEquals(2, stats.getActiveLoans());
            assertEquals(1, stats.getOverdueLoans());
            assertEquals(1, stats.getAvailableCopies());
        }
    }
}
