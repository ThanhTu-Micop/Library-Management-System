package com.library.dao;

import com.library.dao.jdbc.JdbcBookAuthorDAO;
import com.library.dao.support.TestDatabase;
import com.library.dao.support.TestFixtures;
import com.library.model.Author;
import com.library.model.Book;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DAO tests for {@link JdbcBookAuthorDAO} against {@code library_test}.
 */
class JdbcBookAuthorDAOTest {

    private final BookAuthorDAO bookAuthorDao = new JdbcBookAuthorDAO();

    @Test
    void replaceAndFindAuthors() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            Book book = TestFixtures.insertBook(c, "978-BA-000-001", "Author Link Book");
            bookAuthorDao.replaceForBook(c, book.getBookId(), List.of(1, 2));

            List<Author> authors = bookAuthorDao.findByBookId(c, book.getBookId());
            assertEquals(2, authors.size());
            assertEquals("Nam Cao", authors.get(0).getName());
            assertEquals("Nguyen Nhat Anh", authors.get(1).getName());
            assertNotNull(authors.get(0).getCreatedAt());

            bookAuthorDao.replaceForBook(c, book.getBookId(), List.of(3));
            List<Author> replaced = bookAuthorDao.findByBookId(c, book.getBookId());
            assertEquals(1, replaced.size());
            assertEquals("Robert C. Martin", replaced.get(0).getName());
        }
    }

    @Test
    void duplicateAuthorIdsAreDeduplicated() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            Book book = TestFixtures.insertBook(c, "978-BA-000-001", "Author Link Book");
            bookAuthorDao.replaceForBook(c, book.getBookId(), Arrays.asList(1, 1, 1));

            List<Author> authors = bookAuthorDao.findByBookId(c, book.getBookId());
            assertEquals(1, authors.size());
            assertEquals("Nguyen Nhat Anh", authors.get(0).getName());
        }
    }

    @Test
    void replaceWithEmptyListRemovesEveryLink() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            Book book = TestFixtures.insertBook(c, "978-BA-000-001", "Author Link Book");
            bookAuthorDao.replaceForBook(c, book.getBookId(), List.of(1, 2));
            bookAuthorDao.replaceForBook(c, book.getBookId(), List.of());
            assertTrue(bookAuthorDao.findByBookId(c, book.getBookId()).isEmpty());
        }
    }

    @Test
    void deleteByBookIdToleratesZeroRows() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            Book book = TestFixtures.insertBook(c, "978-BA-000-001", "Author Link Book");
            assertDoesNotThrow(() -> bookAuthorDao.deleteByBookId(c, book.getBookId()));
            assertTrue(bookAuthorDao.findByBookId(c, book.getBookId()).isEmpty());
        }
    }
}
