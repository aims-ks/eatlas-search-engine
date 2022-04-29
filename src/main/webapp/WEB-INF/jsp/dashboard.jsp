<!DOCTYPE html>
<%@page contentType="text/html"%>
<%@page pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt"%>

<%-- Variables accessible in templates --%>
<c:set var="title" value="Dashboard" scope="request"/>
<c:set var="dashboardActive" value="active" scope="request"/>
<c:set var="messages" value="${it.messages}" scope="request"/>

<html lang="en">
<head>
    <title>eAtlas Search Engine - ${title}</title>
    <script src="<c:url value="/js/admin.js" />"></script>
    <link rel="stylesheet" href="<c:url value="/css/admin.css" />">
</head>

<body>
    <c:import url="include/header.jsp"/>

    <form method="post">
        <div class="box">
            <h2>Search engine</h2>

            <h3>Status</h3>
            <div>
                <p>
                    <span class="${it.status.reachable ? "ok" : "error"}">
                        ${it.status.reachable ? "Online" : "Offline"}
                    </span>
                </p>
            </div>

            <c:if test="${not empty it.status.indexes}">
                <h3>List of available indexes</h3>
                <div>
                    <ul>
                        <c:forEach items="${it.status.indexes}" var="index" varStatus="loopStatus">
                            <li><c:out value="${index}" /></li>
                        </c:forEach>
                    </ul>
                </div>
            </c:if>

            <c:if test="${not empty it.status.exception}">
                <h3>Error</h3>
                <div>
                    <div class="exception hover">
                        <span class="clickable">
                            <%-- Display the exception class name and message (not all exception have a "message") --%>
                            <c:out value="${it.status.exception.getClass().name}"/>
                            <c:if test="${not empty it.status.exception.message}">
                                - <c:out value="${it.status.exception.message}"/>
                            </c:if>
                        </span>
                        <ul class="stacktrace collapsible">
                            <c:forEach var="stacktraceElement" items="${it.status.exception.stackTrace}">
                                <li>
                                    <c:out value="${stacktraceElement}"/>
                                </li>
                            </c:forEach>
                        </ul>
                    </div>

                    <p>
                        Check that the <i>Elastic Search</i> service is running and the <i>Elastic Search URLs</i>
                        are set properly in the <a href="<c:url value="/admin/settings" />">settings</a> page.
                    </p>
                </div>
            </c:if>

            <h3>Configuration file</h3>
            <div class="file-status">
                <p><c:out value="${it.configFile.absolutePath}" /></p>
                <ul>
                    <li>
                        <span class="${it.configFile.exists() ? "ok" : "error"}">${it.configFile.exists() ? "Exists" : "Doesn\'t exists"}</span>,
                        <span class="${it.configFile.canRead() ? "ok" : "error"}">${it.configFile.canRead() ? "Readable" : "Not readable"}</span>,
                        <span class="${it.configFile.canWrite() ? "ok" : "error"}">${it.configFile.canWrite() ? "Writable" : "Not writable"}</span>
                    </li>
                    <li><span class="label">Last modified</span> <fmt:formatDate value="${it.configFileLastModifiedDate}" pattern="dd/MM/yyyy HH:mm:ss"/></li>
                </ul>

                <button class="reload" name="reload-config-button" value="reload-button" title="Reload configuration">Reload configuration file</button>
            </div>

            <h3>State file</h3>
            <div class="file-status">
                <p><c:out value="${it.stateFile.absolutePath}" /></p>
                <ul>
                    <li>
                        <span class="${it.stateFile.exists() ? "ok" : "error"}">${it.stateFile.exists() ? "Exists" : "Doesn\'t exists"}</span>,
                        <span class="${it.stateFile.canRead() ? "ok" : "error"}">${it.stateFile.canRead() ? "Readable" : "Not readable"}</span>,
                        <span class="${it.stateFile.canWrite() ? "ok" : "error"}">${it.stateFile.canWrite() ? "Writable" : "Not writable"}</span>
                    </li>
                    <li><span class="label">Last modified</span> <fmt:formatDate value="${it.stateFileLastModifiedDate}" pattern="dd/MM/yyyy HH:mm:ss"/></li>
                </ul>

                <button class="reload" name="reload-state-button" value="reload-button" title="Reload state">Reload state file</button>
            </div>

            <%-- Image cache folders --%>
            <h3>Cache directory</h3>
            <div class="file-status">
                <p><c:out value="${it.imageCacheDirectory.absolutePath}" /></p>
                <ul>
                    <li>
                        <span class="${it.imageCacheDirectory.exists() ? "ok" : "error"}">${it.imageCacheDirectory.exists() ? "Exists" : "Doesn\'t exists"}</span>,
                        <span class="${it.imageCacheDirectory.canRead() ? "ok" : "error"}">${it.imageCacheDirectory.canRead() ? "Readable" : "Not readable"}</span>,
                        <span class="${it.imageCacheDirectory.canWrite() ? "ok" : "error"}">${it.imageCacheDirectory.canWrite() ? "Writable" : "Not writable"}</span>
                    </li>
                </ul>
            </div>
        </div>

        <div class="box">
            <h2>Indexes</h2>

            <table>
                <tr class="table-header">
                    <th>Index</th>
                    <th>Indexer</th>
                    <th>Type</th>
                    <th>Document count</th>
                    <th>Cache directory</th>
                    <th>Last indexed</th>
                    <th>Last runtime</th>
                </tr>

                <c:forEach items="${it.config.indexers}" var="indexer" varStatus="loopStatus">
                    <tr class="${(loopStatus.index+1) % 2 == 0 ? 'even' : 'odd'}">
                        <td><c:out value="${indexer.index}" /></td>
                        <td class="${indexer.enabled ? "enabled" : "disabled"}">
                            ${indexer.enabled ? "Enabled" : "Disabled"}
                        </td>
                        <td><c:out value="${indexer.type}" /></td>
                        <td class="number">${indexer.state.count}</td>
                        <c:set var="imageCacheDir" value="${it.imageCacheDirectories.get(indexer.index)}"/>
                        <td class="rights" title="${imageCacheDir.absolutePath}">
                            <span class="${imageCacheDir.exists() ? "ok" : "error"}">${imageCacheDir.exists() ? "Exists" : "Doesn\'t exists"}</span>,
                            <span class="${imageCacheDir.canRead() ? "ok" : "error"}">${imageCacheDir.canRead() ? "Readable" : "Not readable"}</span>,
                            <span class="${imageCacheDir.canWrite() ? "ok" : "error"}">${imageCacheDir.canWrite() ? "Writable" : "Not writable"}</span>
                        </td>
                        <td class="date"><fmt:formatDate value="${indexer.state.lastIndexedDate}" pattern="dd/MM/yyyy HH:mm"/></td>
                        <td class="number">${indexer.state.lastIndexRuntimeFormatted}</td>
                    </tr>
                </c:forEach>
            </table>
        </div>
    </form>

    <jsp:include page="include/footer.jsp" />
</body>

</html>
