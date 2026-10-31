<%-- Shared Previous/Next pagination control. Preserves the current query parameters. --%>
<%@ tag body-content="empty" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ attribute name="pageData" type="com.library.model.Page" required="true" %>
<%@ attribute name="baseUrl" type="java.lang.String" required="true" %>

<c:if test="${pageData.totalPages gt 1}">
    <div class="pagination mt-2">
        <c:if test="${not pageData.first}">
            <c:url var="previousUrl" value="${baseUrl}">
                <c:param name="page" value="${pageData.page - 1}" />
                <c:forEach var="entry" items="${pageContext.request.parameterMap}">
                    <c:if test="${entry.key ne 'page' and not empty entry.value[0]}">
                        <c:param name="${entry.key}" value="${entry.value[0]}" />
                    </c:if>
                </c:forEach>
            </c:url>
            <a href="${previousUrl}" class="btn btn-sm btn-secondary">Previous</a>
        </c:if>
        <span class="current">Page <c:out value="${pageData.page + 1}" /> / <c:out value="${pageData.totalPages}" /></span>
        <c:if test="${not pageData.last}">
            <c:url var="nextUrl" value="${baseUrl}">
                <c:param name="page" value="${pageData.page + 1}" />
                <c:forEach var="entry" items="${pageContext.request.parameterMap}">
                    <c:if test="${entry.key ne 'page' and not empty entry.value[0]}">
                        <c:param name="${entry.key}" value="${entry.value[0]}" />
                    </c:if>
                </c:forEach>
            </c:url>
            <a href="${nextUrl}" class="btn btn-sm btn-secondary">Next</a>
        </c:if>
    </div>
</c:if>
