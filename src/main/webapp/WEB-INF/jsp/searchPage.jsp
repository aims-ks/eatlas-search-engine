<!DOCTYPE html>
<%@page contentType="text/html"%>
<%@page pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>

<%-- Variables accessible in templates --%>
<c:set var="title" value="Search page" scope="request"/>
<c:set var="searchActive" value="active" scope="request"/>
<c:set var="messages" value="${it.messages}" scope="request"/>

<html lang="en">
<head>
    <title>eAtlas Search Engine - ${title}</title>
    <link rel="stylesheet" href="<c:url value="/css/admin.css" />">
</head>

<body>
    <c:import url="include/header.jsp"/>

    <div class="box">
        <h2>Search</h2>

        <%-- Form send parameter using GET, to make the search query visible in the URL (sharable) --%>
        <form method="get">
            <h3>Query</h3>
            <label>
                <input type="text" name="query" value="<c:out value="${it.query}"/>" />
            </label>
            <input type="submit" value="Search">

            <h3>Indexes</h3>
            <ul>
                <c:forEach items="${it.config.indexers}" var="indexer">
                    <li>
                        <label>
                            <input type="checkbox" name="indexes" value="${indexer.index}"
                                <c:if test="${it.indexes.contains(indexer.index)}">
                                    checked="checked"
                                </c:if>
                            /> ${indexer.index}
                        </label>
                    </li>
                </c:forEach>
            </ul>
        </form>
    </div>


    <c:choose>
        <c:when test="${not empty it.results}">
            <div class="box">
                <h3>Result summary</h3>

                <div class="summary">
                    <p>${it.results.summary.hits} results found</p>
                    <ul>
                        <c:forEach items="${it.results.summary.indexSummaries.values()}" var="indexSummary">
                            <li>${indexSummary.index}: ${indexSummary.hits}</li>
                        </c:forEach>
                    </ul>
                </div>
            </div>

            <div class="results">
                <c:forEach items="${it.results.searchResults}" var="searchResult">
                    <div class="box result">
                        <h3 class="title">
                            <a href="${searchResult.entity.link}" target="_blank">
                                <c:out value="${searchResult.entity.title}"/>
                            </a>
                        </h3>

                        <div class="preview">
                            <div class="thumbnail">
                                <c:choose>
                                    <%-- Image downloaded, cached and served by the search engine --%>
                                    <c:when test="${not empty searchResult.entity.cachedThumbnailFilename}">
                                        <img src="<c:url value="/public/img/v1/${searchResult.entity.index}/${searchResult.entity.cachedThumbnailFilename}" />" alt="Thumbnail" />
                                    </c:when>

                                    <%-- External preview image --%>
                                    <c:when test="${not empty searchResult.entity.thumbnailUrl}">
                                        <img src="${searchResult.entity.thumbnailUrl}" alt="Thumbnail" />
                                    </c:when>

                                    <c:otherwise>
                                        <div class="no-thumbnail">No thumbnail</div>
                                    </c:otherwise>
                                </c:choose>
                            </div>

                            <ul class="highlights">
                                <c:forEach items="${searchResult.highlights}" var="highlight">
                                    <li>${highlight}</li>
                                </c:forEach>
                            </ul>

                            <div class="index">${searchResult.entity.index}</div>
                        </div>

                        <div class="jsonResult">View JSON Result
                            <pre class="json"><c:out value="${searchResult.toJSON().toString(2)}"/></pre>
                        </div>
                    </div>
                </c:forEach>

                <%-- Pager --%>
                <div class="box pager">
                    <ul>
                        <c:forEach begin="1" end="${it.nbPage}" varStatus="page">
                            <c:url value="/admin/search" var="url">
                                <c:param name="query" value="${it.query}" />
                                <c:forEach items="${it.indexes}" var="index">
                                    <c:param name="indexes" value="${index}" />
                                </c:forEach>
                                <c:param name="hitsPerPage" value="${it.hitsPerPage}" />
                                <c:param name="page" value="${page.index}" />
                            </c:url>
                            <li><a href="${url}">${page.index}</a></li>
                        </c:forEach>
                    </ul>
                </div>
            </div>
        </c:when>

        <c:otherwise>
            <div class="box">
                <h3>Results</h3>
                <p>No search results</p>
            </div>
        </c:otherwise>
    </c:choose>

    <jsp:include page="include/footer.jsp" />
</body>

</html>
