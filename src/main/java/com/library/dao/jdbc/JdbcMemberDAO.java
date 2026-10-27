package com.library.dao.jdbc;

import com.library.dao.MemberDAO;
import com.library.exception.DataAccessException;
import com.library.model.Member;
import com.library.model.Page;
import com.library.model.criteria.MemberSearchCriteria;
import com.library.model.enums.MemberStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JDBC implementation of {@link MemberDAO}.
 *
 * <p>Handwritten SQL through JDBC; no ORM. Email uniqueness is case-insensitive, blank
 * emails are normalized to {@code NULL}, and list reads include the active-loan count.
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
            "SELECT COUNT(*) FROM members WHERE member_code = ? AND (? IS NULL OR member_id <> ?)";

    private static final String EMAIL_EXISTS =
            "SELECT COUNT(*) FROM members " +
            "WHERE LOWER(email) = LOWER(?) AND (? IS NULL OR member_id <> ?)";

    @Override
    public Optional<Member> findById(Connection c, int memberId) {
        try (PreparedStatement ps = c.prepareStatement(FIND_BY_ID)) {
            ps.setInt(1, memberId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapMember(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find member by id: " + memberId, e);
        }
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

        try (PreparedStatement countPs = c.prepareStatement(countSb.toString())) {
            fillParams(countPs, params.subList(0, filterParamCount));
            try (ResultSet rs = countPs.executeQuery()) {
                long total = rs.next() ? rs.getLong(1) : 0;
                List<Member> content = new ArrayList<>();
                if (total > 0) {
                    try (PreparedStatement dataPs = c.prepareStatement(dataSb.toString())) {
                        fillParams(dataPs, params);
                        try (ResultSet drs = dataPs.executeQuery()) {
                            while (drs.next()) {
                                content.add(mapMember(drs));
                            }
                        }
                    }
                }
                return new Page<>(content, total, criteria.getPage(), criteria.getSize());
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to search members", e);
        }
    }

    @Override
    public List<Member> findAllActive(Connection c) {
        try (PreparedStatement ps = c.prepareStatement(FIND_ALL_ACTIVE);
             ResultSet rs = ps.executeQuery()) {
            List<Member> list = new ArrayList<>();
            while (rs.next()) {
                list.add(mapMember(rs));
            }
            return list;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find all active members", e);
        }
    }

    @Override
    public int insert(Connection c, Member member) {
        try (PreparedStatement ps = c.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, member.getMemberCode());
            ps.setString(2, member.getFullName());
            String email = normalizeEmail(member.getEmail());
            if (email != null) {
                ps.setString(3, email);
            } else {
                ps.setNull(3, Types.VARCHAR);
            }
            ps.setString(4, member.getStatus() != null ? member.getStatus().name() : MemberStatus.ACTIVE.name());
            int affected = ps.executeUpdate();
            if (affected == 0) {
                throw new DataAccessException("Insert member failed: no rows affected");
            }
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new DataAccessException("Insert member failed: no generated key");
                }
                member.setMemberId(keys.getInt(1));
            }
            return affected;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to insert member: " + member.getMemberCode(), e);
        }
    }

    @Override
    public void update(Connection c, Member member) {
        try (PreparedStatement ps = c.prepareStatement(UPDATE)) {
            ps.setString(1, member.getMemberCode());
            ps.setString(2, member.getFullName());
            String email = normalizeEmail(member.getEmail());
            if (email != null) {
                ps.setString(3, email);
            } else {
                ps.setNull(3, Types.VARCHAR);
            }
            ps.setString(4, member.getStatus() != null ? member.getStatus().name() : MemberStatus.ACTIVE.name());
            ps.setInt(5, member.getMemberId());
            int affected = ps.executeUpdate();
            if (affected == 0) {
                throw new DataAccessException("Update member failed: no rows affected, id=" + member.getMemberId());
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to update member id=" + member.getMemberId(), e);
        }
    }

    @Override
    public boolean memberCodeExists(Connection c, String code, Integer excludeMemberId) {
        return exists(c, MEMBER_CODE_EXISTS, code, excludeMemberId);
    }

    @Override
    public boolean emailExists(Connection c, String email, Integer excludeMemberId) {
        String normalized = normalizeEmail(email);
        if (normalized == null) {
            return false;
        }
        return exists(c, EMAIL_EXISTS, normalized, excludeMemberId);
    }

    private boolean exists(Connection c, String sql, String value, Integer excludeId) {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, value);
            if (excludeId == null) {
                ps.setNull(2, Types.INTEGER);
                ps.setNull(3, Types.INTEGER);
            } else {
                ps.setInt(2, excludeId);
                ps.setInt(3, excludeId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getLong(1) > 0;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to check existence: " + value, e);
        }
    }

    private void appendFilters(StringBuilder countSb, StringBuilder dataSb,
                               MemberSearchCriteria criteria, List<Object> params) {
        if (criteria.getKeyword() != null && !criteria.getKeyword().isBlank()) {
            countSb.append(" AND (unaccent(m.full_name) ILIKE unaccent(?) OR m.member_code ILIKE ?)");
            dataSb.append(" AND (unaccent(m.full_name) ILIKE unaccent(?) OR m.member_code ILIKE ?)");
            String like = likePattern(criteria.getKeyword());
            params.add(like);
            params.add(like);
        }
        if (criteria.getStatus() != null) {
            countSb.append(" AND m.status = ?");
            dataSb.append(" AND m.status = ?");
            params.add(criteria.getStatus().name());
        }
    }

    private String likePattern(String value) {
        if (value == null) {
            return null;
        }
        String escaped = value.trim()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }
    private void fillParams(PreparedStatement ps, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            ps.setObject(i + 1, params.get(i));
        }
    }

    private String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }
        String trimmed = email.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Member mapMember(ResultSet rs) throws SQLException {
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
