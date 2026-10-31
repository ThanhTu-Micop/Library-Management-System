package com.library.dao.jdbc;

import com.library.exception.DataAccessException;
import com.library.model.Page;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.IntConsumer;

/**
 * Shared JDBC mechanics used by the handwritten DAO implementations.
 *
 * <p>The helper centralizes parameter binding, LIKE escaping, single-row and list reads,
 * pagination (count plus data query), generated-key inserts, and affected-row checks. It
 * does not own transactions: every method receives the caller's {@link Connection} and
 * never commits, rolls back, or closes it.
 */
final class JdbcSupport {

    private JdbcSupport() {
    }

    /** Maps one result-set row to a domain object. */
    @FunctionalInterface
    interface RowMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }

    /**
     * Escapes a keyword for a {@code LIKE}/{@code ILIKE} pattern and wraps it in {@code %}.
     *
     * @param value raw user keyword, possibly {@code null}
     * @return the escaped pattern, or {@code null} when the keyword is {@code null}
     */
    static String likePattern(String value) {
        if (value == null) {
            return null;
        }
        String escaped = value.trim()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }

    /**
     * Binds every parameter with {@link PreparedStatement#setObject(int, Object)}.
     */
    static void fillParams(PreparedStatement ps, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            ps.setObject(i + 1, params[i]);
        }
    }

    static void fillParams(PreparedStatement ps, List<?> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            ps.setObject(i + 1, params.get(i));
        }
    }

    /**
     * Executes a query expected to return at most one row.
     */
    static <T> Optional<T> queryOne(Connection c, String sql, String errorMessage,
                                    RowMapper<T> mapper, Object... params) {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            fillParams(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapper.map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException(errorMessage, e);
        }
    }

    /**
     * Executes a query and maps every returned row.
     */
    static <T> List<T> queryList(Connection c, String sql, String errorMessage,
                                 RowMapper<T> mapper, Object... params) {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            fillParams(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                List<T> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(mapper.map(rs));
                }
                return list;
            }
        } catch (SQLException e) {
            throw new DataAccessException(errorMessage, e);
        }
    }

    /**
     * Executes a query whose first column is a numeric value.
     */
    static long queryLong(Connection c, String sql, String errorMessage, Object... params) {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            fillParams(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        } catch (SQLException e) {
            throw new DataAccessException(errorMessage, e);
        }
    }

    /**
     * Returns whether a count query finds at least one matching row.
     */
    static boolean exists(Connection c, String sql, String errorMessage, Object... params) {
        return queryLong(c, sql, errorMessage, params) > 0;
    }

    /**
     * Executes an update/delete and returns the affected-row count.
     */
    static int update(Connection c, String sql, String errorMessage, Object... params) {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            fillParams(ps, params);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException(errorMessage, e);
        }
    }

    /**
     * Executes an update/delete and treats zero affected rows as a failure.
     */
    static void updateExpectingRow(Connection c, String sql, String errorMessage, Object... params) {
        if (update(c, sql, errorMessage, params) == 0) {
            throw new DataAccessException(errorMessage + ": no rows affected");
        }
    }

    /**
     * Executes an insert with generated keys and writes the generated ID back to the model.
     */
    static int insert(Connection c, String sql, String errorMessage, IntConsumer idSetter,
                      Object... params) {
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            fillParams(ps, params);
            int affected = ps.executeUpdate();
            if (affected == 0) {
                throw new DataAccessException(errorMessage + ": no rows affected");
            }
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new DataAccessException(errorMessage + ": no generated key");
                }
                idSetter.accept(keys.getInt(1));
            }
            return affected;
        } catch (SQLException e) {
            throw new DataAccessException(errorMessage, e);
        }
    }

    /**
     * Runs the count query and, when rows exist, the paginated data query.
     *
     * <p>{@code params} contains the filter parameters followed by the {@code LIMIT} and
     * {@code OFFSET} values. {@code filterParamCount} marks where the filter parameters end.
     */
    static <T> Page<T> search(Connection c, String countSql, String dataSql,
                              List<Object> params, int filterParamCount,
                              int page, int size, String errorMessage, RowMapper<T> mapper) {
        try (PreparedStatement countPs = c.prepareStatement(countSql)) {
            fillParams(countPs, params.subList(0, filterParamCount));
            try (ResultSet rs = countPs.executeQuery()) {
                long total = rs.next() ? rs.getLong(1) : 0;
                List<T> content = new ArrayList<>();
                if (total > 0) {
                    try (PreparedStatement dataPs = c.prepareStatement(dataSql)) {
                        fillParams(dataPs, params);
                        try (ResultSet drs = dataPs.executeQuery()) {
                            while (drs.next()) {
                                content.add(mapper.map(drs));
                            }
                        }
                    }
                }
                return new Page<>(content, total, page, size);
            }
        } catch (SQLException e) {
            throw new DataAccessException(errorMessage, e);
        }
    }
}
