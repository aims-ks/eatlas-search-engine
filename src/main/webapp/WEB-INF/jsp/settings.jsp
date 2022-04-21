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

    <form method="post">
        <div class="box">
            <h2>ElasticSearch settings</h2>

            <div class="field">
                <label for="imageCacheDirectory">
                    <span class="label">Image cache directory</span>
                    <input type="text"
                        id="imageCacheDirectory"
                        name="imageCacheDirectory"
                        required="required"
                        value="<c:out value="${it.config.imageCacheDirectory}" />" />
                </label>
                <div class="desc">Folder path on the server used by the search engine to save cached and generated thumbnails.</div>
                <div class="desc">Example: /var/lib/tomcat9/conf/Catalina/data/eatlas-search-engine/</div>
            </div>

            <div class="field">
                <label for="globalThumbnailTTL">
                    <span class="label">Default thumbnail TTL (in days)</span>
                    <input type="number"
                        id="globalThumbnailTTL"
                        name="globalThumbnailTTL"
                        min="0"
                        value="<c:out value="${it.config.globalThumbnailTTL}" />" />
                </label>
                <div class="desc">Time to wait before re-downloading the thumbnail. Can be overwritten in indexer settings.</div>
                <div class="desc">Default: 30 days</div>
            </div>

            <div class="field">
                <label for="globalBrokenThumbnailTTL">
                    <span class="label">Default broken thumbnail TTL (in days)</span>
                    <input type="number"
                        id="globalBrokenThumbnailTTL"
                        name="globalBrokenThumbnailTTL"
                        min="0"
                        value="<c:out value="${it.config.globalBrokenThumbnailTTL}" />" />
                </label>
                <div class="desc">Time to wait before re-attempting to download thumbnail which previously failed. Can be overwritten in indexer settings.</div>
                <div class="desc">Default: 0 days</div>
            </div>

            <p>TODO Server settings: ElasticSearch URL, port, etc</p>
        </div>

        <div class="box">
            <h2>Indexes settings</h2>

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

                            <div class="field">
                                <label for="${indexer.index}_enabled">
                                    <span class="label">Indexation</span>
                                    <input type="checkbox"
                                        id="${indexer.index}_enabled"
                                        name="${indexer.index}_enabled"
                                        ${indexer.enabled ? "checked=\"checked\"" : ""} /> Enabled
                                </label>
                                <div class="desc">Check to enable automatic indexation from the cron.</div>
                            </div>

                            <div class="field">
                                <label for="${indexer.index}_index">
                                    <span class="label">Index</span>
                                    <input type="text"
                                        id="${indexer.index}_index"
                                        name="${indexer.index}_index"
                                        pattern="[a-zA-Z0-9_-]+"
                                        required="required"
                                        value="<c:out value="${indexer.index}" />" />
                                </label>
                                <div class="desc">Index ID. This is the value that needs to be copied in Drupal search settings to search from this index.</div>
                                <div class="desc">Valid characters: "a-z", "A-Z", "0-9", "_" and "-"</div>
                                <div class="desc">Example: eatlas_article</div>
                            </div>

                            <div class="field">
                                <label for="${indexer.index}_thumbnailTTL">
                                    <span class="label">Thumbnail TTL (in days)</span>
                                    <input type="number"
                                        id="${indexer.index}_thumbnailTTL"
                                        name="${indexer.index}_thumbnailTTL"
                                        min="0"
                                        value="<c:out value="${indexer.thumbnailTTL}" />" />
                                </label>
                                <div class="desc">Time to wait before re-downloading the thumbnail.</div>
                            </div>

                            <div class="field">
                                <label for="${indexer.index}_brokenThumbnailTTL">
                                    <span class="label">Broken thumbnail TTL (in days)</span>
                                    <input type="number"
                                        id="${indexer.index}_brokenThumbnailTTL"
                                        name="${indexer.index}_brokenThumbnailTTL"
                                        min="0"
                                        value="<c:out value="${indexer.brokenThumbnailTTL}" default="" />" />
                                </label>
                                <div class="desc">Time to wait before re-attempting to download thumbnail which previously failed.</div>
                            </div>

                            <!--
                                Output the forms for each index type.
                                Switch to the right one using JS when the dropdown value changes.
                            -->

                            <c:choose>
                                <c:when test="${indexer.type == 'DrupalNodeIndexer'}">
                                    <h3>DrupalNodeIndexer fields</h3>

                                    <div class="DrupalNodeIndexer_form">
                                        <div class="field">
                                            <label for="${indexer.index}_drupalUrl">
                                                <span class="label">Drupal URL</span>
                                                <input type="text"
                                                    id="${indexer.index}_drupalUrl"
                                                    name="${indexer.index}_drupalUrl"
                                                    required="required"
                                                    value="<c:out value="${indexer.drupalUrl}" />" />
                                            </label>
                                        </div>

                                        <div class="field">
                                            <label for="${indexer.index}_drupalVersion">
                                                <span class="label">Drupal version</span>
                                                <input type="text"
                                                    id="${indexer.index}_drupalVersion"
                                                    name="${indexer.index}_drupalVersion"
                                                    value="<c:out value="${indexer.drupalVersion}" />" />
                                            </label>
                                        </div>

                                        <div class="field">
                                            <label for="${indexer.index}_drupalNodeType">
                                                <span class="label">Drupal node type</span>
                                                <input type="text"
                                                    id="${indexer.index}_drupalNodeType"
                                                    name="${indexer.index}_drupalNodeType"
                                                    required="required"
                                                    value="<c:out value="${indexer.drupalNodeType}" />" />
                                            </label>
                                        </div>

                                        <div class="field">
                                            <label for="${indexer.index}_drupalPreviewImageField">
                                                <span class="label">Drupal preview image field ID</span>
                                                <input type="text"
                                                    id="${indexer.index}_drupalPreviewImageField"
                                                    name="${indexer.index}_drupalPreviewImageField"
                                                    value="<c:out value="${indexer.drupalPreviewImageField}" />" />
                                            </label>
                                        </div>
                                    </div>
                                </c:when>

                                <c:when test="${indexer.type == 'DrupalMediaIndexer'}">
                                    <h3>DrupalMediaIndexer fields</h3>

                                    <div class="DrupalMediaIndexer_form">
                                        <div class="field">
                                            <label for="${indexer.index}_drupalUrl">
                                                <span class="label">Drupal URL</span>
                                                <input type="text"
                                                    id="${indexer.index}_drupalUrl"
                                                    name="${indexer.index}_drupalUrl"
                                                    required="required"
                                                    value="<c:out value="${indexer.drupalUrl}" />" />
                                            </label>
                                        </div>

                                        <div class="field">
                                            <label for="${indexer.index}_drupalVersion">
                                                <span class="label">Drupal version</span>
                                                <input type="text"
                                                    id="${indexer.index}_drupalVersion"
                                                    name="${indexer.index}_drupalVersion"
                                                    value="<c:out value="${indexer.drupalVersion}" />" />
                                            </label>
                                        </div>

                                        <div class="field">
                                            <label for="${indexer.index}_drupalMediaType">
                                                <span class="label">Drupal media type</span>
                                                <input type="text"
                                                    id="${indexer.index}_drupalMediaType"
                                                    name="${indexer.index}_drupalMediaType"
                                                    required="required"
                                                    value="<c:out value="${indexer.drupalMediaType}" />" />
                                            </label>
                                        </div>

                                        <div class="field">
                                            <label for="${indexer.index}_drupalPreviewImageField">
                                                <span class="label">Drupal preview image field ID</span>
                                                <input type="text"
                                                    id="${indexer.index}_drupalPreviewImageField"
                                                    name="${indexer.index}_drupalPreviewImageField"
                                                    value="<c:out value="${indexer.drupalPreviewImageField}" />" />
                                            </label>
                                        </div>

                                        <div class="field">
                                            <label for="${indexer.index}_drupalTitleField">
                                                <span class="label">Drupal title field ID</span>
                                                <input type="text"
                                                    id="${indexer.index}_drupalTitleField"
                                                    name="${indexer.index}_drupalTitleField"
                                                    value="<c:out value="${indexer.drupalTitleField}" />" />
                                            </label>
                                        </div>

                                        <div class="field">
                                            <label for="${indexer.index}_drupalDescriptionField">
                                                <span class="label">Drupal description field ID</span>
                                                <input type="text"
                                                    id="${indexer.index}_drupalDescriptionField"
                                                    name="${indexer.index}_drupalDescriptionField"
                                                    value="<c:out value="${indexer.drupalDescriptionField}" />" />
                                            </label>
                                        </div>
                                    </div>
                                </c:when>

                                <c:when test="${indexer.type == 'DrupalExternalLinkNodeIndexer'}">
                                    <h3>DrupalExternalLinkNodeIndexer fields</h3>

                                    <div class="DrupalExternalLinkNodeIndexer_form">
                                        <div class="field">
                                            <label for="${indexer.index}_drupalUrl">
                                                <span class="label">Drupal URL</span>
                                                <input type="text"
                                                    id="${indexer.index}_drupalUrl"
                                                    name="${indexer.index}_drupalUrl"
                                                    required="required"
                                                    value="<c:out value="${indexer.drupalUrl}" />" />
                                            </label>
                                        </div>

                                        <div class="field">
                                            <label for="${indexer.index}_drupalVersion">
                                                <span class="label">Drupal version</span>
                                                <input type="text"
                                                    id="${indexer.index}_drupalVersion"
                                                    name="${indexer.index}_drupalVersion"
                                                    value="<c:out value="${indexer.drupalVersion}" />" />
                                            </label>
                                        </div>

                                        <div class="field">
                                            <label for="${indexer.index}_drupalNodeType">
                                                <span class="label">Drupal node type</span>
                                                <input type="text"
                                                    id="${indexer.index}_drupalNodeType"
                                                    name="${indexer.index}_drupalNodeType"
                                                    required="required"
                                                    value="<c:out value="${indexer.drupalNodeType}" />" />
                                            </label>
                                        </div>

                                        <div class="field">
                                            <label for="${indexer.index}_drupalPreviewImageField">
                                                <span class="label">Drupal preview image field ID</span>
                                                <input type="text"
                                                    id="${indexer.index}_drupalPreviewImageField"
                                                    name="${indexer.index}_drupalPreviewImageField"
                                                    value="<c:out value="${indexer.drupalPreviewImageField}" />" />
                                            </label>
                                        </div>

                                        <div class="field">
                                            <label for="${indexer.index}_drupalExternalUrlField">
                                                <span class="label">Drupal external URL field ID</span>
                                                <input type="text"
                                                    id="${indexer.index}_drupalExternalUrlField"
                                                    name="${indexer.index}_drupalExternalUrlField"
                                                    value="<c:out value="${indexer.drupalExternalUrlField}" />" />
                                            </label>
                                        </div>

                                        <div class="field">
                                            <label for="${indexer.index}_drupalContentOverwriteField">
                                                <span class="label">Drupal content overwrite field ID</span>
                                                <input type="text"
                                                    id="${indexer.index}_drupalContentOverwriteField"
                                                    name="${indexer.index}_drupalContentOverwriteField"
                                                    value="<c:out value="${indexer.drupalContentOverwriteField}" />" />
                                            </label>
                                        </div>
                                    </div>
                                </c:when>

                                <c:when test="${indexer.type == 'GeoNetworkIndexer'}">
                                    <h3>GeoNetworkIndexer fields</h3>

                                    <div class="GeoNetworkIndexer_form">
                                        <div class="field">
                                            <label for="${indexer.index}_geoNetworkUrl">
                                                <span class="label">GeoNetwork URL</span>
                                                <input type="text"
                                                    id="${indexer.index}_geoNetworkUrl"
                                                    name="${indexer.index}_geoNetworkUrl"
                                                    required="required"
                                                    value="<c:out value="${indexer.geoNetworkUrl}" />" />
                                            </label>
                                        </div>

                                        <div class="field">
                                            <label for="${indexer.index}_geoNetworkVersion">
                                                <span class="label">GeoNetwork version</span>
                                                <input type="text"
                                                    id="${indexer.index}_geoNetworkVersion"
                                                    name="${indexer.index}_geoNetworkVersion"
                                                    value="<c:out value="${indexer.geoNetworkVersion}" />" />
                                            </label>
                                        </div>
                                    </div>
                                </c:when>

                                <c:when test="${indexer.type == 'AtlasMapperIndexer'}">
                                    <h3>AtlasMapperIndexer fields</h3>

                                    <div class="AtlasMapperIndexer_form">
                                        <div class="field">
                                            <label for="${indexer.index}_atlasMapperClientUrl">
                                                <span class="label">AtlasMapper client URL</span>
                                                <input type="text"
                                                    id="${indexer.index}_atlasMapperClientUrl"
                                                    name="${indexer.index}_atlasMapperClientUrl"
                                                    required="required"
                                                    value="<c:out value="${indexer.atlasMapperClientUrl}" />" />
                                            </label>
                                        </div>

                                        <div class="field">
                                            <label for="${indexer.index}_atlasMapperVersion">
                                                <span class="label">AtlasMapper version</span>
                                                <input type="text"
                                                    id="${indexer.index}_atlasMapperVersion"
                                                    name="${indexer.index}_atlasMapperVersion"
                                                    value="<c:out value="${indexer.atlasMapperVersion}" />" />
                                            </label>
                                        </div>
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
        </div>

        <div class="box">
            <div class="submit">
                <%--
                    TODO Trigger a modal dialog asking for a commit message (buttons: [Cancel], [Commit]).
                        If [Commit] is pressed, config is saved and committed.
                        If [Cancel] is pressed, changes are not saved (Warning message saying "Changes not saved").
                --%>
                <button class="save" title="save">Commit changes</button>
            </div>
        </div>

    </form>

    <jsp:include page="include/footer.jsp" />
</body>

</html>
