/*
 *  Copyright (C) 2022 Australian Institute of Marine Science
 *
 *  Contact: Gael Lafond <g.lafond@aims.gov.au>
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package au.gov.aims.eatlas.searchengine.index;

import au.gov.aims.eatlas.searchengine.client.SearchClient;
import au.gov.aims.eatlas.searchengine.entity.EntityUtils;
import au.gov.aims.eatlas.searchengine.entity.ExternalLink;
import au.gov.aims.eatlas.searchengine.rest.ImageCache;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.net.URL;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class DrupalExternalLinkNodeIndexer extends AbstractIndexer<ExternalLink> {
    private static final Logger LOGGER = Logger.getLogger(DrupalExternalLinkNodeIndexer.class.getName());
    private static final int THREAD_POOL_SIZE = 10;

    // Number of Drupal node to index per page.
    //     Larger number = less request, more RAM
    private static final int INDEX_PAGE_SIZE = 100;

    private String drupalUrl;
    private String drupalVersion;
    private String drupalNodeType;
    private String drupalPreviewImageField;
    private String drupalExternalUrlField;
    private String drupalContentOverwriteField;

    public static DrupalExternalLinkNodeIndexer fromJSON(String index, JSONObject json) {
        if (json == null || json.isEmpty()) {
            return null;
        }

        return new DrupalExternalLinkNodeIndexer(
            index,
            json.optString("drupalUrl", null),
            json.optString("drupalVersion", null),
            json.optString("drupalNodeType", null),
            json.optString("drupalPreviewImageField", null),
            json.optString("drupalExternalUrlField", null),
            json.optString("drupalContentOverwriteField", null));
    }

    public JSONObject toJSON() {
        return this.getJsonBase()
            .put("drupalUrl", this.drupalUrl)
            .put("drupalVersion", this.drupalVersion)
            .put("drupalNodeType", this.drupalNodeType)
            .put("drupalPreviewImageField", this.drupalPreviewImageField)
            .put("drupalExternalUrlField", this.drupalExternalUrlField)
            .put("drupalContentOverwriteField", this.drupalContentOverwriteField);
    }

    public ExternalLink load(JSONObject json) {
        return ExternalLink.load(json);
    }

    /**
     * index: eatlas-article
     * drupalUrl: http://localhost:9090
     * drupalVersion: 9.0
     * drupalNodeType: article
     */
    public DrupalExternalLinkNodeIndexer(
            String index,
            String drupalUrl,
            String drupalVersion,
            String drupalNodeType,
            String drupalPreviewImageField,
            String drupalExternalUrlField,
            String drupalContentOverwriteField
    ) {
        super(index);
        this.drupalUrl = drupalUrl;
        this.drupalVersion = drupalVersion;
        this.drupalNodeType = drupalNodeType;
        this.drupalPreviewImageField = drupalPreviewImageField;
        this.drupalExternalUrlField = drupalExternalUrlField;
        this.drupalContentOverwriteField = drupalContentOverwriteField;
    }

    @Override
    public boolean supportsIndexLatest() {
        return true;
    }

    @Override
    protected void internalIndex(SearchClient client, Long lastHarvested) {
        boolean fullHarvest = lastHarvested == null;
        long harvestStart = System.currentTimeMillis();

        Set<String> usedThumbnails = null;
        if (fullHarvest) {
            usedThumbnails = Collections.synchronizedSet(new HashSet<String>());
        }

        // There is no easy way to know how many nodes needs indexing.
        // Use the total number of nodes we have in the index, by looking at the number in the state.
        IndexerState state = this.getState();
        Long total = null;
        if (state != null) {
            total = state.getCount();
        }
        this.setTotal(total);

        ThreadPoolExecutor threadPool = (ThreadPoolExecutor) Executors.newFixedThreadPool(THREAD_POOL_SIZE);

        int nodeFound, page = 0;
        boolean stop = false;
        do {
            // Ordered by lastModified (changed).
            // If the parameter lastHarvested is set, harvest nodes until we found a node that was last modified before
            //     the lastHarvested parameter.
            // "http://localhost:9090/jsonapi/node/article?include=field_image&sort=-changed&page[limit]=100&page[offset]=0"
            String url = String.format("%s/jsonapi/node/%s?include=%s&sort=-changed&page[limit]=%d&page[offset]=%d",
                this.drupalUrl, this.drupalNodeType, this.drupalPreviewImageField, INDEX_PAGE_SIZE, page * INDEX_PAGE_SIZE);

            nodeFound = 0;
            String responseStr = null;
            try {
                responseStr = EntityUtils.harvestGetURL(url);
            } catch(Exception ex) {
                LOGGER.warn(String.format("Exception occurred while requesting a page of Drupal external link nodes. Node type: %s",  this.drupalNodeType), ex);
            }
            if (responseStr != null && !responseStr.isEmpty()) {
                JSONObject jsonResponse = new JSONObject(responseStr);

                JSONArray jsonNodes = jsonResponse.optJSONArray("data");
                JSONArray jsonIncluded = jsonResponse.optJSONArray("included");

                nodeFound = jsonNodes == null ? 0 : jsonNodes.length();

                for (int i=0; i<nodeFound; i++) {
                    JSONObject jsonApiNode = jsonNodes.optJSONObject(i);
                    ExternalLink externalLink = new ExternalLink(this.getIndex(), jsonApiNode);

                    DrupalExternalLinkNodeIndexerThread thread = new DrupalExternalLinkNodeIndexerThread(
                        client, externalLink, jsonApiNode, jsonIncluded, usedThumbnails, page+1, i+1, nodeFound);

                    threadPool.execute(thread);

                    // NOTE: Drupal last modified date (aka changed date) are rounded to seconds,
                    //     and can be a bit off. Use a 10s margin for safety.
                    if (!fullHarvest && lastHarvested != null && externalLink.getLastModified() < lastHarvested + 10000) {
                        stop = true;
                        break;
                    }
                }
            }
            page++;
        } while(!stop && nodeFound == INDEX_PAGE_SIZE);

        threadPool.shutdown();
        try {
            threadPool.awaitTermination(1, TimeUnit.HOURS);
        } catch(InterruptedException ex) {
            LOGGER.error(String.format("The DrupalNode indexation for external link node type %s was interrupted",
                    this.drupalNodeType), ex);
        }

        // Only cleanup when we are doing a full harvest
        if (fullHarvest) {
            this.cleanUp(client, harvestStart, usedThumbnails, String.format("Drupal external link node of type %s", this.drupalNodeType));
        }
    }

    private static String getExternalLink(JSONObject jsonApiNode, String externalLinkField) {
        if (externalLinkField == null || externalLinkField.isEmpty()) {
            return null;
        }
        JSONObject jsonAttributes = jsonApiNode == null ? null : jsonApiNode.optJSONObject("attributes");
        JSONObject jsonExternalLink = jsonAttributes == null ? null : jsonAttributes.optJSONObject(externalLinkField);
        return jsonExternalLink == null ? null : jsonExternalLink.optString("uri", null);
    }

    private static String getDrupalContentOverwrite(JSONObject jsonApiNode, String drupalContentOverwriteField) {
        if (drupalContentOverwriteField == null || drupalContentOverwriteField.isEmpty()) {
            return null;
        }
        JSONObject jsonAttributes = jsonApiNode == null ? null : jsonApiNode.optJSONObject("attributes");
        return jsonAttributes == null ? null : jsonAttributes.optString(drupalContentOverwriteField, null);
    }

    private static String getPreviewImageUUID(JSONObject jsonApiNode, String previewImageField) {
        if (previewImageField == null || previewImageField.isEmpty()) {
            return null;
        }
        JSONObject jsonRelationships = jsonApiNode == null ? null : jsonApiNode.optJSONObject("relationships");
        JSONObject jsonRelFieldImage = jsonRelationships == null ? null : jsonRelationships.optJSONObject(previewImageField);
        JSONObject jsonRelFieldImageData = jsonRelFieldImage == null ? null : jsonRelFieldImage.optJSONObject("data");
        return jsonRelFieldImageData == null ? null : jsonRelFieldImageData.optString("id", null);
    }

    private static String findPreviewImageRelativePath(String imageUUID, JSONArray included) {
        if (imageUUID == null || included == null) {
            return null;
        }

        for (int i=0; i < included.length(); i++) {
            JSONObject jsonInclude = included.optJSONObject(i);
            String includeId = jsonInclude == null ? null : jsonInclude.optString("id", null);
            if (imageUUID.equals(includeId)) {
                JSONObject jsonIncludeAttributes = jsonInclude == null ? null : jsonInclude.optJSONObject("attributes");
                JSONObject jsonIncludeAttributesUri = jsonIncludeAttributes == null ? null : jsonIncludeAttributes.optJSONObject("uri");
                return jsonIncludeAttributesUri == null ? null : jsonIncludeAttributesUri.optString("url", null);
            }
        }

        return null;
    }

    public String getDrupalUrl() {
        return this.drupalUrl;
    }

    public void setDrupalUrl(String drupalUrl) {
        this.drupalUrl = drupalUrl;
    }

    public String getDrupalVersion() {
        return this.drupalVersion;
    }

    public void setDrupalVersion(String drupalVersion) {
        this.drupalVersion = drupalVersion;
    }

    public String getDrupalNodeType() {
        return this.drupalNodeType;
    }

    public void setDrupalNodeType(String drupalNodeType) {
        this.drupalNodeType = drupalNodeType;
    }

    public String getDrupalPreviewImageField() {
        return this.drupalPreviewImageField;
    }

    public void setDrupalPreviewImageField(String drupalPreviewImageField) {
        this.drupalPreviewImageField = drupalPreviewImageField;
    }

    public String getDrupalExternalUrlField() {
        return this.drupalExternalUrlField;
    }

    public void setDrupalExternalUrlField(String drupalExternalUrlField) {
        this.drupalExternalUrlField = drupalExternalUrlField;
    }

    public String getDrupalContentOverwriteField() {
        return this.drupalContentOverwriteField;
    }

    public void setDrupalContentOverwriteField(String drupalContentOverwriteField) {
        this.drupalContentOverwriteField = drupalContentOverwriteField;
    }

    public class DrupalExternalLinkNodeIndexerThread extends Thread {
        private final SearchClient client;
        private final ExternalLink externalLink;
        private final JSONObject jsonApiNode;
        private final JSONArray jsonIncluded;

        private final Set<String> usedThumbnails;
        private final int page;
        private final int current;
        private final int pageTotal;

        public DrupalExternalLinkNodeIndexerThread(
                SearchClient client,
                ExternalLink externalLink,
                JSONObject jsonApiNode,
                JSONArray jsonIncluded,
                Set<String> usedThumbnails,
                int page, int current, int pageTotal
        ) {
            this.client = client;
            this.externalLink = externalLink;
            this.jsonApiNode = jsonApiNode;
            this.jsonIncluded = jsonIncluded;

            this.usedThumbnails = usedThumbnails;
            this.page = page;
            this.current = current;
            this.pageTotal = pageTotal;
        }

        @Override
        public void run() {
            if (DrupalExternalLinkNodeIndexer.this.drupalExternalUrlField != null) {
                String externalLinkStr = DrupalExternalLinkNodeIndexer.getExternalLink(this.jsonApiNode, DrupalExternalLinkNodeIndexer.this.drupalExternalUrlField);
                if (externalLinkStr != null && !externalLinkStr.isEmpty()) {
                    URL externalLink = null;
                    try {
                        externalLink = new URL(externalLinkStr);
                    } catch(Exception ex) {
                        LOGGER.warn(String.format("Invalid URL found for Drupal external link node ID: %s, node type: %s",
                                this.externalLink.getId(), DrupalExternalLinkNodeIndexer.this.drupalNodeType), ex);
                    }

                    String content = null;

                    // If there is a content overwrite, don't even attempt to download the URL
                    String contentOverwrite = DrupalExternalLinkNodeIndexer.getDrupalContentOverwrite(this.jsonApiNode, DrupalExternalLinkNodeIndexer.this.drupalContentOverwriteField);
                    if (contentOverwrite != null && !contentOverwrite.isEmpty()) {
                        content = contentOverwrite;
                    } else {

                        // Download the text content of the URL
                        try {
                            content = EntityUtils.harvestURLText(externalLinkStr);
                        } catch (Exception ex) {
                            LOGGER.error(String.format("Exception occurred while harvesting URL for Drupal external link node %s, node type: %s. URL %s. Error message: %s",
                                    this.externalLink.getId(), DrupalExternalLinkNodeIndexer.this.drupalNodeType, externalLinkStr, ex.getMessage()));
                            LOGGER.trace("Stacktrace", ex);
                        }
                    }

                    if (content != null) {
                        this.externalLink.setDocument(content);

                        // Overwrite fields to make the results look more like an external link
                        this.externalLink.setLink(externalLink);

                        // Thumbnail (aka preview image)
                        URL baseUrl = ExternalLink.getDrupalBaseUrl(this.jsonApiNode);
                        if (baseUrl != null && DrupalExternalLinkNodeIndexer.this.drupalPreviewImageField != null) {
                            String previewImageUUID = DrupalExternalLinkNodeIndexer.getPreviewImageUUID(this.jsonApiNode, DrupalExternalLinkNodeIndexer.this.drupalPreviewImageField);
                            if (previewImageUUID != null) {
                                String previewImageRelativePath = DrupalExternalLinkNodeIndexer.findPreviewImageRelativePath(previewImageUUID, this.jsonIncluded);
                                if (previewImageRelativePath != null) {
                                    URL thumbnailUrl = null;
                                    try {
                                        thumbnailUrl = new URL(baseUrl, previewImageRelativePath);
                                    } catch(Exception ex) {
                                        LOGGER.warn(String.format("Exception occurred while creating a thumbnail URL for Drupal node: %s, node type: %s",
                                                this.externalLink.getId(), DrupalExternalLinkNodeIndexer.this.drupalNodeType), ex);
                                    }
                                    this.externalLink.setThumbnailUrl(thumbnailUrl);

                                    // Create the thumbnail if it's missing or outdated
                                    ExternalLink oldExternalLink = DrupalExternalLinkNodeIndexer.this.safeGet(this.client, ExternalLink.class, this.externalLink.getId());
                                    if (this.externalLink.isThumbnailOutdated(oldExternalLink, DrupalExternalLinkNodeIndexer.this.getSafeThumbnailTTL(), DrupalExternalLinkNodeIndexer.this.getSafeBrokenThumbnailTTL())) {
                                        try {
                                            File cachedThumbnailFile = ImageCache.cache(thumbnailUrl, DrupalExternalLinkNodeIndexer.this.getIndex(), this.externalLink.getId());
                                            if (cachedThumbnailFile != null) {
                                                this.externalLink.setCachedThumbnailFilename(cachedThumbnailFile.getName());
                                            }
                                        } catch(Exception ex) {
                                            LOGGER.warn(String.format("Exception occurred while creating a thumbnail for Drupal node: %s, node type: %s",
                                                    this.externalLink.getId(), DrupalExternalLinkNodeIndexer.this.drupalNodeType), ex);
                                        }
                                        this.externalLink.setThumbnailLastIndexed(System.currentTimeMillis());
                                    } else {
                                        this.externalLink.useCachedThumbnail(oldExternalLink);
                                    }
                                }
                            }
                        }

                        if (this.usedThumbnails != null) {
                            String thumbnailFilename = this.externalLink.getCachedThumbnailFilename();
                            if (thumbnailFilename != null) {
                                this.usedThumbnails.add(thumbnailFilename);
                            }
                        }

                        try {
                            IndexResponse indexResponse = DrupalExternalLinkNodeIndexer.this.index(client, this.externalLink);

                            LOGGER.debug(String.format("[Page %d: %d/%d] Indexing drupal external link node ID: %s, URL: %s, index response status: %s",
                                    this.page, this.current, this.pageTotal,
                                    this.externalLink.getNid(),
                                    externalLinkStr,
                                    indexResponse.result()));
                        } catch(Exception ex) {
                            LOGGER.warn(String.format("Exception occurred while indexing an external link Drupal node: %s, node type: %s, URL: %s",
                                    this.externalLink.getId(), DrupalExternalLinkNodeIndexer.this.drupalNodeType, externalLinkStr), ex);
                        }
                    }
                }
            }

            DrupalExternalLinkNodeIndexer.this.incrementCompleted();
        }
    }
}
