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

import au.gov.aims.eatlas.searchengine.client.ESClient;
import au.gov.aims.eatlas.searchengine.entity.DrupalNode;
import au.gov.aims.eatlas.searchengine.entity.EntityUtils;
import au.gov.aims.eatlas.searchengine.rest.ImageCache;
import org.apache.log4j.Logger;
import org.elasticsearch.action.index.IndexResponse;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;

public class DrupalNodeIndexer extends AbstractIndexer<DrupalNode> {
    private static final Logger LOGGER = Logger.getLogger(DrupalNodeIndexer.class.getName());

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

    public DrupalNode load(JSONObject json) {
        return DrupalNode.load(json);
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
    protected void internalHarvest(ESClient client, Long lastHarvested) {
        boolean fullHarvest = lastHarvested == null;
        long harvestStart = System.currentTimeMillis();

        Set<String> usedThumbnails = null;
        if (fullHarvest) {
            usedThumbnails = new HashSet<String>();
        }

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
                LOGGER.warn(String.format("Exception occurred while requesting a page of Drupal nodes. Node type: %s",  this.drupalNodeType), ex);
            }
            if (responseStr != null && !responseStr.isEmpty()) {
                JSONObject jsonResponse = new JSONObject(responseStr);

                JSONArray jsonNodes = jsonResponse.optJSONArray("data");
                JSONArray jsonIncluded = jsonResponse.optJSONArray("included");

                nodeFound = jsonNodes == null ? 0 : jsonNodes.length();

                for (int i=0; i<nodeFound; i++) {
                    JSONObject jsonApiNode = jsonNodes.optJSONObject(i);
                    DrupalNode drupalNode = new DrupalNode(this.getIndex(), jsonApiNode);

                    // Thumbnail (aka preview image)
                    URL baseUrl = DrupalNode.getDrupalBaseUrl(jsonApiNode);
                    if (baseUrl != null && this.drupalPreviewImageField != null) {
                        String previewImageUUID = DrupalNodeIndexer.getPreviewImageUUID(jsonApiNode, this.drupalPreviewImageField);
                        if (previewImageUUID != null) {
                            String previewImageRelativePath = DrupalNodeIndexer.findPreviewImageRelativePath(previewImageUUID, jsonIncluded);
                            if (previewImageRelativePath != null) {
                                URL thumbnailUrl = null;
                                try {
                                    thumbnailUrl = new URL(baseUrl, previewImageRelativePath);
                                } catch(Exception ex) {
                                    LOGGER.warn(String.format("Exception occurred while creating a thumbnail URL for Drupal node: %s, node type: %s",
                                            drupalNode.getId(), this.drupalNodeType), ex);
                                }
                                drupalNode.setThumbnailUrl(thumbnailUrl);

                                // Create the thumbnail if it's missing or outdated
                                DrupalNode oldNode = this.safeGet(client, drupalNode.getId());
                                if (drupalNode.isThumbnailOutdated(oldNode, this.getThumbnailTTL(), this.getBrokenThumbnailTTL())) {
                                    try {
                                        File cachedThumbnailFile = ImageCache.cache(thumbnailUrl, this.getIndex(), drupalNode.getId());
                                        if (cachedThumbnailFile != null) {
                                            drupalNode.setCachedThumbnailFilename(cachedThumbnailFile.getName());
                                        }
                                    } catch(Exception ex) {
                                        LOGGER.warn(String.format("Exception occurred while creating a thumbnail for Drupal node: %s, node type: %s",
                                                drupalNode.getId(), this.drupalNodeType), ex);
                                    }
                                    drupalNode.setThumbnailLastIndexed(System.currentTimeMillis());
                                } else {
                                    drupalNode.useCachedThumbnail(oldNode);
                                }
                            }
                        }
                    }

                    if (usedThumbnails != null) {
                        String thumbnailFilename = drupalNode.getCachedThumbnailFilename();
                        if (thumbnailFilename != null) {
                            usedThumbnails.add(thumbnailFilename);
                        }
                    }

                    // NOTE: Drupal last modified date (aka changed date) are rounded to seconds,
                    //     and can be a bit off. Use a 10s margin for safety.
                    if (!fullHarvest && lastHarvested != null && drupalNode.getLastModified() < lastHarvested + 10000) {
                        stop = true;
                        break;
                    }

                    try {
                        IndexResponse indexResponse = this.index(client, drupalNode);

                        // NOTE: We don't know how many nodes (or pages or nodes) there is.
                        //     We index until we reach the bottom of the barrel...
                        LOGGER.debug(String.format("[Page %d: %d/%d] Indexing drupal node ID: %s, status: %d",
                                page+1, i+1, nodeFound,
                                drupalNode.getNid(),
                                indexResponse.status().getStatus()));
                    } catch(Exception ex) {
                        LOGGER.warn(String.format("Exception occurred while indexing a Drupal node: %s, node type: %s", drupalNode.getId(), this.drupalNodeType), ex);
                    }
                }
            }
            page++;
        } while(!stop && nodeFound == INDEX_PAGE_SIZE);

        // Only cleanup when we are doing a full harvest
        if (fullHarvest) {
            this.cleanUp(client, harvestStart, usedThumbnails, String.format("Drupal node of type %s", this.drupalNodeType));
        }
    }

    private static String getPreviewImageUUID(JSONObject jsonApiNode, String previewImageField) {
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
}
