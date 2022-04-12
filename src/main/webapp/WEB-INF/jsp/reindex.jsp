<!DOCTYPE html>
<%@page contentType="text/html"%>
<%@page pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt"%>

<%-- Variables accessible in templates --%>
<c:set var="title" value="Re-indexation page" scope="request"/>
<c:set var="reindexActive" value="active" scope="request"/>
<c:set var="messages" value="${it.messages}" scope="request"/>

<html lang="en">
<head>
    <title>eAtlas Search Engine - ${title}</title>
    <script src="<c:url value="/js/admin.js" />"></script>
    <link rel="stylesheet" href="<c:url value="/css/admin.css" />">
</head>

<body>
    <c:import url="include/header.jsp"/>

    <div class="box">
        <h2>Re-indexation page</h2>

        <table>
            <tr class="table-header">
                <th>Index</th>
                <th>Type</th>
                <th>Document count</th>
                <th>Last indexed</th>
                <th>Last runtime</th>
                <th>Actions</th>
            </tr>

            <c:forEach items="${it.config.indexers}" var="indexer" varStatus="loopStatus">
                <tr class="${(loopStatus.index+1) % 2 == 0 ? 'even' : 'odd'}">
                    <td>${indexer.index}</td>
                    <td>${indexer.type}</td>
                    <td class="number">${indexer.state.count}</td>
                    <td class="date"><fmt:formatDate value="${indexer.state.lastIndexedDate}" pattern="dd/MM/yyyy HH:mm"/></td>
                    <td class="number">${indexer.state.lastIndexRuntimeFormatted}</td>
                    <td class="buttons">
                        <button class="index" title="Re-index">Re-index</button>
                    </td>
                </tr>
            </c:forEach>
        </table>

        <button class="refresh" title="Re-index all">Re-index all</button>

        <button class="refresh" title="Refresh count">Refresh indexes document count</button>
    </div>

    <jsp:include page="include/footer.jsp" />
</body>

</html>
