<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>

<c:import url="/WEB-INF/views/common/head.jsp">
    <c:param name="pageTitle" value="Loan history" />
    <c:param name="currentPage" value="loans-history" />
</c:import>

<h1 class="page-title">Loan history</h1>
<p class="page-subtitle mt-2">View every loan, including returned and overdue items.</p>

<div class="toolbar mb-2">
    <a href="<c:url value='/loans/borrow' />" class="btn btn-primary">New loan</a>
</div>

<div class="table-wrap">
    <form accept-charset="UTF-8" method="get" action="<c:url value='/loans/history' />" style="margin:0;">
        <div style="display:flex; gap:0.5rem; flex-wrap:wrap; margin-bottom:0.75rem;">
            <select name="memberId" class="form-select" style="min-width:200px;">
                <option value="">All members</option>
                <c:forEach var="m" items="${activeMembers}">
                    <option value="${m.memberId}" <c:if test="${m.memberId == criteria.memberId}">selected</c:if>><c:out value="${m.memberCode}" /> &mdash; <c:out value="${m.fullName}" /></option>
                </c:forEach>
            </select>
            <select name="status" class="form-select" style="min-width:150px;">
                <option value="">All statuses</option>
                <option value="ACTIVE" <c:if test="${criteria.status == 'ACTIVE'}">selected</c:if>>Active</option>
                <option value="RETURNED" <c:if test="${criteria.status == 'RETURNED'}">selected</c:if>>Returned</option>
            </select>
            <input type="date" name="fromDate" class="form-control" style="min-width:160px;" value="<c:out value='${criteria.fromDate}' default='' />">
            <input type="date" name="toDate" class="form-control" style="min-width:160px;" value="<c:out value='${criteria.toDate}' default='' />">
            <button type="submit" class="btn btn-primary">Filter</button>
            <a href="<c:url value='/loans/history' />" class="btn btn-secondary">Clear filters</a>
        </div>
    </form>

    <table>
        <thead>
            <tr>
                <th>Loan ID</th>
                <th>Book</th>
                <th>Copy</th>
                <th>Member</th>
                <th>Loan date</th>
                <th>Due date</th>
                <th>Return date</th>
                <th>Status</th>
            </tr>
        </thead>
        <tbody>
            <c:if test="${empty loanPage.content}">
                <tr>
                    <td colspan="8" class="empty-state">No loans match the selected filters.</td>
                </tr>
            </c:if>
            <c:forEach var="l" items="${loanPage.content}">
                <tr>
                    <td><c:out value="${l.loanId}" /></td>
                    <td><c:out value="${l.bookTitle}" default="Unknown" /></td>
                    <td><c:out value="${l.copyCode}" default="Unknown" /></td>
                    <td><c:out value="${l.memberName}" default="Unknown" /></td>
                    <td><c:out value="${l.loanDate}" /></td>
                    <td><c:out value="${l.dueDate}" /></td>
                    <td><c:out value="${l.returnDate}" default="—" /></td>
                    <td>
                        <c:choose>
                            <c:when test="${not empty l.returnDate}">
                                <span class="status-badge status-returned">Returned</span>
                            </c:when>
                            <c:when test="${l.overdue}">
                                <span class="status-badge status-overdue">Overdue (<c:out value="${l.daysOverdue}" /> days)</span>
                            </c:when>
                            <c:otherwise>
                                <span class="status-badge status-active">Active</span>
                            </c:otherwise>
                        </c:choose>
                    </td>
                </tr>
            </c:forEach>
        </tbody>
    </table>
</div>

<c:if test="${loanPage.totalPages gt 1}">
    <div class="pagination mt-2">
        <c:if test="${not loanPage.first}">
            <c:url var="previousUrl" value="/loans/history">
                <c:param name="page" value="${loanPage.page - 1}" />
                <c:param name="size" value="${loanPage.size}" />
                <c:if test="${criteria.memberId != null}"><c:param name="memberId" value="${criteria.memberId}" /></c:if>
                <c:if test="${criteria.status != null}"><c:param name="status" value="${criteria.status}" /></c:if>
                <c:if test="${criteria.fromDate != null}"><c:param name="fromDate" value="${criteria.fromDate}" /></c:if>
                <c:if test="${criteria.toDate != null}"><c:param name="toDate" value="${criteria.toDate}" /></c:if>
            </c:url>
            <a href="${previousUrl}" class="btn btn-sm btn-secondary">Previous</a>
        </c:if>
        <span class="current">Page <c:out value="${loanPage.page + 1}" /> / <c:out value="${loanPage.totalPages}" /></span>
        <c:if test="${not loanPage.last}">
            <c:url var="nextUrl" value="/loans/history">
                <c:param name="page" value="${loanPage.page + 1}" />
                <c:param name="size" value="${loanPage.size}" />
                <c:if test="${criteria.memberId != null}"><c:param name="memberId" value="${criteria.memberId}" /></c:if>
                <c:if test="${criteria.status != null}"><c:param name="status" value="${criteria.status}" /></c:if>
                <c:if test="${criteria.fromDate != null}"><c:param name="fromDate" value="${criteria.fromDate}" /></c:if>
                <c:if test="${criteria.toDate != null}"><c:param name="toDate" value="${criteria.toDate}" /></c:if>
            </c:url>
            <a href="${nextUrl}" class="btn btn-sm btn-secondary">Next</a>
        </c:if>
    </div>
</c:if>

<c:import url="/WEB-INF/views/common/foot.jsp" />
