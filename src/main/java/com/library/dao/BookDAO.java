package com.library.dao;

import com.library.model.Book;
import com.library.model.Page;
import com.library.model.criteria.BookSearchCriteria;

import java.sql.Connection;
import java.util.Optional;

/**
 * DAO contract for {@link Book}.
 *
 * <p>All methods receive the caller's {@link Connection}; DAOs never commit, roll back, or
 * close it. Transaction boundaries belong to the service layer.
 *
 * <p>{@code findById} and {@code search} populate the display projections
 * {@code publisherName}, {@code categoryName}, and {@code availableCopies}. Authors are not
 * loaded by this DAO; callers use {@link BookAuthorDAO#findByBookId} for that list.
 * {@code insert} returns the affected row count and assigns the generated ID to the model.
 * {@code search} applies every non-null criterion.
 */
public interface BookDAO {

    Optional<Book> findById(Connection c, int bookId);

    Page<Book> search(Connection c, BookSearchCriteria criteria);

    /**
     * Inserts a book and sets its generated ID on {@code book}.
     *
     * @return affected rows
     */
    int insert(Connection c, Book book);

    void update(Connection c, Book book);

    void delete(Connection c, int bookId);

    boolean isbnExists(Connection c, String isbn, Integer excludeBookId);
}
