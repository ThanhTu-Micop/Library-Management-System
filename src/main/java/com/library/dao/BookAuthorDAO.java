package com.library.dao;

import com.library.model.Author;

import java.sql.Connection;
import java.util.List;

/**
 * DAO contract for the {@code book_authors} many-to-many relationship.
 *
 * <p>All methods receive the caller's {@link Connection}; DAOs never commit, roll back, or
 * close it. Transaction boundaries belong to the service layer.
 */
public interface BookAuthorDAO {

    List<Author> findByBookId(Connection c, int bookId);

    /**
     * Replaces every author link for a book with the supplied author IDs.
     *
     * <p>Duplicate IDs are ignored. The caller owns the transaction.
     */
    void replaceForBook(Connection c, int bookId, List<Integer> authorIds);

    /**
     * Deletes every author link for a book. Zero affected rows is not an error.
     */
    void deleteByBookId(Connection c, int bookId);
}
