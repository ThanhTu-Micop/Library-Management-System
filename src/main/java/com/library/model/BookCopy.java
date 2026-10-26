package com.library.model;

import com.library.model.enums.CopyStatus;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * A physical copy of a book.
 *
 * <p>The optional {@code bookTitle} is a display projection populated by DAO read queries
 * that join the {@code books} table.
 */
public class BookCopy {

    private Integer copyId;
    private Integer bookId;
    private String copyCode;
    private CopyStatus status;
    private LocalDateTime createdAt;
    private String bookTitle;

    public BookCopy() {
    }

    public BookCopy(Integer copyId, Integer bookId, String copyCode, CopyStatus status) {
        this.copyId = copyId;
        this.bookId = bookId;
        this.copyCode = copyCode;
        this.status = status;
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

    public String getCopyCode() {
        return copyCode;
    }

    public void setCopyCode(String copyCode) {
        this.copyCode = copyCode;
    }

    public CopyStatus getStatus() {
        return status;
    }

    public void setStatus(CopyStatus status) {
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BookCopy)) return false;
        BookCopy bookCopy = (BookCopy) o;
        return Objects.equals(copyId, bookCopy.copyId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(copyId);
    }

    @Override
    public String toString() {
        return "BookCopy{copyId=" + copyId + ", copyCode='" + copyCode + "', status=" + status + "}";
    }
}
