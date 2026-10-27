package com.library.dao.jdbc;

import com.library.dao.AuthorDAO;
import com.library.exception.DataAccessException;
import com.library.model.Author;
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
 * JDBC implementation of {@link AuthorDAO}.
 *
 * <p>Handwritten SQL through JDBC; no ORM.
 */
public class JdbcAuthorDAO implements AuthorDAO {

    private static final String FIND_BY_ID =
            "SELECT author_id, name, created_at FROM authors WHERE author_id = ?";

    private static final String COUNT_SEARCH_BASE =
            "SELECT COUNT(*) FROM authors WHERE 1 = 1";

    private static final String SEARCH_BASE =
            "SELECT author_id, name, created_at FROM authors WHERE 1 = 1";

    private static final String INSERT = "INSERT INTO authors (name) VALUES (?)";

    private static final String UPDATE = "UPDATE authors SET name = ? WHERE author_id = ?";

    private static final String DELETE = "DELETE FROM authors WHERE author_id = ?";

    private static final String NAME_EXISTS =
            "SELECT COUNT(*) FROM authors WHERE name = ? AND (? IS NULL OR author_id <> ?)";

    @Override
    public Optional<Author> findById(Connection c, int authorId) {
        try (PreparedStatement ps = c.prepareStatement(FIND_BY_ID)) {
            ps.setInt(1, authorId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapAuthor(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find author by id: " + authorId, e);
        }
    }

    @Override
    public Page<Author> search(Connection c, BookSearchCriteria criteria) {
        StringBuilder countSb = new StringBuilder(COUNT_SEARCH_BASE);
        StringBuilder dataSb = new StringBuilder(SEARCH_BASE);
        List<Object> params = new ArrayList<>();

        if (criteria.getKeyword() != null && !criteria.getKeyword().isBlank()) {
            countSb.append(" AND unaccent(name) ILIKE unaccent(?)");
            dataSb.append(" AND unaccent(name) ILIKE unaccent(?)");
            params.add(likePattern(criteria.getKeyword()));
        }

        int filterParamCount = params.size();

        dataSb.append(" ORDER BY author_id");
        dataSb.append(" LIMIT ? OFFSET ?");
        params.add(criteria.getSize());
        params.add((long) criteria.getPage() * criteria.getSize());

        try (PreparedStatement countPs = c.prepareStatement(countSb.toString())) {
            fillParams(countPs, params.subList(0, filterParamCount));
            try (ResultSet rs = countPs.executeQuery()) {
                long total = rs.next() ? rs.getLong(1) : 0;
                List<Author> content = new ArrayList<>();
                if (total > 0) {
                    try (PreparedStatement dataPs = c.prepareStatement(dataSb.toString())) {
                        fillParams(dataPs, params);
                        try (ResultSet drs = dataPs.executeQuery()) {
                            while (drs.next()) {
                                content.add(mapAuthor(drs));
                            }
                        }
                    }
                }
                return new Page<>(content, total, criteria.getPage(), criteria.getSize());
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to search authors", e);
        }
    }

    @Override
    public int insert(Connection c, Author author) {
        try (PreparedStatement ps = c.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, author.getName());
            int affected = ps.executeUpdate();
            if (affected == 0) {
                throw new DataAccessException("Insert author failed: no rows affected");
            }
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new DataAccessException("Insert author failed: no generated key");
                }
                author.setAuthorId(keys.getInt(1));
            }
            return affected;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to insert author: " + author.getName(), e);
        }
    }

    @Override
    public void update(Connection c, Author author) {
        try (PreparedStatement ps = c.prepareStatement(UPDATE)) {
            ps.setString(1, author.getName());
            ps.setInt(2, author.getAuthorId());
            int affected = ps.executeUpdate();
            if (affected == 0) {
                throw new DataAccessException("Update author failed: no rows affected, id=" + author.getAuthorId());
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to update author id=" + author.getAuthorId(), e);
        }
    }

    @Override
    public void delete(Connection c, int authorId) {
        try (PreparedStatement ps = c.prepareStatement(DELETE)) {
            ps.setInt(1, authorId);
            int affected = ps.executeUpdate();
            if (affected == 0) {
                throw new DataAccessException("Delete author failed: no rows affected, id=" + authorId);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to delete author id=" + authorId, e);
        }
    }

    @Override
    public boolean nameExists(Connection c, String name, Integer excludeAuthorId) {
        try (PreparedStatement ps = c.prepareStatement(NAME_EXISTS)) {
            ps.setString(1, name);
            if (excludeAuthorId == null) {
                ps.setNull(2, java.sql.Types.INTEGER);
                ps.setNull(3, java.sql.Types.INTEGER);
            } else {
                ps.setInt(2, excludeAuthorId);
                ps.setInt(3, excludeAuthorId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getLong(1) > 0;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to check author name exists: " + name, e);
        }
    }

    private String likePattern(String value) {
        if (value == null) {
            return null;
        }
        String escaped = value.trim()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }
    private void fillParams(PreparedStatement ps, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            ps.setObject(i + 1, params.get(i));
        }
    }

    private Author mapAuthor(ResultSet rs) throws SQLException {
        Author author = new Author();
        author.setAuthorId(rs.getInt("author_id"));
        author.setName(rs.getString("name"));
        author.setCreatedAt(rs.getObject("created_at", LocalDateTime.class));
        return author;
    }
}
