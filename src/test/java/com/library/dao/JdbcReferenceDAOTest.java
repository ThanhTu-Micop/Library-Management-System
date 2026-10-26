package com.library.dao;

import com.library.dao.jdbc.JdbcAuthorDAO;
import com.library.dao.jdbc.JdbcCategoryDAO;
import com.library.dao.jdbc.JdbcPublisherDAO;
import com.library.dao.support.TestDatabase;
import com.library.exception.DataAccessException;
import com.library.model.Author;
import com.library.model.Category;
import com.library.model.Publisher;
import org.junit.jupiter.api.Test;

import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DAO tests for reference-data mapping and case-insensitive category names.
 */
class JdbcReferenceDAOTest {

    private final CategoryDAO categoryDao = new JdbcCategoryDAO();
    private final AuthorDAO authorDao = new JdbcAuthorDAO();
    private final PublisherDAO publisherDao = new JdbcPublisherDAO();

    @Test
    void categoryNamesAreUniqueCaseInsensitively() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestDatabase.resetAll(c);

            Category category = new Category(null, "Novel");
            assertEquals(1, categoryDao.insert(c, category));
            assertNotNull(category.getCategoryId());
            assertNotNull(categoryDao.findById(c, category.getCategoryId()).orElseThrow().getCreatedAt());
            assertTrue(categoryDao.nameExists(c, "novel", null));
            assertFalse(categoryDao.nameExists(c, "science", null));

            DataAccessException error = assertThrows(DataAccessException.class,
                    () -> categoryDao.insert(c, new Category(null, "NOVEL")));
            assertEquals("23505", error.getSqlState());
        }
    }

    @Test
    void authorAndPublisherCreatedAtAreMapped() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestDatabase.resetAll(c);

            Author author = new Author(null, "Test Author");
            assertEquals(1, authorDao.insert(c, author));
            assertNotNull(author.getAuthorId());
            assertNotNull(authorDao.findById(c, author.getAuthorId()).orElseThrow().getCreatedAt());

            Publisher publisher = new Publisher(null, "Test Publisher");
            assertEquals(1, publisherDao.insert(c, publisher));
            assertNotNull(publisher.getPublisherId());
            assertNotNull(publisherDao.findById(c, publisher.getPublisherId()).orElseThrow().getCreatedAt());
        }
    }
}
