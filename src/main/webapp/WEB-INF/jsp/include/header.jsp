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
                <li class="${requestScope.dashboardActive}"><a href="${pageContext.servletContext.contextPath}/admin/">Overview</a></li> <!-- List indexes in table, each row have: number of doc, edit button (goes to Manage), index button (goes to Index) -->
                <li class="${requestScope.manageActive}"><a href="${pageContext.servletContext.contextPath}/admin/manage">Manage</a></li> <!-- Create, edit, delete index -->
                <li class="${requestScope.reindexActive}"><a href="${pageContext.servletContext.contextPath}/admin/reindex">Reindex</a></li> <!-- Re-index an index or all indexes, with progress bar -->
                <li class="${requestScope.searchActive}"><a href="${pageContext.servletContext.contextPath}/admin/search">Search</a></li> <!-- Test the search. Checkbox to choose which index to search from. -->
            </ul>
        </nav>

        <div class="content">
            <c:if test="${not empty requestScope.messages.messages}">
                <div class="message box">
                    <c:forEach items="${requestScope.messages.messages}" var="message">
                        <div class="${message.level.cssClass}">${message.message}</div>
                    </c:forEach>
                </div>
            </c:if>
