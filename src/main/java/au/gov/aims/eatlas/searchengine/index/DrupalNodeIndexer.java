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
import au.gov.aims.eatlas.searchengine.rest.ImageCache;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import org.apache.http.client.utils.URIBuilder;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.net.URL;
import java.util.Set;

public class DrupalNodeIndexer extends DrupalEntityIndexer<DrupalNode> {
    private static final Logger LOGGER = Logger.getLogger(DrupalNodeIndexer.class.getName());

    private String drupalPrepressField;

    public static DrupalNodeIndexer fromJSON(String index, JSONObject json) {
        if (json == null || json.isEmpty()) {
            return null;
        }

        return new DrupalNodeIndexer(
            index,
            json.optString("drupalUrl", null),
            json.optString("drupalVersion", null),
            json.optString("drupalNodeType", null),
            json.optString("drupalPreviewImageField", null),
            json.optString("drupalPrepressField", null));
    }

    public JSONObject toJSON() {
        return this.getJsonBase()
            .put("drupalNodeType", this.getDrupalBundleId())
            .put("drupalPrepressField", this.drupalPrepressField);
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
    public DrupalNodeIndexer(
            String index,
            String drupalUrl,
            String drupalVersion,
            String drupalNodeType,
            String drupalPreviewImageField,
            String drupalPrepressField) {

        super(index, drupalUrl, drupalVersion, "node", drupalNodeType, drupalPreviewImageField);
        this.drupalPrepressField = drupalPrepressField;
    }

    @Override
    public boolean supportsIndexLatest() {
        return true;
    }

    @Override
    public URIBuilder buildDrupalApiUrl(int page, Messages messages) {
        URIBuilder uriBuilder = super.buildDrupalApiUrl(page, messages);
        if (uriBuilder != null) {
            if (this.drupalPrepressField != null && !this.drupalPrepressField.isEmpty()) {
                uriBuilder.setParameter(String.format("filter[%s]", this.drupalPrepressField), "0");
            }
        }

        return uriBuilder;
    }

    @Override
    public DrupalNode createDrupalEntity(JSONObject jsonApiNode, Messages messages) {
        return new DrupalNode(this.getIndex(), jsonApiNode, messages);
    }

    @Override
    public Thread createIndexerThread(
            SearchClient client,
            Messages messages,
            DrupalNode drupalNode,
            JSONObject jsonApiNode,
            JSONArray jsonIncluded,
            Set<String> usedThumbnails,
            int page, int current, int nodeFound) {

        return new DrupalNodeIndexer.DrupalNodeIndexerThread(
            client, messages, drupalNode, jsonApiNode, jsonIncluded, usedThumbnails, page, current, nodeFound);
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

    public String getDrupalPrepressField() {
        return this.drupalPrepressField;
    }

    public void setDrupalPrepressField(String drupalPrepressField) {
        this.drupalPrepressField = drupalPrepressField;
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
            if (baseUrl != null && DrupalNodeIndexer.this.getDrupalPreviewImageField() != null) {
                String previewImageUUID = DrupalNodeIndexer.getPreviewImageUUID(this.jsonApiNode, DrupalNodeIndexer.this.getDrupalPreviewImageField());
                if (previewImageUUID != null) {
                    String previewImageRelativePath = DrupalNodeIndexer.findPreviewImageRelativePath(previewImageUUID, this.jsonIncluded);
                    if (previewImageRelativePath != null) {
                        URL thumbnailUrl = null;
                        try {
                            thumbnailUrl = new URL(baseUrl, previewImageRelativePath);
                        } catch(Exception ex) {
                            this.messages.addMessage(Messages.Level.WARNING, String.format("Exception occurred while creating a thumbnail URL for Drupal node: %s, node type: %s",
                                    this.drupalNode.getId(), DrupalNodeIndexer.this.getDrupalBundleId()), ex);
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
                                        this.drupalNode.getId(), DrupalNodeIndexer.this.getDrupalBundleId()), ex);
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
                IndexResponse indexResponse = DrupalNodeIndexer.this.indexEntity(this.client, this.drupalNode);

                // NOTE: We don't know how many nodes (or pages of nodes) there is.
                //     We index until we reach the bottom of the barrel...
                LOGGER.debug(String.format("[Page %d: %d/%d] Indexing drupal node ID: %s, index response status: %s",
                        this.page, this.current, this.pageTotal,
                        this.drupalNode.getNid(),
                        indexResponse.result()));
            } catch(Exception ex) {
                this.messages.addMessage(Messages.Level.WARNING,
                        String.format("Exception occurred while indexing a Drupal node: %s, node type: %s",
                                this.drupalNode.getId(), DrupalNodeIndexer.this.getDrupalBundleId()), ex);
            }

            DrupalNodeIndexer.this.incrementCompleted();
        }
    }
}
