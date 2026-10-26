<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>

<c:import url="/WEB-INF/views/common/head.jsp">
    <c:param name="pageTitle" value="Dashboard" />
    <c:param name="currentPage" value="dashboard" />
</c:import>

<h1 class="page-title">Dashboard</h1>
<p class="page-subtitle mt-2">Library activity at a glance.</p>

<div class="toolbar mb-2">
    <c:if test="${not empty dashboardStats}">
        <div class="flash flash-success" style="display:inline-block; margin-right:1rem;">
            <strong>Books:</strong> <c:out value="${dashboardStats.totalBooks}" /> &nbsp;
            <strong>Members:</strong> <c:out value="${dashboardStats.totalMembers}" /> &nbsp;
            <strong>Copies:</strong> <c:out value="${dashboardStats.totalCopies}" /> &nbsp;
            <strong>Active loans:</strong> <c:out value="${dashboardStats.activeLoans}" /> &nbsp;
            <strong>Overdue:</strong> <c:out value="${dashboardStats.overdueLoans}" /> &nbsp;
            <strong>Available:</strong> <c:out value="${dashboardStats.availableCopies}" />
        </div>
    </c:if>
</div>

<div class="table-wrap">
    <table>
        <thead>
            <tr>
                <th>Metric</th>
                <th>Value</th>
                <th>Description</th>
            </tr>
        </thead>
        <tbody>
            <c:if test="${empty dashboardStats}">
                <tr>
                    <td colspan="3" class="empty-state">No dashboard data is available.</td>
                </tr>
            </c:if>
            <c:if test="${not empty dashboardStats}">
                <tr>
                    <td><strong>Total books</strong></td>
                    <td><c:out value="${dashboardStats.totalBooks}" /></td>
                    <td>Number of book titles in the catalog</td>
                </tr>
                <tr>
                    <td><strong>Total members</strong></td>
                    <td><c:out value="${dashboardStats.totalMembers}" /></td>
                    <td>Number of registered members</td>
                </tr>
                <tr>
                    <td><strong>Total copies</strong></td>
                    <td><c:out value="${dashboardStats.totalCopies}" /></td>
                    <td>Number of physical book copies</td>
                </tr>
                <tr>
                    <td><strong>Active loans</strong></td>
                    <td><c:out value="${dashboardStats.activeLoans}" /></td>
                    <td>Loans that have not been returned</td>
                </tr>
                <tr>
                    <td><strong>Overdue loans</strong></td>
                    <td class="<c:if test="${dashboardStats.overdueLoans gt 0}">text-danger</c:if>"><strong><c:out value="${dashboardStats.overdueLoans}" /></strong></td>
                    <td>Active loans whose due date is before today</td>
                </tr>
                <tr>
                    <td><strong>Available copies</strong></td>
                    <td><c:out value="${dashboardStats.availableCopies}" /></td>
                    <td>Copies whose status is AVAILABLE</td>
                </tr>
            </c:if>
        </tbody>
    </table>
</div>

<div class="mt-2">
    <a href="<c:url value='/books' />" class="btn btn-primary">Manage books</a>
    <a href="<c:url value='/members' />" class="btn btn-secondary">Manage members</a>
    <a href="<c:url value='/loans/borrow' />" class="btn btn-primary">Borrow a book</a>
</div>

<c:import url="/WEB-INF/views/common/foot.jsp" />
