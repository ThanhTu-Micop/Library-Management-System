<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>

<c:set var="isCreate" value="${empty book.bookId}" />
<c:choose>
    <c:when test="${isCreate}">
        <c:url var="formAction" value="/books/new" />
    </c:when>
    <c:otherwise>
        <c:url var="formAction" value="/books/edit">
            <c:param name="id" value="${book.bookId}" />
        </c:url>
    </c:otherwise>
</c:choose>

<c:import url="/WEB-INF/views/common/head.jsp">
    <c:param name="pageTitle" value="${isCreate ? 'Add book' : 'Edit book'}" />
    <c:param name="currentPage" value="books" />
</c:import>

<h1 class="page-title">${isCreate ? 'Add a new book' : 'Edit book'}</h1>
<p class="page-subtitle mt-2">Enter the book details, publisher, category, authors, and publication year.</p>

<div class="form-card">
    <form method="post" action="${formAction}" data-guard>
        <input type="hidden" name="_csrf" value="<c:out value='${csrfToken}' />" />

        <div class="form-group">
            <label class="form-label" for="isbn">ISBN <span class="text-danger">*</span></label>
            <input type="text" id="isbn" name="isbn"
                   class="form-control <c:if test='${not empty fieldErrors.isbn}'>error</c:if>"
                   value="<c:out value='${book.isbn}' default='' />"
                   minlength="13" maxlength="20" pattern=".{13,20}" required>
            <c:if test="${not empty fieldErrors.isbn}"><p class="field-error" role="alert"><c:out value="${fieldErrors.isbn}" /></p></c:if>
        </div>

        <div class="form-group">
            <label class="form-label" for="title">Title <span class="text-danger">*</span></label>
            <input type="text" id="title" name="title"
                   class="form-control <c:if test='${not empty fieldErrors.title}'>error</c:if>"
                   value="<c:out value='${book.title}' default='' />" maxlength="255" required>
            <c:if test="${not empty fieldErrors.title}"><p class="field-error" role="alert"><c:out value="${fieldErrors.title}" /></p></c:if>
        </div>

        <div class="form-group">
            <label class="form-label" for="publisherId">Publisher <span class="text-danger">*</span></label>
            <select id="publisherId" name="publisherId" class="form-select" required>
                <option value="">-- Select a publisher --</option>
                <c:forEach var="p" items="${publishers}">
                    <option value="${p.publisherId}" <c:if test="${p.publisherId == book.publisherId}">selected</c:if>><c:out value="${p.name}" /></option>
                </c:forEach>
            </select>
            <c:if test="${not empty fieldErrors.publisherId}"><p class="field-error" role="alert"><c:out value="${fieldErrors.publisherId}" /></p></c:if>
        </div>

        <div class="form-group">
            <label class="form-label" for="categoryId">Category <span class="text-danger">*</span></label>
            <select id="categoryId" name="categoryId" class="form-select" required>
                <option value="">-- Select a category --</option>
                <c:forEach var="cat" items="${categories}">
                    <option value="${cat.categoryId}" <c:if test="${cat.categoryId == book.categoryId}">selected</c:if>><c:out value="${cat.name}" /></option>
                </c:forEach>
            </select>
            <c:if test="${not empty fieldErrors.categoryId}"><p class="field-error" role="alert"><c:out value="${fieldErrors.categoryId}" /></p></c:if>
        </div>

        <div class="form-group">
            <label class="form-label" for="publicationYear">Publication year</label>
            <input type="number" id="publicationYear" name="publicationYear"
                   class="form-control <c:if test='${not empty fieldErrors.publicationYear}'>error</c:if>"
                   value="<c:out value='${book.publicationYear}' default='' />" min="1000" max="2100">
            <c:if test="${not empty fieldErrors.publicationYear}"><p class="field-error" role="alert"><c:out value="${fieldErrors.publicationYear}" /></p></c:if>
        </div>

        <div class="form-group">
            <label class="form-label" for="authorIds">Authors <span class="text-danger">*</span></label>
            <select id="authorIds" name="authorIds" class="form-select" multiple size="6" required>
                <c:forEach var="a" items="${authors}">
                    <c:set var="selectedAuthor" value="false" />
                    <c:forEach var="selectedId" items="${authorIds}">
                        <c:if test="${selectedId == a.authorId}"><c:set var="selectedAuthor" value="true" /></c:if>
                    </c:forEach>
                    <option value="${a.authorId}" <c:if test="${selectedAuthor}">selected</c:if>><c:out value="${a.name}" /></option>
                </c:forEach>
            </select>
            <c:if test="${not empty fieldErrors.authorIds}"><p class="field-error" role="alert"><c:out value="${fieldErrors.authorIds}" /></p></c:if>
        </div>

        <c:if test="${isCreate}">
            <div class="form-group">
                <label class="form-label" for="initialCopyCount">Initial copies</label>
                <input type="number" id="initialCopyCount" name="initialCopyCount"
                       class="form-control <c:if test='${not empty fieldErrors.initialCopyCount}'>error</c:if>"
                       value="<c:out value='${initialCopyCount}' default='0' />" min="0">
                <p class="text-muted">The service creates this many copies with the AVAILABLE status.</p>
                <c:if test="${not empty fieldErrors.initialCopyCount}"><p class="field-error" role="alert"><c:out value="${fieldErrors.initialCopyCount}" /></p></c:if>
            </div>
        </c:if>

        <div class="form-actions">
            <button type="submit" class="btn btn-primary">Save</button>
            <a href="<c:url value='/books' />" class="btn btn-secondary">Cancel</a>
        </div>
    </form>
</div>

<c:import url="/WEB-INF/views/common/foot.jsp" />
