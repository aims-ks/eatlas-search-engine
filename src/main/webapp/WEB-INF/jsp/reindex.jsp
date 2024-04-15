<!DOCTYPE html>
<%@page contentType="text/html"%>
<%@page pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt"%>

<c:set var="baseURL" value="${pageContext.request.scheme}://${pageContext.request.localName}:${pageContext.request.localPort}" />

<%-- Variables accessible in templates --%>
<c:set var="title" value="Re-indexation page" scope="request"/>
<c:set var="reindexActive" value="active" scope="request"/>
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
        <h2>Cron configuration</h2>

        <p>
            Add the following entries to the server's crontab, to keep the search indexes up to date.
        </p>

        <c:url var="harvestNewURL" value="/public/index/v1/reindex">
            <c:param name="full" value="false" />
            <c:param name="token" value="${it.config.reindexToken}" />
        </c:url>
        <c:url var="fullHarvestURL" value="/public/index/v1/reindex">
            <c:param name="full" value="true" />
            <c:param name="token" value="${it.config.reindexToken}" />
        </c:url>

        <pre>
0   2   *   *   *   curl --silent "${baseURL}${harvestNewURL}" &gt; /dev/null
0   0   1   *   *   curl --silent "${baseURL}${fullHarvestURL}" &gt; /dev/null</pre>
    </div>

    <div class="box">
        <h2>Re-indexation</h2>

        <form method="post" data-progress-url="<c:url value="/admin/reindex/progress" />">
            <table>
                <tr class="table-header">
                    <th>Index</th>
                    <th>Indexer</th>
                    <th>Type</th>
                    <th>Document count</th>
                    <th>Last indexed</th>
                    <th>Last runtime</th>
                    <th>Progress</th>
                    <th>Actions</th>
                </tr>

                <c:forEach items="${it.config.indexers}" var="indexer" varStatus="loopStatus">
                    <tr class="${(loopStatus.index+1) % 2 == 0 ? 'even' : 'odd'}">
                        <td><c:out value="${indexer.index}" /></td>
                        <td class="${indexer.enabled ? "enabled" : "disabled"}">
                            ${indexer.enabled ? "Enabled" : "Disabled"}
                        </td>
                        <td><c:out value="${indexer.type}" /></td>
                        <td class="number">${indexer.state.count}</td>
                        <td class="date"><fmt:formatDate value="${indexer.state.lastIndexedDate}" pattern="dd/MM/yyyy HH:mm"/></td>
                        <td class="number">${indexer.state.lastIndexRuntimeFormatted}</td>
                        <td class="progress">
                            <progress class="index-progress disabled" value="0" max="100" id="progress_${indexer.index}" data-progress-url="<c:url value="/admin/reindex/OLDprogress"><c:param name="index" value="${indexer.index}" /></c:url>"></progress>
                        </td>
                        <td class="buttons">
                            <button class="index" name="reindex-button" value="${indexer.index}" title="Reindex">Reindex</button>
                            <button class="index-latest" name="index-latest-button" value="${indexer.index}" title="Index latest" <c:if test="${not indexer.supportsIndexLatest()}">disabled="disabled"</c:if>>Index latest</button>
                        </td>
                    </tr>
                </c:forEach>
            </table>

            <button class="index-all" name="reindex-all-button" value="reindex" title="Reindex all enabled indexes">Reindex all</button>
            <button class="index-latest-all" name="index-latest-all-button" value="index-latest" title="Index latest content for all enabled indexes">Index latest all</button>

            <button class="refresh" name="refresh-count-button" value="refresh-count" title="Refresh count">Refresh indexes document count</button>
        </form>
    </div>

    <c:import url="include/footer.jsp"/>
</body>

</html>
