<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>

<c:import url="/WEB-INF/views/common/head.jsp">
    <c:param name="pageTitle" value="Books" />
    <c:param name="currentPage" value="books" />
</c:import>

<h1 class="page-title">Manage books</h1>
<p class="page-subtitle mt-2">Search, view, add, edit, and delete book titles.</p>

<div class="toolbar mb-2">
    <a href="<c:url value='/books/new' />" class="btn btn-primary">+ Add book</a>
</div>

<div class="table-wrap">
    <form accept-charset="UTF-8" method="get" action="<c:url value='/books' />" style="margin:0;">
        <div style="display:flex; gap:0.5rem; flex-wrap:wrap; margin-bottom:0.75rem;">
            <input type="text" name="keyword" class="form-control" placeholder="Search by ISBN or title"
                   value="<c:out value='${criteria.keyword}' default='' />" style="min-width:260px;">
            <select name="publisherId" class="form-select" style="min-width:160px;">
                <option value="">All publishers</option>
                <c:forEach var="p" items="${publishers}">
                    <option value="${p.publisherId}" <c:if test="${p.publisherId == criteria.publisherId}">selected</c:if>><c:out value="${p.name}" /></option>
                </c:forEach>
            </select>
            <select name="categoryId" class="form-select" style="min-width:160px;">
                <option value="">All categories</option>
                <c:forEach var="cat" items="${categories}">
                    <option value="${cat.categoryId}" <c:if test="${cat.categoryId == criteria.categoryId}">selected</c:if>><c:out value="${cat.name}" /></option>
                </c:forEach>
            </select>
            <input type="text" name="authorName" class="form-control" placeholder="Author name"
                   value="<c:out value='${criteria.authorName}' default='' />" style="min-width:160px;">
            <label class="form-checkbox" style="margin:0;">
                <input type="checkbox" name="availableOnly" value="true" <c:if test="${criteria.availableOnly}">checked</c:if>> Available copies only
            </label>
            <button type="submit" class="btn btn-primary">Search</button>
            <a href="<c:url value='/books' />" class="btn btn-secondary">Clear filters</a>
        </div>
    </form>

    <table>
        <thead>
            <tr>
                <th>ID</th>
                <th>ISBN</th>
                <th>Title</th>
                <th>Publisher</th>
                <th>Category</th>
                <th>Year</th>
                <th>Availability</th>
                <th>Actions</th>
            </tr>
        </thead>
        <tbody>
            <c:if test="${empty bookPage.content}">
                <tr>
                    <td colspan="8" class="empty-state">No matching books were found.</td>
                </tr>
            </c:if>
            <c:forEach var="b" items="${bookPage.content}">
                <tr>
                    <td><c:out value="${b.bookId}" /></td>
                    <td><c:out value="${b.isbn}" /></td>
                    <td><c:out value="${b.title}" /></td>
                    <td><c:out value="${b.publisherName}" default="Unknown" /></td>
                    <td><c:out value="${b.categoryName}" default="Unknown" /></td>
                    <td><c:out value="${b.publicationYear}" default="—" /></td>
                    <td>
                        <c:choose>
                            <c:when test="${b.availableCopies gt 0}">
                                <span class="status-badge status-available">Available (<c:out value="${b.availableCopies}" />)</span>
                            </c:when>
                            <c:otherwise>
                                <span class="status-badge status-borrowed">Unavailable</span>
                            </c:otherwise>
                        </c:choose>
                    </td>
                    <td class="actions-cell">
                        <a href="<c:url value='/books/view' />?id=<c:out value='${b.bookId}' />" class="btn btn-sm btn-secondary">View</a>
                        <a href="<c:url value='/books/edit' />?id=<c:out value='${b.bookId}' />" class="btn btn-sm btn-secondary">Edit</a>
                        <a href="<c:url value='/books/delete' />?id=<c:out value='${b.bookId}' />" class="btn btn-sm btn-danger"
                           data-confirm="Delete this book? Books with loan history cannot be deleted.">Delete</a>
                    </td>
                </tr>
            </c:forEach>
        </tbody>
    </table>
</div>

<c:if test="${bookPage.totalPages gt 1}">
    <div class="pagination mt-2">
        <c:if test="${not bookPage.first}">
            <c:url var="previousUrl" value="/books">
                <c:param name="page" value="${bookPage.page - 1}" />
                <c:param name="size" value="${bookPage.size}" />
                <c:if test="${not empty criteria.keyword}"><c:param name="keyword" value="${criteria.keyword}" /></c:if>
                <c:if test="${criteria.publisherId != null}"><c:param name="publisherId" value="${criteria.publisherId}" /></c:if>
                <c:if test="${criteria.categoryId != null}"><c:param name="categoryId" value="${criteria.categoryId}" /></c:if>
                <c:if test="${not empty criteria.authorName}"><c:param name="authorName" value="${criteria.authorName}" /></c:if>
                <c:if test="${criteria.availableOnly}"><c:param name="availableOnly" value="true" /></c:if>
            </c:url>
            <a href="${previousUrl}" class="btn btn-sm btn-secondary">Previous</a>
        </c:if>
        <span class="current">Page <c:out value="${bookPage.page + 1}" /> / <c:out value="${bookPage.totalPages}" /></span>
        <c:if test="${not bookPage.last}">
            <c:url var="nextUrl" value="/books">
                <c:param name="page" value="${bookPage.page + 1}" />
                <c:param name="size" value="${bookPage.size}" />
                <c:if test="${not empty criteria.keyword}"><c:param name="keyword" value="${criteria.keyword}" /></c:if>
                <c:if test="${criteria.publisherId != null}"><c:param name="publisherId" value="${criteria.publisherId}" /></c:if>
                <c:if test="${criteria.categoryId != null}"><c:param name="categoryId" value="${criteria.categoryId}" /></c:if>
                <c:if test="${not empty criteria.authorName}"><c:param name="authorName" value="${criteria.authorName}" /></c:if>
                <c:if test="${criteria.availableOnly}"><c:param name="availableOnly" value="true" /></c:if>
            </c:url>
            <a href="${nextUrl}" class="btn btn-sm btn-secondary">Next</a>
        </c:if>
    </div>
</c:if>

<c:import url="/WEB-INF/views/common/foot.jsp" />
