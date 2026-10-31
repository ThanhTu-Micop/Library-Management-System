package com.library.dao.support;

import com.library.dao.BookCopyDAO;
import com.library.dao.BookDAO;
import com.library.dao.LoanDAO;
import com.library.dao.MemberDAO;
import com.library.dao.jdbc.JdbcBookCopyDAO;
import com.library.dao.jdbc.JdbcBookDAO;
import com.library.dao.jdbc.JdbcLoanDAO;
import com.library.dao.jdbc.JdbcMemberDAO;
import com.library.model.Book;
import com.library.model.BookCopy;
import com.library.model.Loan;
import com.library.model.Member;
import com.library.model.enums.CopyStatus;
import com.library.model.enums.LoanStatus;
import com.library.model.enums.MemberStatus;
import com.library.config.AppConfig;

import java.sql.Connection;
import java.time.LocalDate;

/**
 * Shared row builders for integration tests.
 *
 * <p>Every test starts from the same reference data and creates books, copies, members, and
 * active loans the same way. Keeping those steps here removes copy-pasted fixtures from the
 * individual test classes while leaving assertions and test-specific SQL in place.
 */
public final class TestFixtures {

    private static final BookDAO BOOK_DAO = new JdbcBookDAO();
    private static final BookCopyDAO COPY_DAO = new JdbcBookCopyDAO();
    private static final MemberDAO MEMBER_DAO = new JdbcMemberDAO();
    private static final LoanDAO LOAN_DAO = new JdbcLoanDAO();

    private TestFixtures() {
    }

    /**
     * Clears all application tables and seeds the stable publisher/category/author rows.
     */
    public static void prepare(Connection c) throws Exception {
        TestDatabase.resetAll(c);
        TestDatabase.seedReferenceData(c);
    }

    /** Builds a book that references publisher 1 and category 1, published in 2020. */
    public static Book newBook(String isbn, String title) {
        return new Book(null, isbn, title, 1, 1, 2020);
    }

    /** Inserts a book with publisher 1, category 1, and publication year 2020. */
    public static Book insertBook(Connection c, String isbn, String title) {
        return insertBook(c, isbn, title, 2020);
    }

    /** Inserts a book with publisher 1, category 1, and the supplied publication year. */
    public static Book insertBook(Connection c, String isbn, String title, int publicationYear) {
        Book book = new Book(null, isbn, title, 1, 1, publicationYear);
        BOOK_DAO.insert(c, book);
        return book;
    }

    /** Inserts a physical copy of {@code book}. */
    public static BookCopy insertCopy(Connection c, Book book, String code, CopyStatus status) {
        BookCopy copy = new BookCopy(null, book.getBookId(), code, status);
        COPY_DAO.insert(c, copy);
        return copy;
    }

    /** Inserts an ACTIVE member. */
    public static Member insertMember(Connection c, String code, String name, String email) {
        return insertMember(c, code, name, email, MemberStatus.ACTIVE);
    }

    /** Inserts a member with the supplied status. */
    public static Member insertMember(Connection c, String code, String name, String email,
                                      MemberStatus status) {
        Member member = new Member(null, code, name, email, status);
        MEMBER_DAO.insert(c, member);
        return member;
    }

    /** Inserts an ACTIVE loan whose due date is exactly 14 calendar days after {@code loanDate}. */
    public static Loan insertActiveLoan(Connection c, BookCopy copy, Member member,
                                        LocalDate loanDate) {
        Loan loan = new Loan(null, copy.getCopyId(), member.getMemberId(),
                loanDate, loanDate.plusDays(AppConfig.LOAN_PERIOD_DAYS), null, LoanStatus.ACTIVE);
        LOAN_DAO.insert(c, loan);
        return loan;
    }
}
