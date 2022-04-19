<!DOCTYPE html>
<%@page contentType="text/html"%>
<%@page pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt"%>

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
        <h2>ElasticSearch settings</h2>

        <form method="post">
            <input type="hidden" name="formType" value="global" />
            <div>
                <label for="imageCacheDirectory">
                    Image cache directory:
                    <input type="text"
                        id="imageCacheDirectory"
                        name="imageCacheDirectory"
                        value="<c:out value="${it.config.imageCacheDirectory}" />" />
                </label>
            </div>

            <div>
                <label for="globalThumbnailTTL">
                    Default thumbnail TTL (in days):
                    <input type="number"
                        id="globalThumbnailTTL"
                        name="globalThumbnailTTL"
                        min="0"
                        value="<c:out value="${it.config.globalThumbnailTTL}" />" />
                </label>
            </div>

            <div>
                <label for="globalBrokenThumbnailTTL">
                    Default broken thumbnail TTL (in days):
                    <input type="number"
                        id="globalBrokenThumbnailTTL"
                        name="globalBrokenThumbnailTTL"
                        min="0"
                        value="<c:out value="${it.config.globalBrokenThumbnailTTL}" />" />
                </label>
            </div>

            <p>TODO Server settings: ElasticSearch URL, port, etc</p>

            <div class="submit">
                <button class="save" title="save">Save</button>
            </div>
        </form>
    </div>

    <div class="box">
        <h2>Indexes settings</h2>

        <p>TODO Index settings: List all indexes, with all fields in text boxes. Hide / Show with CSS hover, add JS to show on click</p>

        <form method="post">
            <input type="hidden" name="formType" value="indexes" />

            <table>
                <tr class="table-header">
                    <th>Index</th>
                    <th>Type</th>
                    <th>Document count</th>
                    <th>Last indexed</th>
                    <th>Last runtime</th>
                    <th>Actions</th>
                </tr>

                <c:forEach items="${it.config.indexers}" var="indexer" varStatus="loopStatus">
                    <c:set var="cssClass" value="${(loopStatus.index+1) % 2 == 0 ? 'even' : 'odd'}"/>
                    <tr class="${cssClass}">
                        <td><c:out value="${indexer.index}" /></td>
                        <td><c:out value="${indexer.type}" /></td>
                        <td class="number">${indexer.state.count}</td>
                        <td class="date"><fmt:formatDate value="${indexer.state.lastIndexedDate}" pattern="dd/MM/yyyy HH:mm"/></td>
                        <td class="number">${indexer.state.lastIndexRuntimeFormatted}</td>
                        <td class="buttons">
                            <button type="button" class="edit editFormButton" id="${indexer.index}" title="Edit">Edit</button>
                            <button type="button" class="delete" title="Delete">Delete</button>
                        </td>
                    </tr>
                    <tr id="formRow_${indexer.index}" class="${cssClass}">
                        <td colspan="6">
                            <h3>Generic fields</h3>

                            <div>
                                <label for="${indexer.index}_enabled">
                                    <input type="checkbox"
                                        id="${indexer.index}_enabled"
                                        name="${indexer.index}_enabled"
                                        ${indexer.enabled ? "checked=\"checked\"" : ""} /> Enabled
                                </label>
                            </div>

                            <div>
                                <label for="${indexer.index}_thumbnailTTL">
                                    Thumbnail TTL (in days)
                                    <input type="number"
                                        id="${indexer.index}_thumbnailTTL"
                                        name="${indexer.index}_thumbnailTTL"
                                        min="0"
                                        value="<c:out value="${indexer.thumbnailTTL}" />" />
                                </label>
                            </div>

                            <div>
                                <label for="${indexer.index}_brokenThumbnailTTL">
                                    Broken thumbnail TTL (in days)
                                    <input type="number"
                                        id="${indexer.index}_brokenThumbnailTTL"
                                        name="${indexer.index}_brokenThumbnailTTL"
                                        min="0"
                                        value="<c:out value="${indexer.brokenThumbnailTTL}" default="" />" />
                                </label>
                            </div>

                            <!--
                                Output the forms for each index type.
                                Switch to the right one using JS when the dropdown value changes.
                            -->

                            <c:choose>
                                <c:when test="${indexer.type == 'DrupalNodeIndexer'}">
                                    <h3>DrupalNodeIndexer fields</h3>

                                    <div class="DrupalNodeIndexer_form">
                                        private String drupalUrl;
                                        private String drupalVersion;
                                        private String drupalNodeType;
                                        private String drupalPreviewImageField;
                                    </div>
                                </c:when>

                                <c:when test="${indexer.type == 'DrupalMediaIndexer'}">
                                    <h3>DrupalMediaIndexer fields</h3>

                                    <div class="DrupalMediaIndexer_form">
                                        TODO

                                        private String drupalUrl;
                                        private String drupalVersion;
                                        private String drupalMediaType;
                                    </div>
                                </c:when>

                                <c:when test="${indexer.type == 'ExternalLinkIndexer'}">
                                    <h3>ExternalLinkIndexer fields</h3>

                                    <div class="ExternalLinkIndexer_form">
                                        private List[ExternalLinkEntry] externalLinkEntries;
                                    </div>
                                </c:when>

                                <c:when test="${indexer.type == 'GeoNetworkIndexer'}">
                                    <h3>GeoNetworkIndexer fields</h3>

                                    <div class="GeoNetworkIndexer_form">
                                        private String geoNetworkUrl;
                                        private String geoNetworkVersion;
                                    </div>
                                </c:when>

                                <c:when test="${indexer.type == 'AtlasMapperIndexer'}">
                                    <h3>AtlasMapperIndexer fields</h3>

                                    <div class="AtlasMapperIndexer_form">
                                        private String atlasMapperClientUrl;
                                        private String atlasMapperVersion;
                                    </div>
                                </c:when>
                            </c:choose>
                        </td>
                    </tr>
                </c:forEach>
            </table>

            <div>
                <button type="button" class="add" title="Add an index">Add an index</button>
            </div>

            <div class="submit">
                <button class="save" title="save">Save</button>
            </div>
        </form>
    </div>

    <jsp:include page="include/footer.jsp" />
</body>

</html>
