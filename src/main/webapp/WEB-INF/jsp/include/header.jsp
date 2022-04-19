<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>

    <div class="header">
        <h1>eAtlas Search Engine - ${requestScope.title}</h1>
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
                <li class="${requestScope.dashboardActive}"><a href="<c:url value="/admin/" />">Status</a></li> <!-- List indexes in table, each row have: number of doc, edit button (goes to Manage), index button (goes to Index) -->
                <li class="${requestScope.settingsActive}"><a href="<c:url value="/admin/settings" />">Settings</a></li> <!-- Create, edit, delete index -->
                <li class="${requestScope.reindexActive}"><a href="<c:url value="/admin/reindex" />">Reindex</a></li> <!-- Re-index an index or all indexes, with progress bar -->
                <li class="${requestScope.searchActive}"><a href="<c:url value="/admin/search" />">Test search</a></li> <!-- Test the search. Checkbox to choose which index to search from. -->
            </ul>
        </nav>

        <div class="content">
            <c:if test="${not empty requestScope.messages.messages}">
                <div class="message box">
                    <c:forEach items="${requestScope.messages.messages}" var="message">
                        <div class="${message.level.cssClass}">
                            <c:out value="${message.message}"/>

                            <c:if test="${not empty message.exception}">
                                <div class="exception hover">
                                    <span class="clickable">
                                        <%-- Display the exception class name and message (not all exception have a "message") --%>
                                        <c:out value="${message.exception.getClass().name}"/>
                                        <c:if test="${not empty message.exception.message}">
                                            - <c:out value="${message.exception.message}"/>
                                        </c:if>
                                    </span>
                                    <ul class="stacktrace collapsible">
                                        <c:forEach var="stacktraceElement" items="${message.exception.stackTrace}">
                                            <li>
                                                <c:out value="${stacktraceElement}"/>
                                            </li>
                                        </c:forEach>
                                    </ul>
                                </div>
                            </c:if>

                        </div>
                    </c:forEach>
                </div>
            </c:if>
