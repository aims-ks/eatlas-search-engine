<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>

    <div class="header">
        <h1>eAtlas Search Engine - ${it.title}</h1>
        <nav>
            <ul class="user-menu">
                <li><a href="<c:url value="/admin/user" />">${it.privateConfig.user.display()}</a></li>
                <li><a href="<c:url value="/public/logout" />">Logout</a></li>
            </ul>
        </nav>
    </div>

    <div class="main">
        <nav class="main-menu">
            <ul class="menu">
                <li class="${it.dashboardActive}"><a href="<c:url value="/admin/" />">Status</a></li> <!-- List indexes in table, each row have: number of doc, edit button (goes to Manage), index button (goes to Index) -->
                <li class="${it.settingsActive}"><a href="<c:url value="/admin/settings" />">Settings</a></li> <!-- Create, edit, delete index -->
                <li class="${it.reindexActive}"><a href="<c:url value="/admin/reindex" />">Reindex</a></li> <!-- Reindex an index or all indexes, with progress bar -->
                <li class="${it.searchActive}"><a href="<c:url value="/admin/search" />">Test search</a></li> <!-- Test the search. Checkbox to choose which index to search from. -->
                <li class="${it.helpActive}"><a href="<c:url value="/admin/help" />">Help</a></li> <!-- Help page. -->
            </ul>
        </nav>

        <div class="content">
            <c:import url="include/logger.jsp"/>
