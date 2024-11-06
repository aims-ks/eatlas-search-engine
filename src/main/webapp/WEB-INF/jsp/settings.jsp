<!DOCTYPE html>
<%@page contentType="text/html"%>
<%@page pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt"%>

<c:choose>
    <c:when test="${not empty header['X-Forwarded-Host']}">
        <c:set var="baseURL" value="${header['X-Forwarded-Proto'] == null ? pageContext.request.scheme : header['X-Forwarded-Proto']}://${header['X-Forwarded-Host']}" />
    </c:when>
    <c:otherwise>
        <c:set var="baseURL" value="${pageContext.request.scheme}://${pageContext.request.serverName}:${pageContext.request.serverPort}" />
    </c:otherwise>
</c:choose>

<c:set var="defaultSearchEngineBaseURL" value="${baseURL}${pageContext.request.contextPath}" />

<html lang="en">
<head>
    <title>eAtlas Search Engine - ${it.title}</title>
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
                    <span id="elasticSearchUrls" class="multiple-text">
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
                    </span>
                </label>
                <div class="desc">URLs to the Elastic Search server.</div>
                <div class="desc"><strong>Example</strong>: <code>http://localhost:9200</code>, <code>http://localhost:9300</code></div>
            </div>

            <div class="field">
                <div class="field-group">
                    <label for="elasticSearchNumberOfShards">
                        <span class="label">Number of shards</span>
                        <input type="text"
                                id="elasticSearchNumberOfShards"
                                name="elasticSearchNumberOfShards"
                                data-lpignore="true"
                                value="<c:out value="${it.config.elasticSearchNumberOfShards}" />" />
                    </label>
                    <label for="elasticSearchNumberOfReplicas">
                        <span class="label">Number of replicas</span>
                        <input type="text"
                                id="elasticSearchNumberOfReplicas"
                                name="elasticSearchNumberOfReplicas"
                                data-lpignore="true"
                                value="<c:out value="${it.config.elasticSearchNumberOfReplicas}" />" />
                    </label>
                </div>
                <div class="desc">Number of shards and replicas. Set to 1 and 0 respectively for running with a <em>single-node</em> server.</div>
                <div class="desc"><strong>NOTE</strong>: Those settings only applies to new index. To apply to existing indices, delete the indices and the search engine will re-create them using the new settings.</div>
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
                <div class="desc"><strong>Example</strong>: <code>/var/lib/tomcat9/conf/Catalina/data/eatlas-search-engine/</code></div>
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
                <div class="desc"><strong>Default</strong>: <code>30</code> days</div>
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
                <div class="desc"><strong>Default</strong>: <code>0</code> days</div>
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
                    <code>${baseURL}<c:url value="/public/index/v1/reindex">
                        <c:param name="full" value="false" />
                        <c:param name="token" value="${it.config.reindexToken}" />
                    </c:url></code>
                </div>
            </div>

            <div class="field">
                <label for="searchEngineBaseUrl">
                    <span class="label required">Search engine base URL</span>
                    <input type="text"
                        id="searchEngineBaseUrl"
                        name="searchEngineBaseUrl"
                        data-lpignore="true"
                        required="required"
                        value="<c:out value="${it.config.searchEngineBaseUrl}" default="${defaultSearchEngineBaseURL}" />" />
                </label>
                <div class="desc">Base URL used to craft URL to preview image in search results.</div>
                <div class="desc"><strong>Default</strong>: <code>${defaultSearchEngineBaseURL}</code></div>
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
                                <div class="desc">The buttons <em>Reindex all</em> and <em>Index latest all</em> from the <em>Reindex</em> tab only process enabled indexes.</div>
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
                                <div class="desc"><strong>Valid characters</strong>: "a-z", "A-Z", "0-9", "_" and "-"</div>
                                <div class="desc"><strong>Example</strong>: <code>eatlas_article</code></div>
                            </div>

                            <div class="field">
                                <label for="${indexer.index}_indexName">
                                    <span class="label required">Index name</span>
                                    <input type="text"
                                        id="${indexer.index}_indexName"
                                        name="${indexer.index}_indexName"
                                        data-lpignore="true"
                                        required="required"
                                        value="<c:out value="${indexer.indexName}" />" />
                                </label>
                                <div class="desc">The display name of the index.</div>
                                <div class="desc"><strong>Example</strong>: <code>eAtlas articles</code></div>
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
                                <div class="desc"><strong>Default</strong>: Default thumbnail TTL, defined above</div>
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
                                <div class="desc"><strong>Default</strong>: Default broken thumbnail TTL, defined above</div>
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
                                            <div class="desc"><strong>Example</strong>: <code>https://eatlas.org.au</code></div>
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
                                                    value="<c:out value="${indexer.drupalBundleId}" />" />
                                            </label>
                                            <div class="desc">Type of node indexed by this indexer.</div>
                                            <div class="desc"><strong>Example</strong>: <code>article</code></div>
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
                                            <div class="desc"><strong>Drupal field type</strong>: Image or Media</div>
                                            <div class="desc"><strong>Example</strong>: <code>field_image</code></div>
                                        </div>

                                        <div class="field">
                                            <label for="${indexer.index}_drupalIndexedFields">
                                                <span class="label">Drupal indexed field IDs</span>
                                                <input type="text"
                                                    id="${indexer.index}_drupalIndexedFields"
                                                    name="${indexer.index}_drupalIndexedFields"
                                                    data-lpignore="true"
                                                    value="<c:out value="${indexer.drupalIndexedFields}" />" />
                                            </label>
                                            <div class="desc">
                                                Drupal internal field IDs to index.
                                                Multiple fields can be specified, using a comma separated list.
                                            </div>
                                            <div class="desc"><strong>Drupal field type</strong>: Text or Paragraph</div>
                                            <div class="desc"><strong>Example</strong>: <code>field_body, field_references</code></div>
                                        </div>

                                        <div class="field">
                                            <label for="${indexer.index}_drupalGeoJSONField">
                                                <span class="label">Drupal GeoJSON field ID</span>
                                                <input type="text"
                                                    id="${indexer.index}_drupalGeoJSONField"
                                                    name="${indexer.index}_drupalGeoJSONField"
                                                    data-lpignore="true"
                                                    value="<c:out value="${indexer.drupalGeoJSONField}" />" />
                                            </label>
                                            <div class="desc">Drupal internal field ID for the node's GeoJSON.</div>
                                            <div class="desc"><strong>Drupal field type</strong>: Text (plain, long)</div>
                                            <div class="desc"><strong>Example</strong>: <code>field_geojson</code></div>
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
                                            <div class="desc"><strong>Example</strong>: <code>https://eatlas.org.au</code></div>
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
                                                    value="<c:out value="${indexer.drupalBundleId}" />" />
                                            </label>
                                            <div class="desc">Type of media indexed by this indexer.</div>
                                            <div class="desc"><strong>Example</strong>: <code>image</code></div>
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
                                            <div class="desc"><strong>Drupal field type</strong>: Image</div>
                                            <div class="desc"><strong>Example</strong>: <code>thumbnail</code></div>
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
                                            <div class="desc"><strong>Drupal field type</strong>: Text (plain)</div>
                                            <div class="desc"><strong>Example</strong>: <code>name</code></div>
                                        </div>

                                        <div class="field">
                                            <label for="${indexer.index}_drupalIndexedFields">
                                                <span class="label">Drupal indexed field IDs</span>
                                                <input type="text"
                                                    id="${indexer.index}_drupalIndexedFields"
                                                    name="${indexer.index}_drupalIndexedFields"
                                                    data-lpignore="true"
                                                    value="<c:out value="${indexer.drupalIndexedFields}" />" />
                                            </label>
                                            <div class="desc">
                                                Drupal internal field IDs to index.
                                                Multiple fields can be specified, using a comma separated list.
                                            </div>
                                            <div class="desc"><strong>Drupal field type</strong>: Text or Paragraph</div>
                                            <div class="desc"><strong>Example</strong>: <code>field_description, field_author</code></div>
                                        </div>

                                        <div class="field">
                                            <label for="${indexer.index}_drupalGeoJSONField">
                                                <span class="label">Drupal GeoJSON field ID</span>
                                                <input type="text"
                                                    id="${indexer.index}_drupalGeoJSONField"
                                                    name="${indexer.index}_drupalGeoJSONField"
                                                    data-lpignore="true"
                                                    value="<c:out value="${indexer.drupalGeoJSONField}" />" />
                                            </label>
                                            <div class="desc">Drupal internal field ID for the media's GeoJSON.</div>
                                            <div class="desc"><strong>Drupal field type</strong>: Text (plain, long)</div>
                                            <div class="desc"><strong>Example</strong>: <code>field_geojson</code></div>
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
                                            <div class="desc"><strong>Example</strong>: <code>https://eatlas.org.au</code></div>
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
                                                    value="<c:out value="${indexer.drupalBundleId}" />" />
                                            </label>
                                            <div class="desc">Type of node indexed by this indexer.</div>
                                            <div class="desc"><strong>Example</strong>: <code>external_link</code></div>
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
                                            <div class="desc"><strong>Drupal field type</strong>: Image or Media</div>
                                            <div class="desc"><strong>Example</strong>: <code>field_image</code></div>
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
                                            <div class="desc"><strong>Drupal field type</strong>: Link</div>
                                            <div class="desc"><strong>Example</strong>: <code>field_external_link</code></div>
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
                                            <div class="desc"><strong>Drupal field type</strong>: Text (plain, long)</div>
                                            <div class="desc"><strong>Example</strong>: <code>field_content_overwrite</code></div>
                                        </div>

                                        <div class="field">
                                            <label for="${indexer.index}_drupalGeoJSONField">
                                                <span class="label">Drupal GeoJSON field ID</span>
                                                <input type="text"
                                                    id="${indexer.index}_drupalGeoJSONField"
                                                    name="${indexer.index}_drupalGeoJSONField"
                                                    data-lpignore="true"
                                                    value="<c:out value="${indexer.drupalGeoJSONField}" />" />
                                            </label>
                                            <div class="desc">Drupal internal field ID for the node's GeoJSON.</div>
                                            <div class="desc"><strong>Drupal field type</strong>: Text (plain, long)</div>
                                            <div class="desc"><strong>Example</strong>: <code>field_geojson</code></div>
                                        </div>
                                    </div>
                                </c:when>


                                <c:when test="${indexer.type == 'DrupalBlockIndexer'}">
                                    <h3>DrupalBlockIndexer fields</h3>

                                    <div class="DrupalBlockIndexer_form">
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
                                            <div class="desc"><strong>Example</strong>: <code>https://eatlas.org.au</code></div>
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
                                            <label for="${indexer.index}_drupalBlockType">
                                                <span class="label required">Drupal block type</span>
                                                <input type="text"
                                                    id="${indexer.index}_drupalBlockType"
                                                    name="${indexer.index}_drupalBlockType"
                                                    data-lpignore="true"
                                                    required="required"
                                                    value="<c:out value="${indexer.drupalBundleId}" />" />
                                            </label>
                                            <div class="desc">Type of block indexed by this indexer.</div>
                                            <div class="desc"><strong>Example</strong>: <code>basic</code>, <code>network</code>, <code>marine_park</code></div>
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
                                            <div class="desc"><strong>Drupal field type</strong>: Image or Media</div>
                                            <div class="desc"><strong>Example</strong>: <code>field_image</code></div>
                                        </div>

                                        <div class="field">
                                            <label for="${indexer.index}_drupalIndexedFields">
                                                <span class="label">Drupal indexed field IDs</span>
                                                <input type="text"
                                                    id="${indexer.index}_drupalIndexedFields"
                                                    name="${indexer.index}_drupalIndexedFields"
                                                    data-lpignore="true"
                                                    value="<c:out value="${indexer.drupalIndexedFields}" />" />
                                            </label>
                                            <div class="desc">
                                                Drupal internal field IDs to index.
                                                Multiple fields can be specified, using a comma separated list.
                                            </div>
                                            <div class="desc"><strong>Drupal field type</strong>: Text or Paragraph</div>
                                            <div class="desc"><strong>Example</strong>: <code>field_body, field_references</code></div>
                                        </div>

                                        <div class="field">
                                            <label for="${indexer.index}_drupalGeoJSONField">
                                                <span class="label">Drupal GeoJSON field ID</span>
                                                <input type="text"
                                                    id="${indexer.index}_drupalGeoJSONField"
                                                    name="${indexer.index}_drupalGeoJSONField"
                                                    data-lpignore="true"
                                                    value="<c:out value="${indexer.drupalGeoJSONField}" />" />
                                            </label>
                                            <div class="desc">Drupal internal field ID for the node's GeoJSON.</div>
                                            <div class="desc"><strong>Drupal field type</strong>: Text (plain, long)</div>
                                            <div class="desc"><strong>Example</strong>: <code>field_geojson</code></div>
                                        </div>
                                    </div>
                                </c:when>


                                <c:when test="${indexer.type == 'GeoNetworkIndexer'}">
                                    <h3>Old GeoNetworkIndexer fields</h3>

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
                                            <div class="desc"><strong>Example</strong>: <code>https://eatlas.org.au/geonetwork</code></div>
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

                                <c:when test="${indexer.type == 'GeoNetworkCswIndexer'}">
                                    <h3>GeoNetworkCswIndexer fields</h3>

                                    <div class="GeoNetworkCswIndexer_form">
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
                                            <div class="desc"><strong>Example</strong>: <code>https://eatlas.org.au/geonetwork</code></div>
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

                                        <div class="field">
                                            <label for="${indexer.index}_geoNetworkCategories">
                                                <span class="label">Categories</span>
                                                <input type="text"
                                                    id="${indexer.index}_geoNetworkCategories"
                                                    name="${indexer.index}_geoNetworkCategories"
                                                    data-lpignore="true"
                                                    value="<c:out value="${indexer.geoNetworkCategoriesAsString}" />" />
                                            </label>
                                            <div class="desc">Coma separated list of categories to index. It will only select records containing all the categories (boolean "AND").</div>
                                            <div class="desc">Leave blank to index all metadata records, regardless of their categories. Use exclamation mark to exclude a category.</div>
                                            <div class="desc"><strong>Example</strong>: <code>eatlas, nwatlas, !demo, !test</code></div>
                                            <div class="desc"><strong>NOTE</strong>: Due to a bug in GeoNetwork's CSW API, the space character in the <em>Non Custodian</em> category is interpreted as 2 category: <em>Non</em> and <em>Custodian</em>. To select all <em>Non Custodian</em> records, set categories to <code>Non, Custodian</code>. To select all <em>Custodian</em> records, set categories to <code>!Non, Custodian</code>.</div>
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
                                            <div class="desc">The list of layer must be available at "<code>atlasMapperClientUrl</code>/config/main.json"</div>
                                            <div class="desc"><strong>Example</strong>: <code>https://maps.eatlas.org.au</code></div>
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

                                        <div class="field">
                                            <label for="${indexer.index}_baseLayerUrl">
                                                <span class="label">Base layer URL</span>
                                                <input type="text"
                                                    id="${indexer.index}_baseLayerUrl"
                                                    name="${indexer.index}_baseLayerUrl"
                                                    data-lpignore="true"
                                                    value="<c:out value="${indexer.baseLayerUrl}" />" />
                                            </label>
                                            <div class="desc">Base layer URL, used to generate layer thumbnail.</div>
                                            <div class="desc">It must include the placeholders {BBOX}, {WIDTH} and {HEIGHT}</div>
                                            <div class="desc"><strong>Example</strong>: <code>https://maps.eatlas.org.au/maps/wms?SERVICE=WMS&REQUEST=GetMap&LAYERS=ea-be:World_Bright-Earth-e-Atlas-basemap&FORMAT=image/jpeg&TRANSPARENT=false&VERSION=1.1.1&SRS=EPSG:4326&BBOX={BBOX}&WIDTH={WIDTH}&HEIGHT={HEIGHT}</code></div>
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
                        <option value="DrupalBlockIndexer">DrupalBlockIndexer</option>
                        <option value="GeoNetworkIndexer">Old GeoNetworkIndexer</option>
                        <option value="GeoNetworkCswIndexer">GeoNetworkCswIndexer</option>
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

    <c:import url="include/footer.jsp"/>
</body>

</html>
