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
import com.library.model.Page;
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
 * <p>This covers the 14-day rule, one active loan per copy, returned-loan history,
 * caller-supplied dates, and joined display fields.
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

    @Test
    void validDueDateIsExactlyLoanDatePlus14Days() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            Book book = TestFixtures.newBook("978-14DAY-000-001", "14 Day Rule");
            bookDao.insert(c, book);
            BookCopy copy = new BookCopy(null, book.getBookId(), "14DAY-CP-001", CopyStatus.AVAILABLE);
            copyDao.insert(c, copy);
            Member member = new Member(null, "M-14DAY-001", "Rule Tester", "rule@test.com", MemberStatus.ACTIVE);
            memberDao.insert(c, member);

            LocalDate loanDate = LocalDate.of(2026, 10, 25);
            Loan loan = new Loan(null, copy.getCopyId(), member.getMemberId(),
                    loanDate, loanDate.plusDays(14), null, LoanStatus.ACTIVE);
            loanDao.insert(c, loan);

            Loan found = loanDao.findByIdForUpdate(c, loan.getLoanId()).orElseThrow();
            assertEquals(loanDate.plusDays(14), found.getDueDate());
        }
    }

    @Test
    void invalidDueDateIsRejectedByCheckConstraint() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            Book book = TestFixtures.newBook("978-BADDUE-000-001", "Bad Due Date");
            bookDao.insert(c, book);
            BookCopy copy = new BookCopy(null, book.getBookId(), "BADDUE-CP-001", CopyStatus.AVAILABLE);
            copyDao.insert(c, copy);
            Member member = new Member(null, "M-BADDUE-001", "Bad Due", "bad@test.com", MemberStatus.ACTIVE);
            memberDao.insert(c, member);

            LocalDate loanDate = LocalDate.of(2026, 10, 25);
            DataAccessException error = assertThrows(DataAccessException.class,
                    () -> loanDao.insert(c, new Loan(null, copy.getCopyId(), member.getMemberId(),
                            loanDate, loanDate.plusDays(13), null, LoanStatus.ACTIVE)));
            assertEquals(AppConfig.SQLSTATE_CHECK_VIOLATION, error.getSqlState());
        }
    }

    @Test
    void onlyOneActiveLoanPerCopy() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            Book book = TestFixtures.newBook("978-ACTIVE-000-001", "Active Unique");
            bookDao.insert(c, book);
            BookCopy copy = new BookCopy(null, book.getBookId(), "ACTIVE-CP-001", CopyStatus.AVAILABLE);
            copyDao.insert(c, copy);
            Member first = new Member(null, "M-ACT-001", "First", "first@test.com", MemberStatus.ACTIVE);
            Member second = new Member(null, "M-ACT-002", "Second", "second@test.com", MemberStatus.ACTIVE);
            memberDao.insert(c, first);
            memberDao.insert(c, second);

            LocalDate loanDate = LocalDate.of(2026, 10, 25);
            loanDao.insert(c, new Loan(null, copy.getCopyId(), first.getMemberId(),
                    loanDate, loanDate.plusDays(14), null, LoanStatus.ACTIVE));

            DataAccessException error = assertThrows(DataAccessException.class,
                    () -> loanDao.insert(c, new Loan(null, copy.getCopyId(), second.getMemberId(),
                            loanDate, loanDate.plusDays(14), null, LoanStatus.ACTIVE)));
            assertEquals(AppConfig.SQLSTATE_UNIQUE_VIOLATION, error.getSqlState());
        }
    }

    @Test
    void countActiveAndOverdueUseCallerSuppliedToday() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            Book book = TestFixtures.newBook("978-COUNT-000-001", "Count Book");
            bookDao.insert(c, book);
            BookCopy copy1 = new BookCopy(null, book.getBookId(), "CNT-CP-001", CopyStatus.BORROWED);
            BookCopy copy2 = new BookCopy(null, book.getBookId(), "CNT-CP-002", CopyStatus.BORROWED);
            copyDao.insert(c, copy1);
            copyDao.insert(c, copy2);
            Member member = new Member(null, "M-COUNT-001", "Counter", "cnt@test.com", MemberStatus.ACTIVE);
            memberDao.insert(c, member);

            LocalDate today = LocalDate.of(2026, 10, 25);
            loanDao.insert(c, new Loan(null, copy1.getCopyId(), member.getMemberId(),
                    today.minusDays(3), today.plusDays(11), null, LoanStatus.ACTIVE));
            loanDao.insert(c, new Loan(null, copy2.getCopyId(), member.getMemberId(),
                    today.minusDays(24), today.minusDays(10), null, LoanStatus.ACTIVE));

            assertEquals(2, loanDao.countActive(c));
            assertEquals(1, loanDao.countOverdue(c, today));
            assertEquals(2, loanDao.countOverdue(c, today.plusDays(12)));

            LoanSearchCriteria criteria = new LoanSearchCriteria();
            criteria.setOverdueOnly(true);
            criteria.setToday(today);
            Page<Loan> overdue = loanDao.search(c, criteria);
            assertEquals(1, overdue.getTotalElements());
            assertEquals(copy2.getCopyId(), overdue.getContent().get(0).getCopyId());
        }
    }

    @Test
    void existsByBookIdIncludesReturnedLoans() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            Book book = TestFixtures.newBook("978-HIST-000-001", "History Book");
            bookDao.insert(c, book);
            BookCopy copy = new BookCopy(null, book.getBookId(), "HIST-CP-001", CopyStatus.BORROWED);
            copyDao.insert(c, copy);
            Member member = new Member(null, "M-HIST-001", "History", "hist@test.com", MemberStatus.ACTIVE);
            memberDao.insert(c, member);

            LocalDate loanDate = LocalDate.of(2026, 9, 1);
            Loan loan = new Loan(null, copy.getCopyId(), member.getMemberId(),
                    loanDate, loanDate.plusDays(14), null, LoanStatus.ACTIVE);
            loanDao.insert(c, loan);
            assertTrue(loanDao.existsByBookId(c, book.getBookId()));

            loanDao.markReturned(c, loan.getLoanId(), loanDate.plusDays(5));
            assertTrue(loanDao.existsByBookId(c, book.getBookId()));
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
