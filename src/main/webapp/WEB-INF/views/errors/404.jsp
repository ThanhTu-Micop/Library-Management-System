<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>

<c:import url="/WEB-INF/views/common/head.jsp">
    <c:param name="pageTitle" value="Page not found" />
    <c:param name="currentPage" value="errors" />
</c:import>

<h1 class="page-title">404 &mdash; Page not found</h1>
<p class="page-subtitle mt-2">The page you requested does not exist.</p>

<div class="mt-2">
    <a href="<c:url value='/dashboard' />" class="btn btn-primary">Back to dashboard</a>
</div>

<c:import url="/WEB-INF/views/common/foot.jsp" />
