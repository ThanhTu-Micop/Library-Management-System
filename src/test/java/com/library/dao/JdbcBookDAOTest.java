package com.library.dao;

import com.library.dao.jdbc.JdbcBookAuthorDAO;
import com.library.dao.jdbc.JdbcBookCopyDAO;
import com.library.dao.jdbc.JdbcBookDAO;
import com.library.dao.support.TestDatabase;
import com.library.exception.DataAccessException;
import com.library.model.Book;
import com.library.model.BookCopy;
import com.library.model.Page;
import com.library.model.criteria.BookSearchCriteria;
import com.library.model.enums.CopyStatus;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DAO tests for {@link JdbcBookDAO} against {@code library_test}.
 */
class JdbcBookDAOTest {

    private final BookDAO dao = new JdbcBookDAO();
    private final BookCopyDAO copyDao = new JdbcBookCopyDAO();
    private final BookAuthorDAO bookAuthorDao = new JdbcBookAuthorDAO();

    private void prepare(Connection c) throws Exception {
        TestDatabase.resetAll(c);
        TestDatabase.seedReferenceData(c);
    }

    @Test
    void insertAndFindById() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            prepare(c);
            Book book = new Book(null, "978-000-000-001", "Clean Code", 1, 1, 2008);
            int affected = dao.insert(c, book);
            assertEquals(1, affected);
            assertNotNull(book.getBookId());

            Optional<Book> found = dao.findById(c, book.getBookId());
            assertTrue(found.isPresent());
            assertEquals("Clean Code", found.get().getTitle());
            assertEquals("978-000-000-001", found.get().getIsbn());
            assertEquals("Literature Publisher", found.get().getPublisherName());
            assertEquals("Novel", found.get().getCategoryName());
            assertEquals(0, found.get().getAvailableCopies());
            assertNotNull(found.get().getCreatedAt());
        }
    }

    @Test
    void findByIdNotFound() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            prepare(c);
            assertFalse(dao.findById(c, 99999).isPresent());
        }
    }

    @Test
    void updateBook() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            prepare(c);
            Book book = new Book(null, "978-000-000-002", "Old Title", 1, 1, 2000);
            dao.insert(c, book);

            book.setTitle("New Title");
            book.setPublicationYear(2001);
            book.setPublisherId(2);
            book.setCategoryId(2);
            dao.update(c, book);

            Optional<Book> found = dao.findById(c, book.getBookId());
            assertTrue(found.isPresent());
            assertEquals("New Title", found.get().getTitle());
            assertEquals(2001, found.get().getPublicationYear());
            assertEquals("Science and Technology Publisher", found.get().getPublisherName());
            assertEquals("Science", found.get().getCategoryName());
        }
    }

    @Test
    void deleteBook() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            prepare(c);
            Book book = new Book(null, "978-000-000-003", "To Delete", 1, 1, 1999);
            dao.insert(c, book);

            dao.delete(c, book.getBookId());
            assertFalse(dao.findById(c, book.getBookId()).isPresent());
        }
    }

    @Test
    void isbnMustBeUnique() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            prepare(c);
            dao.insert(c, new Book(null, "978-DUP-000-001", "Book One", 1, 1, 2010));

            DataAccessException error = assertThrows(DataAccessException.class,
                    () -> dao.insert(c, new Book(null, "978-DUP-000-001", "Book Two", 1, 1, 2011)));
            assertEquals("23505", error.getSqlState());
            assertTrue(dao.isbnExists(c, "978-DUP-000-001", null));
            assertFalse(dao.isbnExists(c, "978-NEW-000-001", null));
        }
    }

    @Test
    void isbnLengthBoundaries() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            prepare(c);
            Book thirteen = new Book(null, "1234567890123", "Thirteen", 1, 1, 2010);
            Book twenty = new Book(null, "12345678901234567890", "Twenty", 1, 1, 2010);
            assertEquals(1, dao.insert(c, thirteen));
            assertEquals(1, dao.insert(c, twenty));

            DataAccessException error = assertThrows(DataAccessException.class,
                    () -> dao.insert(c, new Book(null, "123456789012", "Twelve", 1, 1, 2010)));
            assertEquals("23514", error.getSqlState());
        }
    }

    @Test
    void isbnIsTrimmed() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            prepare(c);
            Book book = new Book(null, "  978-TRIM-000-001  ", "Trimmed", 1, 1, 2020);
            dao.insert(c, book);

            Book found = dao.findById(c, book.getBookId()).orElseThrow();
            assertEquals("978-TRIM-000-001", found.getIsbn());
            assertTrue(dao.isbnExists(c, " 978-TRIM-000-001 ", null));
        }
    }

    @Test
    void nullPublicationYearRemainsNull() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            prepare(c);
            Book book = new Book(null, "978-YEAR-000-001", "Unknown Year", 1, 1, null);
            dao.insert(c, book);

            Book found = dao.findById(c, book.getBookId()).orElseThrow();
            assertNull(found.getPublicationYear());
            assertNotNull(found.getCreatedAt());
        }
    }

    @Test
    void keywordFilterRejectsNonMatchingBook() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            prepare(c);
            dao.insert(c, new Book(null, "978-KW-000-001", "Clean Code", 1, 1, 2008));
            dao.insert(c, new Book(null, "978-KW-000-002", "Foundation", 2, 2, 1951));

            BookSearchCriteria criteria = new BookSearchCriteria();
            criteria.setKeyword("Clean");
            Page<Book> page = dao.search(c, criteria);
            assertEquals(1, page.getTotalElements());
            assertEquals("Clean Code", page.getContent().get(0).getTitle());

            criteria.setKeyword("NoSuchTitle");
            assertEquals(0, dao.search(c, criteria).getTotalElements());
        }
    }

    @Test
    void publisherAndCategoryFilters() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            prepare(c);
            dao.insert(c, new Book(null, "978-FLT-000-001", "Publisher One", 1, 1, 2000));
            dao.insert(c, new Book(null, "978-FLT-000-002", "Publisher Two", 2, 2, 2001));

            BookSearchCriteria criteria = new BookSearchCriteria();
            criteria.setPublisherId(1);
            assertEquals(1, dao.search(c, criteria).getTotalElements());

            criteria.setPublisherId(null);
            criteria.setCategoryId(2);
            Page<Book> page = dao.search(c, criteria);
            assertEquals(1, page.getTotalElements());
            assertEquals("Publisher Two", page.getContent().get(0).getTitle());
        }
    }

    @Test
    void authorNameFilter() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            prepare(c);
            Book book = new Book(null, "978-AUT-000-001", "Clean Code", 1, 1, 2008);
            dao.insert(c, book);
            bookAuthorDao.replaceForBook(c, book.getBookId(), List.of(3));

            BookSearchCriteria criteria = new BookSearchCriteria();
            criteria.setAuthorName("Robert");
            assertEquals(1, dao.search(c, criteria).getTotalElements());

            criteria.setAuthorName("Isaac");
            assertEquals(0, dao.search(c, criteria).getTotalElements());
        }
    }

    @Test
    void availableOnlyFilter() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            prepare(c);
            Book available = new Book(null, "978-AVL-000-001", "Available Book", 1, 1, 2000);
            Book borrowed = new Book(null, "978-AVL-000-002", "Borrowed Book", 1, 1, 2001);
            dao.insert(c, available);
            dao.insert(c, borrowed);
            copyDao.insert(c, new BookCopy(null, available.getBookId(), "AVL-CP-001", CopyStatus.AVAILABLE));
            copyDao.insert(c, new BookCopy(null, borrowed.getBookId(), "AVL-CP-002", CopyStatus.BORROWED));

            BookSearchCriteria criteria = new BookSearchCriteria();
            criteria.setAvailableOnly(true);
            Page<Book> page = dao.search(c, criteria);
            assertEquals(1, page.getTotalElements());
            assertEquals("Available Book", page.getContent().get(0).getTitle());
            assertEquals(1, page.getContent().get(0).getAvailableCopies());
        }
    }

    @Test
    void paginationAppliesFiltersToCountAndData() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            prepare(c);
            for (int i = 1; i <= 5; i++) {
                dao.insert(c, new Book(null, "978-PAGE-000-00" + i,
                        "Filter Book " + i, 1, 1, 2010 + i));
            }
            dao.insert(c, new Book(null, "978-PAGE-000-099", "Other Publisher Book", 2, 2, 2020));

            BookSearchCriteria criteria = new BookSearchCriteria();
            criteria.setKeyword("Filter Book");
            criteria.setPublisherId(1);
            criteria.setPage(0);
            criteria.setSize(2);

            Page<Book> page = dao.search(c, criteria);
            assertEquals(5, page.getTotalElements());
            assertEquals(3, page.getTotalPages());
            assertEquals(2, page.getContent().size());

            criteria.setPage(2);
            Page<Book> last = dao.search(c, criteria);
            assertEquals(1, last.getContent().size());
            assertTrue(last.isLast());
        }
    }
}
