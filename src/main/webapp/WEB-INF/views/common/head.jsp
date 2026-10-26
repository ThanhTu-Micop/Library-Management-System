<%-- Common head, header, and flash messages. --%>
<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<c:set var="pageTitle" value="${empty param.pageTitle ? 'Library Management System' : param.pageTitle}" />
<c:set var="currentPage" value="${param.currentPage}" />
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title><c:out value="${pageTitle}" /></title>
    <link rel="stylesheet" href="<c:url value='/assets/css/app.css' />">
    <c:if test="${not empty csrfToken}">
        <meta name="_csrf" content="<c:out value='${csrfToken}' />">
    </c:if>
</head>
<body>
<div class="app">
    <header class="app-header">
        <a href="<c:url value='/dashboard' />" class="app-brand">Library Management</a>
        <nav class="app-nav">
            <a href="<c:url value='/dashboard' />" <c:if test="${currentPage == 'dashboard'}">class="active"</c:if>>Dashboard</a>
            <a href="<c:url value='/books' />" <c:if test="${currentPage == 'books'}">class="active"</c:if>>Books</a>
            <a href="<c:url value='/members' />" <c:if test="${currentPage == 'members'}">class="active"</c:if>>Members</a>
            <a href="<c:url value='/loans/borrow' />" <c:if test="${currentPage == 'loans'}">class="active"</c:if>>Borrow / Return</a>
            <a href="<c:url value='/loans/history' />" <c:if test="${currentPage == 'loans-history'}">class="active"</c:if>>Loan History</a>
            <a href="<c:url value='/loans/overdue' />" <c:if test="${currentPage == 'loans-overdue'}">class="active"</c:if>>Overdue</a>
        </nav>
    </header>

    <main class="app-main">
        <c:if test="${not empty flashSuccess}">
            <div class="flash flash-success" role="alert" aria-live="polite">
                <strong>Success:</strong> <c:out value="${flashSuccess}" />
            </div>
        </c:if>
        <c:if test="${not empty flashError}">
            <div class="flash flash-error" role="alert" aria-live="polite">
                <strong>Error:</strong> <c:out value="${flashError}" />
            </div>
        </c:if>
        <c:if test="${not empty globalError}">
            <div class="flash flash-error" role="alert" aria-live="polite">
                <strong>Error:</strong> <c:out value="${globalError}" />
            </div>
        </c:if>
        <c:if test="${not empty globalErrors}">
            <div class="flash flash-error" role="alert" aria-live="polite">
                <strong><c:out value="${fn:length(globalErrors)}" /> error(s):</strong>
                <ul class="error-list">
                    <c:forEach var="err" items="${globalErrors}">
                        <li><c:out value="${err}" /></li>
                    </c:forEach>
                </ul>
            </div>
        </c:if>
