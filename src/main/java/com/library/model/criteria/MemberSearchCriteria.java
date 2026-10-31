package com.library.model.criteria;

import com.library.model.enums.MemberStatus;

/**
 * Search filters for the member list.
 *
 * <p>The DAO applies every non-null criterion. {@code page} is zero-based.
 */
public class MemberSearchCriteria extends PageCriteria {

    private String keyword; // matches full name or member code
    private MemberStatus status;

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

    @Override
    public String toString() {
        return "MemberSearchCriteria{keyword='" + keyword + "', status=" + status
                + ", page=" + getPage() + ", size=" + getSize() + "}";
    }
}
