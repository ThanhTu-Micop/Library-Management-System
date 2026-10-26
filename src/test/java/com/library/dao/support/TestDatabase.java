package com.library.dao.support;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

/**
 * Test database helper for {@code library_test}.
 *
 * <p>The JDBC URL is resolved from {@code TEST_DB_URL}, then {@code DB_URL}, then the
 * built-in local default. The resolved URL must contain {@code _test}; this guard prevents
 * destructive test cleanup from running against the development database.
 *
 * <p>Credentials come from {@code DB_USER} and {@code DB_PASSWORD}. No secret is stored in
 * this class.
 */
public final class TestDatabase {

    private static final String DEFAULT_URL = "jdbc:postgresql://127.0.0.1:5432/library_test";
    private static final String DEFAULT_USER = "library_app";
    private static final String ENV_TEST_URL = "TEST_DB_URL";
    private static final String ENV_URL = "DB_URL";
    private static final String ENV_USER = "DB_USER";
    private static final String ENV_PASSWORD = "DB_PASSWORD";

    private TestDatabase() {
    }

    /**
     * Opens a connection to the test database.
     *
     * @return a new connection
     * @throws SQLException when the connection cannot be created
     */
    public static Connection getConnection() throws SQLException {
        String url = resolveUrl();
        String user = System.getenv(ENV_USER);
        String password = System.getenv(ENV_PASSWORD);

        if (user == null || user.isBlank()) {
            user = DEFAULT_USER;
        }

        Properties props = new Properties();
        props.setProperty("user", user);
        props.setProperty("password", password != null ? password : "");
        props.setProperty("ssl", "false");
        props.setProperty("loginTimeout", "10");

        return DriverManager.getConnection(url, props);
    }

    private static String resolveUrl() {
        String url = firstNonBlank(System.getenv(ENV_TEST_URL), System.getenv(ENV_URL), DEFAULT_URL);
        if (url == null || !url.contains("_test")) {
            throw new IllegalStateException(
                    "Refusing to run tests: the resolved JDBC URL must contain '_test'");
        }
        return url;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    /**
     * Clears every application table and restarts identity sequences.
     *
     * <p>{@code TRUNCATE ... RESTART IDENTITY CASCADE} keeps hard-coded reference IDs stable
     * between tests and is safer than row-by-row deletes.
     *
     * @param c an open connection
     * @throws SQLException when cleanup fails
     */
    public static void resetAll(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.execute("TRUNCATE book_authors, loans, book_copies, books, "
                    + "authors, publishers, categories, members "
                    + "RESTART IDENTITY CASCADE");
        }
    }

    /**
     * Inserts stable reference rows for publishers, categories, and authors.
     *
     * <p>Call {@link #resetAll(Connection)} first so the IDs are 1-based and predictable.
     *
     * @param c an open connection
     * @throws SQLException when seeding fails
     */
    public static void seedReferenceData(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.execute("INSERT INTO publishers (name) VALUES "
                    + "('Literature Publisher'), "
                    + "('Science and Technology Publisher'), "
                    + "('Technology Publisher')");
            st.execute("INSERT INTO categories (name) VALUES "
                    + "('Novel'), ('Science'), ('History'), ('Technology')");
            st.execute("INSERT INTO authors (name) VALUES "
                    + "('Nguyen Nhat Anh'), ('Nam Cao'), ('Robert C. Martin'), ('Isaac Asimov')");
        }
    }
}
