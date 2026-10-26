package com.library.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A book title in the library catalog.
 *
 * <p>Display-only projections are populated by DAO reads: {@code publisherName},
 * {@code categoryName}, and {@code availableCopies} come from {@code JdbcBookDAO}, while
 * {@code authors} is loaded separately through {@code BookAuthorDAO.findByBookId}. Write
 * operations use only the persisted columns and ignore these projections.
 */
public class Book {

    private Integer bookId;
    private String isbn;
    private String title;
    private Integer publisherId;
    private Integer categoryId;
    private Integer publicationYear;
    private LocalDateTime createdAt;

    private String publisherName;
    private String categoryName;
    private List<Author> authors = new ArrayList<>();
    private Integer availableCopies;

    public Book() {
    }

    public Book(Integer bookId, String isbn, String title, Integer publisherId, Integer categoryId, Integer publicationYear) {
        this.bookId = bookId;
        this.isbn = isbn;
        this.title = title;
        this.publisherId = publisherId;
        this.categoryId = categoryId;
        this.publicationYear = publicationYear;
    }

    public Integer getBookId() {
        return bookId;
    }

    public void setBookId(Integer bookId) {
        this.bookId = bookId;
    }

    public String getIsbn() {
        return isbn;
    }

    public void setIsbn(String isbn) {
        this.isbn = isbn;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Integer getPublisherId() {
        return publisherId;
    }

    public void setPublisherId(Integer publisherId) {
        this.publisherId = publisherId;
    }

    public Integer getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Integer categoryId) {
        this.categoryId = categoryId;
    }

    public Integer getPublicationYear() {
        return publicationYear;
    }

    public void setPublicationYear(Integer publicationYear) {
        this.publicationYear = publicationYear;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getPublisherName() {
        return publisherName;
    }

    public void setPublisherName(String publisherName) {
        this.publisherName = publisherName;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public List<Author> getAuthors() {
        return authors;
    }

    public void setAuthors(List<Author> authors) {
        this.authors = authors != null ? authors : new ArrayList<>();
    }

    public Integer getAvailableCopies() {
        return availableCopies;
    }

    public void setAvailableCopies(Integer availableCopies) {
        this.availableCopies = availableCopies;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Book)) return false;
        Book book = (Book) o;
        return Objects.equals(bookId, book.bookId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(bookId);
    }

    @Override
    public String toString() {
        return "Book{bookId=" + bookId + ", isbn='" + isbn + "', title='" + title + "'}";
    }
}
