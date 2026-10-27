package com.library.model.criteria;

import com.library.model.enums.MemberStatus;

/**
 * Search filters for the member list.
 *
 * <p>The DAO applies every non-null criterion. {@code page} is zero-based.
 */
public class MemberSearchCriteria {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_SIZE = 100;

    private String keyword; // matches full name or member code
    private MemberStatus status;
    private Integer page = DEFAULT_PAGE;
    private Integer size = DEFAULT_SIZE;

    public MemberSearchCriteria() {
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public MemberStatus getStatus() {
        return status;
    }

    public void setStatus(MemberStatus status) {
        this.status = status;
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
        return "MemberSearchCriteria{keyword='" + keyword + "', status=" + status + ", page=" + page + ", size=" + size + "}";
    }
}
