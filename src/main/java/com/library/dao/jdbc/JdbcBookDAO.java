package com.library.dao.jdbc;

import com.library.dao.BookDAO;
import com.library.model.Book;
import com.library.model.Page;
import com.library.model.criteria.BookSearchCriteria;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JDBC implementation of {@link BookDAO}.
 *
 * <p>Handwritten SQL through JDBC; no ORM. Reads use enriched joins and an available-copy
 * subquery so list/detail views can render display fields directly. Shared statement
 * mechanics live in {@link JdbcSupport}.
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
            "SELECT COUNT(*) FROM books WHERE isbn = ? AND book_id <> ?";

    @Override
    public Optional<Book> findById(Connection c, int bookId) {
        return JdbcSupport.queryOne(c, FIND_BY_ID,
                "Failed to find book by id: " + bookId, JdbcBookDAO::mapBook, bookId);
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
        params.add((long) criteria.getPage() * criteria.getSize());

        return JdbcSupport.search(c, countSb.toString(), dataSb.toString(), params,
                filterParamCount, criteria.getPage(), criteria.getSize(),
                "Failed to search books", JdbcBookDAO::mapBook);
    }

    @Override
    public int insert(Connection c, Book book) {
        String isbn = trimIsbn(book.getIsbn());
        return JdbcSupport.insert(c, INSERT, "Failed to insert book: " + isbn,
                book::setBookId,
                isbn, book.getTitle(), book.getPublisherId(), book.getCategoryId(),
                book.getPublicationYear());
    }

    @Override
    public void update(Connection c, Book book) {
        JdbcSupport.updateExpectingRow(c, UPDATE,
                "Failed to update book id=" + book.getBookId(),
                trimIsbn(book.getIsbn()), book.getTitle(), book.getPublisherId(),
                book.getCategoryId(), book.getPublicationYear(), book.getBookId());
    }

    @Override
    public void delete(Connection c, int bookId) {
        JdbcSupport.updateExpectingRow(c, DELETE,
                "Failed to delete book id=" + bookId, bookId);
    }

    @Override
    public boolean isbnExists(Connection c, String isbn, Integer excludeBookId) {
        String value = trimIsbn(isbn);
        if (value == null || value.isBlank()) {
            return false;
        }
        int excluded = excludeBookId != null ? excludeBookId : -1;
        return JdbcSupport.exists(c, ISBN_EXISTS,
                "Failed to check ISBN exists: " + value, value, excluded);
    }

    private void appendSearchFilters(StringBuilder countSb, StringBuilder dataSb,
                                     BookSearchCriteria criteria, List<Object> params) {
        if (criteria.getKeyword() != null && !criteria.getKeyword().isBlank()) {
            countSb.append(" AND (unaccent(b.title) ILIKE unaccent(?) OR b.isbn ILIKE ?)");
            dataSb.append(" AND (unaccent(b.title) ILIKE unaccent(?) OR b.isbn ILIKE ?)");
            String like = JdbcSupport.likePattern(criteria.getKeyword());
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
                    "WHERE ba.book_id = b.book_id AND unaccent(a.name) ILIKE unaccent(?))";
            countSb.append(clause);
            dataSb.append(clause);
            params.add(JdbcSupport.likePattern(criteria.getAuthorName()));
        }
        if (Boolean.TRUE.equals(criteria.getAvailableOnly())) {
            String clause = " AND EXISTS (SELECT 1 FROM book_copies bc " +
                    "WHERE bc.book_id = b.book_id AND bc.status = 'AVAILABLE')";
            countSb.append(clause);
            dataSb.append(clause);
        }
    }

    private String trimIsbn(String isbn) {
        return isbn != null ? isbn.trim() : null;
    }

    private static Book mapBook(ResultSet rs) throws SQLException {
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
