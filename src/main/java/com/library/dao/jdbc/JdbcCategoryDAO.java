package com.library.dao.jdbc;

import com.library.dao.CategoryDAO;
import com.library.exception.DataAccessException;
import com.library.model.Category;
import com.library.model.Page;
import com.library.model.criteria.BookSearchCriteria;

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
 * JDBC implementation of {@link CategoryDAO}.
 *
 * <p>Handwritten SQL through JDBC; no ORM. Name uniqueness is case-insensitive, matching
 * the {@code ux_categories_name_lower} index.
 */
public class JdbcCategoryDAO implements CategoryDAO {

    private static final String FIND_BY_ID =
            "SELECT category_id, name, created_at FROM categories WHERE category_id = ?";

    private static final String COUNT_SEARCH_BASE =
            "SELECT COUNT(*) FROM categories WHERE 1 = 1";

    private static final String SEARCH_BASE =
            "SELECT category_id, name, created_at FROM categories WHERE 1 = 1";

    private static final String INSERT = "INSERT INTO categories (name) VALUES (?)";

    private static final String UPDATE = "UPDATE categories SET name = ? WHERE category_id = ?";

    private static final String DELETE = "DELETE FROM categories WHERE category_id = ?";

    private static final String NAME_EXISTS =
            "SELECT COUNT(*) FROM categories WHERE LOWER(name) = LOWER(?) " +
            "AND (? IS NULL OR category_id <> ?)";

    @Override
    public Optional<Category> findById(Connection c, int categoryId) {
        try (PreparedStatement ps = c.prepareStatement(FIND_BY_ID)) {
            ps.setInt(1, categoryId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapCategory(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find category by id: " + categoryId, e);
        }
    }

    @Override
    public Page<Category> search(Connection c, BookSearchCriteria criteria) {
        StringBuilder countSb = new StringBuilder(COUNT_SEARCH_BASE);
        StringBuilder dataSb = new StringBuilder(SEARCH_BASE);
        List<Object> params = new ArrayList<>();

        if (criteria.getKeyword() != null && !criteria.getKeyword().isBlank()) {
            countSb.append(" AND name ILIKE ?");
            dataSb.append(" AND name ILIKE ?");
            params.add("%" + criteria.getKeyword().trim() + "%");
        }

        int filterParamCount = params.size();

        dataSb.append(" ORDER BY category_id");
        dataSb.append(" LIMIT ? OFFSET ?");
        params.add(criteria.getSize());
        params.add(criteria.getPage() * criteria.getSize());

        try (PreparedStatement countPs = c.prepareStatement(countSb.toString())) {
            fillParams(countPs, params.subList(0, filterParamCount));
            try (ResultSet rs = countPs.executeQuery()) {
                long total = rs.next() ? rs.getLong(1) : 0;
                List<Category> content = new ArrayList<>();
                if (total > 0) {
                    try (PreparedStatement dataPs = c.prepareStatement(dataSb.toString())) {
                        fillParams(dataPs, params);
                        try (ResultSet drs = dataPs.executeQuery()) {
                            while (drs.next()) {
                                content.add(mapCategory(drs));
                            }
                        }
                    }
                }
                return new Page<>(content, total, criteria.getPage(), criteria.getSize());
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to search categories", e);
        }
    }

    @Override
    public int insert(Connection c, Category category) {
        try (PreparedStatement ps = c.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, category.getName());
            int affected = ps.executeUpdate();
            if (affected == 0) {
                throw new DataAccessException("Insert category failed: no rows affected");
            }
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new DataAccessException("Insert category failed: no generated key");
                }
                category.setCategoryId(keys.getInt(1));
            }
            return affected;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to insert category: " + category.getName(), e);
        }
    }

    @Override
    public void update(Connection c, Category category) {
        try (PreparedStatement ps = c.prepareStatement(UPDATE)) {
            ps.setString(1, category.getName());
            ps.setInt(2, category.getCategoryId());
            int affected = ps.executeUpdate();
            if (affected == 0) {
                throw new DataAccessException("Update category failed: no rows affected, id=" + category.getCategoryId());
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to update category id=" + category.getCategoryId(), e);
        }
    }

    @Override
    public void delete(Connection c, int categoryId) {
        try (PreparedStatement ps = c.prepareStatement(DELETE)) {
            ps.setInt(1, categoryId);
            int affected = ps.executeUpdate();
            if (affected == 0) {
                throw new DataAccessException("Delete category failed: no rows affected, id=" + categoryId);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to delete category id=" + categoryId, e);
        }
    }

    @Override
    public boolean nameExists(Connection c, String name, Integer excludeCategoryId) {
        try (PreparedStatement ps = c.prepareStatement(NAME_EXISTS)) {
            ps.setString(1, name);
            if (excludeCategoryId == null) {
                ps.setNull(2, java.sql.Types.INTEGER);
                ps.setNull(3, java.sql.Types.INTEGER);
            } else {
                ps.setInt(2, excludeCategoryId);
                ps.setInt(3, excludeCategoryId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getLong(1) > 0;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to check category name exists: " + name, e);
        }
    }

    private void fillParams(PreparedStatement ps, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            ps.setObject(i + 1, params.get(i));
        }
    }

    private Category mapCategory(ResultSet rs) throws SQLException {
        Category category = new Category();
        category.setCategoryId(rs.getInt("category_id"));
        category.setName(rs.getString("name"));
        category.setCreatedAt(rs.getObject("created_at", LocalDateTime.class));
        return category;
    }
}
