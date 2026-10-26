package com.library.model.criteria;

import com.library.model.enums.MemberStatus;

/**
 * Search filters for the member list.
 *
 * <p>The DAO applies every non-null criterion. {@code page} is zero-based.
 */
public class MemberSearchCriteria {

    private String keyword; // matches full name or member code
    private MemberStatus status;
    private Integer page = 0;
    private Integer size = 10;

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
        return page;
    }

    public void setPage(Integer page) {
        this.page = page;
    }

    public Integer getSize() {
        return size;
    }

    public void setSize(Integer size) {
        this.size = size;
    }

    @Override
    public String toString() {
        return "MemberSearchCriteria{keyword='" + keyword + "', status=" + status + ", page=" + page + ", size=" + size + "}";
    }
}
