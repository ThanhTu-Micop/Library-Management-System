package com.library.dao;

import com.library.model.DashboardStats;

import java.sql.Connection;
import java.time.LocalDate;

/**
 * DAO contract for dashboard statistics.
 *
 * <p>All methods receive the caller's {@link Connection}; DAOs never commit, roll back, or
 * close it. The overdue count is computed against the caller-supplied {@code today}.
 */
public interface DashboardDAO {

    DashboardStats getStats(Connection c, LocalDate today);
}
