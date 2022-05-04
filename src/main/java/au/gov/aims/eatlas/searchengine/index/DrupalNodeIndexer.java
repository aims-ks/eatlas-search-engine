/*
 *  Copyright (C) 2020 Australian Institute of Marine Science
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

import au.gov.aims.eatlas.searchengine.admin.rest.Messages;
import au.gov.aims.eatlas.searchengine.client.SearchClient;
import au.gov.aims.eatlas.searchengine.entity.DrupalNode;
import au.gov.aims.eatlas.searchengine.entity.EntityUtils;
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

public class DrupalNodeIndexer extends AbstractIndexer<DrupalNode> {
    private static final Logger LOGGER = Logger.getLogger(DrupalNodeIndexer.class.getName());
    private static final int THREAD_POOL_SIZE = 10;

    // Number of Drupal node to index per page.
    //     Larger number = less request, more RAM
    private static final int INDEX_PAGE_SIZE = 100;

    private String drupalUrl;
    private String drupalVersion;
    private String drupalNodeType;
    private String drupalPreviewImageField;

    public static DrupalNodeIndexer fromJSON(String index, JSONObject json) {
        if (json == null || json.isEmpty()) {
            return null;
        }

        return new DrupalNodeIndexer(
            index,
            json.optString("drupalUrl", null),
            json.optString("drupalVersion", null),
            json.optString("drupalNodeType", null),
            json.optString("drupalPreviewImageField", null));
    }

    public JSONObject toJSON() {
        return this.getJsonBase()
            .put("drupalUrl", this.drupalUrl)
            .put("drupalVersion", this.drupalVersion)
            .put("drupalNodeType", this.drupalNodeType)
            .put("drupalPreviewImageField", this.drupalPreviewImageField);
    }

    @Override
    public boolean validate() {
        if (!super.validate()) {
            return false;
        }
        if (this.drupalUrl == null || this.drupalUrl.isEmpty()) {
            return false;
        }
        if (this.drupalNodeType == null || this.drupalNodeType.isEmpty()) {
            return false;
        }
        return true;
    }

    public DrupalNode load(JSONObject json, Messages messages) {
        return DrupalNode.load(json, messages);
    }

    /**
     * index: eatlas-article
     * drupalUrl: http://localhost:9090
     * drupalVersion: 9.0
     * drupalNodeType: article
     */
    public DrupalNodeIndexer(String index, String drupalUrl, String drupalVersion, String drupalNodeType, String drupalPreviewImageField) {
        super(index);
        this.drupalUrl = drupalUrl;
        this.drupalVersion = drupalVersion;
        this.drupalNodeType = drupalNodeType;
        this.drupalPreviewImageField = drupalPreviewImageField;
    }

    @Override
    public boolean supportsIndexLatest() {
        return true;
    }

    @Override
    protected void internalIndex(SearchClient client, Long lastHarvested, Messages messages) {
        boolean fullHarvest = lastHarvested == null;
        long harvestStart = System.currentTimeMillis();

        Set<String> usedThumbnails = null;
        if (fullHarvest) {
            usedThumbnails = Collections.synchronizedSet(new HashSet<String>());

            // There is no easy way to know how many nodes needs indexing.
            // Use the total number of nodes we have in the index, by looking at the number in the state.
            IndexerState state = this.getState();
            Long total = null;
            if (state != null) {
                total = state.getCount();
            }
            this.setTotal(total);
        }

        ThreadPoolExecutor threadPool = (ThreadPoolExecutor) Executors.newFixedThreadPool(THREAD_POOL_SIZE);

        long totalFound = 0;
        int nodeFound, page = 0;
        boolean stop = false;
        boolean crashed = false;
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
                responseStr = EntityUtils.harvestGetURL(url, messages);
            } catch(Exception ex) {
                if (!crashed) {
                    messages.addMessage(Messages.Level.WARNING, String.format("Exception occurred while requesting a page of Drupal nodes. Node type: %s",  this.drupalNodeType), ex);
                }
                crashed = true;
            }
            if (responseStr != null && !responseStr.isEmpty()) {
                JSONObject jsonResponse = new JSONObject(responseStr);

                JSONArray jsonNodes = jsonResponse.optJSONArray("data");
                JSONArray jsonIncluded = jsonResponse.optJSONArray("included");

                nodeFound = jsonNodes == null ? 0 : jsonNodes.length();
                totalFound += nodeFound;
                if (fullHarvest) {
                    if (this.getTotal() != null && this.getTotal() < totalFound) {
                        this.setTotal(totalFound);
                    }
                } else {
                    this.setTotal(totalFound);
                }

                for (int i=0; i<nodeFound; i++) {
                    JSONObject jsonApiNode = jsonNodes.optJSONObject(i);
                    DrupalNode drupalNode = new DrupalNode(this.getIndex(), jsonApiNode, messages);

                    DrupalNodeIndexer.DrupalNodeIndexerThread thread = new DrupalNodeIndexer.DrupalNodeIndexerThread(
                        client, messages, drupalNode, jsonApiNode, jsonIncluded, usedThumbnails, page+1, i+1, nodeFound);

                    threadPool.execute(thread);

                    // NOTE: Drupal last modified date (aka changed date) are rounded to seconds,
                    //     and can be a bit off. Use a 10s margin for safety.
                    if (!fullHarvest && lastHarvested != null && drupalNode.getLastModified() < lastHarvested + 10000) {
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
            messages.addMessage(Messages.Level.ERROR, String.format("The DrupalNode indexation for node type %s was interrupted",
                    this.drupalNodeType), ex);
        }

        // Only cleanup when we are doing a full harvest
        if (!crashed && fullHarvest) {
            this.cleanUp(client, harvestStart, usedThumbnails, String.format("Drupal node of type %s", this.drupalNodeType), messages);
        }
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

    public class DrupalNodeIndexerThread extends Thread {
        private final SearchClient client;
        private final Messages messages;
        private final DrupalNode drupalNode;
        private final JSONObject jsonApiNode;
        private final JSONArray jsonIncluded;
        private final Set<String> usedThumbnails;
        private final int page;
        private final int current;
        private final int pageTotal;

        public DrupalNodeIndexerThread(
                SearchClient client,
                Messages messages,
                DrupalNode drupalNode,
                JSONObject jsonApiNode,
                JSONArray jsonIncluded,
                Set<String> usedThumbnails,
                int page, int current, int pageTotal
        ) {

            this.client = client;
            this.messages = messages;
            this.drupalNode = drupalNode;
            this.jsonApiNode = jsonApiNode;
            this.jsonIncluded = jsonIncluded;
            this.usedThumbnails = usedThumbnails;
            this.page = page;
            this.current = current;
            this.pageTotal = pageTotal;
        }

        @Override
        public void run() {
            // Thumbnail (aka preview image)
            URL baseUrl = DrupalNode.getDrupalBaseUrl(this.jsonApiNode, this.messages);
            if (baseUrl != null && DrupalNodeIndexer.this.drupalPreviewImageField != null) {
                String previewImageUUID = DrupalNodeIndexer.getPreviewImageUUID(this.jsonApiNode, DrupalNodeIndexer.this.drupalPreviewImageField);
                if (previewImageUUID != null) {
                    String previewImageRelativePath = DrupalNodeIndexer.findPreviewImageRelativePath(previewImageUUID, this.jsonIncluded);
                    if (previewImageRelativePath != null) {
                        URL thumbnailUrl = null;
                        try {
                            thumbnailUrl = new URL(baseUrl, previewImageRelativePath);
                        } catch(Exception ex) {
                            this.messages.addMessage(Messages.Level.WARNING, String.format("Exception occurred while creating a thumbnail URL for Drupal node: %s, node type: %s",
                                    this.drupalNode.getId(), DrupalNodeIndexer.this.drupalNodeType), ex);
                        }
                        this.drupalNode.setThumbnailUrl(thumbnailUrl);

                        // Create the thumbnail if it's missing or outdated
                        DrupalNode oldNode = DrupalNodeIndexer.this.safeGet(this.client, DrupalNode.class, this.drupalNode.getId(), this.messages);
                        if (this.drupalNode.isThumbnailOutdated(oldNode, DrupalNodeIndexer.this.getSafeThumbnailTTL(), DrupalNodeIndexer.this.getSafeBrokenThumbnailTTL(), this.messages)) {
                            try {
                                File cachedThumbnailFile = ImageCache.cache(thumbnailUrl, DrupalNodeIndexer.this.getIndex(), this.drupalNode.getId(), this.messages);
                                if (cachedThumbnailFile != null) {
                                    this.drupalNode.setCachedThumbnailFilename(cachedThumbnailFile.getName());
                                }
                            } catch(Exception ex) {
                                this.messages.addMessage(Messages.Level.WARNING, String.format("Exception occurred while creating a thumbnail for Drupal node: %s, node type: %s",
                                        this.drupalNode.getId(), DrupalNodeIndexer.this.drupalNodeType), ex);
                            }
                            this.drupalNode.setThumbnailLastIndexed(System.currentTimeMillis());
                        } else {
                            this.drupalNode.useCachedThumbnail(oldNode);
                        }
                    }
                }
            }

            if (this.usedThumbnails != null) {
                String thumbnailFilename = drupalNode.getCachedThumbnailFilename();
                if (thumbnailFilename != null) {
                    this.usedThumbnails.add(thumbnailFilename);
                }
            }

            try {
                IndexResponse indexResponse = DrupalNodeIndexer.this.index(this.client, this.drupalNode);

                // NOTE: We don't know how many nodes (or pages of nodes) there is.
                //     We index until we reach the bottom of the barrel...
                LOGGER.debug(String.format("[Page %d: %d/%d] Indexing drupal node ID: %s, index response status: %s",
                        this.page, this.current, this.pageTotal,
                        this.drupalNode.getNid(),
                        indexResponse.result()));
            } catch(Exception ex) {
                this.messages.addMessage(Messages.Level.WARNING,
                        String.format("Exception occurred while indexing a Drupal node: %s, node type: %s", this.drupalNode.getId(), DrupalNodeIndexer.this.drupalNodeType), ex);
            }

            DrupalNodeIndexer.this.incrementCompleted();
        }
    }
}
