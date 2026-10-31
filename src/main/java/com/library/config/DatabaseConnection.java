package com.library.config;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Database configuration and PostgreSQL connection factory.
 *
 * <p>Connection details are read from environment variables:
 * <ul>
 *   <li>{@code DB_URL} - JDBC URL, for example
 *       {@code jdbc:postgresql://127.0.0.1:5432/library_db}</li>
 *   <li>{@code DB_USER} - database user, for example {@code library_app}</li>
 *   <li>{@code DB_PASSWORD} - password; never commit it to source control</li>
 * </ul>
 *
 * <p>Optional external password file: set {@code DB_CONFIG} to a file containing the password
 * on its first line. The file must stay outside the repository.
 *
 * <p>The caller owns the returned connection, including closing it and any transaction
 * boundaries. This class never commits or rolls back.
 */
public final class DatabaseConnection {

    private static final String DEFAULT_URL = "jdbc:postgresql://127.0.0.1:5432/library_db";
    private static final String DEFAULT_USER = "library_app";
    private static final String ENV_URL = "DB_URL";
    private static final String ENV_USER = "DB_USER";
    private static final String ENV_PASSWORD = "DB_PASSWORD";
    private static final String ENV_CONFIG = "DB_CONFIG";

    private DatabaseConnection() {
    }

    /**
     * Opens a new database connection.
     *
     * <p>The caller is responsible for closing the connection, typically with
     * try-with-resources.
     *
     * @return a new connection
     * @throws SQLException when the connection cannot be created
     */
    public static Connection getConnection() throws SQLException {
        String url = System.getenv(ENV_URL);
        String user = System.getenv(ENV_USER);
        String password = System.getenv(ENV_PASSWORD);

        if (url == null || url.isBlank()) {
            url = DEFAULT_URL;
        }
        if (user == null || user.isBlank()) {
            user = DEFAULT_USER;
        }
        if (password == null || password.isBlank()) {
            password = loadExternalPassword();
        }

        Properties props = new Properties();
        props.setProperty("user", user);
        props.setProperty("password", password != null ? password : "");
        props.setProperty("ssl", "false");
        props.setProperty("loginTimeout", "10");

        return DriverManager.getConnection(url, props);
    }

    /**
     * Reads the password from the external file referenced by {@code DB_CONFIG}, if any.
     *
     * @return the password value, or {@code null} when the file is not configured or readable
     */
    private static String loadExternalPassword() {
        String configPath = System.getenv(ENV_CONFIG);
        if (configPath == null || configPath.isBlank()) {
            return null;
        }
        Path path = Paths.get(configPath);
        if (!Files.isReadable(path)) {
            return null;
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(path.toFile()))) {
            String line = reader.readLine();
            return line != null ? line.trim() : null;
        } catch (IOException e) {
            return null;
        }
    }

}
