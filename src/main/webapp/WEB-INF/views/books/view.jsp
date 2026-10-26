<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>

<c:import url="/WEB-INF/views/common/head.jsp">
    <c:param name="pageTitle" value="Book details" />
    <c:param name="currentPage" value="books" />
</c:import>

<h1 class="page-title">Book details</h1>
<p class="page-subtitle mt-2">Book metadata, authors, and physical copies.</p>

<c:choose>
    <c:when test="${empty book}">
        <div class="empty-state">The book was not found.</div>
        <a href="<c:url value='/books' />" class="btn btn-primary">Back to the book list</a>
    </c:when>
    <c:otherwise>
        <div class="form-card" style="max-width:600px;">
            <div class="form-group">
                <label class="form-label">Book ID</label>
                <p class="form-control"><c:out value="${book.bookId}" /></p>
            </div>
            <div class="form-group">
                <label class="form-label">ISBN</label>
                <p class="form-control"><c:out value="${book.isbn}" /></p>
            </div>
            <div class="form-group">
                <label class="form-label">Title</label>
                <p class="form-control"><c:out value="${book.title}" /></p>
            </div>
            <div class="form-group">
                <label class="form-label">Publisher</label>
                <p class="form-control"><c:out value="${book.publisherName}" default="Unknown" /></p>
            </div>
            <div class="form-group">
                <label class="form-label">Category</label>
                <p class="form-control"><c:out value="${book.categoryName}" default="Unknown" /></p>
            </div>
            <div class="form-group">
                <label class="form-label">Publication year</label>
                <p class="form-control"><c:out value="${book.publicationYear}" default="Unknown" /></p>
            </div>
            <div class="form-group">
                <label class="form-label">Authors</label>
                <p class="form-control">
                    <c:forEach var="a" items="${book.authors}" varStatus="status">
                        <c:out value="${a.name}" /><c:if test="${not status.last}">, </c:if>
                    </c:forEach>
                    <c:if test="${empty book.authors}">No authors assigned</c:if>
                </p>
            </div>
            <div class="form-group">
                <label class="form-label">Available copies</label>
                <p class="form-control"><c:out value="${book.availableCopies}" default="0" /></p>
            </div>
        </div>

        <h2 class="page-title mt-2">Book copies</h2>
        <div class="table-wrap">
            <table>
                <thead>
                    <tr>
                        <th>Copy code</th>
                        <th>Status</th>
                    </tr>
                </thead>
                <tbody>
                    <c:if test="${empty copies}">
                        <tr><td colspan="2" class="empty-state">This book has no copies.</td></tr>
                    </c:if>
                    <c:forEach var="cp" items="${copies}">
                        <tr>
                            <td><c:out value="${cp.copyCode}" /></td>
                            <td><span class="status-badge status-${fn:toLowerCase(cp.status)}"><c:out value="${cp.status}" /></span></td>
                        </tr>
                    </c:forEach>
                </tbody>
            </table>
        </div>

        <div class="mt-2">
            <a href="<c:url value='/books/edit' />?id=<c:out value='${book.bookId}' />" class="btn btn-secondary">Edit book</a>
            <a href="<c:url value='/books/delete' />?id=<c:out value='${book.bookId}' />" class="btn btn-danger"
               data-confirm="Delete this book? Books with loan history cannot be deleted.">Delete book</a>
            <a href="<c:url value='/books' />" class="btn btn-secondary">Back</a>
        </div>
    </c:otherwise>
</c:choose>

<c:import url="/WEB-INF/views/common/foot.jsp" />
