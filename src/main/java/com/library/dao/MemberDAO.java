package com.library.dao;

import com.library.model.Member;
import com.library.model.Page;
import com.library.model.criteria.MemberSearchCriteria;

import java.sql.Connection;
import java.util.List;
import java.util.Optional;

/**
 * DAO contract for {@link Member}.
 *
 * <p>All methods receive the caller's {@link Connection}; DAOs never commit, roll back, or
 * close it. Transaction boundaries belong to the service layer.
 *
 * <p>{@code search} and {@code findAllActive} populate {@code activeLoanCount}.
 */
public interface MemberDAO {

    Optional<Member> findById(Connection c, int memberId);

    Page<Member> search(Connection c, MemberSearchCriteria criteria);

    List<Member> findAllActive(Connection c);

    /**
     * Inserts a member and sets its generated ID on {@code member}.
     *
     * @return affected rows
     */
    int insert(Connection c, Member member);

    void update(Connection c, Member member);

    boolean memberCodeExists(Connection c, String code, Integer excludeMemberId);

    boolean emailExists(Connection c, String email, Integer excludeMemberId);
}
