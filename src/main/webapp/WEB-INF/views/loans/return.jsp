<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>

<c:import url="/WEB-INF/views/common/head.jsp">
    <c:param name="pageTitle" value="Return a book" />
    <c:param name="currentPage" value="loans" />
</c:import>

<h1 class="page-title">Return a book</h1>
<p class="page-subtitle mt-2">Select an active loan and record the return date.</p>

<div class="form-card">
    <c:if test="${empty activeLoans}">
        <div class="empty-state">There are no active loans to return.</div>
    </c:if>
    <c:if test="${not empty activeLoans}">
        <form accept-charset="UTF-8" method="post" action="<c:url value='/loans/return' />" data-guard>
            <input type="hidden" name="_csrf" value="<c:out value='${csrfToken}' />" />

            <div class="form-group">
                <label class="form-label" for="loanId">Active loan <span class="text-danger">*</span></label>
                <select id="loanId" name="loanId" class="form-select" required>
                    <option value="">-- Select an active loan --</option>
                    <c:forEach var="l" items="${activeLoans}">
                        <option value="${l.loanId}">
                            <c:out value="${l.bookTitle}" /> &mdash; <c:out value="${l.memberName}" /> &mdash; copy <c:out value="${l.copyCode}" /> &mdash; due <c:out value="${l.dueDate}" /><c:if test="${l.overdue}"> (overdue)</c:if>
                        </option>
                    </c:forEach>
                </select>
                <c:if test="${not empty fieldErrors.loanId}"><p class="field-error" role="alert"><c:out value="${fieldErrors.loanId}" /></p></c:if>
            </div>

            <div class="form-group">
                <label class="form-label" for="returnDate">Return date</label>
                <input type="date" id="returnDate" name="returnDate"
                       class="form-control <c:if test='${not empty fieldErrors.returnDate}'>error</c:if>"
                       value="<c:out value='${returnDate}' default='' />" required>
                <c:if test="${not empty fieldErrors.returnDate}"><p class="field-error" role="alert"><c:out value="${fieldErrors.returnDate}" /></p></c:if>
            </div>

            <div class="form-actions">
                <button type="submit" class="btn btn-primary">Return book</button>
                <a href="<c:url value='/dashboard' />" class="btn btn-secondary">Cancel</a>
            </div>
        </form>
    </c:if>
</div>

<c:import url="/WEB-INF/views/common/foot.jsp" />
