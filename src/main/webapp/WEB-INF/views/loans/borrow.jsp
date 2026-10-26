<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>

<c:import url="/WEB-INF/views/common/head.jsp">
    <c:param name="pageTitle" value="Borrow a book" />
    <c:param name="currentPage" value="loans" />
</c:import>

<h1 class="page-title">Borrow a book</h1>
<p class="page-subtitle mt-2">Select an active member and an available copy. The due date is always 14 calendar days after the loan date.</p>

<div class="form-card">
    <form method="post" action="<c:url value='/loans/borrow' />" data-guard>
        <input type="hidden" name="_csrf" value="<c:out value='${csrfToken}' />" />

        <div class="form-group">
            <label class="form-label" for="memberId">Member <span class="text-danger">*</span></label>
            <select id="memberId" name="memberId" class="form-select <c:if test='${not empty fieldErrors.memberId}'>error</c:if>" required>
                <option value="">-- Select an active member --</option>
                <c:forEach var="m" items="${activeMembers}">
                    <option value="${m.memberId}" <c:if test="${m.memberId == param.memberId}">selected</c:if>><c:out value="${m.memberCode}" /> &mdash; <c:out value="${m.fullName}" /></option>
                </c:forEach>
            </select>
            <c:if test="${not empty fieldErrors.memberId}"><p class="field-error" role="alert"><c:out value="${fieldErrors.memberId}" /></p></c:if>
        </div>

        <div class="form-group">
            <label class="form-label" for="copyId">Available copy <span class="text-danger">*</span></label>
            <select id="copyId" name="copyId" class="form-select <c:if test='${not empty fieldErrors.copyId}'>error</c:if>" required>
                <option value="">-- Select an available copy --</option>
                <c:forEach var="cp" items="${availableCopies}">
                    <option value="${cp.copyId}" <c:if test="${cp.copyId == param.copyId}">selected</c:if>><c:out value="${cp.copyCode}" /> &mdash; <c:out value="${cp.bookTitle}" /></option>
                </c:forEach>
            </select>
            <c:if test="${not empty fieldErrors.copyId}"><p class="field-error" role="alert"><c:out value="${fieldErrors.copyId}" /></p></c:if>
        </div>

        <div class="form-group">
            <label class="form-label" for="loanDate">Loan date</label>
            <input type="date" id="loanDate" name="loanDate" class="form-control" value="<c:out value='${loanDate}' default='' />">
            <p class="text-muted">The due date is computed automatically as the loan date plus 14 calendar days.</p>
        </div>

        <div class="form-group">
            <label class="form-label">Due date (computed)</label>
            <p class="form-control"><c:out value="${dueDate}" default="—" /></p>
        </div>

        <div class="form-actions">
            <button type="submit" class="btn btn-primary">Borrow book</button>
            <a href="<c:url value='/dashboard' />" class="btn btn-secondary">Cancel</a>
        </div>
    </form>
</div>

<c:import url="/WEB-INF/views/common/foot.jsp" />
