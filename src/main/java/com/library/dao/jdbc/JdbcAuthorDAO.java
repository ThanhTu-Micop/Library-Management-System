package com.library.dao.jdbc;

import com.library.dao.AuthorDAO;
import com.library.model.Author;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;

/**
 * JDBC implementation of {@link AuthorDAO}.
 *
 * <p>Handwritten SQL through JDBC; no ORM. CRUD, pagination, and duplicate-name checks are
 * inherited from {@link AbstractNamedEntityDAO}; this class supplies the author table
 * metadata and row mapping.
 */
public class JdbcAuthorDAO extends AbstractNamedEntityDAO<Author> implements AuthorDAO {

    public JdbcAuthorDAO() {
        super("authors", "author_id", "author", false);
    }

    @Override
    protected Author mapRow(ResultSet rs) throws SQLException {
        Author author = new Author();
        author.setAuthorId(rs.getInt("author_id"));
        author.setName(rs.getString("name"));
        author.setCreatedAt(rs.getObject("created_at", LocalDateTime.class));
        return author;
    }

    @Override
    protected void setId(Author author, int id) {
        author.setAuthorId(id);
    }

    @Override
    protected int getId(Author author) {
        return author.getAuthorId();
    }

    @Override
    protected String getName(Author author) {
        return author.getName();
    }
}
