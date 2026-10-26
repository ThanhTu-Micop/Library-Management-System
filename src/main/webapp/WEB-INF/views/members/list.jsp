<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>

<c:import url="/WEB-INF/views/common/head.jsp">
    <c:param name="pageTitle" value="Members" />
    <c:param name="currentPage" value="members" />
</c:import>

<h1 class="page-title">Manage members</h1>
<p class="page-subtitle mt-2">Search, add, and edit library members.</p>

<div class="toolbar mb-2">
    <a href="<c:url value='/members/new' />" class="btn btn-primary">+ Add member</a>
</div>

<div class="table-wrap">
    <form method="get" action="<c:url value='/members' />" style="margin:0;">
        <div style="display:flex; gap:0.5rem; flex-wrap:wrap; margin-bottom:0.75rem;">
            <input type="text" name="keyword" class="form-control" placeholder="Search by code or full name"
                   value="<c:out value='${criteria.keyword}' default='' />" style="min-width:260px;">
            <select name="status" class="form-select" style="min-width:160px;">
                <option value="">All statuses</option>
                <option value="ACTIVE" <c:if test="${criteria.status == 'ACTIVE'}">selected</c:if>>Active</option>
                <option value="INACTIVE" <c:if test="${criteria.status == 'INACTIVE'}">selected</c:if>>Inactive</option>
            </select>
            <button type="submit" class="btn btn-primary">Search</button>
            <a href="<c:url value='/members' />" class="btn btn-secondary">Clear filters</a>
        </div>
    </form>

    <table>
        <thead>
            <tr>
                <th>Member code</th>
                <th>Full name</th>
                <th>Email</th>
                <th>Status</th>
                <th>Active loans</th>
                <th>Actions</th>
            </tr>
        </thead>
        <tbody>
            <c:if test="${empty memberPage.content}">
                <tr>
                    <td colspan="6" class="empty-state">No matching members were found.</td>
                </tr>
            </c:if>
            <c:forEach var="m" items="${memberPage.content}">
                <tr>
                    <td><c:out value="${m.memberCode}" /></td>
                    <td><c:out value="${m.fullName}" /></td>
                    <td><c:out value="${m.email}" default="—" /></td>
                    <td><span class="status-badge status-${fn:toLowerCase(m.status)}"><c:out value="${m.status}" /></span></td>
                    <td><c:out value="${m.activeLoanCount}" /></td>
                    <td class="actions-cell">
                        <a href="<c:url value='/members/edit' />?id=<c:out value='${m.memberId}' />" class="btn btn-sm btn-secondary">Edit</a>
                        <c:url var="historyUrl" value="/loans/history">
                            <c:param name="memberId" value="${m.memberId}" />
                        </c:url>
                        <a href="${historyUrl}" class="btn btn-sm btn-secondary">History</a>
                    </td>
                </tr>
            </c:forEach>
        </tbody>
    </table>
</div>

<c:if test="${memberPage.totalPages gt 1}">
    <div class="pagination mt-2">
        <c:if test="${not memberPage.first}">
            <c:url var="previousUrl" value="/members">
                <c:param name="page" value="${memberPage.page - 1}" />
                <c:param name="size" value="${memberPage.size}" />
                <c:if test="${not empty criteria.keyword}"><c:param name="keyword" value="${criteria.keyword}" /></c:if>
                <c:if test="${criteria.status != null}"><c:param name="status" value="${criteria.status}" /></c:if>
            </c:url>
            <a href="${previousUrl}" class="btn btn-sm btn-secondary">Previous</a>
        </c:if>
        <span class="current">Page <c:out value="${memberPage.page + 1}" /> / <c:out value="${memberPage.totalPages}" /></span>
        <c:if test="${not memberPage.last}">
            <c:url var="nextUrl" value="/members">
                <c:param name="page" value="${memberPage.page + 1}" />
                <c:param name="size" value="${memberPage.size}" />
                <c:if test="${not empty criteria.keyword}"><c:param name="keyword" value="${criteria.keyword}" /></c:if>
                <c:if test="${criteria.status != null}"><c:param name="status" value="${criteria.status}" /></c:if>
            </c:url>
            <a href="${nextUrl}" class="btn btn-sm btn-secondary">Next</a>
        </c:if>
    </div>
</c:if>

<c:import url="/WEB-INF/views/common/foot.jsp" />
