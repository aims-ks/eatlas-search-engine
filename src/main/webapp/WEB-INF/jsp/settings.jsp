<!DOCTYPE html>
<%@page contentType="text/html"%>
<%@page pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt"%>

<c:set var="baseURL" value="${pageContext.request.scheme}://${pageContext.request.localName}:${pageContext.request.localPort}" />

<%-- Variables accessible in templates --%>
<c:set var="title" value="Settings" scope="request"/>
<c:set var="settingsActive" value="active" scope="request"/>
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

    <form method="post">
        <div class="box">
            <h2>ElasticSearch settings</h2>

            <div class="field">
                <label for="elasticSearchUrls">
                    <span class="label required">Elastic Search URLs</span>
                    <div id="elasticSearchUrls" class="multiple-text">
                        <c:forEach items="${it.config.elasticSearchUrls}" var="elasticSearchUrl" varStatus="loopStatus">
                            <%-- "data-lpignore" is used to prevent LastPass filling the field. --%>
                            <input type="text"
                                name="elasticSearchUrl"
                                data-lpignore="true"
                                value="<c:out value="${elasticSearchUrl}" />" />
                        </c:forEach>
                        <!-- To add a URL -->
                        <input type="text"
                            name="elasticSearchUrl"
                            data-lpignore="true"
                            value="" />
                    </div>
                </label>
                <div class="desc">URLs to the Elastic Search server.</div>
                <div class="desc">Example: http://localhost:9200, http://localhost:9300</div>
            </div>

            <div class="field">
                <label for="imageCacheDirectory">
                    <span class="label required">Image cache directory</span>
                    <input type="text"
                        id="imageCacheDirectory"
                        name="imageCacheDirectory"
                        data-lpignore="true"
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
                        data-lpignore="true"
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
                        data-lpignore="true"
                        min="0"
                        value="<c:out value="${it.config.globalBrokenThumbnailTTL}" />" />
                </label>
                <div class="desc">Time to wait before re-attempting to download thumbnail which previously failed. Can be overwritten in indexer settings.</div>
                <div class="desc">Default: 0 days</div>
            </div>

            <div class="field">
                <label for="reindexToken">
                    <span class="label required">Reindex token</span>
                    <input type="text"
                        id="reindexToken"
                        name="reindexToken"
                        data-lpignore="true"
                        required="required"
                        value="<c:out value="${it.config.reindexToken}" />" />
                </label>
                <div class="desc">Token used to call the re-indexation API from the cron.</div>
                <div class="desc">
                    Test re-indexation URL:
                    ${baseURL}<c:url value="/public/index/v1/reindex">
                        <c:param name="full" value="false" />
                        <c:param name="token" value="${it.config.reindexToken}" />
                    </c:url>
                </div>
            </div>
        </div>

        <div class="box">
            <h2>Indexes settings</h2>

            <table>
                <tr class="table-header">
                    <th>Index</th>
                    <th>Indexer</th>
                    <th>Type</th>
                    <th>Document count</th>
                    <th>Last indexed</th>
                    <th>Last runtime</th>
                    <th>Actions</th>
                </tr>

                <c:forEach items="${it.config.indexers}" var="indexer" varStatus="loopStatus">
                    <c:set var="cssClass" value="${(loopStatus.index+1) % 2 == 0 ? 'even' : 'odd'} ${indexer.validate() ? 'valid' : 'invalid'}"/>
                    <tr class="${cssClass}">
                        <td><c:out value="${indexer.index}" /></td>
                        <td class="${indexer.enabled ? "enabled" : "disabled"}">
                            ${indexer.enabled ? "Enabled" : "Disabled"}
                        </td>
                        <td><c:out value="${indexer.type}" /></td>
                        <td class="number">${indexer.state.count}</td>
                        <td class="date"><fmt:formatDate value="${indexer.state.lastIndexedDate}" pattern="dd/MM/yyyy HH:mm"/></td>
                        <td class="number">${indexer.state.lastIndexRuntimeFormatted}</td>
                        <td class="buttons" id="${indexer.index}">
                            <button type="button" class="edit editFormButton" title="Edit" ${indexer.validate() ? '' : 'disabled="disabled"'}>Edit</button>

                            <!-- Dummy button used for form submission using the Enter button -->
                            <button class="hiddenSubmitButton" name="save-button" value="save" title="save">Save</button>

                            <button type="submit"
                                class="delete"
                                name="delete-button"
                                value="${indexer.index}"
                                <%-- Do not validate the edit form (hidden, bellow) when the delete button is pressed. --%>
                                formnovalidate="formnovalidate"
                                onClick="return window.confirm('Are you sure you want to delete the index: <c:out value="${indexer.index}" />?');"
                                title="Delete">Delete</button>
                        </td>
                    </tr>
                    <tr id="formRow_${indexer.index}" class="${cssClass}">
                        <td colspan="7">
                            <h3>Generic fields</h3>

                            <div class="field">
                                <label for="${indexer.index}_enabled">
                                    <span class="label">Indexer</span>
                                    <input type="checkbox"
                                        id="${indexer.index}_enabled"
                                        name="${indexer.index}_enabled"
                                        ${indexer.enabled ? "checked=\"checked\"" : ""} /> Enabled
                                </label>
                                <div class="desc">Check to enable automatic indexation from the cron.</div>
                                <div class="desc">The buttons "Re-index all" and "Index latest all" from the Reindex tab only process enabled indexes.</div>
                            </div>

                            <div class="field">
                                <label for="${indexer.index}_index">
                                    <span class="label required">Index</span>
                                    <input type="text"
                                        id="${indexer.index}_index"
                                        name="${indexer.index}_index"
                                        data-lpignore="true"
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
                                        data-lpignore="true"
                                        min="0"
                                        value="<c:out value="${indexer.thumbnailTTL}" />" />
                                </label>
                                <div class="desc">Time to wait before re-downloading the thumbnail.</div>
                                <div class="desc">Default: Default thumbnail TTL, defined above</div>
                            </div>

                            <div class="field">
                                <label for="${indexer.index}_brokenThumbnailTTL">
                                    <span class="label">Broken thumbnail TTL (in days)</span>
                                    <input type="number"
                                        id="${indexer.index}_brokenThumbnailTTL"
                                        name="${indexer.index}_brokenThumbnailTTL"
                                        data-lpignore="true"
                                        min="0"
                                        value="<c:out value="${indexer.brokenThumbnailTTL}" default="" />" />
                                </label>
                                <div class="desc">Time to wait before re-attempting to download thumbnail which previously failed.</div>
                                <div class="desc">Default: Default broken thumbnail TTL, defined above</div>
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
                                                <span class="label required">Drupal URL</span>
                                                <input type="text"
                                                    id="${indexer.index}_drupalUrl"
                                                    name="${indexer.index}_drupalUrl"
                                                    data-lpignore="true"
                                                    required="required"
                                                    value="<c:out value="${indexer.drupalUrl}" />" />
                                            </label>
                                            <div class="desc">Drupal base URL, used for API calls.</div>
                                            <div class="desc">Example: https://eatlas.org.au</div>
                                        </div>

                                        <div class="field">
                                            <label for="${indexer.index}_drupalVersion">
                                                <span class="label">Drupal version</span>
                                                <input type="text"
                                                    id="${indexer.index}_drupalVersion"
                                                    name="${indexer.index}_drupalVersion"
                                                    data-lpignore="true"
                                                    value="<c:out value="${indexer.drupalVersion}" />" />
                                            </label>
                                            <div class="desc">Version of Drupal, to use the proper API version.</div>
                                        </div>

                                        <div class="field">
                                            <label for="${indexer.index}_drupalNodeType">
                                                <span class="label required">Drupal node type</span>
                                                <input type="text"
                                                    id="${indexer.index}_drupalNodeType"
                                                    name="${indexer.index}_drupalNodeType"
                                                    data-lpignore="true"
                                                    required="required"
                                                    value="<c:out value="${indexer.drupalNodeType}" />" />
                                            </label>
                                            <div class="desc">Type of node indexed by this indexer.</div>
                                            <div class="desc">Example: article</div>
                                        </div>

                                        <div class="field">
                                            <label for="${indexer.index}_drupalPreviewImageField">
                                                <span class="label">Drupal preview image field ID</span>
                                                <input type="text"
                                                    id="${indexer.index}_drupalPreviewImageField"
                                                    name="${indexer.index}_drupalPreviewImageField"
                                                    data-lpignore="true"
                                                    value="<c:out value="${indexer.drupalPreviewImageField}" />" />
                                            </label>
                                            <div class="desc">Drupal internal field ID for the preview image.</div>
                                            <div class="desc">Drupal field type: Image</div>
                                            <div class="desc">Example: field_image</div>
                                        </div>
                                    </div>
                                </c:when>

                                <c:when test="${indexer.type == 'DrupalMediaIndexer'}">
                                    <h3>DrupalMediaIndexer fields</h3>

                                    <div class="DrupalMediaIndexer_form">
                                        <div class="field">
                                            <label for="${indexer.index}_drupalUrl">
                                                <span class="label required">Drupal URL</span>
                                                <input type="text"
                                                    id="${indexer.index}_drupalUrl"
                                                    name="${indexer.index}_drupalUrl"
                                                    data-lpignore="true"
                                                    required="required"
                                                    value="<c:out value="${indexer.drupalUrl}" />" />
                                            </label>
                                            <div class="desc">Drupal base URL, used for API calls.</div>
                                            <div class="desc">Example: https://eatlas.org.au</div>
                                        </div>

                                        <div class="field">
                                            <label for="${indexer.index}_drupalVersion">
                                                <span class="label">Drupal version</span>
                                                <input type="text"
                                                    id="${indexer.index}_drupalVersion"
                                                    name="${indexer.index}_drupalVersion"
                                                    data-lpignore="true"
                                                    value="<c:out value="${indexer.drupalVersion}" />" />
                                            </label>
                                            <div class="desc">Version of Drupal, to use the proper API version.</div>
                                        </div>

                                        <div class="field">
                                            <label for="${indexer.index}_drupalMediaType">
                                                <span class="label required">Drupal media type</span>
                                                <input type="text"
                                                    id="${indexer.index}_drupalMediaType"
                                                    name="${indexer.index}_drupalMediaType"
                                                    data-lpignore="true"
                                                    required="required"
                                                    value="<c:out value="${indexer.drupalMediaType}" />" />
                                            </label>
                                            <div class="desc">Type of media indexed by this indexer.</div>
                                            <div class="desc">Example: image</div>
                                        </div>

                                        <div class="field">
                                            <label for="${indexer.index}_drupalPreviewImageField">
                                                <span class="label">Drupal preview image field ID</span>
                                                <input type="text"
                                                    id="${indexer.index}_drupalPreviewImageField"
                                                    name="${indexer.index}_drupalPreviewImageField"
                                                    data-lpignore="true"
                                                    value="<c:out value="${indexer.drupalPreviewImageField}" />" />
                                            </label>
                                            <div class="desc">Drupal internal field ID for the preview image.</div>
                                            <div class="desc">Drupal field type: Image</div>
                                            <div class="desc">Example: thumbnail</div>
                                        </div>

                                        <div class="field">
                                            <label for="${indexer.index}_drupalTitleField">
                                                <span class="label">Drupal title field ID</span>
                                                <input type="text"
                                                    id="${indexer.index}_drupalTitleField"
                                                    name="${indexer.index}_drupalTitleField"
                                                    data-lpignore="true"
                                                    value="<c:out value="${indexer.drupalTitleField}" />" />
                                            </label>
                                            <div class="desc">Drupal internal field ID for the media's title.</div>
                                            <div class="desc">Drupal field type: Text (plain)</div>
                                            <div class="desc">Example: field_title</div>
                                        </div>

                                        <div class="field">
                                            <label for="${indexer.index}_drupalDescriptionField">
                                                <span class="label">Drupal description field ID</span>
                                                <input type="text"
                                                    id="${indexer.index}_drupalDescriptionField"
                                                    name="${indexer.index}_drupalDescriptionField"
                                                    data-lpignore="true"
                                                    value="<c:out value="${indexer.drupalDescriptionField}" />" />
                                            </label>
                                            <div class="desc">Drupal internal field ID for the media's description.</div>
                                            <div class="desc">Drupal field type: Text (formatted, long)</div>
                                            <div class="desc">Example: field_description</div>
                                        </div>
                                    </div>
                                </c:when>

                                <c:when test="${indexer.type == 'DrupalExternalLinkNodeIndexer'}">
                                    <h3>DrupalExternalLinkNodeIndexer fields</h3>

                                    <div class="DrupalExternalLinkNodeIndexer_form">
                                        <div class="field">
                                            <label for="${indexer.index}_drupalUrl">
                                                <span class="label required">Drupal URL</span>
                                                <input type="text"
                                                    id="${indexer.index}_drupalUrl"
                                                    name="${indexer.index}_drupalUrl"
                                                    data-lpignore="true"
                                                    required="required"
                                                    value="<c:out value="${indexer.drupalUrl}" />" />
                                            </label>
                                            <div class="desc">Drupal base URL, used for API calls.</div>
                                            <div class="desc">Example: https://eatlas.org.au</div>
                                        </div>

                                        <div class="field">
                                            <label for="${indexer.index}_drupalVersion">
                                                <span class="label">Drupal version</span>
                                                <input type="text"
                                                    id="${indexer.index}_drupalVersion"
                                                    name="${indexer.index}_drupalVersion"
                                                    data-lpignore="true"
                                                    value="<c:out value="${indexer.drupalVersion}" />" />
                                            </label>
                                            <div class="desc">Version of Drupal, to use the proper API version.</div>
                                        </div>

                                        <div class="field">
                                            <label for="${indexer.index}_drupalNodeType">
                                                <span class="label required">Drupal node type</span>
                                                <input type="text"
                                                    id="${indexer.index}_drupalNodeType"
                                                    name="${indexer.index}_drupalNodeType"
                                                    data-lpignore="true"
                                                    required="required"
                                                    value="<c:out value="${indexer.drupalNodeType}" />" />
                                            </label>
                                            <div class="desc">Type of node indexed by this indexer.</div>
                                            <div class="desc">Example: external_link</div>
                                        </div>

                                        <div class="field">
                                            <label for="${indexer.index}_drupalPreviewImageField">
                                                <span class="label">Drupal preview image field ID</span>
                                                <input type="text"
                                                    id="${indexer.index}_drupalPreviewImageField"
                                                    name="${indexer.index}_drupalPreviewImageField"
                                                    data-lpignore="true"
                                                    value="<c:out value="${indexer.drupalPreviewImageField}" />" />
                                            </label>
                                            <div class="desc">Drupal internal field ID for the preview image.</div>
                                            <div class="desc">Drupal field type: Image</div>
                                            <div class="desc">Example: field_image</div>
                                        </div>

                                        <div class="field">
                                            <label for="${indexer.index}_drupalExternalUrlField">
                                                <span class="label">Drupal external URL field ID</span>
                                                <input type="text"
                                                    id="${indexer.index}_drupalExternalUrlField"
                                                    name="${indexer.index}_drupalExternalUrlField"
                                                    data-lpignore="true"
                                                    value="<c:out value="${indexer.drupalExternalUrlField}" />" />
                                            </label>
                                            <div class="desc">Drupal internal field ID for the external page's URL.</div>
                                            <div class="desc">Drupal field type: Link</div>
                                            <div class="desc">Example: field_external_link</div>
                                        </div>

                                        <div class="field">
                                            <label for="${indexer.index}_drupalContentOverwriteField">
                                                <span class="label">Drupal content overwrite field ID</span>
                                                <input type="text"
                                                    id="${indexer.index}_drupalContentOverwriteField"
                                                    name="${indexer.index}_drupalContentOverwriteField"
                                                    data-lpignore="true"
                                                    value="<c:out value="${indexer.drupalContentOverwriteField}" />" />
                                            </label>
                                            <div class="desc">Drupal internal field ID for the field used to overwrite the page content.</div>
                                            <div class="desc">This field should only be used when the search engine is unable to download the page content;
                                                because it's created by JavaScript, hidden behind a disclaimer page, requires authentication, etc.</div>
                                            <div class="desc">Drupal field type: Text (plain, long)</div>
                                            <div class="desc">Example: field_content_overwrite</div>
                                        </div>
                                    </div>
                                </c:when>

                                <c:when test="${indexer.type == 'GeoNetworkIndexer'}">
                                    <h3>GeoNetworkIndexer fields</h3>

                                    <div class="GeoNetworkIndexer_form">
                                        <div class="field">
                                            <label for="${indexer.index}_geoNetworkUrl">
                                                <span class="label required">GeoNetwork URL</span>
                                                <input type="text"
                                                    id="${indexer.index}_geoNetworkUrl"
                                                    name="${indexer.index}_geoNetworkUrl"
                                                    data-lpignore="true"
                                                    required="required"
                                                    value="<c:out value="${indexer.geoNetworkUrl}" />" />
                                            </label>
                                            <div class="desc">GeoNetwork base URL, used for API calls.</div>
                                            <div class="desc">Example: https://eatlas.org.au/geonetwork</div>
                                        </div>

                                        <div class="field">
                                            <label for="${indexer.index}_geoNetworkVersion">
                                                <span class="label">GeoNetwork version</span>
                                                <input type="text"
                                                    id="${indexer.index}_geoNetworkVersion"
                                                    name="${indexer.index}_geoNetworkVersion"
                                                    data-lpignore="true"
                                                    value="<c:out value="${indexer.geoNetworkVersion}" />" />
                                            </label>
                                            <div class="desc">Version of GeoNetwork, to use the proper API version.</div>
                                        </div>
                                    </div>
                                </c:when>

                                <c:when test="${indexer.type == 'AtlasMapperIndexer'}">
                                    <h3>AtlasMapperIndexer fields</h3>

                                    <div class="AtlasMapperIndexer_form">
                                        <div class="field">
                                            <label for="${indexer.index}_atlasMapperClientUrl">
                                                <span class="label required">AtlasMapper client URL</span>
                                                <input type="text"
                                                    id="${indexer.index}_atlasMapperClientUrl"
                                                    name="${indexer.index}_atlasMapperClientUrl"
                                                    data-lpignore="true"
                                                    required="required"
                                                    value="<c:out value="${indexer.atlasMapperClientUrl}" />" />
                                            </label>
                                            <div class="desc">AtlasMapper base URL, used to request the list of layer.</div>
                                            <div class="desc">The list of layer must be available at "[atlasMapperClientUrl]/config/main.json"</div>
                                            <div class="desc">Example: https://maps.eatlas.org.au</div>
                                        </div>

                                        <div class="field">
                                            <label for="${indexer.index}_atlasMapperVersion">
                                                <span class="label">AtlasMapper version</span>
                                                <input type="text"
                                                    id="${indexer.index}_atlasMapperVersion"
                                                    name="${indexer.index}_atlasMapperVersion"
                                                    data-lpignore="true"
                                                    value="<c:out value="${indexer.atlasMapperVersion}" />" />
                                            </label>
                                            <div class="desc">Version of AtlasMapper.</div>
                                            <div class="desc">Used to properly parse the configuration.</div>
                                        </div>
                                    </div>
                                </c:when>
                            </c:choose>
                        </td>
                    </tr>
                </c:forEach>
            </table>

            <!-- Dummy button used for form submission using the Enter button -->
            <button class="hiddenSubmitButton" name="save-button" value="save" title="save">Save</button>

            <div>
                <label for="newIndexType" class="new-index">
                    <select id="newIndexType" name="newIndexType">
                        <option value="">-- Choose an index type --</option>
                        <option value="DrupalNodeIndexer">DrupalNodeIndexer</option>
                        <option value="DrupalMediaIndexer">DrupalMediaIndexer</option>
                        <option value="DrupalExternalLinkNodeIndexer">DrupalExternalLinkNodeIndexer</option>
                        <option value="GeoNetworkIndexer">GeoNetworkIndexer</option>
                        <option value="AtlasMapperIndexer">AtlasMapperIndexer</option>
                    </select>

                    <button type="submit"
                        class="add"
                        name="add-index-button"
                        value="addIndex"
                        onClick="validateNotEmpty('newIndexType')"
                        title="Add an index">Add</button>
                </label>
            </div>
        </div>

        <div class="box">
            <div class="submit">
                <button class="save" name="save-button" value="save" title="save">Save</button>

                <%--
                    TODO Trigger a modal dialog asking for a commit message (buttons: [Cancel], [Commit]).
                        If [Commit] is pressed, config is saved and committed.
                        If [Cancel] is pressed, changes are not saved (Warning message saying "Changes not saved").
                --%>
                <button class="commit" name="commit-button" value="commit" title="commit">Commit to GitHub</button>
            </div>
        </div>

    </form>

    <jsp:include page="include/footer.jsp" />
</body>

</html>
