package com.library.dao.jdbc;

import com.library.dao.DashboardDAO;
import com.library.exception.DataAccessException;
import com.library.model.DashboardStats;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;

/**
 * JDBC implementation of {@link DashboardDAO}.
 *
 * <p>Handwritten SQL through JDBC; no ORM. The overdue metric uses the caller-supplied
 * {@code today} instead of the database clock so tests are deterministic.
 */
public class JdbcDashboardDAO implements DashboardDAO {

    private static final String COUNT_BOOKS = "SELECT COUNT(*) FROM books";
    private static final String COUNT_MEMBERS = "SELECT COUNT(*) FROM members";
    private static final String COUNT_COPIES = "SELECT COUNT(*) FROM book_copies";
    private static final String COUNT_ACTIVE_LOANS = "SELECT COUNT(*) FROM loans WHERE return_date IS NULL";
    private static final String COUNT_OVERDUE_LOANS =
            "SELECT COUNT(*) FROM loans WHERE return_date IS NULL AND due_date < ?";
    private static final String COUNT_AVAILABLE_COPIES =
            "SELECT COUNT(*) FROM book_copies WHERE status = 'AVAILABLE'";

    @Override
    public DashboardStats getStats(Connection c, LocalDate today) {
        DashboardStats stats = new DashboardStats();
        try {
            stats.setTotalBooks(count(c, COUNT_BOOKS));
            stats.setTotalMembers(count(c, COUNT_MEMBERS));
            stats.setTotalCopies(count(c, COUNT_COPIES));
            stats.setActiveLoans(count(c, COUNT_ACTIVE_LOANS));
            stats.setOverdueLoans(count(c, COUNT_OVERDUE_LOANS, today));
            stats.setAvailableCopies(count(c, COUNT_AVAILABLE_COPIES));
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load dashboard stats", e);
        }
        return stats;
    }

    private long count(Connection c, String sql, Object... params) throws SQLException {
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
