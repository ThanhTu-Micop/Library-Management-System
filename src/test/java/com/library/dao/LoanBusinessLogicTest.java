package com.library.dao;

import com.library.dao.jdbc.JdbcLoanDAO;
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
import com.library.config.AppConfig;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Business-logic tests for the loan domain.
 *
 * <p>These tests focus on the <em>rules</em> the loan subsystem is supposed to enforce:
 * the 14-day borrowing period (including calendar edge cases such as leap-year February
 * and year boundaries), the lifecycle of {@code markReturned} (idempotency, ordering,
 * same-day returns, future returns), the one-active-loan-per-copy invariant under
 * re-borrow history, and the determinism of date-sensitive counters and filters.
 *
 * <p>They complement {@link JdbcLoanDAOTest} by exercising edge cases that the existing
 * happy-path tests do not cover, and by asserting on the negative branches
 * (CHECK violations, no-op updates, status transitions that must be rejected).
 */
class LoanBusinessLogicTest {

    private final LoanDAO loanDao = new JdbcLoanDAO();

    // ------------------------------------------------------------------
    // 14-day rule: calendar edge cases
    // ------------------------------------------------------------------

    @Test
    void dueDateExactlyFourteenDaysAcrossMonthBoundary() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            Book book = TestFixtures.insertBook(c, "978-MB-000-000-001", "Month Boundary Book");
            BookCopy copy = TestFixtures.insertCopy(c, book, "MB-CP-001", CopyStatus.AVAILABLE);
            Member member = TestFixtures.insertMember(c, "M-MB-001", "Member MB", "mb@test.com");

            // Jan 20 + 14 days = Feb 3 (crosses Januaryâ†’February boundary).
            LocalDate loanDate = LocalDate.of(2026, 1, 20);
            Loan loan = TestFixtures.insertActiveLoan(c, copy, member, loanDate);

            Loan found = loanDao.findByIdForUpdate(c, loan.getLoanId()).orElseThrow();
            assertEquals(LocalDate.of(2026, 2, 3), found.getDueDate());
        }
    }

    @Test
    void dueDateExactlyFourteenDaysInLeapYear() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            Book book = TestFixtures.insertBook(c, "978-LP-000-000-001", "Leap Year Book");
            BookCopy copy = TestFixtures.insertCopy(c, book, "LP-CP-001", CopyStatus.AVAILABLE);
            Member member = TestFixtures.insertMember(c, "M-LP-001", "Member LP", "lp@test.com");

            // Feb 16, 2024 (leap year) + 14 days = Mar 1, 2024 (crosses Feb 29).
            LocalDate loanDate = LocalDate.of(2024, 2, 16);
            Loan loan = TestFixtures.insertActiveLoan(c, copy, member, loanDate);

            Loan found = loanDao.findByIdForUpdate(c, loan.getLoanId()).orElseThrow();
            assertEquals(LocalDate.of(2024, 3, 1), found.getDueDate(),
                    "2024 is a leap year; Feb 16 + 14 days must be Mar 1 (not Feb 29 + 1 = Mar 1 anyway, but Feb 29 must be skipped correctly)");
        }
    }

    @Test
    void dueDateExactlyFourteenDaysInNonLeapYear() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            Book book = TestFixtures.insertBook(c, "978-NL-000-000-001", "Non Leap Book");
            BookCopy copy = TestFixtures.insertCopy(c, book, "NL-CP-001", CopyStatus.AVAILABLE);
            Member member = TestFixtures.insertMember(c, "M-NL-001", "Member NL", "nl@test.com");

            // Feb 16, 2026 (non-leap) + 14 days = Mar 2, 2026 (no Feb 29 to skip).
            LocalDate loanDate = LocalDate.of(2026, 2, 16);
            Loan loan = TestFixtures.insertActiveLoan(c, copy, member, loanDate);

            Loan found = loanDao.findByIdForUpdate(c, loan.getLoanId()).orElseThrow();
            assertEquals(LocalDate.of(2026, 3, 2), found.getDueDate());
        }
    }

    @Test
    void dueDateExactlyFourteenDaysAcrossYearBoundary() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            Book book = TestFixtures.insertBook(c, "978-YB-000-000-001", "Year Boundary Book");
            BookCopy copy = TestFixtures.insertCopy(c, book, "YB-CP-001", CopyStatus.AVAILABLE);
            Member member = TestFixtures.insertMember(c, "M-YB-001", "Member YB", "yb@test.com");

            // Dec 20, 2026 + 14 days = Jan 3, 2027 (crosses calendar year).
            LocalDate loanDate = LocalDate.of(2026, 12, 20);
            Loan loan = TestFixtures.insertActiveLoan(c, copy, member, loanDate);

            Loan found = loanDao.findByIdForUpdate(c, loan.getLoanId()).orElseThrow();
            assertEquals(LocalDate.of(2027, 1, 3), found.getDueDate());
        }
    }

    @Test
    void dueDateFifteenDaysAfterLoanDateIsRejected() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            Book book = TestFixtures.insertBook(c, "978-D15-000-000-001", "15 Day Rejected");
            BookCopy copy = TestFixtures.insertCopy(c, book, "D15-CP-001", CopyStatus.AVAILABLE);
            Member member = TestFixtures.insertMember(c, "M-D15-001", "Member D15", "d15@test.com");

            LocalDate loanDate = LocalDate.of(2026, 6, 1);
            DataAccessException error = assertThrows(DataAccessException.class,
                    () -> loanDao.insert(c, new Loan(null, copy.getCopyId(), member.getMemberId(),
                            loanDate, loanDate.plusDays(15), null, LoanStatus.ACTIVE)));
            assertEquals(AppConfig.SQLSTATE_CHECK_VIOLATION, error.getSqlState());
        }
    }

    // ------------------------------------------------------------------
    // markReturned: idempotency, ordering, edge cases
    // ------------------------------------------------------------------

    @Test
    void markReturnedIsIdempotent() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            Book book = TestFixtures.insertBook(c, "978-IDEM-000-000-001", "Idempotent Return Book");
            BookCopy copy = TestFixtures.insertCopy(c, book, "IDEM-CP-001", CopyStatus.AVAILABLE);
            Member member = TestFixtures.insertMember(c, "M-IDEM-001", "Member Idem", "idem@test.com");

            LocalDate loanDate = LocalDate.of(2026, 9, 1);
            Loan loan = TestFixtures.insertActiveLoan(c, copy, member, loanDate);

            // First call: 1 row affected.
            assertEquals(1, loanDao.markReturned(c, loan.getLoanId(), loanDate.plusDays(5)));

            // Second call: 0 rows affected â€” the WHERE clause excludes returned loans.
            assertEquals(0, loanDao.markReturned(c, loan.getLoanId(), loanDate.plusDays(7)));

            // Third call after a week: still 0 rows. Idempotency holds.
            assertEquals(0, loanDao.markReturned(c, loan.getLoanId(), loanDate.plusDays(14)));

            Loan found = loanDao.findByIdForUpdate(c, loan.getLoanId()).orElseThrow();
            assertEquals(LoanStatus.RETURNED, found.getStatus());
            // The return_date from the FIRST call is preserved; subsequent no-ops do not overwrite it.
            assertEquals(loanDate.plusDays(5), found.getReturnDate());
        }
    }

    @Test
    void markReturnedWithDateBeforeLoanDateIsRejected() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            Book book = TestFixtures.insertBook(c, "978-BR-000-000-001", "Before Loan Date Book");
            BookCopy copy = TestFixtures.insertCopy(c, book, "BR-CP-001", CopyStatus.AVAILABLE);
            Member member = TestFixtures.insertMember(c, "M-BR-001", "Member BR", "br@test.com");

            LocalDate loanDate = LocalDate.of(2026, 8, 10);
            Loan loan = TestFixtures.insertActiveLoan(c, copy, member, loanDate);

            DataAccessException error = assertThrows(DataAccessException.class,
                    () -> loanDao.markReturned(c, loan.getLoanId(), loanDate.minusDays(1)));
            assertEquals(AppConfig.SQLSTATE_CHECK_VIOLATION, error.getSqlState(),
                    "return_date >= loan_date is enforced by CHECK constraint loans_return_date_check");

            // Verify the loan is still ACTIVE (the failed UPDATE was atomic).
            Loan found = loanDao.findByIdForUpdate(c, loan.getLoanId()).orElseThrow();
            assertEquals(LoanStatus.ACTIVE, found.getStatus());
            assertNull(found.getReturnDate());
        }
    }

    @Test
    void markReturnedOnSameDayAsLoanDateIsAllowed() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            Book book = TestFixtures.insertBook(c, "978-SD-000-000-001", "Same Day Return Book");
            BookCopy copy = TestFixtures.insertCopy(c, book, "SD-CP-001", CopyStatus.AVAILABLE);
            Member member = TestFixtures.insertMember(c, "M-SD-001", "Member SD", "sd@test.com");

            LocalDate loanDate = LocalDate.of(2026, 7, 15);
            Loan loan = TestFixtures.insertActiveLoan(c, copy, member, loanDate);

            assertEquals(1, loanDao.markReturned(c, loan.getLoanId(), loanDate));
            Loan found = loanDao.findByIdForUpdate(c, loan.getLoanId()).orElseThrow();
            assertEquals(LoanStatus.RETURNED, found.getStatus());
            assertEquals(loanDate, found.getReturnDate());
        }
    }

    @Test
    void markReturnedWithFutureReturnDateIsAllowedByDatabase() throws Exception {
        // This test documents the E11 gap noted in README Â§6: the schema does not enforce
        // that return_date <= CURRENT_DATE. The DAO also does not check it. The check
        // constraint only requires return_date >= loan_date. If a future fix adds the
        // constraint, this test will fail and must be updated to expect a 23514 error.
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            Book book = TestFixtures.insertBook(c, "978-FU-000-000-001", "Future Return Book");
            BookCopy copy = TestFixtures.insertCopy(c, book, "FU-CP-001", CopyStatus.AVAILABLE);
            Member member = TestFixtures.insertMember(c, "M-FU-001", "Member FU", "fu@test.com");

            LocalDate loanDate = LocalDate.of(2026, 5, 1);
            Loan loan = TestFixtures.insertActiveLoan(c, copy, member, loanDate);

            // return_date far in the future â€” accepted today because no rule forbids it.
            LocalDate futureReturn = LocalDate.of(2099, 12, 31);
            assertEquals(1, loanDao.markReturned(c, loan.getLoanId(), futureReturn));
            Loan found = loanDao.findByIdForUpdate(c, loan.getLoanId()).orElseThrow();
            assertEquals(futureReturn, found.getReturnDate());
        }
    }

    @Test
    void markReturnedOnNonExistentLoanIdReturnsZero() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            int affected = loanDao.markReturned(c, 999_999, LocalDate.of(2026, 1, 1));
            assertEquals(0, affected,
                    "markReturned on a non-existent loan_id must be a no-op returning 0, not an exception");
        }
    }

    // ------------------------------------------------------------------
    // Status transitions
    // ------------------------------------------------------------------

    @Test
    void markReturnedCannotReactivateAnAlreadyReturnedLoan() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            Book book = TestFixtures.insertBook(c, "978-RE-000-000-001", "Reactivate Attempt Book");
            BookCopy copy = TestFixtures.insertCopy(c, book, "RE-CP-001", CopyStatus.AVAILABLE);
            Member member = TestFixtures.insertMember(c, "M-RE-001", "Member RE", "re@test.com");

            LocalDate loanDate = LocalDate.of(2026, 4, 1);
            Loan loan = TestFixtures.insertActiveLoan(c, copy, member, loanDate);

            loanDao.markReturned(c, loan.getLoanId(), loanDate.plusDays(3));

            // Calling markReturned again with a different date does not reactivate; status stays RETURNED.
            assertEquals(0, loanDao.markReturned(c, loan.getLoanId(), loanDate.plusDays(10)));
            Loan found = loanDao.findByIdForUpdate(c, loan.getLoanId()).orElseThrow();
            assertEquals(LoanStatus.RETURNED, found.getStatus());
            assertEquals(loanDate.plusDays(3), found.getReturnDate(),
                    "Original return_date is preserved; the no-op call does not overwrite it.");
        }
    }

    @Test
    void findActiveExcludesReturnedLoans() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            Book book = TestFixtures.insertBook(c, "978-FA-000-000-001", "Find Active Book");
            BookCopy copyA = TestFixtures.insertCopy(c, book, "FA-CP-001", CopyStatus.AVAILABLE);
            BookCopy copyB = TestFixtures.insertCopy(c, book, "FA-CP-002", CopyStatus.AVAILABLE);
            Member member = TestFixtures.insertMember(c, "M-FA-001", "Member FA", "fa@test.com");

            LocalDate today = LocalDate.of(2026, 10, 25);
            Loan active = TestFixtures.insertActiveLoan(c, copyA, member, today.minusDays(2));
            Loan toReturn = TestFixtures.insertActiveLoan(c, copyB, member, today.minusDays(3));
            loanDao.markReturned(c, toReturn.getLoanId(), today);

            List<Loan> activeLoans = loanDao.findActive(c);
            assertEquals(1, activeLoans.size());
            assertEquals(active.getLoanId(), activeLoans.get(0).getLoanId());
            assertNull(activeLoans.get(0).getReturnDate());
        }
    }

    @Test
    void loanInsertedWithReturnedStatusRequiresReturnDate() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            Book book = TestFixtures.insertBook(c, "978-IR-000-000-001", "Historical Loan Book");
            BookCopy copy = TestFixtures.insertCopy(c, book, "IR-CP-001", CopyStatus.AVAILABLE);
            Member member = TestFixtures.insertMember(c, "M-IR-001", "Member IR", "ir@test.com");

            LocalDate loanDate = LocalDate.of(2026, 3, 1);

            // RETURNED with return_date â€” accepted (inserting historical data).
            Loan historical = new Loan(null, copy.getCopyId(), member.getMemberId(),
                    loanDate, loanDate.plusDays(14), loanDate.plusDays(5), LoanStatus.RETURNED);
            assertEquals(1, loanDao.insert(c, historical));
            assertEquals(LoanStatus.RETURNED, loanDao.findByIdForUpdate(c, historical.getLoanId()).orElseThrow().getStatus());

            // RETURNED without return_date â€” rejected by CHECK constraint.
            DataAccessException error = assertThrows(DataAccessException.class,
                    () -> loanDao.insert(c, new Loan(null, copy.getCopyId(), member.getMemberId(),
                            loanDate.plusDays(30), loanDate.plusDays(44), null, LoanStatus.RETURNED)));
            assertEquals(AppConfig.SQLSTATE_CHECK_VIOLATION, error.getSqlState());
        }
    }

    // ------------------------------------------------------------------
    // Multiple loans: history and parallel borrows
    // ------------------------------------------------------------------

    @Test
    void multipleLoanHistoryPerCopyAllowed() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            Book book = TestFixtures.insertBook(c, "978-HIS-000-000-001", "History Per Copy Book");
            BookCopy copy = TestFixtures.insertCopy(c, book, "HIS-CP-001", CopyStatus.AVAILABLE);
            Member member = TestFixtures.insertMember(c, "M-HIS-001", "Member HIS", "his@test.com");

            // First borrow-return cycle.
            LocalDate d1 = LocalDate.of(2026, 1, 5);
            Loan l1 = TestFixtures.insertActiveLoan(c, copy, member, d1);
            loanDao.markReturned(c, l1.getLoanId(), d1.plusDays(10));

            // Second borrow-return cycle on the same copy.
            LocalDate d2 = LocalDate.of(2026, 2, 10);
            Loan l2 = TestFixtures.insertActiveLoan(c, copy, member, d2);
            loanDao.markReturned(c, l2.getLoanId(), d2.plusDays(7));

            // Third borrow currently active.
            LocalDate d3 = LocalDate.of(2026, 3, 15);
            Loan l3 = TestFixtures.insertActiveLoan(c, copy, member, d3);

            // History for this copy: 3 loans total, 2 returned, 1 active.
            LoanSearchCriteria criteria = new LoanSearchCriteria();
            criteria.setCopyId(copy.getCopyId());
            Page<Loan> page = loanDao.search(c, criteria);
            assertEquals(3, page.getTotalElements());

            assertEquals(1, loanDao.countActive(c));
            assertTrue(loanDao.findActiveByCopyId(c, copy.getCopyId()).isPresent());
            assertEquals(l3.getLoanId(), loanDao.findActiveByCopyId(c, copy.getCopyId()).orElseThrow().getLoanId());
        }
    }

    @Test
    void multipleActiveLoansForDifferentCopiesOfSameBookAllowed() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            Book book = TestFixtures.insertBook(c, "978-MC-000-000-001", "Multi Copy Book");
            BookCopy copyA = TestFixtures.insertCopy(c, book, "MC-CP-001", CopyStatus.AVAILABLE);
            BookCopy copyB = TestFixtures.insertCopy(c, book, "MC-CP-002", CopyStatus.AVAILABLE);
            Member member = TestFixtures.insertMember(c, "M-MC-001", "Member MC", "mc@test.com");

            LocalDate today = LocalDate.of(2026, 10, 25);
            Loan l1 = TestFixtures.insertActiveLoan(c, copyA, member, today.minusDays(1));
            Loan l2 = TestFixtures.insertActiveLoan(c, copyB, member, today.minusDays(2));

            // The unique partial index is on copy_id (WHERE return_date IS NULL), not book_id.
            // Two active loans on DIFFERENT copies of the SAME book are both valid.
            assertNotNull(l1.getLoanId());
            assertNotNull(l2.getLoanId());
            assertEquals(2, loanDao.countActive(c));
        }
    }

    @Test
    void multipleActiveLoansForSameMemberDifferentBooksAllowed() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            Book bookA = TestFixtures.insertBook(c, "978-MA-000-000-001", "Book A");
            Book bookB = TestFixtures.insertBook(c, "978-MA-000-000-002", "Book B");
            BookCopy copyA = TestFixtures.insertCopy(c, bookA, "MA-CP-001", CopyStatus.AVAILABLE);
            BookCopy copyB = TestFixtures.insertCopy(c, bookB, "MA-CP-002", CopyStatus.AVAILABLE);
            Member member = TestFixtures.insertMember(c, "M-MA-001", "Member MA", "ma@test.com");

            LocalDate today = LocalDate.of(2026, 10, 25);
            TestFixtures.insertActiveLoan(c, copyA, member, today.minusDays(1));
            TestFixtures.insertActiveLoan(c, copyB, member, today.minusDays(2));

            // One member can have multiple active loans on different books/copies.
            LoanSearchCriteria criteria = new LoanSearchCriteria();
            criteria.setMemberId(member.getMemberId());
            criteria.setStatus(LoanStatus.ACTIVE);
            assertEquals(2, loanDao.search(c, criteria).getTotalElements());
        }
    }

    // ------------------------------------------------------------------
    // Date-sensitive counters and filters
    // ------------------------------------------------------------------

    @Test
    void countActiveExcludesReturnedLoans() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            Book book = TestFixtures.insertBook(c, "978-CA-000-000-001", "Count Active Book");
            BookCopy c1 = TestFixtures.insertCopy(c, book, "CA-CP-001", CopyStatus.AVAILABLE);
            BookCopy c2 = TestFixtures.insertCopy(c, book, "CA-CP-002", CopyStatus.AVAILABLE);
            BookCopy c3 = TestFixtures.insertCopy(c, book, "CA-CP-003", CopyStatus.AVAILABLE);
            Member member = TestFixtures.insertMember(c, "M-CA-001", "Member CA", "ca@test.com");

            LocalDate today = LocalDate.of(2026, 10, 25);
            Loan l1 = TestFixtures.insertActiveLoan(c, c1, member, today.minusDays(2));
            Loan l2 = TestFixtures.insertActiveLoan(c, c2, member, today.minusDays(3));
            Loan l3 = TestFixtures.insertActiveLoan(c, c3, member, today.minusDays(4));

            loanDao.markReturned(c, l3.getLoanId(), today);

            assertEquals(2, loanDao.countActive(c),
                    "countActive must count only loans with return_date IS NULL");

            // Returning one more reduces the count.
            loanDao.markReturned(c, l2.getLoanId(), today);
            assertEquals(1, loanDao.countActive(c));
        }
    }

    @Test
    void countOverdueDependsOnCallerSuppliedToday() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            Book book = TestFixtures.insertBook(c, "978-CO-000-000-001", "Count Overdue Book");
            BookCopy copy = TestFixtures.insertCopy(c, book, "CO-CP-001", CopyStatus.AVAILABLE);
            Member member = TestFixtures.insertMember(c, "M-CO-001", "Member CO", "co@test.com");

            // LoanDate=Oct 1, DueDate=Oct 15.
            LocalDate loanDate = LocalDate.of(2026, 10, 1);
            TestFixtures.insertActiveLoan(c, copy, member, loanDate);

            // On Oct 10: not yet overdue (due Oct 15).
            assertEquals(0, loanDao.countOverdue(c, LocalDate.of(2026, 10, 10)));
            // On Oct 15: not overdue (due_date < today is false; due_date == today).
            assertEquals(0, loanDao.countOverdue(c, LocalDate.of(2026, 10, 15)));
            // On Oct 16: overdue by 1 day.
            assertEquals(1, loanDao.countOverdue(c, LocalDate.of(2026, 10, 16)));
            // On Nov 1: overdue by 17 days.
            assertEquals(1, loanDao.countOverdue(c, LocalDate.of(2026, 11, 1)));
        }
    }

    @Test
    void searchOverdueOnlyUsesCriteriaTodayNotSystemClock() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            Book book = TestFixtures.insertBook(c, "978-SO-000-000-001", "Search Overdue Book");
            BookCopy copy = TestFixtures.insertCopy(c, book, "SO-CP-001", CopyStatus.AVAILABLE);
            Member member = TestFixtures.insertMember(c, "M-SO-001", "Member SO", "so@test.com");

            LocalDate loanDate = LocalDate.of(2026, 10, 1);
            TestFixtures.insertActiveLoan(c, copy, member, loanDate);

            // Pretend "today" is Oct 10 â€” not overdue.
            LoanSearchCriteria notOverdue = new LoanSearchCriteria();
            notOverdue.setOverdueOnly(true);
            notOverdue.setToday(LocalDate.of(2026, 10, 10));
            assertEquals(0, loanDao.search(c, notOverdue).getTotalElements());

            // Pretend "today" is Oct 20 â€” overdue.
            LoanSearchCriteria overdue = new LoanSearchCriteria();
            overdue.setOverdueOnly(true);
            overdue.setToday(LocalDate.of(2026, 10, 20));
            Page<Loan> result = loanDao.search(c, overdue);
            assertEquals(1, result.getTotalElements());
            assertEquals(copy.getCopyId(), result.getContent().get(0).getCopyId());
        }
    }

    @Test
    void searchWithDateRangeFilterIsInclusive() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            Book book = TestFixtures.insertBook(c, "978-DR-000-000-001", "Date Range Book");
            BookCopy cp1 = TestFixtures.insertCopy(c, book, "DR-CP-001", CopyStatus.AVAILABLE);
            BookCopy cp2 = TestFixtures.insertCopy(c, book, "DR-CP-002", CopyStatus.AVAILABLE);
            BookCopy cp3 = TestFixtures.insertCopy(c, book, "DR-CP-003", CopyStatus.AVAILABLE);
            Member member = TestFixtures.insertMember(c, "M-DR-001", "Member DR", "dr@test.com");

            TestFixtures.insertActiveLoan(c, cp1, member, LocalDate.of(2026, 1, 10));
            TestFixtures.insertActiveLoan(c, cp2, member, LocalDate.of(2026, 2, 15));
            TestFixtures.insertActiveLoan(c, cp3, member, LocalDate.of(2026, 3, 20));

            LoanSearchCriteria criteria = new LoanSearchCriteria();
            criteria.setFromDate(LocalDate.of(2026, 2, 1));
            criteria.setToDate(LocalDate.of(2026, 2, 28));
            Page<Loan> february = loanDao.search(c, criteria);
            assertEquals(1, february.getTotalElements());
            assertEquals(LocalDate.of(2026, 2, 15), february.getContent().get(0).getLoanDate());
        }
    }

    @Test
    void searchByStatusReturnsOnlyMatchingLoans() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            Book book = TestFixtures.insertBook(c, "978-SS-000-000-001", "Status Search Book");
            BookCopy cp1 = TestFixtures.insertCopy(c, book, "SS-CP-001", CopyStatus.AVAILABLE);
            BookCopy cp2 = TestFixtures.insertCopy(c, book, "SS-CP-002", CopyStatus.AVAILABLE);
            Member member = TestFixtures.insertMember(c, "M-SS-001", "Member SS", "ss@test.com");

            LocalDate today = LocalDate.of(2026, 10, 25);
            Loan active = TestFixtures.insertActiveLoan(c, cp1, member, today.minusDays(2));
            Loan returned = TestFixtures.insertActiveLoan(c, cp2, member, today.minusDays(5));
            loanDao.markReturned(c, returned.getLoanId(), today.minusDays(1));

            LoanSearchCriteria activeOnly = new LoanSearchCriteria();
            activeOnly.setStatus(LoanStatus.ACTIVE);
            Page<Loan> activePage = loanDao.search(c, activeOnly);
            assertEquals(1, activePage.getTotalElements());
            assertEquals(active.getLoanId(), activePage.getContent().get(0).getLoanId());

            LoanSearchCriteria returnedOnly = new LoanSearchCriteria();
            returnedOnly.setStatus(LoanStatus.RETURNED);
            Page<Loan> returnedPage = loanDao.search(c, returnedOnly);
            assertEquals(1, returnedPage.getTotalElements());
            assertEquals(returned.getLoanId(), returnedPage.getContent().get(0).getLoanId());
        }
    }

    @Test
    void findActiveByCopyIdReturnsEmptyAfterReturn() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            Book book = TestFixtures.insertBook(c, "978-FAC-000-000-001", "Find Active By Copy Book");
            BookCopy copy = TestFixtures.insertCopy(c, book, "FAC-CP-001", CopyStatus.AVAILABLE);
            Member member = TestFixtures.insertMember(c, "M-FAC-001", "Member FAC", "fac@test.com");

            LocalDate today = LocalDate.of(2026, 10, 25);
            Loan loan = TestFixtures.insertActiveLoan(c, copy, member, today);

            Optional<Loan> active = loanDao.findActiveByCopyId(c, copy.getCopyId());
            assertTrue(active.isPresent());
            assertEquals(loan.getLoanId(), active.get().getLoanId());

            loanDao.markReturned(c, loan.getLoanId(), today);

            assertFalse(loanDao.findActiveByCopyId(c, copy.getCopyId()).isPresent(),
                    "After return, no active loan should exist for that copy");
        }
    }
}

