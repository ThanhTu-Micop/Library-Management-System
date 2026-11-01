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
import com.library.model.enums.MemberStatus;
import com.library.config.AppConfig;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DAO tests for {@link JdbcMemberDAO} against {@code library_test}.
 *
 * <p>Natural-key uniqueness now covers both INSERT and UPDATE in one test per key, and the
 * active-loan projection is cross-checked against the authoritative {@code loans} table.
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

    /** Member codes are unique on INSERT and UPDATE; the exists helper excludes the edited row. */
    @Test
    void memberCodeIsUniqueAndExistsExcludesSelf() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            Member first = TestFixtures.insertMember(c, "M-UNQ-001", "User One", "u1@test.com");
            Member second = TestFixtures.insertMember(c, "M-UNQ-002", "User Two", "u2@test.com");

            assertAll(
                    () -> assertTrue(dao.memberCodeExists(c, "M-UNQ-001", null)),
                    () -> assertFalse(dao.memberCodeExists(c, "M-UNQ-003", null)),
                    () -> assertFalse(dao.memberCodeExists(c, "M-UNQ-001", first.getMemberId()),
                            "memberCodeExists must exclude the row being edited"));

            DataAccessException insertError = assertThrows(DataAccessException.class,
                    () -> TestFixtures.insertMember(c, "M-UNQ-001", "Duplicate", "dup@test.com"));
            assertEquals(AppConfig.SQLSTATE_UNIQUE_VIOLATION, insertError.getSqlState());

            second.setMemberCode("M-UNQ-001");
            DataAccessException updateError = assertThrows(DataAccessException.class,
                    () -> dao.update(c, second));
            assertEquals(AppConfig.SQLSTATE_UNIQUE_VIOLATION, updateError.getSqlState(),
                    "UPDATE must hit the same unique constraint as INSERT");
        }
    }

    /** Email uniqueness is case-insensitive on INSERT and UPDATE; exists excludes self. */
    @Test
    void emailIsUniqueCaseInsensitivelyAndExistsExcludesSelf() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            Member first = TestFixtures.insertMember(c, "M-EML-001", "User One", "Two@Test.com");
            Member second = TestFixtures.insertMember(c, "M-EML-002", "User Two", "second@test.com");

            assertAll(
                    () -> assertTrue(dao.emailExists(c, "two@test.com", null)),
                    () -> assertTrue(dao.emailExists(c, "TWO@test.COM", null)),
                    () -> assertFalse(dao.emailExists(c, "three@test.com", null)),
                    () -> assertFalse(dao.emailExists(c, "two@test.com", first.getMemberId()),
                            "emailExists must exclude the row being edited"),
                    () -> assertFalse(dao.emailExists(c, "TWO@TEST.COM", first.getMemberId())));

            DataAccessException insertError = assertThrows(DataAccessException.class,
                    () -> TestFixtures.insertMember(c, "M-EML-003", "User Three", "two@TEST.com"));
            assertEquals(AppConfig.SQLSTATE_UNIQUE_VIOLATION, insertError.getSqlState());

            second.setEmail("TWO@TEST.COM");
            DataAccessException updateError = assertThrows(DataAccessException.class,
                    () -> dao.update(c, second));
            assertEquals(AppConfig.SQLSTATE_UNIQUE_VIOLATION, updateError.getSqlState(),
                    "UPDATE must hit the same partial unique index ux_members_email_lower as INSERT");
        }
    }

    /** Blank strings become NULL on INSERT and UPDATE; an explicit NULL stays NULL. */
    @Test
    void blankAndNullEmailAreNormalizedOnInsertAndUpdate() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);

            Member blankInsert = TestFixtures.insertMember(c, "M-BLANK-001", "Blank Insert", "   ");
            assertNull(dao.findById(c, blankInsert.getMemberId()).orElseThrow().getEmail(),
                    "JdbcMemberDAO.insert must normalize blank email to NULL");
            assertFalse(dao.emailExists(c, "   ", null),
                    "emailExists on a blank string must return false");

            Member blankUpdate = TestFixtures.insertMember(c, "M-BLANK-002", "Blank Update", "real@test.com");
            blankUpdate.setEmail("   ");
            dao.update(c, blankUpdate);
            assertNull(dao.findById(c, blankUpdate.getMemberId()).orElseThrow().getEmail(),
                    "JdbcMemberDAO.update must normalize blank email to NULL");

            Member nullUpdate = TestFixtures.insertMember(c, "M-NULL-001", "Null Update", "real2@test.com");
            nullUpdate.setEmail(null);
            dao.update(c, nullUpdate);
            assertNull(dao.findById(c, nullUpdate.getMemberId()).orElseThrow().getEmail(),
                    "A NULL email must remain NULL on UPDATE");
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

    /**
     * The projected {@code activeLoanCount} must equal the live {@code loans} count,
     * drop as loans are returned, and ignore returned history entirely.
     */
    @Test
    void activeLoanCountTracksOnlyOpenLoans() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            Member member = TestFixtures.insertMember(c, "M-COUNT-001", "Count Member", "count@test.com");
            Book book = TestFixtures.insertBook(c, "978-MCNT-000-001", "Member Count Book");
            BookCopy cp1 = TestFixtures.insertCopy(c, book, "MCNT-CP-001", CopyStatus.AVAILABLE);
            BookCopy cp2 = TestFixtures.insertCopy(c, book, "MCNT-CP-002", CopyStatus.AVAILABLE);
            BookCopy cp3 = TestFixtures.insertCopy(c, book, "MCNT-CP-003", CopyStatus.AVAILABLE);

            LocalDate today = LocalDate.of(2026, 10, 25);
            Loan l1 = TestFixtures.insertActiveLoan(c, cp1, member, today);
            Loan l2 = TestFixtures.insertActiveLoan(c, cp2, member, today);
            Loan l3 = TestFixtures.insertActiveLoan(c, cp3, member, today);

            MemberSearchCriteria criteria = new MemberSearchCriteria();
            criteria.setKeyword("Count Member");
            Member searched = dao.search(c, criteria).getContent().get(0);
            assertEquals(3, searched.getActiveLoanCount());

            long rawActive = rawCount(c,
                    "SELECT COUNT(*) FROM loans WHERE member_id = ? AND return_date IS NULL",
                    member.getMemberId());
            assertEquals(rawActive, searched.getActiveLoanCount());

            loanDao.markReturned(c, l1.getLoanId(), today);
            assertEquals(2, dao.findById(c, member.getMemberId()).orElseThrow().getActiveLoanCount());

            loanDao.markReturned(c, l2.getLoanId(), today);
            Member afterTwo = dao.findById(c, member.getMemberId()).orElseThrow();
            assertEquals(1, afterTwo.getActiveLoanCount());

            List<Member> active = dao.findAllActive(c);
            assertEquals(1, active.size());
            assertEquals(1, active.get(0).getActiveLoanCount());

            loanDao.markReturned(c, l3.getLoanId(), today);
            assertEquals(0, dao.findById(c, member.getMemberId()).orElseThrow().getActiveLoanCount(),
                    "activeLoanCount must reflect only return_date IS NULL, not total loan history");
            assertEquals(3, rawCount(c, "SELECT COUNT(*) FROM loans WHERE member_id = ?",
                    member.getMemberId()), "Loan history must remain in the table");
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
