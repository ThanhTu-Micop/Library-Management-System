package com.library.dao;

import com.library.dao.jdbc.JdbcBookCopyDAO;
import com.library.dao.jdbc.JdbcLoanDAO;
import com.library.dao.jdbc.JdbcMemberDAO;
import com.library.dao.support.TestDatabase;
import com.library.dao.support.TestFixtures;
import com.library.exception.DataAccessException;
import com.library.model.Book;
import com.library.model.BookCopy;
import com.library.model.Loan;
import com.library.model.Member;
import com.library.model.enums.CopyStatus;
import com.library.model.enums.LoanStatus;
import com.library.model.enums.MemberStatus;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DAO tests for {@link JdbcBookCopyDAO} against {@code library_test}.
 */
class JdbcBookCopyDAOTest {

    private final BookCopyDAO copyDao = new JdbcBookCopyDAO();
    private final MemberDAO memberDao = new JdbcMemberDAO();
    private final LoanDAO loanDao = new JdbcLoanDAO();

    @Test
    void insertAndFindByBookId() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            Book book = TestFixtures.insertBook(c, "978-COPY-000-001", "Copy Book");
            BookCopy copy = new BookCopy(null, book.getBookId(), "COPY-001", CopyStatus.AVAILABLE);
            assertEquals(1, copyDao.insert(c, copy));
            assertNotNull(copy.getCopyId());

            List<BookCopy> copies = copyDao.findByBookId(c, book.getBookId());
            assertEquals(1, copies.size());
            assertEquals("COPY-001", copies.get(0).getCopyCode());
            assertEquals(CopyStatus.AVAILABLE, copies.get(0).getStatus());
            assertNotNull(copies.get(0).getCreatedAt());
        }
    }

    @Test
    void findByIdForUpdateAndUpdateStatus() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            Book book = TestFixtures.insertBook(c, "978-COPY-000-002", "Status Book");
            BookCopy copy = new BookCopy(null, book.getBookId(), "COPY-002", CopyStatus.AVAILABLE);
            copyDao.insert(c, copy);

            Optional<BookCopy> locked = copyDao.findByIdForUpdate(c, copy.getCopyId());
            assertTrue(locked.isPresent());

            assertEquals(1, copyDao.updateStatus(c, copy.getCopyId(), CopyStatus.BORROWED));
            assertEquals(CopyStatus.BORROWED, copyDao.findByBookId(c, book.getBookId()).get(0).getStatus());
        }
    }

    @Test
    void findAvailableWithBookReturnsOnlyAvailableCopies() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            Book book = TestFixtures.insertBook(c, "978-COPY-000-003", "Available Copy Book");
            copyDao.insert(c, new BookCopy(null, book.getBookId(), "COPY-003-A", CopyStatus.AVAILABLE));
            copyDao.insert(c, new BookCopy(null, book.getBookId(), "COPY-003-B", CopyStatus.BORROWED));
            copyDao.insert(c, new BookCopy(null, book.getBookId(), "COPY-003-C", CopyStatus.MAINTENANCE));

            List<BookCopy> available = copyDao.findAvailableWithBook(c);
            assertEquals(1, available.size());
            assertEquals("COPY-003-A", available.get(0).getCopyCode());
            assertEquals("Available Copy Book", available.get(0).getBookTitle());
        }
    }

    @Test
    void deleteByBookIdWithZeroCopiesSucceeds() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            Book book = TestFixtures.insertBook(c, "978-COPY-000-004", "No Copies Book");
            assertDoesNotThrow(() -> copyDao.deleteByBookId(c, book.getBookId()));
            assertTrue(copyDao.findByBookId(c, book.getBookId()).isEmpty());
        }
    }

    @Test
    void deleteByBookIdWithMultipleCopies() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            Book book = TestFixtures.insertBook(c, "978-COPY-000-005", "Many Copies Book");
            copyDao.insert(c, new BookCopy(null, book.getBookId(), "COPY-005-A", CopyStatus.AVAILABLE));
            copyDao.insert(c, new BookCopy(null, book.getBookId(), "COPY-005-B", CopyStatus.MAINTENANCE));

            copyDao.deleteByBookId(c, book.getBookId());
            assertTrue(copyDao.findByBookId(c, book.getBookId()).isEmpty());
        }
    }

    @Test
    void deletingCopiesWithLoanHistoryIsRejected() throws Exception {
        try (Connection c = TestDatabase.getConnection()) {
            TestFixtures.prepare(c);
            Book book = TestFixtures.insertBook(c, "978-COPY-000-006", "Loan Protected Book");
            BookCopy copy = new BookCopy(null, book.getBookId(), "COPY-006-A", CopyStatus.BORROWED);
            copyDao.insert(c, copy);
            Member member = new Member(null, "M-COPY-001", "Copy Member", "copy@test.com", MemberStatus.ACTIVE);
            memberDao.insert(c, member);

            LocalDate loanDate = LocalDate.of(2026, 10, 25);
            loanDao.insert(c, new Loan(null, copy.getCopyId(), member.getMemberId(),
                    loanDate, loanDate.plusDays(14), null, LoanStatus.ACTIVE));

            DataAccessException error = assertThrows(DataAccessException.class,
                    () -> copyDao.deleteByBookId(c, book.getBookId()));
            assertEquals("23503", error.getSqlState());
        }
    }
}
