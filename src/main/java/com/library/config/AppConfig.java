package com.library.config;

/**
 * Shared application constants owned by Person 1.
 *
 * <p>This class holds no database password. Credentials are resolved from environment
 * variables by {@link DatabaseConnection}.
 */
public final class AppConfig {

    /** SQLState for a unique violation (PostgreSQL). */
    public static final String SQLSTATE_UNIQUE_VIOLATION = "23505";

    /** SQLState for a foreign-key violation (PostgreSQL). */
    public static final String SQLSTATE_FOREIGN_KEY_VIOLATION = "23503";

    /** SQLState for a check violation (PostgreSQL). */
    public static final String SQLSTATE_CHECK_VIOLATION = "23514";

    /** Loan period in calendar days. */
    public static final int LOAN_PERIOD_DAYS = 14;

    /** Default page size for paginated screens. */
    public static final int DEFAULT_PAGE_SIZE = 10;

    /** First page index (zero-based). */
    public static final int DEFAULT_PAGE = 0;

    private AppConfig() {
    }
}
