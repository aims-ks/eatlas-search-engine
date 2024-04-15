<!DOCTYPE html>
<%@page contentType="text/html"%>
<%@page pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt"%>

<%-- Variables accessible in templates --%>
<c:set var="title" value="Help" scope="request"/>
<c:set var="helpActive" value="active" scope="request"/>
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
        <h2>Elastic Search</h2>

        <h3>Health check</h3>
        <div>
            <pre>$ curl "${it.elasticSearchUrl}/_cluster/health?pretty"</pre>
        </div>

        <h3>View disk watermark settings</h3>
        <div>
            <pre>$ curl "${it.elasticSearchUrl}/_cluster/settings?pretty"</pre>
        </div>

        <h3>Update disk watermark settings</h3>
        <div>
            <pre>$ curl -X PUT "${it.elasticSearchUrl}/_cluster/settings" -H 'Content-Type: application/json' -d'
{
  "persistent": {
    "cluster.routing.allocation.disk.watermark.low": "95%",
    "cluster.routing.allocation.disk.watermark.high": "97%",
    "cluster.routing.allocation.disk.watermark.flood_stage": "99%"
  }
}'</pre>
        </div>

    </div>

    <c:import url="include/footer.jsp"/>
</body>

</html>
