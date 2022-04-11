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
    <link rel="stylesheet" href="${pageContext.servletContext.contextPath}/css/admin.css">
</head>

<body>
    <c:import url="include/header.jsp"/>

    <div class="box">
        <h2>Search</h2>
    </div>

    <jsp:include page="include/footer.jsp" />
</body>

</html>
