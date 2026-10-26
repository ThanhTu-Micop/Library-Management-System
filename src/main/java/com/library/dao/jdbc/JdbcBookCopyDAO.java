package com.library.dao.jdbc;

import com.library.dao.BookCopyDAO;
import com.library.exception.DataAccessException;
import com.library.model.BookCopy;
import com.library.model.enums.CopyStatus;

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
 * JDBC implementation of {@link BookCopyDAO}.
 *
 * <p>Handwritten SQL through JDBC; no ORM. Join reads populate {@code bookTitle}.
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
        try (PreparedStatement ps = c.prepareStatement(FIND_BY_BOOK_ID)) {
            ps.setInt(1, bookId);
            try (ResultSet rs = ps.executeQuery()) {
                List<BookCopy> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(mapBookCopy(rs));
                }
                return list;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find book copies by book id: " + bookId, e);
        }
    }

    @Override
    public Optional<BookCopy> findByIdForUpdate(Connection c, int copyId) {
        try (PreparedStatement ps = c.prepareStatement(FIND_BY_ID_FOR_UPDATE)) {
            ps.setInt(1, copyId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapBookCopy(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to lock book copy id: " + copyId, e);
        }
    }

    @Override
    public int insert(Connection c, BookCopy copy) {
        try (PreparedStatement ps = c.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, copy.getBookId());
            ps.setString(2, copy.getCopyCode());
            ps.setString(3, copy.getStatus() != null ? copy.getStatus().name() : CopyStatus.AVAILABLE.name());
            int affected = ps.executeUpdate();
            if (affected == 0) {
                throw new DataAccessException("Insert book copy failed: no rows affected");
            }
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new DataAccessException("Insert book copy failed: no generated key");
                }
                copy.setCopyId(keys.getInt(1));
            }
            return affected;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to insert book copy: " + copy.getCopyCode(), e);
        }
    }

    @Override
    public int updateStatus(Connection c, int copyId, CopyStatus status) {
        try (PreparedStatement ps = c.prepareStatement(UPDATE_STATUS)) {
            ps.setString(1, status.name());
            ps.setInt(2, copyId);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to update book copy status id=" + copyId, e);
        }
    }

    @Override
    public void deleteByBookId(Connection c, int bookId) {
        try (PreparedStatement ps = c.prepareStatement(DELETE_BY_BOOK_ID)) {
            ps.setInt(1, bookId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to delete book copies by book id: " + bookId, e);
        }
    }

    @Override
    public List<BookCopy> findAvailableWithBook(Connection c) {
        try (PreparedStatement ps = c.prepareStatement(FIND_AVAILABLE_WITH_BOOK);
             ResultSet rs = ps.executeQuery()) {
            List<BookCopy> list = new ArrayList<>();
            while (rs.next()) {
                BookCopy copy = mapBookCopy(rs);
                copy.setBookTitle(rs.getString("book_title"));
                list.add(copy);
            }
            return list;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find available book copies", e);
        }
    }

    private BookCopy mapBookCopy(ResultSet rs) throws SQLException {
        BookCopy copy = new BookCopy();
        copy.setCopyId(rs.getInt("copy_id"));
        copy.setBookId(rs.getInt("book_id"));
        copy.setCopyCode(rs.getString("copy_code"));
        String status = rs.getString("status");
        copy.setStatus(status != null ? CopyStatus.valueOf(status) : null);
        copy.setCreatedAt(rs.getObject("created_at", LocalDateTime.class));
        return copy;
    }
}
