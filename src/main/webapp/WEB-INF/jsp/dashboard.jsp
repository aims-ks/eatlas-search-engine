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

            <div>
                <p>
                    Status:
                    <span class="${it.status.reachable ? "ok" : "error"}">
                        ${it.status.reachable ? "Online" : "Offline"}
                    </span>
                </p>
            </div>

            <c:if test="${not empty it.status.indexes}">
                <div>
                    <p>List of available indexes:</p>
                    <ul>
                        <c:forEach items="${it.status.indexes}" var="index" varStatus="loopStatus">
                            <li><c:out value="${index}" /></li>
                        </c:forEach>
                    </ul>
                </div>
            </c:if>

            <c:if test="${not empty it.status.exception}">
                <div>
                    <p>
                        Error:
                        <c:out value="${it.status.exception.message}" />
                    </p>

                    <p>
                        Check that the <i>Elastic Search URLs</i> are set properly in the <a href="<c:url value="/admin/settings" />">settings</a> page.
                    </p>
                </div>
            </c:if>

            <div>
                <p>Configuration file: <c:out value="${it.configFile.absolutePath}" /></p>
                <ul>
                    <li>Readable: <span class="${it.configFile.canRead() ? "ok" : "error"}">${it.configFile.canRead() ? "Yes" : "No"}</span></li>
                    <li>Writable: <span class="${it.configFile.canWrite() ? "ok" : "error"}">${it.configFile.canWrite() ? "Yes" : "No"}</span></li>
                    <li>Last modified: <fmt:formatDate value="${it.configFileLastModifiedDate}" pattern="dd/MM/yyyy HH:mm:ss"/></li>
                </ul>

                <button class="reload" name="reload-config-button" value="reload-button" title="Reload configuration">Reload configuration file</button>
            </div>

            <div>
                <p>State file: <c:out value="${it.stateFile.absolutePath}" /></p>
                <ul>
                    <li>Readable: <span class="${it.stateFile.canRead() ? "ok" : "error"}">${it.stateFile.canRead() ? "Yes" : "No"}</span></li>
                    <li>Writable: <span class="${it.stateFile.canWrite() ? "ok" : "error"}">${it.stateFile.canWrite() ? "Yes" : "No"}</span></li>
                    <li>Last modified: <fmt:formatDate value="${it.stateFileLastModifiedDate}" pattern="dd/MM/yyyy HH:mm:ss"/></li>
                </ul>

                <button class="reload" name="reload-state-button" value="reload-button" title="Reload state">Reload state file</button>
            </div>

        </div>

        <div class="box">
            <h2>Indexes</h2>

            <table>
                <tr class="table-header">
                    <th>Index</th>
                    <th>Status</th>
                    <th>Type</th>
                    <th>Document count</th>
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
