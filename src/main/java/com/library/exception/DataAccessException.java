package com.library.exception;

import java.sql.SQLException;

/**
 * Data access exception wrapping a {@link SQLException} while preserving its {@code SQLState}
 * and original cause.
 *
 * <p>Person 1 owns this exception. DAOs throw it instead of leaking {@link SQLException} so
 * the service and controller layers do not depend directly on JDBC.
 *
 * <p>It extends {@link RuntimeException} so DAO methods can follow the common interface
 * contract without declaring checked exceptions.
 */
public class DataAccessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String sqlState;

    public DataAccessException(String message, SQLException cause) {
        super(message, cause);
        this.sqlState = cause != null ? cause.getSQLState() : null;
    }

    public DataAccessException(String message, String sqlState, Throwable cause) {
        super(message, cause);
        this.sqlState = sqlState;
    }

    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
        this.sqlState = cause instanceof SQLException ? ((SQLException) cause).getSQLState() : null;
    }

    public DataAccessException(String message) {
        super(message);
        this.sqlState = null;
    }

    /**
     * Returns the SQLState of the original cause, if available.
     *
     * @return SQLState or {@code null}
     */
    public String getSqlState() {
        return sqlState;
    }

    @Override
    public String getMessage() {
        String base = super.getMessage();
        if (sqlState != null && !sqlState.isEmpty()) {
            return base + " [SQLState=" + sqlState + "]";
        }
        return base;
    }
}
