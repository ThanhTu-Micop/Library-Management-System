package com.library.dao;

import com.library.model.Page;
import com.library.model.Publisher;
import com.library.model.criteria.BookSearchCriteria;

import java.sql.Connection;
import java.util.Optional;

/**
 * DAO contract for {@link Publisher}.
 *
 * <p>All methods receive the caller's {@link Connection}; DAOs never commit, roll back, or
 * close it.
 */
public interface PublisherDAO {

    Optional<Publisher> findById(Connection c, int publisherId);

    Page<Publisher> search(Connection c, BookSearchCriteria criteria);

    int insert(Connection c, Publisher publisher);

    void update(Connection c, Publisher publisher);

    void delete(Connection c, int publisherId);

    boolean nameExists(Connection c, String name, Integer excludePublisherId);
}
