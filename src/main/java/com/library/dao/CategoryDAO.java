package com.library.dao;

import com.library.model.Category;
import com.library.model.Page;
import com.library.model.criteria.BookSearchCriteria;

import java.sql.Connection;
import java.util.Optional;

/**
 * DAO contract for {@link Category}.
 *
 * <p>All methods receive the caller's {@link Connection}; DAOs never commit, roll back, or
 * close it. Category names are compared case-insensitively.
 */
public interface CategoryDAO {

    Optional<Category> findById(Connection c, int categoryId);

    Page<Category> search(Connection c, BookSearchCriteria criteria);

    int insert(Connection c, Category category);

    void update(Connection c, Category category);

    void delete(Connection c, int categoryId);

    boolean nameExists(Connection c, String name, Integer excludeCategoryId);
}
