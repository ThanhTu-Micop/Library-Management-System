package com.library.dao;

import com.library.model.Author;
import com.library.model.Page;
import com.library.model.criteria.BookSearchCriteria;

import java.sql.Connection;
import java.util.Optional;

/**
 * DAO contract for {@link Author}.
 *
 * <p>All methods receive the caller's {@link Connection}; DAOs never commit, roll back, or
 * close it.
 */
public interface AuthorDAO {

    Optional<Author> findById(Connection c, int authorId);

    Page<Author> search(Connection c, BookSearchCriteria criteria);

    int insert(Connection c, Author author);

    void update(Connection c, Author author);

    void delete(Connection c, int authorId);

    boolean nameExists(Connection c, String name, Integer excludeAuthorId);
}
