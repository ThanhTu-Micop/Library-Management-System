package com.library.dao.jdbc;

import com.library.dao.CategoryDAO;
import com.library.model.Category;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;

/**
 * JDBC implementation of {@link CategoryDAO}.
 *
 * <p>Handwritten SQL through JDBC; no ORM. Name uniqueness is case-insensitive, matching
 * the {@code ux_categories_name_lower} index.
 */
public class JdbcCategoryDAO extends AbstractNamedEntityDAO<Category> implements CategoryDAO {

    public JdbcCategoryDAO() {
        super("categories", "category_id", "category", true);
    }

    @Override
    protected Category mapRow(ResultSet rs) throws SQLException {
        Category category = new Category();
        category.setCategoryId(rs.getInt("category_id"));
        category.setName(rs.getString("name"));
        category.setCreatedAt(rs.getObject("created_at", LocalDateTime.class));
        return category;
    }

    @Override
    protected void setId(Category category, int id) {
        category.setCategoryId(id);
    }

    @Override
    protected int getId(Category category) {
        return category.getCategoryId();
    }

    @Override
    protected String getName(Category category) {
        return category.getName();
    }
}
