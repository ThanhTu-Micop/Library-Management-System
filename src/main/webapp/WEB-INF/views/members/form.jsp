<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>

<c:set var="isCreate" value="${empty member.memberId}" />
<c:choose>
    <c:when test="${isCreate}">
        <c:url var="formAction" value="/members/new" />
    </c:when>
    <c:otherwise>
        <c:url var="formAction" value="/members/edit">
            <c:param name="id" value="${member.memberId}" />
        </c:url>
    </c:otherwise>
</c:choose>

<c:import url="/WEB-INF/views/common/head.jsp">
    <c:param name="pageTitle" value="${isCreate ? 'Add member' : 'Edit member'}" />
    <c:param name="currentPage" value="members" />
</c:import>

<h1 class="page-title">${isCreate ? 'Add a new member' : 'Edit member'}</h1>
<p class="page-subtitle mt-2">Enter the member code, full name, email, and status.</p>

<div class="form-card">
    <form accept-charset="UTF-8" method="post" action="${formAction}" data-guard>
        <input type="hidden" name="_csrf" value="<c:out value='${csrfToken}' />" />

        <div class="form-group">
            <label class="form-label" for="memberCode">Member code <span class="text-danger">*</span></label>
            <input type="text" id="memberCode" name="memberCode"
                   class="form-control <c:if test='${not empty fieldErrors.memberCode}'>error</c:if>"
                   value="<c:out value='${member.memberCode}' default='' />" maxlength="50" required>
            <c:if test="${not empty fieldErrors.memberCode}"><p class="field-error" role="alert"><c:out value="${fieldErrors.memberCode}" /></p></c:if>
        </div>

        <div class="form-group">
            <label class="form-label" for="fullName">Full name <span class="text-danger">*</span></label>
            <input type="text" id="fullName" name="fullName"
                   class="form-control <c:if test='${not empty fieldErrors.fullName}'>error</c:if>"
                   value="<c:out value='${member.fullName}' default='' />" maxlength="255" required>
            <c:if test="${not empty fieldErrors.fullName}"><p class="field-error" role="alert"><c:out value="${fieldErrors.fullName}" /></p></c:if>
        </div>

        <div class="form-group">
            <label class="form-label" for="email">Email</label>
            <input type="email" id="email" name="email"
                   class="form-control <c:if test='${not empty fieldErrors.email}'>error</c:if>"
                   value="<c:out value='${member.email}' default='' />" maxlength="255">
            <c:if test="${not empty fieldErrors.email}"><p class="field-error" role="alert"><c:out value="${fieldErrors.email}" /></p></c:if>
        </div>

        <div class="form-group">
            <label class="form-label" for="status">Status <span class="text-danger">*</span></label>
            <select id="status" name="status" class="form-select" required>
                <option value="ACTIVE" <c:if test="${member.status == 'ACTIVE'}">selected</c:if>>Active</option>
                <option value="INACTIVE" <c:if test="${member.status == 'INACTIVE'}">selected</c:if>>Inactive</option>
            </select>
            <c:if test="${not empty fieldErrors.status}"><p class="field-error" role="alert"><c:out value="${fieldErrors.status}" /></p></c:if>
        </div>

        <div class="form-actions">
            <button type="submit" class="btn btn-primary">Save</button>
            <a href="<c:url value='/members' />" class="btn btn-secondary">Cancel</a>
        </div>
    </form>
</div>

<c:import url="/WEB-INF/views/common/foot.jsp" />
