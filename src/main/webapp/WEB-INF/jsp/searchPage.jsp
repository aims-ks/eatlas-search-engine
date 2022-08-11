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
    <link rel="icon" href="<c:url value="/img/favicon.svg" />" type="image/svg+xml">
    <script src="<c:url value="/js/admin.js" />"></script>
    <link rel="stylesheet" href="<c:url value="/css/admin.css" />">
</head>

<body>
    <c:import url="include/header.jsp"/>

    <div class="box">
        <h2>Search</h2>

        <%-- Form send parameter using GET, to make the search query visible in the URL (sharable) --%>
        <form method="get">
            <h3>Query</h3>

            <div class="field">
                <label>
                    <span class="label">Keywords</span>
                    <input type="text" name="query" value="<c:out value="${it.query}"/>" />
                </label>
                <div class="desc">Optional. Default: Returns all documents from the selected indexes.</div>
                <div class="desc">Elastic search query. Write a search query the same way you would write it in Google.</div>
            </div>

            <div class="field">
                <label>
                    <span class="label">Spatial filter (WKT)</span>
                    <input type="text" name="wkt" value="<c:out value="${it.wkt}"/>" />
                </label>
                <div class="desc">Optional. Default: No spatial filter.</div>
                <div class="desc">Restrict search to a spatial extent.</div>
                <div class="desc">Example for GBR: <code>BBOX (142.0, 153.0, -9.5, -22.5)</code></div>
                <div class="desc">You can use this <a href="https://clydedacruz.github.io/openstreetmap-wkt-playground/" target="_blank">Online WKT editor</a> to generate the WKT.</div>
            </div>

            <h3>Indexes</h3>
            <ul>
                <c:forEach items="${it.config.getEnabledIndexers()}" var="indexer">
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

            <div class="submit-search">
                <input type="submit" class="button" value="Search">
            </div>
        </form>
    </div>


    <c:choose>
        <c:when test="${not empty it.results && it.results.summary.hits > 0}">
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

                            <c:url value="/admin/search" var="url">
                                <c:param name="query" value="${it.query}" />
                                <c:param name="wkt" value="${it.wkt}" />
                                <c:forEach items="${it.indexes}" var="index">
                                    <c:param name="indexes" value="${index}" />
                                </c:forEach>
                                <c:param name="hitsPerPage" value="${it.hitsPerPage}" />
                                <c:param name="page" value="${it.page}" />
                                <c:param name="reindex-idx" value="${searchResult.entity.index}" />
                                <c:param name="reindex-id" value="${searchResult.entity.id}" />
                            </c:url>
                            <a class="reindex button" href="${url}">Reindex</a>
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

                        <div class="jsonResult hover">
                            <span class="clickable">View JSON Result</span>
                            <pre class="json collapsible"><c:out value="${searchResult.toJSON().toString(2)}"/></pre>
                        </div>
                    </div>
                </c:forEach>

                <%-- Pager --%>
                <div class="box pager">
                    <ul>
                        <c:if test="${it.page > 1}">
                            <c:url value="/admin/search" var="url">
                                <c:param name="query" value="${it.query}" />
                                <c:param name="wkt" value="${it.wkt}" />
                                <c:forEach items="${it.indexes}" var="index">
                                    <c:param name="indexes" value="${index}" />
                                </c:forEach>
                                <c:param name="hitsPerPage" value="${it.hitsPerPage}" />
                                <c:param name="page" value="1" />
                            </c:url>
                            <li><a class="button" href="${url}" title="First">«</a></li>

                            <c:url value="/admin/search" var="url">
                                <c:param name="query" value="${it.query}" />
                                <c:param name="wkt" value="${it.wkt}" />
                                <c:forEach items="${it.indexes}" var="index">
                                    <c:param name="indexes" value="${index}" />
                                </c:forEach>
                                <c:param name="hitsPerPage" value="${it.hitsPerPage}" />
                                <c:param name="page" value="${it.page - 1}" />
                            </c:url>
                            <li><a class="button" href="${url}" title="Previous">‹</a></li>
                        </c:if>

                        <c:set var="pagerBegin" value="${it.page - 1}" />
                        <c:if test="${pagerBegin < 1}">
                            <c:set var="pagerBegin" value="1" />
                        </c:if>
                        <c:set var="pagerEnd" value="${it.page + 3}" />
                        <c:if test="${pagerEnd > it.nbPage}">
                            <c:set var="pagerEnd" value="${it.nbPage}" />
                        </c:if>

                        <c:forEach begin="${pagerBegin}" end="${pagerEnd}" varStatus="page">
                            <c:choose>
                                <%-- Image downloaded, cached and served by the search engine --%>
                                <c:when test="${page.index == it.page}">
                                    <li><span class="button">${page.index}</span></li>
                                </c:when>

                                <c:otherwise>
                                    <c:url value="/admin/search" var="url">
                                        <c:param name="query" value="${it.query}" />
                                        <c:param name="wkt" value="${it.wkt}" />
                                        <c:forEach items="${it.indexes}" var="index">
                                            <c:param name="indexes" value="${index}" />
                                        </c:forEach>
                                        <c:param name="hitsPerPage" value="${it.hitsPerPage}" />
                                        <c:param name="page" value="${page.index}" />
                                    </c:url>

                                    <li><a class="button" href="${url}">${page.index}</a></li>
                                </c:otherwise>
                            </c:choose>
                        </c:forEach>

                        <c:if test="${it.page < it.nbPage}">
                            <c:url value="/admin/search" var="url">
                                <c:param name="query" value="${it.query}" />
                                <c:param name="wkt" value="${it.wkt}" />
                                <c:forEach items="${it.indexes}" var="index">
                                    <c:param name="indexes" value="${index}" />
                                </c:forEach>
                                <c:param name="hitsPerPage" value="${it.hitsPerPage}" />
                                <c:param name="page" value="${it.page + 1}" />
                            </c:url>
                            <li><a class="button" href="${url}" title="Next">›</a></li>

                            <c:url value="/admin/search" var="url">
                                <c:param name="query" value="${it.query}" />
                                <c:param name="wkt" value="${it.wkt}" />
                                <c:forEach items="${it.indexes}" var="index">
                                    <c:param name="indexes" value="${index}" />
                                </c:forEach>
                                <c:param name="hitsPerPage" value="${it.hitsPerPage}" />
                                <c:param name="page" value="${it.nbPage}" />
                            </c:url>
                            <li><a class="button" href="${url}" title="Last">»</a></li>
                        </c:if>
                    </ul>
                </div>
            </div>
        </c:when>

        <c:otherwise>
            <div class="box">
                <h3>Results</h3>
                <p>No results found</p>
            </div>
        </c:otherwise>
    </c:choose>

    <jsp:include page="include/footer.jsp" />
</body>

</html>
