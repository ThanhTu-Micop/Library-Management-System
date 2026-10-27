package com.library.dao.jdbc;

import com.library.dao.PublisherDAO;
import com.library.exception.DataAccessException;
import com.library.model.Page;
import com.library.model.Publisher;
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
 * JDBC implementation of {@link PublisherDAO}.
 *
 * <p>Handwritten SQL through JDBC; no ORM.
 */
public class JdbcPublisherDAO implements PublisherDAO {

    private static final String FIND_BY_ID =
            "SELECT publisher_id, name, created_at FROM publishers WHERE publisher_id = ?";

    private static final String COUNT_SEARCH_BASE =
            "SELECT COUNT(*) FROM publishers WHERE 1 = 1";

    private static final String SEARCH_BASE =
            "SELECT publisher_id, name, created_at FROM publishers WHERE 1 = 1";

    private static final String INSERT = "INSERT INTO publishers (name) VALUES (?)";

    private static final String UPDATE = "UPDATE publishers SET name = ? WHERE publisher_id = ?";

    private static final String DELETE = "DELETE FROM publishers WHERE publisher_id = ?";

    private static final String NAME_EXISTS =
            "SELECT COUNT(*) FROM publishers WHERE name = ? AND (? IS NULL OR publisher_id <> ?)";

    @Override
    public Optional<Publisher> findById(Connection c, int publisherId) {
        try (PreparedStatement ps = c.prepareStatement(FIND_BY_ID)) {
            ps.setInt(1, publisherId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapPublisher(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find publisher by id: " + publisherId, e);
        }
    }

    @Override
    public Page<Publisher> search(Connection c, BookSearchCriteria criteria) {
        StringBuilder countSb = new StringBuilder(COUNT_SEARCH_BASE);
        StringBuilder dataSb = new StringBuilder(SEARCH_BASE);
        List<Object> params = new ArrayList<>();

        if (criteria.getKeyword() != null && !criteria.getKeyword().isBlank()) {
            countSb.append(" AND unaccent(name) ILIKE unaccent(?)");
            dataSb.append(" AND unaccent(name) ILIKE unaccent(?)");
            params.add(likePattern(criteria.getKeyword()));
        }

        int filterParamCount = params.size();

        dataSb.append(" ORDER BY publisher_id");
        dataSb.append(" LIMIT ? OFFSET ?");
        params.add(criteria.getSize());
        params.add((long) criteria.getPage() * criteria.getSize());

        try (PreparedStatement countPs = c.prepareStatement(countSb.toString())) {
            fillParams(countPs, params.subList(0, filterParamCount));
            try (ResultSet rs = countPs.executeQuery()) {
                long total = rs.next() ? rs.getLong(1) : 0;
                List<Publisher> content = new ArrayList<>();
                if (total > 0) {
                    try (PreparedStatement dataPs = c.prepareStatement(dataSb.toString())) {
                        fillParams(dataPs, params);
                        try (ResultSet drs = dataPs.executeQuery()) {
                            while (drs.next()) {
                                content.add(mapPublisher(drs));
                            }
                        }
                    }
                }
                return new Page<>(content, total, criteria.getPage(), criteria.getSize());
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to search publishers", e);
        }
    }

    @Override
    public int insert(Connection c, Publisher publisher) {
        try (PreparedStatement ps = c.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, publisher.getName());
            int affected = ps.executeUpdate();
            if (affected == 0) {
                throw new DataAccessException("Insert publisher failed: no rows affected");
            }
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new DataAccessException("Insert publisher failed: no generated key");
                }
                publisher.setPublisherId(keys.getInt(1));
            }
            return affected;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to insert publisher: " + publisher.getName(), e);
        }
    }

    @Override
    public void update(Connection c, Publisher publisher) {
        try (PreparedStatement ps = c.prepareStatement(UPDATE)) {
            ps.setString(1, publisher.getName());
            ps.setInt(2, publisher.getPublisherId());
            int affected = ps.executeUpdate();
            if (affected == 0) {
                throw new DataAccessException("Update publisher failed: no rows affected, id=" + publisher.getPublisherId());
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to update publisher id=" + publisher.getPublisherId(), e);
        }
    }

    @Override
    public void delete(Connection c, int publisherId) {
        try (PreparedStatement ps = c.prepareStatement(DELETE)) {
            ps.setInt(1, publisherId);
            int affected = ps.executeUpdate();
            if (affected == 0) {
                throw new DataAccessException("Delete publisher failed: no rows affected, id=" + publisherId);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to delete publisher id=" + publisherId, e);
        }
    }

    @Override
    public boolean nameExists(Connection c, String name, Integer excludePublisherId) {
        try (PreparedStatement ps = c.prepareStatement(NAME_EXISTS)) {
            ps.setString(1, name);
            if (excludePublisherId == null) {
                ps.setNull(2, java.sql.Types.INTEGER);
                ps.setNull(3, java.sql.Types.INTEGER);
            } else {
                ps.setInt(2, excludePublisherId);
                ps.setInt(3, excludePublisherId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getLong(1) > 0;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to check publisher name exists: " + name, e);
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

    private Publisher mapPublisher(ResultSet rs) throws SQLException {
        Publisher publisher = new Publisher();
        publisher.setPublisherId(rs.getInt("publisher_id"));
        publisher.setName(rs.getString("name"));
        publisher.setCreatedAt(rs.getObject("created_at", LocalDateTime.class));
        return publisher;
    }
}
