package com.library.dao;

import com.library.dao.jdbc.JdbcBookAuthorDAO;
import com.library.dao.jdbc.JdbcBookCopyDAO;
import com.library.dao.jdbc.JdbcBookDAO;
import com.library.dao.support.TestDatabase;
import com.library.dao.support.TestFixtures;
import com.library.exception.DataAccessException;
import com.library.model.Book;
import com.library.model.BookCopy;
import com.library.model.Page;
import com.library.model.criteria.BookSearchCriteria;
import com.library.model.enums.CopyStatus;
import com.library.config.AppConfig;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DAO tests for {@link JdbcBookDAO} against {@code library_test}.
 *
 * <p>ISBN uniqueness, length, trimming, publication-year bounds, and the derived
 * {@code availableCopies} projection are verified here because they are part of the book
 * DAO contract. Schema-only UPDATE invariants remain in {@link DataConsistencyTest}.
 */
class JdbcBookDAOTest {

    private final BookDAO dao = new JdbcBookDAO();
    private final BookCopyDAO copyDao = new JdbcBookCopyDAO();
    private final BookAuthorDAO bookAuthorDao = new JdbcBookAuthorDAO();

    @Test
    void insertAndFindById() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
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
            TestFixtures.prepare(c);
            assertFalse(dao.findById(c, 99999).isPresent());
        }
    }

    @Test
    void updateBook() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
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
            TestFixtures.prepare(c);
            Book book = new Book(null, "978-000-000-003", "To Delete", 1, 1, 1999);
            dao.insert(c, book);

            dao.delete(c, book.getBookId());
            assertFalse(dao.findById(c, book.getBookId()).isPresent());
        }
    }

    /**
     * Merges the former insert-only, update-only, and exists-helper ISBN tests.
     * The no-arg {@code isbnExists} and self-excluding variants are both exercised.
     */
    @Test
    void isbnIsUniqueOnInsertAndUpdate() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            Book first = TestFixtures.insertBook(c, "978-DUP-000-001", "Book One");
            Book second = TestFixtures.insertBook(c, "978-DUP-000-002", "Book Two");

            DataAccessException insertError = assertThrows(DataAccessException.class,
                    () -> TestFixtures.insertBook(c, "978-DUP-000-001", "Duplicate Insert"));
            assertEquals(AppConfig.SQLSTATE_UNIQUE_VIOLATION, insertError.getSqlState());

            second.setIsbn("978-DUP-000-001");
            DataAccessException updateError = assertThrows(DataAccessException.class,
                    () -> dao.update(c, second));
            assertEquals(AppConfig.SQLSTATE_UNIQUE_VIOLATION, updateError.getSqlState(),
                    "UPDATE must hit the same unique constraint as INSERT");

            assertAll(
                    () -> assertTrue(dao.isbnExists(c, "978-DUP-000-001", null)),
                    () -> assertFalse(dao.isbnExists(c, "978-NEW-000-001", null)),
                    () -> assertFalse(dao.isbnExists(c, "978-DUP-000-001", first.getBookId()),
                            "isbnExists must exclude the row being edited"),
                    () -> assertEquals("978-DUP-000-002",
                            dao.findById(c, second.getBookId()).orElseThrow().getIsbn(),
                            "The failed UPDATE must leave the stored ISBN unchanged"));
        }
    }

    /**
     * 13 and 20 characters are accepted; 12 fails the CHECK constraint and 21 exceeds
     * {@code VARCHAR(20)}. Both negative branches are part of the same length rule.
     */
    @Test
    void isbnLengthMustBeBetween13And20() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            assertEquals(1, dao.insert(c, new Book(null, "1234567890123", "Thirteen", 1, 1, 2010)));
            assertEquals(1, dao.insert(c, new Book(null, "12345678901234567890", "Twenty", 1, 1, 2010)));

            DataAccessException tooShort = assertThrows(DataAccessException.class,
                    () -> dao.insert(c, new Book(null, "123456789012", "Twelve", 1, 1, 2010)));
            assertEquals(AppConfig.SQLSTATE_CHECK_VIOLATION, tooShort.getSqlState());

            DataAccessException tooLong = assertThrows(DataAccessException.class,
                    () -> dao.insert(c, new Book(null, "123456789012345678901", "TwentyOne", 1, 1, 2010)));
            assertEquals("22001", tooLong.getSqlState(),
                    "21 characters exceed VARCHAR(20) before the CHECK can run");
        }
    }

    /** Insert and update must both trim surrounding whitespace before storing/checking ISBN. */
    @Test
    void isbnIsTrimmedOnInsertAndUpdate() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            Book book = new Book(null, "  978-TRIM-000-001  ", "Trimmed", 1, 1, 2020);
            dao.insert(c, book);

            assertEquals("978-TRIM-000-001",
                    dao.findById(c, book.getBookId()).orElseThrow().getIsbn());
            assertTrue(dao.isbnExists(c, " 978-TRIM-000-001 ", null));

            book.setIsbn("  978-TRIM-000-002  ");
            dao.update(c, book);

            assertEquals("978-TRIM-000-002",
                    dao.findById(c, book.getBookId()).orElseThrow().getIsbn(),
                    "JdbcBookDAO.update must trim ISBN the same way insert does");
            assertTrue(dao.isbnExists(c, " 978-TRIM-000-002 ", null));
        }
    }

    /**
     * A missing publication year stays {@code NULL}; the CHECK range is inclusive
     * (1000 and 2100 accepted, 999 and 2101 rejected).
     */
    @Test
    void publicationYearAcceptsBoundariesAndPreservesNull() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);

            Book unknown = new Book(null, "978-YEAR-000-001", "Unknown Year", 1, 1, null);
            dao.insert(c, unknown);
            assertNull(dao.findById(c, unknown.getBookId()).orElseThrow().getPublicationYear());
            assertNotNull(dao.findById(c, unknown.getBookId()).orElseThrow().getCreatedAt());

            assertEquals(1, dao.insert(c,
                    new Book(null, "978-Y1-000-000-001", "Year 1000", 1, 1, 1000)));
            assertEquals(1, dao.insert(c,
                    new Book(null, "978-Y2-000-000-002", "Year 2100", 1, 1, 2100)));

            DataAccessException tooLow = assertThrows(DataAccessException.class,
                    () -> dao.insert(c, new Book(null, "978-Y3-000-000-003", "Year 999", 1, 1, 999)));
            assertEquals(AppConfig.SQLSTATE_CHECK_VIOLATION, tooLow.getSqlState());

            DataAccessException tooHigh = assertThrows(DataAccessException.class,
                    () -> dao.insert(c, new Book(null, "978-Y4-000-000-004", "Year 2101", 1, 1, 2101)));
            assertEquals(AppConfig.SQLSTATE_CHECK_VIOLATION, tooHigh.getSqlState());
        }
    }

    @Test
    void keywordFilterRejectsNonMatchingBook() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
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
            TestFixtures.prepare(c);
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
            TestFixtures.prepare(c);
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
            TestFixtures.prepare(c);
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
            TestFixtures.prepare(c);
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

    /**
     * The derived {@code availableCopies} field must equal the real count of
     * {@code book_copies.status = 'AVAILABLE'} rows and react to status changes.
     */
    @Test
    void availableCopiesTracksOnlyAvailableCopies() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            Book book = TestFixtures.insertBook(c, "978-AB-000-000-001", "Available Book");

            assertEquals(0, dao.findById(c, book.getBookId()).orElseThrow().getAvailableCopies());

            BookCopy a1 = TestFixtures.insertCopy(c, book, "AB-CP-001", CopyStatus.AVAILABLE);
            BookCopy a2 = TestFixtures.insertCopy(c, book, "AB-CP-002", CopyStatus.AVAILABLE);
            TestFixtures.insertCopy(c, book, "AB-CP-003", CopyStatus.BORROWED);
            TestFixtures.insertCopy(c, book, "AB-CP-004", CopyStatus.MAINTENANCE);

            Book found = dao.findById(c, book.getBookId()).orElseThrow();
            assertEquals(2, found.getAvailableCopies());

            long raw = rawCount(c,
                    "SELECT COUNT(*) FROM book_copies WHERE book_id = ? AND status = 'AVAILABLE'",
                    book.getBookId());
            assertEquals(raw, (long) found.getAvailableCopies());

            copyDao.updateStatus(c, a1.getCopyId(), CopyStatus.BORROWED);
            assertEquals(1, dao.findById(c, book.getBookId()).orElseThrow().getAvailableCopies());

            copyDao.updateStatus(c, a2.getCopyId(), CopyStatus.MAINTENANCE);
            assertEquals(0, dao.findById(c, book.getBookId()).orElseThrow().getAvailableCopies());

            copyDao.updateStatus(c, a1.getCopyId(), CopyStatus.AVAILABLE);
            assertEquals(1, dao.findById(c, book.getBookId()).orElseThrow().getAvailableCopies());
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
