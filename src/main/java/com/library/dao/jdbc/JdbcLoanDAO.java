package com.library.dao.jdbc;

import com.library.dao.LoanDAO;
import com.library.exception.DataAccessException;
import com.library.model.Loan;
import com.library.model.Page;
import com.library.model.criteria.LoanSearchCriteria;
import com.library.model.enums.LoanStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JDBC implementation of {@link LoanDAO}.
 *
 * <p>Handwritten SQL through JDBC; no ORM. History and active-loan list reads join
 * {@code book_copies}, {@code books}, and {@code members} to populate display fields.
 * Locking queries stay on the base {@code loans} table so PostgreSQL does not reject
 * {@code FOR UPDATE} on the nullable side of an outer join.
 */
public class JdbcLoanDAO implements LoanDAO {

    private static final String INSERT =
            "INSERT INTO loans (copy_id, member_id, loan_date, due_date, return_date, status) " +
            "VALUES (?, ?, ?, ?, ?, ?)";

    private static final String BASE_COLUMNS =
            "l.loan_id, l.copy_id, l.member_id, l.loan_date, l.due_date, " +
            "l.return_date, l.status, l.created_at";

    private static final String DISPLAY_COLUMNS =
            BASE_COLUMNS + ", b.title AS book_title, bc.copy_code, m.full_name AS member_name";

    private static final String DISPLAY_JOINS =
            " FROM loans l " +
            "JOIN book_copies bc ON bc.copy_id = l.copy_id " +
            "JOIN books b ON b.book_id = bc.book_id " +
            "JOIN members m ON m.member_id = l.member_id ";

    private static final String FIND_BY_ID_FOR_UPDATE =
            "SELECT " + BASE_COLUMNS + " FROM loans l WHERE l.loan_id = ? FOR UPDATE";

    private static final String FIND_ACTIVE_BY_COPY_ID =
            "SELECT " + BASE_COLUMNS + " FROM loans l " +
            "WHERE l.copy_id = ? AND l.return_date IS NULL FOR UPDATE";

    private static final String MARK_RETURNED =
            "UPDATE loans SET return_date = ?, status = 'RETURNED' WHERE loan_id = ? AND return_date IS NULL";

    private static final String COUNT_SEARCH_BASE =
            "SELECT COUNT(*) FROM loans l WHERE 1 = 1";

    private static final String SEARCH_BASE =
            "SELECT " + DISPLAY_COLUMNS + DISPLAY_JOINS + "WHERE 1 = 1";

    private static final String FIND_ACTIVE =
            "SELECT " + DISPLAY_COLUMNS + DISPLAY_JOINS +
            "WHERE l.return_date IS NULL ORDER BY l.loan_id";

    private static final String EXISTS_BY_BOOK_ID =
            "SELECT COUNT(*) FROM loans l " +
            "JOIN book_copies bc ON bc.copy_id = l.copy_id " +
            "WHERE bc.book_id = ?";

    private static final String COUNT_ACTIVE = "SELECT COUNT(*) FROM loans WHERE return_date IS NULL";

    private static final String COUNT_OVERDUE =
            "SELECT COUNT(*) FROM loans WHERE return_date IS NULL AND due_date < ?";

    @Override
    public int insert(Connection c, Loan loan) {
        try (PreparedStatement ps = c.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, loan.getCopyId());
            ps.setInt(2, loan.getMemberId());
            ps.setObject(3, loan.getLoanDate());
            ps.setObject(4, loan.getDueDate());
            if (loan.getReturnDate() != null) {
                ps.setObject(5, loan.getReturnDate());
            } else {
                ps.setNull(5, java.sql.Types.DATE);
            }
            ps.setString(6, loan.getStatus() != null ? loan.getStatus().name() : LoanStatus.ACTIVE.name());
            int affected = ps.executeUpdate();
            if (affected == 0) {
                throw new DataAccessException("Insert loan failed: no rows affected");
            }
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new DataAccessException("Insert loan failed: no generated key");
                }
                loan.setLoanId(keys.getInt(1));
            }
            return affected;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to insert loan for copyId=" + loan.getCopyId(), e);
        }
    }

    @Override
    public Optional<Loan> findByIdForUpdate(Connection c, int loanId) {
        try (PreparedStatement ps = c.prepareStatement(FIND_BY_ID_FOR_UPDATE)) {
            ps.setInt(1, loanId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapLoan(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to lock loan id: " + loanId, e);
        }
    }

    @Override
    public Optional<Loan> findActiveByCopyId(Connection c, int copyId) {
        try (PreparedStatement ps = c.prepareStatement(FIND_ACTIVE_BY_COPY_ID)) {
            ps.setInt(1, copyId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapLoan(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find active loan by copy id: " + copyId, e);
        }
    }

    @Override
    public int markReturned(Connection c, int loanId, LocalDate returnDate) {
        try (PreparedStatement ps = c.prepareStatement(MARK_RETURNED)) {
            ps.setObject(1, returnDate);
            ps.setInt(2, loanId);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to mark loan returned id=" + loanId, e);
        }
    }

    @Override
    public Page<Loan> search(Connection c, LoanSearchCriteria criteria) {
        StringBuilder countSb = new StringBuilder(COUNT_SEARCH_BASE);
        StringBuilder dataSb = new StringBuilder(SEARCH_BASE);
        List<Object> params = new ArrayList<>();

        appendFilters(countSb, dataSb, criteria, params);
        int filterParamCount = params.size();

        dataSb.append(" ORDER BY l.loan_date DESC, l.loan_id");
        dataSb.append(" LIMIT ? OFFSET ?");
        params.add(criteria.getSize());
        params.add((long) criteria.getPage() * criteria.getSize());

        try (PreparedStatement countPs = c.prepareStatement(countSb.toString())) {
            fillParams(countPs, params.subList(0, filterParamCount));
            try (ResultSet rs = countPs.executeQuery()) {
                long total = rs.next() ? rs.getLong(1) : 0;
                List<Loan> content = new ArrayList<>();
                if (total > 0) {
                    try (PreparedStatement dataPs = c.prepareStatement(dataSb.toString())) {
                        fillParams(dataPs, params);
                        try (ResultSet drs = dataPs.executeQuery()) {
                            while (drs.next()) {
                                content.add(mapLoanWithDisplay(drs));
                            }
                        }
                    }
                }
                return new Page<>(content, total, criteria.getPage(), criteria.getSize());
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to search loans", e);
        }
    }

    @Override
    public List<Loan> findActive(Connection c) {
        try (PreparedStatement ps = c.prepareStatement(FIND_ACTIVE);
             ResultSet rs = ps.executeQuery()) {
            List<Loan> list = new ArrayList<>();
            while (rs.next()) {
                list.add(mapLoanWithDisplay(rs));
            }
            return list;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find active loans", e);
        }
    }

    @Override
    public boolean existsByBookId(Connection c, int bookId) {
        try (PreparedStatement ps = c.prepareStatement(EXISTS_BY_BOOK_ID)) {
            ps.setInt(1, bookId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getLong(1) > 0;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to check loan history by book id: " + bookId, e);
        }
    }

    @Override
    public long countActive(Connection c) {
        try (PreparedStatement ps = c.prepareStatement(COUNT_ACTIVE);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to count active loans", e);
        }
    }

    @Override
    public long countOverdue(Connection c, LocalDate today) {
        try (PreparedStatement ps = c.prepareStatement(COUNT_OVERDUE)) {
            ps.setObject(1, today);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to count overdue loans as of " + today, e);
        }
    }

    private void appendFilters(StringBuilder countSb, StringBuilder dataSb,
                               LoanSearchCriteria criteria, List<Object> params) {
        if (criteria.getMemberId() != null) {
            countSb.append(" AND l.member_id = ?");
            dataSb.append(" AND l.member_id = ?");
            params.add(criteria.getMemberId());
        }
        if (criteria.getCopyId() != null) {
            countSb.append(" AND l.copy_id = ?");
            dataSb.append(" AND l.copy_id = ?");
            params.add(criteria.getCopyId());
        }
        if (criteria.getBookId() != null) {
            String clause = " AND EXISTS (SELECT 1 FROM book_copies bc " +
                    "WHERE bc.copy_id = l.copy_id AND bc.book_id = ?)";
            countSb.append(clause);
            dataSb.append(clause);
            params.add(criteria.getBookId());
        }
        if (criteria.getStatus() != null) {
            countSb.append(" AND l.status = ?");
            dataSb.append(" AND l.status = ?");
            params.add(criteria.getStatus().name());
        }
        if (criteria.getFromDate() != null) {
            countSb.append(" AND l.loan_date >= ?");
            dataSb.append(" AND l.loan_date >= ?");
            params.add(criteria.getFromDate());
        }
        if (criteria.getToDate() != null) {
            countSb.append(" AND l.loan_date <= ?");
            dataSb.append(" AND l.loan_date <= ?");
            params.add(criteria.getToDate());
        }
        if (Boolean.TRUE.equals(criteria.getOverdueOnly())) {
            String clause = " AND l.return_date IS NULL AND l.due_date < ?";
            countSb.append(clause);
            dataSb.append(clause);
            LocalDate today = criteria.getToday() != null ? criteria.getToday() : LocalDate.now();
            params.add(today);
        }
    }

    private void fillParams(PreparedStatement ps, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            ps.setObject(i + 1, params.get(i));
        }
    }

    private Loan mapLoan(ResultSet rs) throws SQLException {
        Loan loan = new Loan();
        loan.setLoanId(rs.getInt("loan_id"));
        loan.setCopyId(rs.getInt("copy_id"));
        loan.setMemberId(rs.getInt("member_id"));
        loan.setLoanDate(rs.getObject("loan_date", LocalDate.class));
        loan.setDueDate(rs.getObject("due_date", LocalDate.class));
        loan.setReturnDate(rs.getObject("return_date", LocalDate.class));
        String status = rs.getString("status");
        loan.setStatus(status != null ? LoanStatus.valueOf(status) : null);
        loan.setCreatedAt(rs.getObject("created_at", LocalDateTime.class));
        return loan;
    }

    private Loan mapLoanWithDisplay(ResultSet rs) throws SQLException {
        Loan loan = mapLoan(rs);
        loan.setBookTitle(rs.getString("book_title"));
        loan.setCopyCode(rs.getString("copy_code"));
        loan.setMemberName(rs.getString("member_name"));
        return loan;
    }
}
