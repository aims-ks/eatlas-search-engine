<!DOCTYPE html>
<%@page contentType="text/html"%>
<%@page pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt"%>

<html lang="en">
<head>
    <title>eAtlas Search Engine - Dashboard</title>
    <link rel="stylesheet" href="${pageContext.servletContext.contextPath}/css/admin.css">
</head>

<body>
    <div class="header">
        <h1>eAtlas Search Engine - Dashboard</h1>
        <nav>
            <ul class="user-menu">
                <li><span>Admin</span></li>
                <li><a href="#">Logout</a></li>
            </ul>
        </nav>
    </div>

    <div class="main">
        <nav>
            <ul class="menu">
                <li><a href="#">Overview</a></li> <!-- List indexes in table, each row have: number of doc, edit button (goes to Manage), index button (goes to Index) -->
                <li><a href="#">Manage</a></li> <!-- Create, edit, delete index -->
                <li><a href="#">Reindex</a></li> <!-- Re-index an index or all indexes, with progress bar -->
                <li><a href="#">Search</a></li> <!-- Test the search. Checkbox to choose which index to search from. -->
            </ul>
        </nav>

        <div class="content">
            <div class="message box">
                <div class="error">Error message</div>
                <div class="info">Info message</div>
                <div class="warning">Warning message</div>
            </div>

            <div class="box">
                <h2>Indexes</h2>

                <table>
                    <tr class="table-header">
                        <th>Index</th>
                        <th>Type</th>
                        <th>Documents</th>
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
                                <button class="edit" title="Edit">Edit</button>
                                <button class="index" title="Re-index">Re-index</button>
                                <button class="delete" title="Delete">Delete</button>
                            </td>
                        </tr>
                    </c:forEach>
                </table>

                <button class="add" title="Add an index">Add an index</button>
                <p>Content of the page</p>
            </div>

        </div>

    </div>

    <div class="footer">
        FOOTER
    </div>
</body>

</html>
