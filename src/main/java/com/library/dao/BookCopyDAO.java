package com.library.dao;

import com.library.model.BookCopy;
import com.library.model.enums.CopyStatus;

import java.sql.Connection;
import java.util.List;
import java.util.Optional;

/**
 * DAO contract for {@link BookCopy}.
 *
 * <p>All methods receive the caller's {@link Connection}; DAOs never commit, roll back, or
 * close it. Transaction boundaries belong to the service layer.
 */
public interface BookCopyDAO {

    List<BookCopy> findByBookId(Connection c, int bookId);

    Optional<BookCopy> findByIdForUpdate(Connection c, int copyId);

    /**
     * Inserts a copy and sets its generated ID on {@code copy}.
     *
     * @return affected rows
     */
    int insert(Connection c, BookCopy copy);

    /**
     * Updates the status of one copy.
     *
     * @return affected rows
     */
    int updateStatus(Connection c, int copyId, CopyStatus status);

    /**
     * Deletes every copy of a book. Zero affected rows is not an error, because a book may
     * legitimately have no copies.
     */
    void deleteByBookId(Connection c, int bookId);

    /**
     * Returns every AVAILABLE copy together with its book title, ordered by title and copy code.
     */
    List<BookCopy> findAvailableWithBook(Connection c);
}
