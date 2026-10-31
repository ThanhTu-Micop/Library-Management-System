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
import com.library.model.criteria.MemberSearchCriteria;
import com.library.model.enums.CopyStatus;
import com.library.model.enums.LoanStatus;
import com.library.model.enums.MemberStatus;
import com.library.config.AppConfig;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DAO tests for {@link JdbcMemberDAO} against {@code library_test}.
 */
class JdbcMemberDAOTest {

    private final MemberDAO dao = new JdbcMemberDAO();
    private final BookDAO bookDao = new JdbcBookDAO();
    private final BookCopyDAO copyDao = new JdbcBookCopyDAO();
    private final LoanDAO loanDao = new JdbcLoanDAO();

    @Test
    void insertAndFindById() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            Member member = new Member(null, "M-TEST-001", "Nguyen Van Test", "nvt@test.com", MemberStatus.ACTIVE);
            int affected = dao.insert(c, member);
            assertEquals(1, affected);
            assertNotNull(member.getMemberId());

            Optional<Member> found = dao.findById(c, member.getMemberId());
            assertTrue(found.isPresent());
            assertEquals("Nguyen Van Test", found.get().getFullName());
            assertNotNull(found.get().getCreatedAt());
        }
    }

    @Test
    void memberCodeMustBeUnique() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            dao.insert(c, new Member(null, "M-UNQ-001", "User One", "u1@test.com", MemberStatus.ACTIVE));
            assertTrue(dao.memberCodeExists(c, "M-UNQ-001", null));
            assertFalse(dao.memberCodeExists(c, "M-UNQ-002", null));

            DataAccessException error = assertThrows(DataAccessException.class,
                    () -> dao.insert(c, new Member(null, "M-UNQ-001", "User Two", "u2@test.com", MemberStatus.ACTIVE)));
            assertEquals(AppConfig.SQLSTATE_UNIQUE_VIOLATION, error.getSqlState());
        }
    }

    @Test
    void emailMustBeUniqueCaseInsensitively() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            dao.insert(c, new Member(null, "M-EML-001", "User Two", "Two@Test.com", MemberStatus.ACTIVE));
            assertTrue(dao.emailExists(c, "two@test.com", null));
            assertTrue(dao.emailExists(c, "TWO@test.COM", null));
            assertFalse(dao.emailExists(c, "three@test.com", null));

            DataAccessException error = assertThrows(DataAccessException.class,
                    () -> dao.insert(c, new Member(null, "M-EML-002", "User Three", "two@TEST.com", MemberStatus.ACTIVE)));
            assertEquals(AppConfig.SQLSTATE_UNIQUE_VIOLATION, error.getSqlState());
        }
    }

    @Test
    void blankEmailIsNormalizedToNull() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            Member member = new Member(null, "M-BLANK-001", "Blank Email", "   ", MemberStatus.ACTIVE);
            dao.insert(c, member);

            Member found = dao.findById(c, member.getMemberId()).orElseThrow();
            assertNull(found.getEmail());
            assertFalse(dao.emailExists(c, "   ", null));
        }
    }

    @Test
    void searchByStatusAndKeyword() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            dao.insert(c, new Member(null, "M-S-001", "Search Active", "sa@test.com", MemberStatus.ACTIVE));
            dao.insert(c, new Member(null, "M-S-002", "Search Inactive", "si@test.com", MemberStatus.INACTIVE));

            MemberSearchCriteria criteria = new MemberSearchCriteria();
            criteria.setStatus(MemberStatus.ACTIVE);
            Page<Member> page = dao.search(c, criteria);
            assertEquals(1, page.getTotalElements());
            assertEquals("Search Active", page.getContent().get(0).getFullName());

            criteria.setStatus(null);
            criteria.setKeyword("Inactive");
            Page<Member> keywordPage = dao.search(c, criteria);
            assertEquals(1, keywordPage.getTotalElements());
            assertEquals("Search Inactive", keywordPage.getContent().get(0).getFullName());
        }
    }

    @Test
    void activeLoanCountIsPopulated() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            Member member = new Member(null, "M-COUNT-001", "Count Member", "count@test.com", MemberStatus.ACTIVE);
            dao.insert(c, member);

            Book book = new Book(null, "978-MCNT-000-001", "Member Count Book", 1, 1, 2020);
            bookDao.insert(c, book);
            BookCopy copy = new BookCopy(null, book.getBookId(), "MCNT-CP-001", CopyStatus.BORROWED);
            copyDao.insert(c, copy);

            LocalDate today = LocalDate.of(2026, 10, 25);
            loanDao.insert(c, new Loan(null, copy.getCopyId(), member.getMemberId(),
                    today.minusDays(3), today.plusDays(11), null, LoanStatus.ACTIVE));

            MemberSearchCriteria criteria = new MemberSearchCriteria();
            criteria.setKeyword("Count Member");
            Member searched = dao.search(c, criteria).getContent().get(0);
            assertEquals(1, searched.getActiveLoanCount());

            List<Member> active = dao.findAllActive(c);
            assertEquals(1, active.size());
            assertEquals(1, active.get(0).getActiveLoanCount());
        }
    }
}
