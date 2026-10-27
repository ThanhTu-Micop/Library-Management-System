package com.library.dao;

import com.library.dao.jdbc.JdbcAuthorDAO;
import com.library.dao.jdbc.JdbcBookAuthorDAO;
import com.library.dao.jdbc.JdbcBookDAO;
import com.library.dao.jdbc.JdbcMemberDAO;
import com.library.dao.support.TestDatabase;
import com.library.model.Author;
import com.library.model.Book;
import com.library.model.Member;
import com.library.model.criteria.BookSearchCriteria;
import com.library.model.criteria.MemberSearchCriteria;
import com.library.model.enums.MemberStatus;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Vietnamese diacritic search tests.
 *
 * <p>PostgreSQL's {@code unaccent} extension normalizes both the stored column and the
 * caller-supplied pattern, so a title such as {@code Chí Phèo} matches {@code Chí Phèo},
 * {@code Chi Pheo}, {@code CHÍ PHÈO}, and {@code chí phèo}. This class also verifies that
 * LIKE wildcards remain escaped while accent normalization is active.
 */
class DiacriticSearchTest {

    private static final String CHI_PHEO_ISBN = "978-604-1-00002-2";
    private static final String CHI_PHEO_TITLE = "Chí Phèo";

    private final BookDAO bookDao = new JdbcBookDAO();
    private final AuthorDAO authorDao = new JdbcAuthorDAO();
    private final BookAuthorDAO bookAuthorDao = new JdbcBookAuthorDAO();
    private final MemberDAO memberDao = new JdbcMemberDAO();

    private void prepare(Connection c) throws Exception {
        TestDatabase.resetAll(c);
        TestDatabase.seedReferenceData(c);
    }

    private Book insertBook(Connection c, String isbn, String title) {
        Book book = new Book(null, isbn, title, 1, 1, 1941);
        bookDao.insert(c, book);
        return book;
    }

    private List<Book> searchBooks(Connection c, String keyword) throws Exception {
        BookSearchCriteria criteria = new BookSearchCriteria();
        criteria.setKeyword(keyword);
        return bookDao.search(c, criteria).getContent();
    }

    private List<Book> searchByAuthor(Connection c, String authorName) throws Exception {
        BookSearchCriteria criteria = new BookSearchCriteria();
        criteria.setAuthorName(authorName);
        return bookDao.search(c, criteria).getContent();
    }

    private List<Member> searchMembers(Connection c, String keyword) throws Exception {
        MemberSearchCriteria criteria = new MemberSearchCriteria();
        criteria.setKeyword(keyword);
        return memberDao.search(c, criteria).getContent();
    }

    @Test
    void accentedKeywordFindsAccentedTitle() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            prepare(c);
            insertBook(c, CHI_PHEO_ISBN, CHI_PHEO_TITLE);

            List<Book> found = searchBooks(c, CHI_PHEO_TITLE);

            assertEquals(1, found.size());
            assertEquals(CHI_PHEO_TITLE, found.get(0).getTitle());
        }
    }

    @Test
    void unaccentedKeywordFindsAccentedTitle() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            prepare(c);
            insertBook(c, CHI_PHEO_ISBN, CHI_PHEO_TITLE);

            List<Book> found = searchBooks(c, "Chi Pheo");

            assertEquals(1, found.size(), "Searching without diacritics must still find Chí Phèo");
            assertEquals(CHI_PHEO_TITLE, found.get(0).getTitle());
        }
    }

    @Test
    void uppercaseAccentedKeywordFindsTitle() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            prepare(c);
            insertBook(c, CHI_PHEO_ISBN, CHI_PHEO_TITLE);

            List<Book> found = searchBooks(c, "CHÍ PHÈO");

            assertEquals(1, found.size(), "ILIKE must remain case-insensitive after unaccent normalization");
        }
    }

    @Test
    void partialUnaccentedKeywordFindsTitle() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            prepare(c);
            insertBook(c, CHI_PHEO_ISBN, CHI_PHEO_TITLE);

            List<Book> found = searchBooks(c, "pheo");

            assertEquals(1, found.size());
            assertEquals(CHI_PHEO_TITLE, found.get(0).getTitle());
        }
    }

    @Test
    void authorNameSearchIsAccentInsensitive() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            prepare(c);
            Author author = new Author(null, "Nguyễn Nhất Anh");
            authorDao.insert(c, author);
            Book book = insertBook(c, "978-604-1-00001-1", "Tôi thấy hoa vàng trên cỏ xanh");
            bookAuthorDao.replaceForBook(c, book.getBookId(), List.of(author.getAuthorId()));

            assertEquals(1, searchByAuthor(c, "Nguyen Nhat Anh").size(),
                    "Unaccented author search must find an accented author name");
            assertEquals(1, searchByAuthor(c, "Nguyễn Nhất Anh").size(),
                    "Accented author search must still find the same author");
        }
    }

    @Test
    void memberNameSearchIsAccentInsensitive() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            prepare(c);
            Member member = new Member(null, "M-ACC-001", "Nguyễn Văn An",
                    "an@example.com", MemberStatus.ACTIVE);
            memberDao.insert(c, member);

            assertEquals(1, searchMembers(c, "Nguyen Van An").size(),
                    "Unaccented member search must find an accented full name");
            assertEquals(1, searchMembers(c, "Nguyễn Văn An").size(),
                    "Accented member search must still find the same member");
        }
    }

    @Test
    void likeWildcardsRemainEscaped() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            prepare(c);
            insertBook(c, "978-WC-000-000-001", "Plain Title");

            assertEquals(0, searchBooks(c, "%").size(),
                    "A bare % must be escaped and must not match every row");
            assertEquals(0, searchBooks(c, "_").size(),
                    "A bare _ must be escaped and must not match every single character");

            insertBook(c, "978-WC-000-000-002", "100% Clean");
            assertEquals(1, searchBooks(c, "100%").size(),
                    "A literal % inside a keyword must still match a title that contains it");
        }
    }
}
