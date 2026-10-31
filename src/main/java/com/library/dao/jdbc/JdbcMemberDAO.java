package com.library.dao.jdbc;

import com.library.dao.MemberDAO;
import com.library.model.Member;
import com.library.model.Page;
import com.library.model.criteria.MemberSearchCriteria;
import com.library.model.enums.MemberStatus;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JDBC implementation of {@link MemberDAO}.
 *
 * <p>Handwritten SQL through JDBC; no ORM. Email uniqueness is case-insensitive, blank
 * emails are normalized to {@code NULL}, and list reads include the active-loan count.
 * Shared statement mechanics live in {@link JdbcSupport}.
 */
public class JdbcMemberDAO implements MemberDAO {

    private static final String SELECT_COLUMNS =
            "SELECT m.member_id, m.member_code, m.full_name, m.email, m.status, m.created_at, " +
            "(SELECT COUNT(*) FROM loans l " +
            " WHERE l.member_id = m.member_id AND l.return_date IS NULL) AS active_loan_count " +
            "FROM members m ";

    private static final String FIND_BY_ID = SELECT_COLUMNS + "WHERE m.member_id = ?";

    private static final String COUNT_SEARCH_BASE =
            "SELECT COUNT(*) FROM members m WHERE 1 = 1";

    private static final String SEARCH_BASE = SELECT_COLUMNS + "WHERE 1 = 1";

    private static final String FIND_ALL_ACTIVE =
            SELECT_COLUMNS + "WHERE m.status = 'ACTIVE' ORDER BY m.member_id";

    private static final String INSERT =
            "INSERT INTO members (member_code, full_name, email, status) VALUES (?, ?, ?, ?)";

    private static final String UPDATE =
            "UPDATE members SET member_code = ?, full_name = ?, email = ?, status = ? WHERE member_id = ?";

    private static final String MEMBER_CODE_EXISTS =
            "SELECT COUNT(*) FROM members WHERE member_code = ? AND member_id <> ?";

    private static final String EMAIL_EXISTS =
            "SELECT COUNT(*) FROM members WHERE LOWER(email) = LOWER(?) AND member_id <> ?";

    @Override
    public Optional<Member> findById(Connection c, int memberId) {
        return JdbcSupport.queryOne(c, FIND_BY_ID,
                "Failed to find member by id: " + memberId, JdbcMemberDAO::mapMember, memberId);
    }

    @Override
    public Page<Member> search(Connection c, MemberSearchCriteria criteria) {
        StringBuilder countSb = new StringBuilder(COUNT_SEARCH_BASE);
        StringBuilder dataSb = new StringBuilder(SEARCH_BASE);
        List<Object> params = new ArrayList<>();

        appendFilters(countSb, dataSb, criteria, params);
        int filterParamCount = params.size();

        dataSb.append(" ORDER BY m.member_id");
        dataSb.append(" LIMIT ? OFFSET ?");
        params.add(criteria.getSize());
        params.add((long) criteria.getPage() * criteria.getSize());

        return JdbcSupport.search(c, countSb.toString(), dataSb.toString(), params,
                filterParamCount, criteria.getPage(), criteria.getSize(),
                "Failed to search members", JdbcMemberDAO::mapMember);
    }

    @Override
    public List<Member> findAllActive(Connection c) {
        return JdbcSupport.queryList(c, FIND_ALL_ACTIVE,
                "Failed to find all active members", JdbcMemberDAO::mapMember);
    }

    @Override
    public int insert(Connection c, Member member) {
        return JdbcSupport.insert(c, INSERT,
                "Failed to insert member: " + member.getMemberCode(),
                member::setMemberId,
                member.getMemberCode(), member.getFullName(), normalizeEmail(member.getEmail()),
                statusName(member.getStatus()));
    }

    @Override
    public void update(Connection c, Member member) {
        JdbcSupport.updateExpectingRow(c, UPDATE,
                "Failed to update member id=" + member.getMemberId(),
                member.getMemberCode(), member.getFullName(), normalizeEmail(member.getEmail()),
                statusName(member.getStatus()), member.getMemberId());
    }

    @Override
    public boolean memberCodeExists(Connection c, String code, Integer excludeMemberId) {
        int excluded = excludeMemberId != null ? excludeMemberId : -1;
        return JdbcSupport.exists(c, MEMBER_CODE_EXISTS,
                "Failed to check member code exists: " + code, code, excluded);
    }

    @Override
    public boolean emailExists(Connection c, String email, Integer excludeMemberId) {
        String normalized = normalizeEmail(email);
        if (normalized == null) {
            return false;
        }
        int excluded = excludeMemberId != null ? excludeMemberId : -1;
        return JdbcSupport.exists(c, EMAIL_EXISTS,
                "Failed to check email exists: " + normalized, normalized, excluded);
    }

    private void appendFilters(StringBuilder countSb, StringBuilder dataSb,
                               MemberSearchCriteria criteria, List<Object> params) {
        if (criteria.getKeyword() != null && !criteria.getKeyword().isBlank()) {
            countSb.append(" AND (unaccent(m.full_name) ILIKE unaccent(?) OR m.member_code ILIKE ?)");
            dataSb.append(" AND (unaccent(m.full_name) ILIKE unaccent(?) OR m.member_code ILIKE ?)");
            String like = JdbcSupport.likePattern(criteria.getKeyword());
            params.add(like);
            params.add(like);
        }
        if (criteria.getStatus() != null) {
            countSb.append(" AND m.status = ?");
            dataSb.append(" AND m.status = ?");
            params.add(criteria.getStatus().name());
        }
    }

    private static String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }
        String trimmed = email.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String statusName(MemberStatus status) {
        return status != null ? status.name() : MemberStatus.ACTIVE.name();
    }

    private static Member mapMember(ResultSet rs) throws SQLException {
        Member member = new Member();
        member.setMemberId(rs.getInt("member_id"));
        member.setMemberCode(rs.getString("member_code"));
        member.setFullName(rs.getString("full_name"));
        member.setEmail(rs.getString("email"));
        String status = rs.getString("status");
        member.setStatus(status != null ? MemberStatus.valueOf(status) : null);
        member.setCreatedAt(rs.getObject("created_at", LocalDateTime.class));
        member.setActiveLoanCount(rs.getLong("active_loan_count"));
        return member;
    }
}
