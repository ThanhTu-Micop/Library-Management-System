package com.library.dao.jdbc;

import com.library.dao.BookCopyDAO;
import com.library.model.BookCopy;
import com.library.model.enums.CopyStatus;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * JDBC implementation of {@link BookCopyDAO}.
 *
 * <p>Handwritten SQL through JDBC; no ORM. Join reads populate {@code bookTitle}. Shared
 * statement mechanics live in {@link JdbcSupport}.
 */
public class JdbcBookCopyDAO implements BookCopyDAO {

    private static final String FIND_BY_BOOK_ID =
            "SELECT copy_id, book_id, copy_code, status, created_at " +
            "FROM book_copies WHERE book_id = ? ORDER BY copy_code";

    private static final String FIND_BY_ID_FOR_UPDATE =
            "SELECT copy_id, book_id, copy_code, status, created_at " +
            "FROM book_copies WHERE copy_id = ? FOR UPDATE";

    private static final String FIND_AVAILABLE_WITH_BOOK =
            "SELECT bc.copy_id, bc.book_id, bc.copy_code, bc.status, bc.created_at, " +
            "b.title AS book_title " +
            "FROM book_copies bc " +
            "JOIN books b ON b.book_id = bc.book_id " +
            "WHERE bc.status = 'AVAILABLE' " +
            "ORDER BY b.title, bc.copy_code";

    private static final String INSERT =
            "INSERT INTO book_copies (book_id, copy_code, status) VALUES (?, ?, ?)";

    private static final String UPDATE_STATUS =
            "UPDATE book_copies SET status = ? WHERE copy_id = ?";

    private static final String DELETE_BY_BOOK_ID = "DELETE FROM book_copies WHERE book_id = ?";

    @Override
    public List<BookCopy> findByBookId(Connection c, int bookId) {
        return JdbcSupport.queryList(c, FIND_BY_BOOK_ID,
                "Failed to find book copies by book id: " + bookId,
                JdbcBookCopyDAO::mapBookCopy, bookId);
    }

    @Override
    public Optional<BookCopy> findByIdForUpdate(Connection c, int copyId) {
        return JdbcSupport.queryOne(c, FIND_BY_ID_FOR_UPDATE,
                "Failed to lock book copy id: " + copyId,
                JdbcBookCopyDAO::mapBookCopy, copyId);
    }

    @Override
    public int insert(Connection c, BookCopy copy) {
        CopyStatus status = copy.getStatus() != null ? copy.getStatus() : CopyStatus.AVAILABLE;
        return JdbcSupport.insert(c, INSERT,
                "Failed to insert book copy: " + copy.getCopyCode(),
                copy::setCopyId, copy.getBookId(), copy.getCopyCode(), status.name());
    }

    @Override
    public int updateStatus(Connection c, int copyId, CopyStatus status) {
        return JdbcSupport.update(c, UPDATE_STATUS,
                "Failed to update book copy status id=" + copyId, status.name(), copyId);
    }

    @Override
    public void deleteByBookId(Connection c, int bookId) {
        JdbcSupport.update(c, DELETE_BY_BOOK_ID,
                "Failed to delete book copies by book id: " + bookId, bookId);
    }

    @Override
    public List<BookCopy> findAvailableWithBook(Connection c) {
        return JdbcSupport.queryList(c, FIND_AVAILABLE_WITH_BOOK,
                "Failed to find available book copies", JdbcBookCopyDAO::mapAvailableCopy);
    }

    private static BookCopy mapBookCopy(ResultSet rs) throws SQLException {
        BookCopy copy = new BookCopy();
        copy.setCopyId(rs.getInt("copy_id"));
        copy.setBookId(rs.getInt("book_id"));
        copy.setCopyCode(rs.getString("copy_code"));
        String status = rs.getString("status");
        copy.setStatus(status != null ? CopyStatus.valueOf(status) : null);
        copy.setCreatedAt(rs.getObject("created_at", LocalDateTime.class));
        return copy;
    }

    private static BookCopy mapAvailableCopy(ResultSet rs) throws SQLException {
        BookCopy copy = mapBookCopy(rs);
        copy.setBookTitle(rs.getString("book_title"));
        return copy;
    }
}
