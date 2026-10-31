package com.library.dao;

import com.library.dao.jdbc.JdbcAuthorDAO;
import com.library.dao.jdbc.JdbcBookDAO;
import com.library.dao.jdbc.JdbcCategoryDAO;
import com.library.dao.jdbc.JdbcLoanDAO;
import com.library.dao.jdbc.JdbcMemberDAO;
import com.library.dao.jdbc.JdbcPublisherDAO;
import com.library.dao.support.TestDatabase;
import com.library.dao.support.TestFixtures;
import com.library.exception.DataAccessException;
import com.library.model.Author;
import com.library.model.Book;
import com.library.model.BookCopy;
import com.library.model.Category;
import com.library.model.Loan;
import com.library.model.Member;
import com.library.model.Publisher;
import com.library.model.enums.CopyStatus;
import com.library.model.enums.LoanStatus;
import com.library.config.AppConfig;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cross-cutting consistency tests for schema-level invariants.
 *
 * <p>The existing per-DAO tests verify happy-path behavior and INSERT-time constraint
 * enforcement. This class focuses on the <em>UPDATE</em> branch and on cross-DAO
 * consistency rules that span multiple tables:
 *
 * <ul>
 *   <li>UNIQUE indexes must reject duplicates on UPDATE, not just on INSERT
 *       (ISBN, member_code, email case-insensitive, category name case-insensitive).</li>
 *   <li>The DAO must keep normalizing blank email to {@code NULL} on UPDATE, not just INSERT.</li>
 *   <li>CHECK constraints (enum values, year range, ISBN length) must reject on UPDATE.</li>
 *   <li>The {@code loans_status_return_date_consistency} CHECK must reject any UPDATE that
 *       would put a loan into an inconsistent state (RETURNED without return_date, ACTIVE
 *       with return_date, return_date &lt; loan_date, due_date &ne; loan_date + 14).</li>
 *   <li>{@code exists} helpers must correctly exclude the row being edited, so a member
 *       updating their own email to the same value is not flagged as a duplicate.</li>
 * </ul>
 *
 * <p>Where the DAO does not expose a method that exercises a particular UPDATE path
 * (for example, directly flipping {@code loans.status} without going through
 * {@code markReturned}), the test uses a raw JDBC {@link Statement} so the schema-level
 * invariant is verified independently of DAO coverage.
 */
class DataConsistencyTest {

    private final BookDAO bookDao = new JdbcBookDAO();
    private final MemberDAO memberDao = new JdbcMemberDAO();
    private final LoanDAO loanDao = new JdbcLoanDAO();
    private final CategoryDAO categoryDao = new JdbcCategoryDAO();
    private final AuthorDAO authorDao = new JdbcAuthorDAO();
    private final PublisherDAO publisherDao = new JdbcPublisherDAO();

    // ------------------------------------------------------------------
    // Email uniqueness (case-insensitive): INSERT + UPDATE
    // ------------------------------------------------------------------

    @Test
    void emailUniqueCaseInsensitiveOnInsert() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            TestFixtures.insertMember(c, "M-1", "Member One", "User@Example.com");

            DataAccessException error = assertThrows(DataAccessException.class,
                    () -> TestFixtures.insertMember(c, "M-2", "Member Two", "USER@example.COM"));
            assertEquals(AppConfig.SQLSTATE_UNIQUE_VIOLATION, error.getSqlState());
        }
    }

    @Test
    void emailUniqueCaseInsensitiveOnUpdate() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            TestFixtures.insertMember(c, "M-1", "First", "first@test.com");
            Member second = TestFixtures.insertMember(c, "M-2", "Second", "second@test.com");

            // Try to update second's email to collide with first's email (different case).
            second.setEmail("FIRST@TEST.COM");
            DataAccessException error = assertThrows(DataAccessException.class,
                    () -> memberDao.update(c, second));
            assertEquals(AppConfig.SQLSTATE_UNIQUE_VIOLATION, error.getSqlState(),
                    "UPDATE must hit the same partial unique index ux_members_email_lower as INSERT");
        }
    }

    @Test
    void emailExistsExcludesSelf() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            Member m = TestFixtures.insertMember(c, "M-1", "Self", "self@test.com");

            // emailExists with the member's own email and own id must return false
            // (otherwise the controller would reject every edit form).
            assertFalse(memberDao.emailExists(c, "self@test.com", m.getMemberId()));
            assertFalse(memberDao.emailExists(c, "SELF@TEST.COM", m.getMemberId()));

            // Without excludeId, the same email must report as existing.
            assertTrue(memberDao.emailExists(c, "self@test.com", null));
        }
    }

    // ------------------------------------------------------------------
    // Member code uniqueness: INSERT + UPDATE
    // ------------------------------------------------------------------

    @Test
    void memberCodeUniqueOnInsert() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            TestFixtures.insertMember(c, "M-DUP", "First", "first@test.com");

            DataAccessException error = assertThrows(DataAccessException.class,
                    () -> TestFixtures.insertMember(c, "M-DUP", "Second", "second@test.com"));
            assertEquals(AppConfig.SQLSTATE_UNIQUE_VIOLATION, error.getSqlState());
        }
    }

    @Test
    void memberCodeUniqueOnUpdate() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            TestFixtures.insertMember(c, "M-A", "A", "a@test.com");
            Member b = TestFixtures.insertMember(c, "M-B", "B", "b@test.com");

            b.setMemberCode("M-A");
            DataAccessException error = assertThrows(DataAccessException.class,
                    () -> memberDao.update(c, b));
            assertEquals(AppConfig.SQLSTATE_UNIQUE_VIOLATION, error.getSqlState());
        }
    }

    @Test
    void memberCodeExistsExcludesSelf() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            Member m = TestFixtures.insertMember(c, "M-SELF", "Self", "self@test.com");
            assertFalse(memberDao.memberCodeExists(c, "M-SELF", m.getMemberId()));
            assertTrue(memberDao.memberCodeExists(c, "M-SELF", null));
        }
    }

    // ------------------------------------------------------------------
    // ISBN uniqueness and trimming: INSERT + UPDATE
    // ------------------------------------------------------------------

    @Test
    void isbnUniqueOnInsert() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            TestFixtures.insertBook(c, "978-DUP-000-000-001", "First");

            DataAccessException error = assertThrows(DataAccessException.class,
                    () -> TestFixtures.insertBook(c, "978-DUP-000-000-001", "Second"));
            assertEquals(AppConfig.SQLSTATE_UNIQUE_VIOLATION, error.getSqlState());
        }
    }

    @Test
    void isbnUniqueOnUpdate() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            TestFixtures.insertBook(c, "978-U1-000-000-001", "Book One");
            Book two = TestFixtures.insertBook(c, "978-U2-000-000-001", "Book Two");

            two.setIsbn("978-U1-000-000-001");
            DataAccessException error = assertThrows(DataAccessException.class,
                    () -> bookDao.update(c, two));
            assertEquals(AppConfig.SQLSTATE_UNIQUE_VIOLATION, error.getSqlState());
        }
    }

    @Test
    void isbnExistsExcludesSelf() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            Book book = TestFixtures.insertBook(c, "978-OWN-000-000-001", "Self ISBN");
            assertFalse(bookDao.isbnExists(c, "978-OWN-000-000-001", book.getBookId()));
            assertTrue(bookDao.isbnExists(c, "978-OWN-000-000-001", null));
        }
    }

    @Test
    void isbnTrimmedOnUpdate() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            Book book = TestFixtures.insertBook(c, "978-TR-000-000-001", "Trim Update");

            book.setIsbn("  978-NEW-000-000-001  ");
            bookDao.update(c, book);

            Book found = bookDao.findById(c, book.getBookId()).orElseThrow();
            assertEquals("978-NEW-000-000-001", found.getIsbn(),
                    "JdbcBookDAO.update must trim ISBN the same way insert does");
        }
    }

    @Test
    void isbnLengthBoundaries() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);

            // 13 chars accepted (lower bound).
            Book thirteen = new Book(null, "1234567890123", "Thirteen", 1, 1, 2020);
            assertEquals(1, bookDao.insert(c, thirteen));

            // 20 chars accepted (upper bound; also equals varchar(20) limit).
            Book twenty = new Book(null, "12345678901234567890", "Twenty", 1, 1, 2020);
            assertEquals(1, bookDao.insert(c, twenty));

            // 12 chars rejected by CHECK constraint books_isbn_length.
            DataAccessException tooShort = assertThrows(DataAccessException.class,
                    () -> bookDao.insert(c, new Book(null, "123456789012", "Twelve", 1, 1, 2020)));
            assertEquals(AppConfig.SQLSTATE_CHECK_VIOLATION, tooShort.getSqlState());

            // 21 chars rejected by varchar(20) â€” string_data_right_truncation.
            DataAccessException tooLong = assertThrows(DataAccessException.class,
                    () -> bookDao.insert(c, new Book(null, "123456789012345678901", "TwentyOne", 1, 1, 2020)));
            assertEquals("22001", tooLong.getSqlState());
        }
    }

    // ------------------------------------------------------------------
    // Category name uniqueness (case-insensitive): INSERT + UPDATE
    // ------------------------------------------------------------------

    @Test
    void categoryNameUniqueCaseInsensitiveOnInsert() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            // Use a name that is not already present in seedReferenceData(), otherwise the
            // first insert fails before the case-insensitive duplicate check is exercised.
            categoryDao.insert(c, new Category(null, "Drama"));

            DataAccessException error = assertThrows(DataAccessException.class,
                    () -> categoryDao.insert(c, new Category(null, "DRAMA")));
            assertEquals(AppConfig.SQLSTATE_UNIQUE_VIOLATION, error.getSqlState());
        }
    }

    @Test
    void categoryNameUniqueCaseInsensitiveOnUpdateViaRawSql() throws Exception {
        // JdbcCategoryDAO does not expose an update() method, so we exercise the unique
        // index directly with a raw SQL UPDATE. The index ux_categories_name_lower
        // must reject a colliding UPDATE just as it rejects a colliding INSERT.
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            // Both names must be absent from seedReferenceData() so the initial inserts succeed.
            categoryDao.insert(c, new Category(null, "Poetry"));
            Category other = new Category(null, "Drama");
            categoryDao.insert(c, other);

            SQLException ex = assertThrows(SQLException.class, () -> {
                try (Statement st = c.createStatement()) {
                    st.execute("UPDATE categories SET name = 'POETRY' WHERE category_id = " + other.getCategoryId());
                }
            });
            assertEquals(AppConfig.SQLSTATE_UNIQUE_VIOLATION, ex.getSQLState());
        }
    }

    // ------------------------------------------------------------------
    // Blank email normalization on UPDATE
    // ------------------------------------------------------------------

    @Test
    void blankEmailNormalizedToNullOnInsert() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            Member m = TestFixtures.insertMember(c, "M-B1", "Blank Insert", "   ");

            Member found = memberDao.findById(c, m.getMemberId()).orElseThrow();
            assertNull(found.getEmail(),
                    "JdbcMemberDAO.insert must normalize blank email to NULL so the CHECK constraint passes");
            assertFalse(memberDao.emailExists(c, "   ", null),
                    "emailExists on a blank string must return false (normalized to NULL, not indexed)");
        }
    }

    @Test
    void blankEmailNormalizedToNullOnUpdate() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            Member m = TestFixtures.insertMember(c, "M-B2", "Blank Update", "real@test.com");

            m.setEmail("   ");
            memberDao.update(c, m);

            Member found = memberDao.findById(c, m.getMemberId()).orElseThrow();
            assertNull(found.getEmail(),
                    "JdbcMemberDAO.update must normalize blank email to NULL â€” same as insert");
        }
    }

    @Test
    void nullEmailIsPreservedOnUpdate() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            Member m = TestFixtures.insertMember(c, "M-N1", "Null Update", "real@test.com");

            m.setEmail(null);
            memberDao.update(c, m);

            Member found = memberDao.findById(c, m.getMemberId()).orElseThrow();
            assertNull(found.getEmail());
        }
    }

    // ------------------------------------------------------------------
    // Publication year boundaries
    // ------------------------------------------------------------------

    @Test
    void publicationYearBoundaries() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);

            // Lower bound 1000 accepted.
            assertEquals(1, bookDao.insert(c, new Book(null, "978-Y1-000-000-001", "Year 1000", 1, 1, 1000)));
            // Upper bound 2100 accepted.
            assertEquals(1, bookDao.insert(c, new Book(null, "978-Y2-000-000-002", "Year 2100", 1, 1, 2100)));
            // 999 rejected.
            DataAccessException tooLow = assertThrows(DataAccessException.class,
                    () -> bookDao.insert(c, new Book(null, "978-Y3-000-000-003", "Year 999", 1, 1, 999)));
            assertEquals(AppConfig.SQLSTATE_CHECK_VIOLATION, tooLow.getSqlState());
            // 2101 rejected.
            DataAccessException tooHigh = assertThrows(DataAccessException.class,
                    () -> bookDao.insert(c, new Book(null, "978-Y4-000-000-004", "Year 2101", 1, 1, 2101)));
            assertEquals(AppConfig.SQLSTATE_CHECK_VIOLATION, tooHigh.getSqlState());
        }
    }

    // ------------------------------------------------------------------
    // Enum CHECK enforcement
    // ------------------------------------------------------------------

    @Test
    void copyStatusInvalidValueRejected() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            Book book = TestFixtures.insertBook(c, "978-CS-000-000-001", "Copy Status Book");

            // Use raw SQL because CopyStatus enum prevents the DAO path from compiling.
            SQLException ex = assertThrows(SQLException.class, () -> {
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO book_copies (book_id, copy_code, status) VALUES (?, ?, ?)")) {
                    ps.setInt(1, book.getBookId());
                    ps.setString(2, "BAD-CP-001");
                    ps.setString(3, "LOST");
                    ps.executeUpdate();
                }
            });
            assertEquals(AppConfig.SQLSTATE_CHECK_VIOLATION, ex.getSQLState());
        }
    }

    @Test
    void loanStatusInvalidValueRejected() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            Book book = TestFixtures.insertBook(c, "978-LS-000-000-001", "Loan Status Book");
            BookCopy copy = TestFixtures.insertCopy(c, book, "LS-CP-001", CopyStatus.AVAILABLE);
            Member member = TestFixtures.insertMember(c, "M-LS-001", "Loan Status Member", "ls@test.com");

            SQLException ex = assertThrows(SQLException.class, () -> {
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO loans (copy_id, member_id, loan_date, due_date, status) "
                                + "VALUES (?, ?, ?, ?, ?)")) {
                    ps.setInt(1, copy.getCopyId());
                    ps.setInt(2, member.getMemberId());
                    ps.setObject(3, LocalDate.of(2026, 1, 1));
                    ps.setObject(4, LocalDate.of(2026, 1, 15));
                    ps.setString(5, "OVERDUE");
                    ps.executeUpdate();
                }
            });
            assertEquals(AppConfig.SQLSTATE_CHECK_VIOLATION, ex.getSQLState());
        }
    }

    @Test
    void memberStatusInvalidValueRejected() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);

            SQLException ex = assertThrows(SQLException.class, () -> {
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO members (member_code, full_name, email, status) VALUES (?, ?, ?, ?)")) {
                    ps.setString(1, "M-MS-001");
                    ps.setString(2, "Bad Status");
                    ps.setString(3, "ms@test.com");
                    ps.setString(4, "BLOCKED");
                    ps.executeUpdate();
                }
            });
            assertEquals(AppConfig.SQLSTATE_CHECK_VIOLATION, ex.getSQLState());
        }
    }

    // ------------------------------------------------------------------
    // loans CHECK constraints: enforced on UPDATE too, not just INSERT
    // ------------------------------------------------------------------

    @Test
    void updateLoanToReturnedWithoutReturnDateIsRejected() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            Book book = TestFixtures.insertBook(c, "978-UR-000-000-001", "Update Returned No Date");
            BookCopy copy = TestFixtures.insertCopy(c, book, "UR-CP-001", CopyStatus.AVAILABLE);
            Member member = TestFixtures.insertMember(c, "M-UR-001", "Member UR", "ur@test.com");

            LocalDate loanDate = LocalDate.of(2026, 4, 1);
            Loan loan = new Loan(null, copy.getCopyId(), member.getMemberId(),
                    loanDate, loanDate.plusDays(14), null, LoanStatus.ACTIVE);
            loanDao.insert(c, loan);

            // Direct UPDATE: set status=RETURNED but leave return_date NULL.
            SQLException ex = assertThrows(SQLException.class, () -> {
                try (Statement st = c.createStatement()) {
                    st.execute("UPDATE loans SET status = 'RETURNED' WHERE loan_id = " + loan.getLoanId());
                }
            });
            assertEquals(AppConfig.SQLSTATE_CHECK_VIOLATION, ex.getSQLState(),
                    "loans_status_return_date_consistency CHECK must reject RETURNED without return_date on UPDATE");

            // Loan is still ACTIVE â€” UPDATE was atomic.
            Loan found = loanDao.findByIdForUpdate(c, loan.getLoanId()).orElseThrow();
            assertEquals(LoanStatus.ACTIVE, found.getStatus());
            assertNull(found.getReturnDate());
        }
    }

    @Test
    void updateLoanToActiveWithReturnDateIsRejected() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            Book book = TestFixtures.insertBook(c, "978-UA-000-000-001", "Update Active With Date");
            BookCopy copy = TestFixtures.insertCopy(c, book, "UA-CP-001", CopyStatus.AVAILABLE);
            Member member = TestFixtures.insertMember(c, "M-UA-001", "Member UA", "ua@test.com");

            LocalDate loanDate = LocalDate.of(2026, 3, 1);
            Loan loan = new Loan(null, copy.getCopyId(), member.getMemberId(),
                    loanDate, loanDate.plusDays(14), loanDate.plusDays(5), LoanStatus.RETURNED);
            loanDao.insert(c, loan);

            // Try to "revive" the loan: set status=ACTIVE but keep return_date set.
            SQLException ex = assertThrows(SQLException.class, () -> {
                try (Statement st = c.createStatement()) {
                    st.execute("UPDATE loans SET status = 'ACTIVE' WHERE loan_id = " + loan.getLoanId());
                }
            });
            assertEquals(AppConfig.SQLSTATE_CHECK_VIOLATION, ex.getSQLState(),
                    "CHECK must reject ACTIVE status when return_date is non-NULL on UPDATE");
        }
    }

    @Test
    void updateLoanDueDateNotFourteenDaysIsRejected() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            Book book = TestFixtures.insertBook(c, "978-UD-000-000-001", "Update Due Date");
            BookCopy copy = TestFixtures.insertCopy(c, book, "UD-CP-001", CopyStatus.AVAILABLE);
            Member member = TestFixtures.insertMember(c, "M-UD-001", "Member UD", "ud@test.com");

            LocalDate loanDate = LocalDate.of(2026, 5, 1);
            Loan loan = new Loan(null, copy.getCopyId(), member.getMemberId(),
                    loanDate, loanDate.plusDays(14), null, LoanStatus.ACTIVE);
            loanDao.insert(c, loan);

            SQLException ex = assertThrows(SQLException.class, () -> {
                try (Statement st = c.createStatement()) {
                    // Try to shrink the borrowing period to 13 days.
                    st.execute("UPDATE loans SET due_date = loan_date + 13 WHERE loan_id = " + loan.getLoanId());
                }
            });
            assertEquals(AppConfig.SQLSTATE_CHECK_VIOLATION, ex.getSQLState(),
                    "loans_due_date_14_days CHECK must reject any due_date != loan_date + 14 on UPDATE");
        }
    }

    @Test
    void updateLoanReturnDateBeforeLoanDateIsRejected() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            Book book = TestFixtures.insertBook(c, "978-UB-000-000-001", "Update Return Before Loan");
            BookCopy copy = TestFixtures.insertCopy(c, book, "UB-CP-001", CopyStatus.AVAILABLE);
            Member member = TestFixtures.insertMember(c, "M-UB-001", "Member UB", "ub@test.com");

            LocalDate loanDate = LocalDate.of(2026, 6, 1);
            Loan loan = new Loan(null, copy.getCopyId(), member.getMemberId(),
                    loanDate, loanDate.plusDays(14), null, LoanStatus.ACTIVE);
            loanDao.insert(c, loan);

            SQLException ex = assertThrows(SQLException.class, () -> {
                try (Statement st = c.createStatement()) {
                    st.execute("UPDATE loans SET return_date = loan_date - 1, status = 'RETURNED' "
                            + "WHERE loan_id = " + loan.getLoanId());
                }
            });
            assertEquals(AppConfig.SQLSTATE_CHECK_VIOLATION, ex.getSQLState(),
                    "loans_return_date_check must reject return_date < loan_date on UPDATE");
        }
    }

    // ------------------------------------------------------------------
    // Reference data: created_at and existence helpers
    // ------------------------------------------------------------------

    @Test
    void authorAndPublisherInsertAssignsIdAndCreatedAt() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            Author author = new Author(null, "Test Author X");
            assertEquals(1, authorDao.insert(c, author));
            assertNotNull(author.getAuthorId());
            assertNotNull(authorDao.findById(c, author.getAuthorId()).orElseThrow().getCreatedAt());

            Publisher publisher = new Publisher(null, "Test Publisher X");
            assertEquals(1, publisherDao.insert(c, publisher));
            assertNotNull(publisher.getPublisherId());
            assertNotNull(publisherDao.findById(c, publisher.getPublisherId()).orElseThrow().getCreatedAt());
        }
    }

    @Test
    void categoryFindByIdReturnsEmptyForUnknownId() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            assertFalse(categoryDao.findById(c, 999_999).isPresent());
        }
    }
}

