package com.library.dao.jdbc;

import com.library.dao.BookDAO;
import com.library.exception.DataAccessException;
import com.library.model.Book;
import com.library.model.Page;
import com.library.model.criteria.BookSearchCriteria;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JDBC implementation of {@link BookDAO}.
 *
 * <p>Handwritten SQL through JDBC; no ORM. Reads use enriched joins and an available-copy
 * subquery so list/detail views can render display fields directly.
 */
public class JdbcBookDAO implements BookDAO {

    private static final String SELECT_COLUMNS =
            "SELECT b.book_id, b.isbn, b.title, b.publisher_id, b.category_id, " +
            "b.publication_year, b.created_at, " +
            "p.name AS publisher_name, c.name AS category_name, " +
            "(SELECT COUNT(*) FROM book_copies bc " +
            " WHERE bc.book_id = b.book_id AND bc.status = 'AVAILABLE') AS available_copies " +
            "FROM books b " +
            "JOIN publishers p ON p.publisher_id = b.publisher_id " +
            "JOIN categories c ON c.category_id = b.category_id ";

    private static final String FIND_BY_ID = SELECT_COLUMNS + "WHERE b.book_id = ?";

    private static final String COUNT_SEARCH_BASE =
            "SELECT COUNT(*) FROM books b WHERE 1 = 1";

    private static final String SEARCH_BASE = SELECT_COLUMNS + "WHERE 1 = 1";

    private static final String INSERT =
            "INSERT INTO books (isbn, title, publisher_id, category_id, publication_year) " +
            "VALUES (?, ?, ?, ?, ?)";

    private static final String UPDATE =
            "UPDATE books SET isbn = ?, title = ?, publisher_id = ?, category_id = ?, publication_year = ? " +
            "WHERE book_id = ?";

    private static final String DELETE = "DELETE FROM books WHERE book_id = ?";

    private static final String ISBN_EXISTS =
            "SELECT COUNT(*) FROM books WHERE isbn = ? AND (? IS NULL OR book_id <> ?)";

    @Override
    public Optional<Book> findById(Connection c, int bookId) {
        try (PreparedStatement ps = c.prepareStatement(FIND_BY_ID)) {
            ps.setInt(1, bookId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapBook(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find book by id: " + bookId, e);
        }
    }

    @Override
    public Page<Book> search(Connection c, BookSearchCriteria criteria) {
        StringBuilder countSb = new StringBuilder(COUNT_SEARCH_BASE);
        StringBuilder dataSb = new StringBuilder(SEARCH_BASE);
        List<Object> params = new ArrayList<>();

        appendSearchFilters(countSb, dataSb, criteria, params);
        int filterParamCount = params.size();

        dataSb.append(" ORDER BY b.book_id");
        dataSb.append(" LIMIT ? OFFSET ?");
        params.add(criteria.getSize());
        params.add(criteria.getPage() * criteria.getSize());

        try (PreparedStatement countPs = c.prepareStatement(countSb.toString())) {
            fillParams(countPs, params.subList(0, filterParamCount));
            try (ResultSet rs = countPs.executeQuery()) {
                long total = rs.next() ? rs.getLong(1) : 0;
                List<Book> content = new ArrayList<>();
                if (total > 0) {
                    try (PreparedStatement dataPs = c.prepareStatement(dataSb.toString())) {
                        fillParams(dataPs, params);
                        try (ResultSet drs = dataPs.executeQuery()) {
                            while (drs.next()) {
                                content.add(mapBook(drs));
                            }
                        }
                    }
                }
                return new Page<>(content, total, criteria.getPage(), criteria.getSize());
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to search books", e);
        }
    }

    @Override
    public int insert(Connection c, Book book) {
        try (PreparedStatement ps = c.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, trimIsbn(book.getIsbn()));
            ps.setString(2, book.getTitle());
            ps.setInt(3, book.getPublisherId());
            ps.setInt(4, book.getCategoryId());
            if (book.getPublicationYear() != null) {
                ps.setInt(5, book.getPublicationYear());
            } else {
                ps.setNull(5, java.sql.Types.INTEGER);
            }
            int affected = ps.executeUpdate();
            if (affected == 0) {
                throw new DataAccessException("Insert book failed: no rows affected");
            }
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new DataAccessException("Insert book failed: no generated key");
                }
                book.setBookId(keys.getInt(1));
            }
            return affected;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to insert book: " + book.getIsbn(), e);
        }
    }

    @Override
    public void update(Connection c, Book book) {
        try (PreparedStatement ps = c.prepareStatement(UPDATE)) {
            ps.setString(1, trimIsbn(book.getIsbn()));
            ps.setString(2, book.getTitle());
            ps.setInt(3, book.getPublisherId());
            ps.setInt(4, book.getCategoryId());
            if (book.getPublicationYear() != null) {
                ps.setInt(5, book.getPublicationYear());
            } else {
                ps.setNull(5, java.sql.Types.INTEGER);
            }
            ps.setInt(6, book.getBookId());
            int affected = ps.executeUpdate();
            if (affected == 0) {
                throw new DataAccessException("Update book failed: no rows affected, id=" + book.getBookId());
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to update book id=" + book.getBookId(), e);
        }
    }

    @Override
    public void delete(Connection c, int bookId) {
        try (PreparedStatement ps = c.prepareStatement(DELETE)) {
            ps.setInt(1, bookId);
            int affected = ps.executeUpdate();
            if (affected == 0) {
                throw new DataAccessException("Delete book failed: no rows affected, id=" + bookId);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to delete book id=" + bookId, e);
        }
    }

    @Override
    public boolean isbnExists(Connection c, String isbn, Integer excludeBookId) {
        String value = trimIsbn(isbn);
        if (value == null || value.isBlank()) {
            return false;
        }
        try (PreparedStatement ps = c.prepareStatement(ISBN_EXISTS)) {
            ps.setString(1, value);
            if (excludeBookId == null) {
                ps.setNull(2, java.sql.Types.INTEGER);
                ps.setNull(3, java.sql.Types.INTEGER);
            } else {
                ps.setInt(2, excludeBookId);
                ps.setInt(3, excludeBookId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getLong(1) > 0;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to check ISBN exists: " + value, e);
        }
    }

    private void appendSearchFilters(StringBuilder countSb, StringBuilder dataSb,
                                     BookSearchCriteria criteria, List<Object> params) {
        if (criteria.getKeyword() != null && !criteria.getKeyword().isBlank()) {
            countSb.append(" AND (b.title ILIKE ? OR b.isbn ILIKE ?)");
            dataSb.append(" AND (b.title ILIKE ? OR b.isbn ILIKE ?)");
            String like = "%" + criteria.getKeyword().trim() + "%";
            params.add(like);
            params.add(like);
        }
        if (criteria.getPublisherId() != null) {
            countSb.append(" AND b.publisher_id = ?");
            dataSb.append(" AND b.publisher_id = ?");
            params.add(criteria.getPublisherId());
        }
        if (criteria.getCategoryId() != null) {
            countSb.append(" AND b.category_id = ?");
            dataSb.append(" AND b.category_id = ?");
            params.add(criteria.getCategoryId());
        }
        if (criteria.getAuthorName() != null && !criteria.getAuthorName().isBlank()) {
            String clause = " AND EXISTS (SELECT 1 FROM book_authors ba " +
                    "JOIN authors a ON a.author_id = ba.author_id " +
                    "WHERE ba.book_id = b.book_id AND a.name ILIKE ?)";
            countSb.append(clause);
            dataSb.append(clause);
            params.add("%" + criteria.getAuthorName().trim() + "%");
        }
        if (Boolean.TRUE.equals(criteria.getAvailableOnly())) {
            String clause = " AND EXISTS (SELECT 1 FROM book_copies bc " +
                    "WHERE bc.book_id = b.book_id AND bc.status = 'AVAILABLE')";
            countSb.append(clause);
            dataSb.append(clause);
        }
    }

    private void fillParams(PreparedStatement ps, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            ps.setObject(i + 1, params.get(i));
        }
    }

    private String trimIsbn(String isbn) {
        return isbn != null ? isbn.trim() : null;
    }

    private Book mapBook(ResultSet rs) throws SQLException {
        Book book = new Book();
        book.setBookId(rs.getInt("book_id"));
        book.setIsbn(rs.getString("isbn"));
        book.setTitle(rs.getString("title"));
        book.setPublisherId(rs.getInt("publisher_id"));
        book.setCategoryId(rs.getInt("category_id"));
        int year = rs.getInt("publication_year");
        book.setPublicationYear(rs.wasNull() ? null : year);
        book.setCreatedAt(rs.getObject("created_at", LocalDateTime.class));
        book.setPublisherName(rs.getString("publisher_name"));
        book.setCategoryName(rs.getString("category_name"));
        book.setAvailableCopies(rs.getInt("available_copies"));
        return book;
    }
}
