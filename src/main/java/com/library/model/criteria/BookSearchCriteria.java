package com.library.model.criteria;

/**
 * Search filters for the book list.
 *
 * <p>The DAO applies every non-null criterion. {@code page} is zero-based.
 */
public class BookSearchCriteria extends PageCriteria {

    private String keyword; // matches title or ISBN
    private Integer publisherId;
    private Integer categoryId;
    private String authorName;
    private Boolean availableOnly; // true: only books with at least one AVAILABLE copy

    public BookSearchCriteria() {
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
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

    public String getAuthorName() {
        return authorName;
    }

    public void setAuthorName(String authorName) {
        this.authorName = authorName;
    }

    public Boolean getAvailableOnly() {
        return availableOnly;
    }

    public void setAvailableOnly(Boolean availableOnly) {
        this.availableOnly = availableOnly;
    }

    @Override
    public String toString() {
        return "BookSearchCriteria{keyword='" + keyword + "', publisherId=" + publisherId + ", categoryId=" + categoryId
                + ", authorName='" + authorName + "', availableOnly=" + availableOnly
                + ", page=" + getPage() + ", size=" + getSize() + "}";
    }
}
