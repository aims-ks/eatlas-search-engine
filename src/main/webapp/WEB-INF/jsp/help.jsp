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

    <div class="box" id="es-diagnosis">
        <h2>Elastic Search - Diagnosis and solutions</h2>

        <p>
            If Elastic Search health status is <span class="status-yellow">Yellow</span> or
            <span class="status-red">Red</span>, it needs to be fixed.
        </p>

        <h3>Diagnose the problem</h3>
        <ul>
            <li>Check Elastic Search health status (see <a href="#es-api">Elastic Search - API</a>)</li>
            <li>Check Elastic Search logs for more details:
                <code>docker logs -f eatlas-searchengine</code>
            </li>
        </ul>

        <h3>Disk</h3>
        <p>
            If you see warnings about <code>disk watermark</code> in Elastic Search logs,
            check available disk space with the bash command <code>df -h</code>
            and compare with disk watermark settings (see <a href="#es-api">Elastic Search - API</a>).
            If the available disk space (in percentage) has reach the "low" or "high" watermark threshold:
        </p>
        <ul>
            <li>Free some disk space.</li>
            <li>Adjust Elastic Search disk watermark settings (see <a href="#es-api">Elastic Search - API</a>).</li>
        </ul>

        <h3>Unassigned shards</h3>
        <p>
            If the Health status shows unassigned shards.<br/>
            Example:<br/>
            <code>"unassigned_shards" : 2</code><br/>
            Try to understand why the shards are unassigned.
            The most likely explanation is because some search indices have
            too many shards and replica.
            The number of shards + replica in an index should never exceed the number of node.<br/>
            Example:<br/>
            <code>
            "number_of_nodes" : 1,<br/>
            "number_of_shards" : 2,<br/>
            "number_of_replicas" : 1,
            </code><br/>
            The number of shards + replica = 3. Number of nodes = 1. The shards + replica needs to be reduced to 1.<br/>
            Example:<br/>
            <code>
            "number_of_nodes" : 1,<br/>
            "number_of_shards" : 1,<br/>
            "number_of_replicas" : 0,
            </code>
        </p>
        <ul>
            <li>You can manually fix the index
                using the <a href="#es-api">Elastic Search API</a>.</li>
            <li>Something probably went wrong during the creation of the index.
                Fix the index creation code in the Search Engine
                (see <code>src/main/java/au/gov/aims/eatlas/searchengine/client/ESClient.java</code>).</li>
        </ul>

    </div>

    <div class="box" id="es-green-status">
        <h2>Elastic Search - <span class="status-green">Green status</span></h2>

        <p>
            Elasticsearch is in optimal condition and operating efficiently.
        </p>
    </div>

    <div class="box" id="es-yellow-status">
        <h2>Elastic Search - <span class="status-yellow">Yellow status</span></h2>

        <p>
            If Elastic Search health status changes to <span class="status-yellow">Yellow</span>,
            something is going wrong.
            The problem needs to be fixed before the health status changes
            to <span class="status-red">Red</span>.
        </p>
    </div>

    <div class="box" id="es-red-status">
        <h2>Elastic Search - <span class="status-red">Red status</span></h2>

        <p>
            When Elastic Search health status changes to <span class="status-red">Red</span>,
            Elastic search becomes readonly.
        </p>
    </div>

    <div class="box" id="es-api">
        <h2>Elastic Search - API</h2>

        <h3>Health status</h3>
        <div>
            <pre>curl "${it.elasticSearchUrl}/_cluster/health?level=indices&pretty"</pre>
        </div>

        <h3>Cluster allocation explanation</h3>
        <div>
            <pre>curl "${it.elasticSearchUrl}/_cluster/allocation/explain?pretty"</pre>
        </div>

        <h3>View disk watermark settings</h3>
        <div>
            <pre>curl "${it.elasticSearchUrl}/_cluster/settings?pretty"</pre>
            <p>
                If the settings are empty, the default settings applies:
            </p>
            <pre>{
  "persistent": {
    "cluster.routing.allocation.disk.watermark.low": "80%",
    "cluster.routing.allocation.disk.watermark.high": "85%",
    "cluster.routing.allocation.disk.watermark.flood_stage": "90%"
  },
  "transient" : { }
}</pre>
        </div>

        <h3>Update disk watermark settings</h3>
        <p>
            To update the settings permanently, update the <code>persistent</code> object.
            Use the <code>transient</code> object for temporary settings
            that will last until the next server reboot.
        </p>
        <div>
            <pre>curl -X PUT "${it.elasticSearchUrl}/_cluster/settings" -H 'Content-Type: application/json' -d'
{
  "persistent": {
    "cluster.routing.allocation.disk.watermark.low": "95%",
    "cluster.routing.allocation.disk.watermark.high": "97%",
    "cluster.routing.allocation.disk.watermark.flood_stage": "99%"
  }
}'</pre>
        </div>

        <h3>Update index settings</h3>
        <div>
            <p>
                <strong>NOTE</strong>: This following examples are for the index <code>drupal_article</code>.
                Change <code>drupal_article</code> for your index name before executing the command.
            </p>
            <p>
                Set the number of replica to 0:
            </p>
            <pre>curl -X PUT "${it.elasticSearchUrl}/drupal_article/_settings" -H "Content-Type: application/json" -d'
{
    "index" : {
        "number_of_replicas" : 0
    }
}'</pre>

            <p>
                Set the number of shards to 1:
            </p>
            <pre>curl -X PUT "${it.elasticSearchUrl}/drupal_article/_settings" -H "Content-Type: application/json" -d'
{
    "index" : {
        "number_of_shards" : 1
    }
}'</pre>
        </div>
    </div>

    <c:import url="include/footer.jsp"/>
</body>

</html>
