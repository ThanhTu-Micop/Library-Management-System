package com.library.dao.jdbc;

import com.library.dao.BookAuthorDAO;
import com.library.exception.DataAccessException;
import com.library.model.Author;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * JDBC implementation of {@link BookAuthorDAO}.
 *
 * <p>Handwritten SQL through JDBC; no ORM. The DAO never commits, rolls back, or closes the
 * caller's connection, so author replacement participates in the service transaction.
 * Single-statement reads and deletes reuse {@link JdbcSupport}.
 */
public class JdbcBookAuthorDAO implements BookAuthorDAO {

    private static final String FIND_BY_BOOK_ID =
            "SELECT a.author_id, a.name, a.created_at " +
            "FROM authors a " +
            "JOIN book_authors ba ON ba.author_id = a.author_id " +
            "WHERE ba.book_id = ? " +
            "ORDER BY a.name";

    private static final String DELETE_BY_BOOK_ID =
            "DELETE FROM book_authors WHERE book_id = ?";

    private static final String INSERT_LINK =
            "INSERT INTO book_authors (book_id, author_id) VALUES (?, ?)";

    @Override
    public List<Author> findByBookId(Connection c, int bookId) {
        return JdbcSupport.queryList(c, FIND_BY_BOOK_ID,
                "Failed to find authors for book id: " + bookId, JdbcBookAuthorDAO::mapAuthor, bookId);
    }

    @Override
    public void replaceForBook(Connection c, int bookId, List<Integer> authorIds) {
        Set<Integer> uniqueIds = new LinkedHashSet<>();
        if (authorIds != null) {
            for (Integer authorId : authorIds) {
                if (authorId != null) {
                    uniqueIds.add(authorId);
                }
            }
        }

        deleteByBookId(c, bookId);
        if (uniqueIds.isEmpty()) {
            return;
        }
        try (PreparedStatement ps = c.prepareStatement(INSERT_LINK)) {
            ps.setInt(1, bookId);
            for (Integer authorId : uniqueIds) {
                ps.setInt(2, authorId);
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to replace authors for book id: " + bookId, e);
        }
    }

    @Override
    public void deleteByBookId(Connection c, int bookId) {
        JdbcSupport.update(c, DELETE_BY_BOOK_ID,
                "Failed to delete authors for book id: " + bookId, bookId);
    }

    private static Author mapAuthor(ResultSet rs) throws SQLException {
        Author author = new Author();
        author.setAuthorId(rs.getInt("author_id"));
        author.setName(rs.getString("name"));
        author.setCreatedAt(rs.getObject("created_at", LocalDateTime.class));
        return author;
    }
}
