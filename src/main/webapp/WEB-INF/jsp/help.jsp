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
        <h2>Elastic Search - API</h2>

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

    <div class="box" id="es-green-status">
        <h2>Elastic Search - <span class="status-green">Green status</span></h2>

        <p>
            Everything is going well. The Elastic Search engine is running smooth.
        </p>
    </div>

    <div class="box" id="es-yellow-status">
        <h2>Elastic Search - <span class="status-yellow">Yellow status</span></h2>

        <!-- TODO Divide this into sections: disk, etc. How to identify and fix the issue. -->
        <p>
            If Elastic Search health status changes to <span class="status-yellow">Yellow</span>,
            something is going wrong.
            The problem needs to be fixed before the health status changes
            to <span class="status-red">Red</span>.
            Check Elastic Search logs for more details.
            Check Elastic Search health status (see "Elastic Search - API" above).
            Check available disk space with "df -h"
            and compare with disk watermark settings (see "Elastic Search - API" above).
            If the disk percentage has reach "low" watermark,
            free some disk space or adjust Elastic Search settings (see "Elastic Search - API" above)
            before it reach the "high" watermark.
        </p>
    </div>

    <div class="box" id="es-red-status">
        <h2>Elastic Search - <span class="status-red">Red status</span></h2>

        <!-- TODO Divide this into sections: disk, etc. How to identify and fix the issue. -->
        <p>
            When Elastic Search health status changes to <span class="status-red">Red</span>,
            Elastic search becomes readonly.
            Check Elastic Search logs for more details.
            Check Elastic Search health status (see "Elastic Search - API" above).
            Check available disk space with "df -h"
            and compare with disk watermark settings (see "Elastic Search - API" above).
            If the disk percentage has reach "high" watermark,
            free some disk space or adjust Elastic Search settings (see "Elastic Search - API" above)."
        </p>
    </div>

    <c:import url="include/footer.jsp"/>
</body>

</html>
