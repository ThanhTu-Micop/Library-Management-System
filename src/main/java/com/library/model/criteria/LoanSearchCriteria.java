package com.library.model.criteria;

import com.library.model.enums.LoanStatus;
import java.time.LocalDate;

/**
 * Search filters for loan history and overdue screens.
 *
 * <p>The DAO applies every non-null criterion. {@code today} is supplied by the caller so
 * date-sensitive queries stay deterministic and testable; it defaults to the current date.
 */
public class LoanSearchCriteria extends PageCriteria {

    private Integer memberId;
    private Integer copyId;
    private Integer bookId;
    private LoanStatus status;
    private LocalDate fromDate;
    private LocalDate toDate;
    private Boolean overdueOnly; // true: only ACTIVE loans whose due date is before today
    private LocalDate today = LocalDate.now();

    public LoanSearchCriteria() {
    }

    public Integer getMemberId() {
        return memberId;
    }

    public void setMemberId(Integer memberId) {
        this.memberId = memberId;
    }

    public Integer getCopyId() {
        return copyId;
    }

    public void setCopyId(Integer copyId) {
        this.copyId = copyId;
    }

    public Integer getBookId() {
        return bookId;
    }

    public void setBookId(Integer bookId) {
        this.bookId = bookId;
    }

    public LoanStatus getStatus() {
        return status;
    }

    public void setStatus(LoanStatus status) {
        this.status = status;
    }

    public LocalDate getFromDate() {
        return fromDate;
    }

    public void setFromDate(LocalDate fromDate) {
        this.fromDate = fromDate;
    }

    public LocalDate getToDate() {
        return toDate;
    }

    public void setToDate(LocalDate toDate) {
        this.toDate = toDate;
    }

    public Boolean getOverdueOnly() {
        return overdueOnly;
    }

    public void setOverdueOnly(Boolean overdueOnly) {
        this.overdueOnly = overdueOnly;
    }

    public LocalDate getToday() {
        return today;
    }

    public void setToday(LocalDate today) {
        this.today = today != null ? today : LocalDate.now();
    }

    @Override
    public String toString() {
        return "LoanSearchCriteria{memberId=" + memberId + ", copyId=" + copyId + ", bookId=" + bookId + ", status=" + status
                + ", fromDate=" + fromDate + ", toDate=" + toDate + ", overdueOnly=" + overdueOnly
                + ", page=" + getPage() + ", size=" + getSize() + ", today=" + today + "}";
    }
}
