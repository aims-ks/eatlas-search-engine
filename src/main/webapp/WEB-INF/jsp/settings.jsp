<!DOCTYPE html>
<%@page contentType="text/html"%>
<%@page pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>

<%-- Variables accessible in templates --%>
<c:set var="title" value="Settings" scope="request"/>
<c:set var="settingsActive" value="active" scope="request"/>
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
        <h2>Settings</h2>

        <p>Server settings: ElasticSearch URL, port, etc</p>

        <p>Index settings: List all indexes, with all fields in text boxes. Hide / Show with CSS hover, add JS to show on click</p>
    </div>

    <jsp:include page="include/footer.jsp" />
</body>

</html>
