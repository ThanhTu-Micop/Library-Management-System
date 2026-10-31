package com.library.dao.jdbc;

import com.library.dao.DashboardDAO;
import com.library.dao.LoanDAO;
import com.library.model.DashboardStats;

import java.sql.Connection;
import java.time.LocalDate;

/**
 * JDBC implementation of {@link DashboardDAO}.
 *
 * <p>Handwritten SQL through JDBC; no ORM. The overdue metric uses the caller-supplied
 * {@code today} instead of the database clock so tests are deterministic. Loan counts are
 * delegated to {@link JdbcLoanDAO} so the predicate lives in one place.
 */
public class JdbcDashboardDAO implements DashboardDAO {

    private static final String COUNT_BOOKS = "SELECT COUNT(*) FROM books";
    private static final String COUNT_MEMBERS = "SELECT COUNT(*) FROM members";
    private static final String COUNT_COPIES = "SELECT COUNT(*) FROM book_copies";
    private static final String COUNT_AVAILABLE_COPIES =
            "SELECT COUNT(*) FROM book_copies WHERE status = 'AVAILABLE'";

    private final LoanDAO loanDao;

    public JdbcDashboardDAO() {
        this(new JdbcLoanDAO());
    }

    JdbcDashboardDAO(LoanDAO loanDao) {
        this.loanDao = loanDao;
    }

    @Override
    public DashboardStats getStats(Connection c, LocalDate today) {
        DashboardStats stats = new DashboardStats();
        stats.setTotalBooks(JdbcSupport.queryLong(c, COUNT_BOOKS,
                "Failed to count books"));
        stats.setTotalMembers(JdbcSupport.queryLong(c, COUNT_MEMBERS,
                "Failed to count members"));
        stats.setTotalCopies(JdbcSupport.queryLong(c, COUNT_COPIES,
                "Failed to count book copies"));
        stats.setActiveLoans(loanDao.countActive(c));
        stats.setOverdueLoans(loanDao.countOverdue(c, today));
        stats.setAvailableCopies(JdbcSupport.queryLong(c, COUNT_AVAILABLE_COPIES,
                "Failed to count available book copies"));
        return stats;
    }
}
