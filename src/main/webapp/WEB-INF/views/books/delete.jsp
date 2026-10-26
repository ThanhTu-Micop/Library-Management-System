<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>

<c:import url="/WEB-INF/views/common/head.jsp">
    <c:param name="pageTitle" value="Delete book" />
    <c:param name="currentPage" value="books" />
</c:import>

<h1 class="page-title">Delete book</h1>
<p class="page-subtitle mt-2">Confirm the deletion of this book and its copies.</p>

<div class="form-card" style="max-width:500px;">
    <div class="flash flash-error" role="alert" aria-live="polite">
        <strong>Warning:</strong> This action cannot be undone. A book with any loan history cannot be deleted.
        Otherwise the book, its copies, and its author links are removed.
    </div>

    <div class="form-group">
        <label class="form-label">Book</label>
        <p class="form-control"><c:out value="${book.title}" /></p>
    </div>
    <div class="form-group">
        <label class="form-label">ISBN</label>
        <p class="form-control"><c:out value="${book.isbn}" /></p>
    </div>
    <div class="form-group">
        <label class="form-label">Copies to delete</label>
        <p class="form-control"><c:out value="${copyCount}" default="0" /></p>
    </div>

    <form method="post" action="<c:url value='/books/delete' />" data-guard>
        <input type="hidden" name="bookId" value="<c:out value='${book.bookId}' />" />
        <input type="hidden" name="_csrf" value="<c:out value='${csrfToken}' />" />
        <div class="form-actions">
            <button type="submit" class="btn btn-danger">Delete book</button>
            <a href="<c:url value='/books/view' />?id=<c:out value='${book.bookId}' />" class="btn btn-secondary">Cancel</a>
        </div>
    </form>
</div>

<c:import url="/WEB-INF/views/common/foot.jsp" />
