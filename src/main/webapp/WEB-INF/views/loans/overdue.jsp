<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="ui" tagdir="/WEB-INF/tags" %>

<c:import url="/WEB-INF/views/common/head.jsp">
    <c:param name="pageTitle" value="Overdue books" />
    <c:param name="currentPage" value="loans-overdue" />
</c:import>

<h1 class="page-title">Overdue books</h1>
<p class="page-subtitle mt-2">Active loans whose due date has already passed.</p>

<div class="table-wrap">
    <table>
        <thead>
            <tr>
                <th>Loan ID</th>
                <th>Book</th>
                <th>Copy</th>
                <th>Member</th>
                <th>Loan date</th>
                <th>Due date</th>
                <th>Days overdue</th>
            </tr>
        </thead>
        <tbody>
            <c:if test="${empty overduePage.content}">
                <tr>
                    <td colspan="7" class="empty-state">There are no overdue books.</td>
                </tr>
            </c:if>
            <c:forEach var="l" items="${overduePage.content}">
                <tr>
                    <td><c:out value="${l.loanId}" /></td>
                    <td><c:out value="${l.bookTitle}" default="Unknown" /></td>
                    <td><c:out value="${l.copyCode}" default="Unknown" /></td>
                    <td><c:out value="${l.memberName}" default="Unknown" /></td>
                    <td><c:out value="${l.loanDate}" /></td>
                    <td><c:out value="${l.dueDate}" /></td>
                    <td class="text-danger"><strong><c:out value="${l.daysOverdue}" /></strong> days</td>
                </tr>
            </c:forEach>
        </tbody>
    </table>
</div>

<ui:pagination pageData="${overduePage}" baseUrl="/loans/overdue" />

<div class="mt-2">
    <a href="<c:url value='/loans/return' />" class="btn btn-primary">Return a book</a>
    <a href="<c:url value='/dashboard' />" class="btn btn-secondary">Back to dashboard</a>
</div>

<c:import url="/WEB-INF/views/common/foot.jsp" />
