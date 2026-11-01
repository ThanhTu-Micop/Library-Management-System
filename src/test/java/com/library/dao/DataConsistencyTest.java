package com.library.dao;

import com.library.dao.jdbc.JdbcBookDAO;
import com.library.dao.jdbc.JdbcCategoryDAO;
import com.library.dao.jdbc.JdbcLoanDAO;
import com.library.dao.jdbc.JdbcMemberDAO;
import com.library.dao.support.TestDatabase;
import com.library.dao.support.TestFixtures;
import com.library.model.Book;
import com.library.model.BookCopy;
import com.library.model.Category;
import com.library.model.Loan;
import com.library.model.Member;
import com.library.model.enums.CopyStatus;
import com.library.model.enums.LoanStatus;
import com.library.config.AppConfig;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Schema-level invariants that cannot be expressed through a normal DAO method.
 *
 * <p>These tests deliberately use raw SQL so the database constraint is verified
 * independently of the DAO wrapper. Per-DAO INSERT/UPDATE behavior and derived projections
 * live in the corresponding {@code Jdbc*DAOTest} classes.
 */
class DataConsistencyTest {

    private final BookDAO bookDao = new JdbcBookDAO();
    private final MemberDAO memberDao = new JdbcMemberDAO();
    private final LoanDAO loanDao = new JdbcLoanDAO();
    private final CategoryDAO categoryDao = new JdbcCategoryDAO();

    /** CHECK constraints reject unknown enum values written by raw SQL. */
    @Test
    void enumColumnsRejectUnknownValues() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            Book book = TestFixtures.insertBook(c, "978-ENUM-000-001", "Enum Book");
            BookCopy copy = TestFixtures.insertCopy(c, book, "ENUM-CP-001", CopyStatus.AVAILABLE);
            Member member = TestFixtures.insertMember(c, "M-ENUM-001", "Enum Member", "enum@test.com");

            assertAll(
                    () -> assertEquals(AppConfig.SQLSTATE_CHECK_VIOLATION,
                            sqlStateOf(c, "INSERT INTO book_copies (book_id, copy_code, status) VALUES ("
                                    + book.getBookId() + ", 'BAD-CP-001', 'LOST')")),
                    () -> assertEquals(AppConfig.SQLSTATE_CHECK_VIOLATION,
                            sqlStateOf(c, "INSERT INTO loans "
                                    + "(copy_id, member_id, loan_date, due_date, status) VALUES ("
                                    + copy.getCopyId() + ", " + member.getMemberId()
                                    + ", DATE '2026-01-01', DATE '2026-01-15', 'OVERDUE')")),
                    () -> assertEquals(AppConfig.SQLSTATE_CHECK_VIOLATION,
                            sqlStateOf(c, "INSERT INTO members "
                                    + "(member_code, full_name, email, status) VALUES "
                                    + "('M-MS-001', 'Bad Status', 'ms@test.com', 'BLOCKED')")));
        }
    }

    /**
     * The loans CHECK pair must reject every inconsistent UPDATE: RETURNED without a date,
     * ACTIVE with a date, and a return date before the loan date.
     */
    @Test
    void loanStatusAndReturnDateMustBeConsistentOnUpdate() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            Book book = TestFixtures.insertBook(c, "978-LCHECK-000-001", "Loan Check Book");
            Member member = TestFixtures.insertMember(c, "M-LCHECK-001", "Loan Check", "lc@test.com");

            BookCopy copy1 = TestFixtures.insertCopy(c, book, "LC-CP-001", CopyStatus.AVAILABLE);
            Loan active = TestFixtures.insertActiveLoan(c, copy1, member, LocalDate.of(2026, 4, 1));
            assertEquals(AppConfig.SQLSTATE_CHECK_VIOLATION,
                    sqlStateOf(c, "UPDATE loans SET status = 'RETURNED' WHERE loan_id = "
                            + active.getLoanId()));

            Loan stillActive = loanDao.findByIdForUpdate(c, active.getLoanId()).orElseThrow();
            assertAll(
                    () -> assertEquals(LoanStatus.ACTIVE, stillActive.getStatus(),
                            "The failed UPDATE must be atomic"),
                    () -> assertNull(stillActive.getReturnDate()));

            BookCopy copy2 = TestFixtures.insertCopy(c, book, "LC-CP-002", CopyStatus.AVAILABLE);
            Loan returned = new Loan(null, copy2.getCopyId(), member.getMemberId(),
                    LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 15),
                    LocalDate.of(2026, 3, 6), LoanStatus.RETURNED);
            loanDao.insert(c, returned);
            assertEquals(AppConfig.SQLSTATE_CHECK_VIOLATION,
                    sqlStateOf(c, "UPDATE loans SET status = 'ACTIVE' WHERE loan_id = "
                            + returned.getLoanId()));

            BookCopy copy3 = TestFixtures.insertCopy(c, book, "LC-CP-003", CopyStatus.AVAILABLE);
            Loan third = TestFixtures.insertActiveLoan(c, copy3, member, LocalDate.of(2026, 6, 1));
            assertEquals(AppConfig.SQLSTATE_CHECK_VIOLATION,
                    sqlStateOf(c, "UPDATE loans SET return_date = loan_date - 1, status = 'RETURNED' "
                            + "WHERE loan_id = " + third.getLoanId()));
            assertNull(loanDao.findByIdForUpdate(c, third.getLoanId()).orElseThrow().getReturnDate());
        }
    }

    /** The 14-day rule is also enforced on UPDATE, not only on INSERT. */
    @Test
    void loanDueDateMustBeLoanDatePlus14OnUpdate() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            Book book = TestFixtures.insertBook(c, "978-UD-000-000-001", "Update Due Date");
            BookCopy copy = TestFixtures.insertCopy(c, book, "UD-CP-001", CopyStatus.AVAILABLE);
            Member member = TestFixtures.insertMember(c, "M-UD-001", "Member UD", "ud@test.com");

            Loan loan = TestFixtures.insertActiveLoan(c, copy, member, LocalDate.of(2026, 5, 1));
            assertEquals(AppConfig.SQLSTATE_CHECK_VIOLATION,
                    sqlStateOf(c, "UPDATE loans SET due_date = loan_date + 13 WHERE loan_id = "
                            + loan.getLoanId()));
        }
    }

    /** Member natural keys remain unique when changed by a raw UPDATE. */
    @Test
    void memberNaturalKeysRejectDuplicatesOnUpdate() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            TestFixtures.insertMember(c, "M-RAW-001", "First", "first@test.com");
            Member second = TestFixtures.insertMember(c, "M-RAW-002", "Second", "second@test.com");

            assertAll(
                    () -> assertEquals(AppConfig.SQLSTATE_UNIQUE_VIOLATION,
                            sqlStateOf(c, "UPDATE members SET email = 'FIRST@TEST.COM' WHERE member_id = "
                                    + second.getMemberId())),
                    () -> assertEquals(AppConfig.SQLSTATE_UNIQUE_VIOLATION,
                            sqlStateOf(c, "UPDATE members SET member_code = 'M-RAW-001' WHERE member_id = "
                                    + second.getMemberId())));

            Member reloaded = memberDao.findById(c, second.getMemberId()).orElseThrow();
            assertAll(
                    () -> assertEquals("second@test.com", reloaded.getEmail()),
                    () -> assertEquals("M-RAW-002", reloaded.getMemberCode()));
        }
    }

    /** Book ISBN and category name unique indexes also reject raw UPDATE collisions. */
    @Test
    void bookAndCategoryNaturalKeysRejectDuplicatesOnUpdate() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            TestFixtures.insertBook(c, "978-RAW-000-001", "First");
            Book second = TestFixtures.insertBook(c, "978-RAW-000-002", "Second");

            Category poetry = new Category(null, "Poetry");
            categoryDao.insert(c, poetry);
            Category drama = new Category(null, "Drama");
            categoryDao.insert(c, drama);

            assertAll(
                    () -> assertEquals(AppConfig.SQLSTATE_UNIQUE_VIOLATION,
                            sqlStateOf(c, "UPDATE books SET isbn = '978-RAW-000-001' WHERE book_id = "
                                    + second.getBookId())),
                    () -> assertEquals(AppConfig.SQLSTATE_UNIQUE_VIOLATION,
                            sqlStateOf(c, "UPDATE categories SET name = 'POETRY' WHERE category_id = "
                                    + drama.getCategoryId())));

            assertEquals("978-RAW-000-002",
                    bookDao.findById(c, second.getBookId()).orElseThrow().getIsbn());
            assertEquals("Drama", categoryDao.findById(c, drama.getCategoryId()).orElseThrow().getName());
        }
    }

    private static String sqlStateOf(Connection c, String sql) {
        SQLException error = assertThrows(SQLException.class, () -> {
            try (Statement st = c.createStatement()) {
                st.execute(sql);
            }
        });
        return error.getSQLState();
    }
}
