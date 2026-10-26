<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>

<c:import url="/WEB-INF/views/common/head.jsp">
    <c:param name="pageTitle" value="System error" />
    <c:param name="currentPage" value="errors" />
</c:import>

<h1 class="page-title">500 &mdash; System error</h1>
<p class="page-subtitle mt-2">An unexpected error occurred. Please try again later or contact an administrator.</p>

<c:if test="${not empty globalError}">
    <div class="flash flash-error" role="alert" aria-live="polite">
        <c:out value="${globalError}" />
    </div>
</c:if>

<div class="mt-2">
    <a href="<c:url value='/dashboard' />" class="btn btn-primary">Back to dashboard</a>
</div>

<c:import url="/WEB-INF/views/common/foot.jsp" />
