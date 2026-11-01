package com.library.dao;

import com.library.dao.jdbc.JdbcBookAuthorDAO;
import com.library.dao.support.TestDatabase;
import com.library.dao.support.TestFixtures;
import com.library.model.Author;
import com.library.model.Book;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DAO tests for {@link JdbcBookAuthorDAO} against {@code library_test}.
 *
 * <p>Replace semantics, order independence, duplicate de-duplication, and empty deletes are
 * all owned here; the cross-table test no longer repeats them.
 */
class JdbcBookAuthorDAOTest {

    private final BookAuthorDAO bookAuthorDao = new JdbcBookAuthorDAO();

    @Test
    void replaceForBookReplacesSetAndIsOrderIndependent() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            Book book1 = TestFixtures.insertBook(c, "978-BA-000-001", "Author Link Book");
            bookAuthorDao.replaceForBook(c, book1.getBookId(), List.of(1, 2));

            List<Author> authors = bookAuthorDao.findByBookId(c, book1.getBookId());
            assertEquals(2, authors.size());
            assertEquals("Nam Cao", authors.get(0).getName());
            assertEquals("Nguyen Nhat Anh", authors.get(1).getName());
            assertNotNull(authors.get(0).getCreatedAt());

            bookAuthorDao.replaceForBook(c, book1.getBookId(), List.of(3));
            List<Author> replaced = bookAuthorDao.findByBookId(c, book1.getBookId());
            assertEquals(1, replaced.size());
            assertEquals("Robert C. Martin", replaced.get(0).getName(),
                    "replaceForBook must remove authors 1 and 2, not append 3 to the existing set");
            assertEquals(1, rawCount(c, "SELECT COUNT(*) FROM book_authors WHERE book_id = ?",
                    book1.getBookId()));

            Book book2 = TestFixtures.insertBook(c, "978-BA-000-002", "Order Book 1");
            Book book3 = TestFixtures.insertBook(c, "978-BA-000-003", "Order Book 2");
            bookAuthorDao.replaceForBook(c, book2.getBookId(), List.of(1, 2, 3));
            bookAuthorDao.replaceForBook(c, book3.getBookId(), List.of(3, 2, 1));

            List<Author> a2 = bookAuthorDao.findByBookId(c, book2.getBookId());
            List<Author> a3 = bookAuthorDao.findByBookId(c, book3.getBookId());
            assertEquals(3, a2.size());
            assertEquals(3, a3.size());
            assertEquals(a2.get(0).getAuthorId(), a3.get(0).getAuthorId());
            assertEquals(a2.get(1).getAuthorId(), a3.get(1).getAuthorId());
            assertEquals(a2.get(2).getAuthorId(), a3.get(2).getAuthorId());
        }
    }

    @Test
    void duplicateAuthorIdsAreDeduplicated() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            Book book = TestFixtures.insertBook(c, "978-BA-000-004", "Author Link Book");
            bookAuthorDao.replaceForBook(c, book.getBookId(), Arrays.asList(1, 1, 1));

            List<Author> authors = bookAuthorDao.findByBookId(c, book.getBookId());
            assertEquals(1, authors.size());
            assertEquals("Nguyen Nhat Anh", authors.get(0).getName());
        }
    }

    @Test
    void emptyReplaceAndDeleteAreSafeNoOps() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            Book book = TestFixtures.insertBook(c, "978-BA-000-005", "Author Link Book");
            bookAuthorDao.replaceForBook(c, book.getBookId(), List.of(1, 2));
            bookAuthorDao.replaceForBook(c, book.getBookId(), List.of());
            assertTrue(bookAuthorDao.findByBookId(c, book.getBookId()).isEmpty());

            assertDoesNotThrow(() -> bookAuthorDao.deleteByBookId(c, book.getBookId()));
            assertTrue(bookAuthorDao.findByBookId(c, book.getBookId()).isEmpty());
        }
    }

    private long rawCount(Connection c, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        }
    }
}
