package com.library.dao.jdbc;

import com.library.model.Page;
import com.library.model.criteria.BookSearchCriteria;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Shared JDBC implementation for reference tables shaped as {@code (id, name, created_at)}:
 * authors, categories, and publishers.
 *
 * <p>Subclasses only supply table metadata and row mapping. The base class owns the common
 * CRUD, keyword search, pagination, and duplicate-name check, so those SQL patterns are not
 * repeated three times. Transactions remain caller-owned.
 *
 * @param <T> the reference entity type
 */
abstract class AbstractNamedEntityDAO<T> {

    private final String table;
    private final String idColumn;
    private final String entityLabel;
    private final boolean caseInsensitiveName;

    AbstractNamedEntityDAO(String table, String idColumn, String entityLabel,
                           boolean caseInsensitiveName) {
        this.table = table;
        this.idColumn = idColumn;
        this.entityLabel = entityLabel;
        this.caseInsensitiveName = caseInsensitiveName;
    }

    public Optional<T> findById(Connection c, int id) {
        String sql = "SELECT " + idColumn + ", name, created_at FROM " + table
                + " WHERE " + idColumn + " = ?";
        return JdbcSupport.queryOne(c, sql,
                "Failed to find " + entityLabel + " by id: " + id, this::mapRow, id);
    }

    public Page<T> search(Connection c, BookSearchCriteria criteria) {
        String countBase = "SELECT COUNT(*) FROM " + table + " WHERE 1 = 1";
        String dataBase = "SELECT " + idColumn + ", name, created_at FROM " + table + " WHERE 1 = 1";
        StringBuilder countSb = new StringBuilder(countBase);
        StringBuilder dataSb = new StringBuilder(dataBase);
        List<Object> params = new ArrayList<>();

        if (criteria.getKeyword() != null && !criteria.getKeyword().isBlank()) {
            String clause = " AND unaccent(name) ILIKE unaccent(?)";
            countSb.append(clause);
            dataSb.append(clause);
            params.add(JdbcSupport.likePattern(criteria.getKeyword()));
        }

        int filterParamCount = params.size();
        dataSb.append(" ORDER BY ").append(idColumn);
        dataSb.append(" LIMIT ? OFFSET ?");
        params.add(criteria.getSize());
        params.add((long) criteria.getPage() * criteria.getSize());

        return JdbcSupport.search(c, countSb.toString(), dataSb.toString(), params,
                filterParamCount, criteria.getPage(), criteria.getSize(),
                "Failed to search " + table, this::mapRow);
    }

    public int insert(Connection c, T entity) {
        String sql = "INSERT INTO " + table + " (name) VALUES (?)";
        return JdbcSupport.insert(c, sql,
                "Failed to insert " + entityLabel + ": " + getName(entity),
                id -> setId(entity, id), getName(entity));
    }

    public void update(Connection c, T entity) {
        String sql = "UPDATE " + table + " SET name = ? WHERE " + idColumn + " = ?";
        JdbcSupport.updateExpectingRow(c, sql,
                "Failed to update " + entityLabel + " id=" + getId(entity),
                getName(entity), getId(entity));
    }

    public void delete(Connection c, int id) {
        String sql = "DELETE FROM " + table + " WHERE " + idColumn + " = ?";
        JdbcSupport.updateExpectingRow(c, sql,
                "Failed to delete " + entityLabel + " id=" + id, id);
    }

    public boolean nameExists(Connection c, String name, Integer excludeId) {
        String comparison = caseInsensitiveName ? "LOWER(name) = LOWER(?)" : "name = ?";
        String sql = "SELECT COUNT(*) FROM " + table + " WHERE " + comparison
                + " AND " + idColumn + " <> ?";
        int excluded = excludeId != null ? excludeId : -1;
        return JdbcSupport.exists(c, sql,
                "Failed to check " + entityLabel + " name exists: " + name, name, excluded);
    }

    /** Maps one row selected as {@code id, name, created_at}. */
    protected abstract T mapRow(ResultSet rs) throws SQLException;

    protected abstract void setId(T entity, int id);

    protected abstract int getId(T entity);

    protected abstract String getName(T entity);
}
