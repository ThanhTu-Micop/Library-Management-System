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
 * <p>Each test now owns one rule: the 14-day calendar rule, the return transition, uniqueness
 * scope, and date-sensitive counters. Boundary cases for the same rule are grouped with
 * {@link org.junit.jupiter.api.Assertions#assertAll}, so every case still reports separately.
 */
class LoanBusinessLogicTest {

    private final LoanDAO loanDao = new JdbcLoanDAO();

    @Test
    void dueDateAcrossMonthAndYearBoundariesIsExactlyFourteenDays() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);

            Book monthBook = TestFixtures.insertBook(c, "978-MB-000-000-001", "Month Boundary Book");
            BookCopy monthCopy = TestFixtures.insertCopy(c, monthBook, "MB-CP-001", CopyStatus.AVAILABLE);
            Member monthMember = TestFixtures.insertMember(c, "M-MB-001", "Member MB", "mb@test.com");
            Loan monthLoan = TestFixtures.insertActiveLoan(c, monthCopy, monthMember,
                    LocalDate.of(2026, 1, 20));

            Book yearBook = TestFixtures.insertBook(c, "978-YB-000-000-001", "Year Boundary Book");
            BookCopy yearCopy = TestFixtures.insertCopy(c, yearBook, "YB-CP-001", CopyStatus.AVAILABLE);
            Member yearMember = TestFixtures.insertMember(c, "M-YB-001", "Member YB", "yb@test.com");
            Loan yearLoan = TestFixtures.insertActiveLoan(c, yearCopy, yearMember,
                    LocalDate.of(2026, 12, 20));

            assertAll(
                    () -> assertEquals(LocalDate.of(2026, 2, 3),
                            loanDao.findByIdForUpdate(c, monthLoan.getLoanId()).orElseThrow().getDueDate(),
                            "Jan 20 + 14 days crosses into February"),
                    () -> assertEquals(LocalDate.of(2027, 1, 3),
                            loanDao.findByIdForUpdate(c, yearLoan.getLoanId()).orElseThrow().getDueDate(),
                            "Dec 20 + 14 days crosses into the next year"));
        }
    }

    @Test
    void dueDateHandlesLeapAndNonLeapFebruary() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);

            Book leapBook = TestFixtures.insertBook(c, "978-LP-000-000-001", "Leap Year Book");
            BookCopy leapCopy = TestFixtures.insertCopy(c, leapBook, "LP-CP-001", CopyStatus.AVAILABLE);
            Member leapMember = TestFixtures.insertMember(c, "M-LP-001", "Member LP", "lp@test.com");
            Loan leapLoan = TestFixtures.insertActiveLoan(c, leapCopy, leapMember,
                    LocalDate.of(2024, 2, 16));

            Book nonLeapBook = TestFixtures.insertBook(c, "978-NL-000-000-001", "Non Leap Book");
            BookCopy nonLeapCopy = TestFixtures.insertCopy(c, nonLeapBook, "NL-CP-001", CopyStatus.AVAILABLE);
            Member nonLeapMember = TestFixtures.insertMember(c, "M-NL-001", "Member NL", "nl@test.com");
            Loan nonLeapLoan = TestFixtures.insertActiveLoan(c, nonLeapCopy, nonLeapMember,
                    LocalDate.of(2026, 2, 16));

            assertAll(
                    () -> assertEquals(LocalDate.of(2024, 3, 1),
                            loanDao.findByIdForUpdate(c, leapLoan.getLoanId()).orElseThrow().getDueDate(),
                            "Feb 16, 2024 + 14 days must include Feb 29"),
                    () -> assertEquals(LocalDate.of(2026, 3, 2),
                            loanDao.findByIdForUpdate(c, nonLeapLoan.getLoanId()).orElseThrow().getDueDate(),
                            "Feb 16, 2026 has no Feb 29 to include"));
        }
    }

    @Test
    void dueDateMustBeExactlyFourteenDays() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            Book book = TestFixtures.insertBook(c, "978-D14-000-000-001", "Due Date Rule");
            BookCopy copy = TestFixtures.insertCopy(c, book, "D14-CP-001", CopyStatus.AVAILABLE);
            Member member = TestFixtures.insertMember(c, "M-D14-001", "Member D14", "d14@test.com");
            LocalDate loanDate = LocalDate.of(2026, 6, 1);

            DataAccessException thirteen = assertThrows(DataAccessException.class,
                    () -> loanDao.insert(c, new Loan(null, copy.getCopyId(), member.getMemberId(),
                            loanDate, loanDate.plusDays(13), null, LoanStatus.ACTIVE)));
            DataAccessException fifteen = assertThrows(DataAccessException.class,
                    () -> loanDao.insert(c, new Loan(null, copy.getCopyId(), member.getMemberId(),
                            loanDate, loanDate.plusDays(15), null, LoanStatus.ACTIVE)));

            assertAll(
                    () -> assertEquals(AppConfig.SQLSTATE_CHECK_VIOLATION, thirteen.getSqlState()),
                    () -> assertEquals(AppConfig.SQLSTATE_CHECK_VIOLATION, fifteen.getSqlState()));
        }
    }

    @Test
    void markReturnedIsIdempotentAndPreservesFirstReturnDate() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            Book book = TestFixtures.insertBook(c, "978-IDEM-000-000-001", "Idempotent Return Book");
            BookCopy copy = TestFixtures.insertCopy(c, book, "IDEM-CP-001", CopyStatus.AVAILABLE);
            Member member = TestFixtures.insertMember(c, "M-IDEM-001", "Member Idem", "idem@test.com");

            LocalDate loanDate = LocalDate.of(2026, 9, 1);
            Loan loan = TestFixtures.insertActiveLoan(c, copy, member, loanDate);

            assertEquals(1, loanDao.markReturned(c, loan.getLoanId(), loanDate.plusDays(5)));
            assertEquals(0, loanDao.markReturned(c, loan.getLoanId(), loanDate.plusDays(7)));
            assertEquals(0, loanDao.markReturned(c, loan.getLoanId(), loanDate.plusDays(14)));

            Loan found = loanDao.findByIdForUpdate(c, loan.getLoanId()).orElseThrow();
            assertAll(
                    () -> assertEquals(LoanStatus.RETURNED, found.getStatus()),
                    () -> assertEquals(loanDate.plusDays(5), found.getReturnDate(),
                            "The first return_date must be preserved by later no-ops"));
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
                    "return_date >= loan_date is enforced by loans_return_date_check");

            Loan found = loanDao.findByIdForUpdate(c, loan.getLoanId()).orElseThrow();
            assertAll(
                    () -> assertEquals(LoanStatus.ACTIVE, found.getStatus()),
                    () -> assertNull(found.getReturnDate()));
        }
    }

    @Test
    void markReturnedAcceptsSameDayAndFutureDates() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            Book book = TestFixtures.insertBook(c, "978-FU-000-000-001", "Boundary Return Book");
            BookCopy sameDayCopy = TestFixtures.insertCopy(c, book, "FU-CP-001", CopyStatus.AVAILABLE);
            BookCopy futureCopy = TestFixtures.insertCopy(c, book, "FU-CP-002", CopyStatus.AVAILABLE);
            Member member = TestFixtures.insertMember(c, "M-FU-001", "Member FU", "fu@test.com");

            LocalDate sameDay = LocalDate.of(2026, 7, 15);
            Loan sameDayLoan = TestFixtures.insertActiveLoan(c, sameDayCopy, member, sameDay);
            assertEquals(1, loanDao.markReturned(c, sameDayLoan.getLoanId(), sameDay));

            Loan futureLoan = TestFixtures.insertActiveLoan(c, futureCopy, member,
                    LocalDate.of(2026, 5, 1));
            LocalDate futureReturn = LocalDate.of(2099, 12, 31);
            assertEquals(1, loanDao.markReturned(c, futureLoan.getLoanId(), futureReturn),
                    "E11 gap: the schema does not forbid return_date in the future");

            Loan sameDayFound = loanDao.findByIdForUpdate(c, sameDayLoan.getLoanId()).orElseThrow();
            Loan futureFound = loanDao.findByIdForUpdate(c, futureLoan.getLoanId()).orElseThrow();
            assertAll(
                    () -> assertEquals(LoanStatus.RETURNED, sameDayFound.getStatus()),
                    () -> assertEquals(sameDay, sameDayFound.getReturnDate()),
                    () -> assertEquals(futureReturn, futureFound.getReturnDate()));
        }
    }

    @Test
    void markReturnedOnNonExistentLoanIdReturnsZero() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            assertEquals(0, loanDao.markReturned(c, 999_999, LocalDate.of(2026, 1, 1)),
                    "markReturned on a missing loan_id must be a no-op returning 0");
        }
    }

    @Test
    void activeLoanQueriesExcludeReturnedLoans() throws Exception {
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
            Optional<Loan> byCopyA = loanDao.findActiveByCopyId(c, copyA.getCopyId());
            assertAll(
                    () -> assertEquals(1, activeLoans.size()),
                    () -> assertEquals(active.getLoanId(), activeLoans.get(0).getLoanId()),
                    () -> assertNull(activeLoans.get(0).getReturnDate()),
                    () -> assertTrue(byCopyA.isPresent()),
                    () -> assertEquals(active.getLoanId(), byCopyA.orElseThrow().getLoanId()),
                    () -> assertFalse(loanDao.findActiveByCopyId(c, copyB.getCopyId()).isPresent()));
        }
    }

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
            assertEquals(2, loanDao.countActive(c));

            loanDao.markReturned(c, l2.getLoanId(), today);
            assertEquals(1, loanDao.countActive(c));
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
            Loan historical = new Loan(null, copy.getCopyId(), member.getMemberId(),
                    loanDate, loanDate.plusDays(14), loanDate.plusDays(5), LoanStatus.RETURNED);
            assertEquals(1, loanDao.insert(c, historical));
            assertEquals(LoanStatus.RETURNED,
                    loanDao.findByIdForUpdate(c, historical.getLoanId()).orElseThrow().getStatus());

            DataAccessException error = assertThrows(DataAccessException.class,
                    () -> loanDao.insert(c, new Loan(null, copy.getCopyId(), member.getMemberId(),
                            loanDate.plusDays(30), loanDate.plusDays(44), null, LoanStatus.RETURNED)));
            assertEquals(AppConfig.SQLSTATE_CHECK_VIOLATION, error.getSqlState());
        }
    }

    @Test
    void multipleLoanHistoryPerCopyAllowed() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            Book book = TestFixtures.insertBook(c, "978-HIS-000-000-001", "History Per Copy Book");
            BookCopy copy = TestFixtures.insertCopy(c, book, "HIS-CP-001", CopyStatus.AVAILABLE);
            Member member = TestFixtures.insertMember(c, "M-HIS-001", "Member HIS", "his@test.com");

            LocalDate d1 = LocalDate.of(2026, 1, 5);
            Loan l1 = TestFixtures.insertActiveLoan(c, copy, member, d1);
            loanDao.markReturned(c, l1.getLoanId(), d1.plusDays(10));

            LocalDate d2 = LocalDate.of(2026, 2, 10);
            Loan l2 = TestFixtures.insertActiveLoan(c, copy, member, d2);
            loanDao.markReturned(c, l2.getLoanId(), d2.plusDays(7));

            LocalDate d3 = LocalDate.of(2026, 3, 15);
            Loan l3 = TestFixtures.insertActiveLoan(c, copy, member, d3);

            LoanSearchCriteria criteria = new LoanSearchCriteria();
            criteria.setCopyId(copy.getCopyId());
            Page<Loan> page = loanDao.search(c, criteria);
            assertAll(
                    () -> assertEquals(3, page.getTotalElements()),
                    () -> assertEquals(1, loanDao.countActive(c)),
                    () -> assertTrue(loanDao.findActiveByCopyId(c, copy.getCopyId()).isPresent()),
                    () -> assertEquals(l3.getLoanId(),
                            loanDao.findActiveByCopyId(c, copy.getCopyId()).orElseThrow().getLoanId()));
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

            assertAll(
                    () -> assertNotNull(l1.getLoanId()),
                    () -> assertNotNull(l2.getLoanId()),
                    () -> assertEquals(2, loanDao.countActive(c)),
                    () -> assertTrue(loanDao.findActiveByCopyId(c, copyA.getCopyId()).isPresent()),
                    () -> assertTrue(loanDao.findActiveByCopyId(c, copyB.getCopyId()).isPresent()));
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

            LoanSearchCriteria criteria = new LoanSearchCriteria();
            criteria.setMemberId(member.getMemberId());
            criteria.setStatus(LoanStatus.ACTIVE);
            assertEquals(2, loanDao.search(c, criteria).getTotalElements());
        }
    }

    /**
     * Overdue means {@code due_date < today}; the caller supplies {@code today}, so the
     * system clock never leaks into the result. Both count and search are checked.
     */
    @Test
    void overdueCountingUsesCallerSuppliedToday() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            // Boundary rule: due today is not overdue; due yesterday is.
            TestFixtures.prepare(c);
            Book boundaryBook = TestFixtures.insertBook(c, "978-CO-000-000-001", "Count Overdue Book");
            BookCopy boundaryCopy = TestFixtures.insertCopy(c, boundaryBook, "CO-CP-001",
                    CopyStatus.AVAILABLE);
            Member boundaryMember = TestFixtures.insertMember(c, "M-CO-001", "Member CO", "co@test.com");
            TestFixtures.insertActiveLoan(c, boundaryCopy, boundaryMember, LocalDate.of(2026, 10, 1));

            assertAll(
                    () -> assertEquals(0, loanDao.countOverdue(c, LocalDate.of(2026, 10, 10))),
                    () -> assertEquals(0, loanDao.countOverdue(c, LocalDate.of(2026, 10, 15))),
                    () -> assertEquals(1, loanDao.countOverdue(c, LocalDate.of(2026, 10, 16))),
                    () -> assertEquals(1, loanDao.countOverdue(c, LocalDate.of(2026, 11, 1))));

            // Count and search share the same predicate on a mixed active/overdue set.
            TestFixtures.prepare(c);
            Book book = TestFixtures.insertBook(c, "978-CNT-000-000-001", "Mixed Overdue Book");
            BookCopy currentCopy = TestFixtures.insertCopy(c, book, "CNT-CP-001", CopyStatus.AVAILABLE);
            BookCopy overdueCopy = TestFixtures.insertCopy(c, book, "CNT-CP-002", CopyStatus.AVAILABLE);
            Member member = TestFixtures.insertMember(c, "M-CNT-001", "Member CNT", "cnt@test.com");

            LocalDate today = LocalDate.of(2026, 10, 25);
            TestFixtures.insertActiveLoan(c, currentCopy, member, today.minusDays(3));
            TestFixtures.insertActiveLoan(c, overdueCopy, member, today.minusDays(24));

            assertEquals(2, loanDao.countActive(c));
            assertEquals(1, loanDao.countOverdue(c, today));
            assertEquals(2, loanDao.countOverdue(c, today.plusDays(12)));

            LoanSearchCriteria criteria = new LoanSearchCriteria();
            criteria.setOverdueOnly(true);
            criteria.setToday(today);
            Page<Loan> overdue = loanDao.search(c, criteria);
            assertAll(
                    () -> assertEquals(1, overdue.getTotalElements()),
                    () -> assertEquals(overdueCopy.getCopyId(), overdue.getContent().get(0).getCopyId()));
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
            assertAll(
                    () -> assertEquals(1, february.getTotalElements()),
                    () -> assertEquals(LocalDate.of(2026, 2, 15),
                            february.getContent().get(0).getLoanDate()));
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

            LoanSearchCriteria returnedOnly = new LoanSearchCriteria();
            returnedOnly.setStatus(LoanStatus.RETURNED);
            Page<Loan> returnedPage = loanDao.search(c, returnedOnly);

            assertAll(
                    () -> assertEquals(1, activePage.getTotalElements()),
                    () -> assertEquals(active.getLoanId(), activePage.getContent().get(0).getLoanId()),
                    () -> assertEquals(1, returnedPage.getTotalElements()),
                    () -> assertEquals(returned.getLoanId(), returnedPage.getContent().get(0).getLoanId()));
        }
    }
}
