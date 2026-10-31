package com.library.model;

import com.library.model.enums.LoanStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * A loan record.
 *
 * <p>The due date is exactly 14 calendar days after the loan date, enforced by a database
 * check constraint. The {@code overdue} and {@code daysOverdue} fields are derived display
 * values: they have no database column, DAO reads leave them unset, and the service layer
 * must compute them before rendering. They are unrelated to the {@code loans.status} value.
 *
 * <p>The display projections {@code bookTitle}, {@code copyCode}, and {@code memberName}
 * are populated by DAO read queries that join the related tables.
 */
public class Loan {

    private Integer loanId;
    private Integer copyId;
    private Integer memberId;
    private LocalDate loanDate;
    private LocalDate dueDate;
    private LocalDate returnDate;
    private LoanStatus status;
    private LocalDateTime createdAt;

    private String bookTitle;
    private String copyCode;
    private String memberName;
    private boolean overdue;
    private long daysOverdue;

    public Loan() {
    }

    public Loan(Integer loanId, Integer copyId, Integer memberId, LocalDate loanDate, LocalDate dueDate, LocalDate returnDate, LoanStatus status) {
        this.loanId = loanId;
        this.copyId = copyId;
        this.memberId = memberId;
        this.loanDate = loanDate;
        this.dueDate = dueDate;
        this.returnDate = returnDate;
        this.status = status;
    }

    public Integer getLoanId() {
        return loanId;
    }

    public void setLoanId(Integer loanId) {
        this.loanId = loanId;
    }

    public Integer getCopyId() {
        return copyId;
    }

    public void setCopyId(Integer copyId) {
        this.copyId = copyId;
    }

    public Integer getMemberId() {
        return memberId;
    }

    public void setMemberId(Integer memberId) {
        this.memberId = memberId;
    }

    public LocalDate getLoanDate() {
        return loanDate;
    }

    public void setLoanDate(LocalDate loanDate) {
        this.loanDate = loanDate;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public LocalDate getReturnDate() {
        return returnDate;
    }

    public void setReturnDate(LocalDate returnDate) {
        this.returnDate = returnDate;
    }

    public LoanStatus getStatus() {
        return status;
    }

    public void setStatus(LoanStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getBookTitle() {
        return bookTitle;
    }

    public void setBookTitle(String bookTitle) {
        this.bookTitle = bookTitle;
    }

    public String getCopyCode() {
        return copyCode;
    }

    public void setCopyCode(String copyCode) {
        this.copyCode = copyCode;
    }

    public String getMemberName() {
        return memberName;
    }

    public void setMemberName(String memberName) {
        this.memberName = memberName;
    }

    /** Returns the derived overdue flag; this getter does not compute it. */
    public boolean isOverdue() {
        return overdue;
    }

    public void setOverdue(boolean overdue) {
        this.overdue = overdue;
    }

    /** Returns the derived overdue-day count; zero when not overdue or not yet computed. */
    public long getDaysOverdue() {
        return daysOverdue;
    }

    public void setDaysOverdue(long daysOverdue) {
        this.daysOverdue = daysOverdue;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Loan)) return false;
        Loan loan = (Loan) o;
        return Objects.equals(loanId, loan.loanId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(loanId);
    }

    @Override
    public String toString() {
        return "Loan{loanId=" + loanId + ", copyId=" + copyId + ", memberId=" + memberId + ", dueDate=" + dueDate + "}";
    }
}
