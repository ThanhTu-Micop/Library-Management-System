package com.library.model.criteria;

/**
 * Search filters for the book list.
 *
 * <p>The DAO applies every non-null criterion. {@code page} is zero-based.
 */
public class BookSearchCriteria {

    private String keyword; // matches title or ISBN
    private Integer publisherId;
    private Integer categoryId;
    private String authorName;
    private Boolean availableOnly; // true: only books with at least one AVAILABLE copy
    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_SIZE = 100;

    private Integer page = DEFAULT_PAGE;
    private Integer size = DEFAULT_SIZE;

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

    public Integer getPage() {
        return page != null ? Math.max(0, page) : DEFAULT_PAGE;
    }

    public void setPage(Integer page) {
        this.page = page != null ? Math.max(0, page) : DEFAULT_PAGE;
    }

    public Integer getSize() {
        return size != null ? Math.min(Math.max(1, size), MAX_SIZE) : DEFAULT_SIZE;
    }

    public void setSize(Integer size) {
        this.size = size != null ? Math.min(Math.max(1, size), MAX_SIZE) : DEFAULT_SIZE;
    }

    @Override
    public String toString() {
        return "BookSearchCriteria{keyword='" + keyword + "', publisherId=" + publisherId + ", categoryId=" + categoryId
                + ", authorName='" + authorName + "', availableOnly=" + availableOnly + ", page=" + page + ", size=" + size + "}";
    }
}
