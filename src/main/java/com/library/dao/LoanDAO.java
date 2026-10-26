package com.library.dao;

import com.library.model.Loan;
import com.library.model.Page;
import com.library.model.criteria.LoanSearchCriteria;

import java.sql.Connection;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * DAO contract for {@link Loan}.
 *
 * <p>All methods receive the caller's {@link Connection}; DAOs never commit, roll back, or
 * close it. Transaction boundaries belong to the service layer.
 *
 * <p>{@code existsByBookId} checks <strong>any</strong> loan history, including returned
 * loans. {@code search} and {@code findActive} populate the display projections
 * {@code bookTitle}, {@code copyCode}, and {@code memberName}. {@code search} uses
 * {@code criteria.getToday()} for the {@code overdueOnly} predicate.
 */
public interface LoanDAO {

    /**
     * Inserts a loan and sets its generated ID on {@code loan}.
     *
     * @return affected rows
     */
    int insert(Connection c, Loan loan);

    Optional<Loan> findByIdForUpdate(Connection c, int loanId);

    Optional<Loan> findActiveByCopyId(Connection c, int copyId);

    /**
     * Marks an active loan as returned.
     *
     * @return affected rows
     */
    int markReturned(Connection c, int loanId, LocalDate returnDate);

    Page<Loan> search(Connection c, LoanSearchCriteria criteria);

    List<Loan> findActive(Connection c);

    boolean existsByBookId(Connection c, int bookId);

    long countActive(Connection c);

    long countOverdue(Connection c, LocalDate today);
}
