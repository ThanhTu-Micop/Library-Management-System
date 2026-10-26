package com.library.model;

import com.library.model.enums.MemberStatus;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * A library member.
 *
 * <p>{@code activeLoanCount} is a read-only display projection populated by member list
 * queries. It counts loans whose return date is still {@code null}.
 */
public class Member {

    private Integer memberId;
    private String memberCode;
    private String fullName;
    private String email;
    private MemberStatus status;
    private LocalDateTime createdAt;
    private long activeLoanCount;

    public Member() {
    }

    public Member(Integer memberId, String memberCode, String fullName, String email, MemberStatus status) {
        this.memberId = memberId;
        this.memberCode = memberCode;
        this.fullName = fullName;
        this.email = email;
        this.status = status;
    }

    public Integer getMemberId() {
        return memberId;
    }

    public void setMemberId(Integer memberId) {
        this.memberId = memberId;
    }

    public String getMemberCode() {
        return memberCode;
    }

    public void setMemberCode(String memberCode) {
        this.memberCode = memberCode;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public MemberStatus getStatus() {
        return status;
    }

    public void setStatus(MemberStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public long getActiveLoanCount() {
        return activeLoanCount;
    }

    public void setActiveLoanCount(long activeLoanCount) {
        this.activeLoanCount = activeLoanCount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Member)) return false;
        Member member = (Member) o;
        return Objects.equals(memberId, member.memberId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(memberId);
    }

    @Override
    public String toString() {
        return "Member{memberId=" + memberId + ", memberCode='" + memberCode + "', fullName='" + fullName + "'}";
    }
}
