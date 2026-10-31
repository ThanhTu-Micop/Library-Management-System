package com.library.dao.jdbc;

import com.library.dao.PublisherDAO;
import com.library.model.Publisher;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;

/**
 * JDBC implementation of {@link PublisherDAO}.
 *
 * <p>Handwritten SQL through JDBC; no ORM. CRUD, pagination, and duplicate-name checks are
 * inherited from {@link AbstractNamedEntityDAO}; this class supplies the publisher table
 * metadata and row mapping.
 */
public class JdbcPublisherDAO extends AbstractNamedEntityDAO<Publisher> implements PublisherDAO {

    public JdbcPublisherDAO() {
        super("publishers", "publisher_id", "publisher", false);
    }

    @Override
    protected Publisher mapRow(ResultSet rs) throws SQLException {
        Publisher publisher = new Publisher();
        publisher.setPublisherId(rs.getInt("publisher_id"));
        publisher.setName(rs.getString("name"));
        publisher.setCreatedAt(rs.getObject("created_at", LocalDateTime.class));
        return publisher;
    }

    @Override
    protected void setId(Publisher publisher, int id) {
        publisher.setPublisherId(id);
    }

    @Override
    protected int getId(Publisher publisher) {
        return publisher.getPublisherId();
    }

    @Override
    protected String getName(Publisher publisher) {
        return publisher.getName();
    }
}
