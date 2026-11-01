package com.library.dao;

import com.library.dao.jdbc.JdbcBookCopyDAO;
import com.library.dao.jdbc.JdbcBookDAO;
import com.library.dao.jdbc.JdbcLoanDAO;
import com.library.dao.jdbc.JdbcMemberDAO;
import com.library.dao.support.TestDatabase;
import com.library.dao.support.TestFixtures;
import com.library.exception.DataAccessException;
import com.library.model.Book;
import com.library.model.BookCopy;
import com.library.model.Loan;
import com.library.model.Member;
import com.library.model.criteria.LoanSearchCriteria;
import com.library.model.enums.CopyStatus;
import com.library.model.enums.LoanStatus;
import com.library.model.enums.MemberStatus;
import com.library.config.AppConfig;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DAO tests for {@link JdbcLoanDAO} and the loan constraints on {@code library_test}.
 *
 * <p>The 14-day rule, return transitions, overdue counting, and uniqueness scope are owned
 * by {@link LoanBusinessLogicTest}; this class keeps persistence, history, and projection
 * checks for the loan DAO itself.
 */
class JdbcLoanDAOTest {

    private final BookDAO bookDao = new JdbcBookDAO();
    private final BookCopyDAO copyDao = new JdbcBookCopyDAO();
    private final MemberDAO memberDao = new JdbcMemberDAO();
    private final LoanDAO loanDao = new JdbcLoanDAO();

    @Test
    void borrowAndReturnFlow() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            Book book = TestFixtures.newBook("978-LOAN-000-001", "Loan Test Book");
            bookDao.insert(c, book);

            BookCopy copy = new BookCopy(null, book.getBookId(), "LOAN-CP-001", CopyStatus.AVAILABLE);
            copyDao.insert(c, copy);

            Member member = new Member(null, "M-LOAN-001", "Borrower", "borrower@test.com", MemberStatus.ACTIVE);
            memberDao.insert(c, member);

            assertFalse(loanDao.findActiveByCopyId(c, copy.getCopyId()).isPresent());

            LocalDate loanDate = LocalDate.of(2026, 10, 1);
            Loan loan = new Loan(null, copy.getCopyId(), member.getMemberId(),
                    loanDate, loanDate.plusDays(14), null, LoanStatus.ACTIVE);
            assertEquals(1, loanDao.insert(c, loan));
            assertNotNull(loan.getLoanId());

            Loan active = loanDao.findActiveByCopyId(c, copy.getCopyId()).orElseThrow();
            assertEquals(loanDate.plusDays(14), active.getDueDate());

            assertEquals(1, loanDao.markReturned(c, loan.getLoanId(), LocalDate.of(2026, 10, 10)));
            assertFalse(loanDao.findActiveByCopyId(c, copy.getCopyId()).isPresent());
        }
    }

    /** The partial unique index permits only one open loan per copy. */
    @Test
    void onlyOneActiveLoanPerCopy() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            Book book = TestFixtures.insertBook(c, "978-ACTIVE-000-001", "Active Unique");
            BookCopy copy = TestFixtures.insertCopy(c, book, "ACTIVE-CP-001", CopyStatus.AVAILABLE);
            Member first = TestFixtures.insertMember(c, "M-ACT-001", "First", "first@test.com");
            Member second = TestFixtures.insertMember(c, "M-ACT-002", "Second", "second@test.com");

            LocalDate loanDate = LocalDate.of(2026, 10, 25);
            TestFixtures.insertActiveLoan(c, copy, first, loanDate);

            DataAccessException error = assertThrows(DataAccessException.class,
                    () -> TestFixtures.insertActiveLoan(c, copy, second, loanDate));
            assertEquals(AppConfig.SQLSTATE_UNIQUE_VIOLATION, error.getSqlState());
        }
    }

    /** {@code existsByBookId} is true for active and returned history, false with no loans. */
    @Test
    void existsByBookIdIncludesAllHistory() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            Book book = TestFixtures.insertBook(c, "978-HIST-000-001", "History Book");
            BookCopy copy = TestFixtures.insertCopy(c, book, "HIST-CP-001", CopyStatus.AVAILABLE);
            Member member = TestFixtures.insertMember(c, "M-HIST-001", "History", "hist@test.com");

            assertFalse(loanDao.existsByBookId(c, book.getBookId()),
                    "A book with no loans must not report existing loan history");

            LocalDate loanDate = LocalDate.of(2026, 9, 1);
            Loan loan = TestFixtures.insertActiveLoan(c, copy, member, loanDate);
            assertTrue(loanDao.existsByBookId(c, book.getBookId()));

            loanDao.markReturned(c, loan.getLoanId(), loanDate.plusDays(5));
            assertTrue(loanDao.existsByBookId(c, book.getBookId()),
                    "existsByBookId checks ANY loan history, including returned loans");
        }
    }

    @Test
    void joinedDisplayFieldsArePopulated() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            Book book = TestFixtures.newBook("978-JOIN-000-001", "Joined Book");
            bookDao.insert(c, book);
            BookCopy copy = new BookCopy(null, book.getBookId(), "JOIN-CP-001", CopyStatus.BORROWED);
            copyDao.insert(c, copy);
            Member member = new Member(null, "M-JOIN-001", "Joined Member", "join@test.com", MemberStatus.ACTIVE);
            memberDao.insert(c, member);

            LocalDate today = LocalDate.of(2026, 10, 25);
            loanDao.insert(c, new Loan(null, copy.getCopyId(), member.getMemberId(),
                    today.minusDays(3), today.plusDays(11), null, LoanStatus.ACTIVE));

            LoanSearchCriteria criteria = new LoanSearchCriteria();
            criteria.setBookId(book.getBookId());
            Loan fromSearch = loanDao.search(c, criteria).getContent().get(0);
            assertEquals("Joined Book", fromSearch.getBookTitle());
            assertEquals("JOIN-CP-001", fromSearch.getCopyCode());
            assertEquals("Joined Member", fromSearch.getMemberName());
            assertNotNull(fromSearch.getCreatedAt());

            List<Loan> active = loanDao.findActive(c);
            assertEquals(1, active.size());
            assertEquals("Joined Book", active.get(0).getBookTitle());
        }
    }
}
