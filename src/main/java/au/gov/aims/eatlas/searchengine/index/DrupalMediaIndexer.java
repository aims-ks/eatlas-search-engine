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

import au.gov.aims.eatlas.searchengine.client.ESClient;
import au.gov.aims.eatlas.searchengine.entity.DrupalMedia;
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

public class DrupalMediaIndexer extends AbstractIndexer<DrupalMedia> {
    private static final Logger LOGGER = Logger.getLogger(DrupalMediaIndexer.class.getName());
    private static final int THREAD_POOL_SIZE = 10;

    // Number of Drupal media to index per page.
    //     Larger number = less request, more RAM
    private static final int INDEX_PAGE_SIZE = 100;

    private String drupalUrl;
    private String drupalVersion;
    private String drupalMediaType;
    private String drupalPreviewImageField;
    private String drupalTitleField;
    private String drupalDescriptionField;

    public static DrupalMediaIndexer fromJSON(String index, IndexerState state, JSONObject json) {
        if (json == null || json.isEmpty()) {
            return null;
        }

        return new DrupalMediaIndexer(
            index, state,
            json.optString("drupalUrl", null),
            json.optString("drupalVersion", null),
            json.optString("drupalMediaType", null),
            json.optString("drupalPreviewImageField", null),
            json.optString("drupalTitleField", null),
            json.optString("drupalDescriptionField", null));
    }

    public JSONObject toJSON() {
        return this.getJsonBase()
            .put("drupalUrl", this.drupalUrl)
            .put("drupalVersion", this.drupalVersion)
            .put("drupalMediaType", this.drupalMediaType)
            .put("drupalPreviewImageField", this.drupalPreviewImageField)
            .put("drupalTitleField", this.drupalTitleField)
            .put("drupalDescriptionField", this.drupalDescriptionField);
    }

    public DrupalMedia load(JSONObject json) {
        return DrupalMedia.load(json);
    }

    /**
     * index: eatlas-image
     * drupalUrl: http://localhost:9090
     * drupalVersion: 9.0
     * drupalMediaType: image
     */
    public DrupalMediaIndexer(
            String index,
            IndexerState state,
            String drupalUrl,
            String drupalVersion,
            String drupalMediaType,
            String drupalPreviewImageField,
            String drupalTitleField,
            String drupalDescriptionField
    ) {

        super(index, state);
        this.drupalUrl = drupalUrl;
        this.drupalVersion = drupalVersion;
        this.drupalMediaType = drupalMediaType;
        this.drupalPreviewImageField = drupalPreviewImageField;
        this.drupalTitleField = drupalTitleField;
        this.drupalDescriptionField = drupalDescriptionField;
    }

    @Override
    protected Long internalHarvest(ESClient client, Long lastHarvested) {
        boolean fullHarvest = lastHarvested == null;
        long harvestStart = System.currentTimeMillis();

        Set<String> usedThumbnails = null;
        if (fullHarvest) {
            usedThumbnails = Collections.synchronizedSet(new HashSet<String>());
        }

        ThreadPoolExecutor threadPool = (ThreadPoolExecutor) Executors.newFixedThreadPool(THREAD_POOL_SIZE);

        int mediaFound, page = 0;
        long count = 0;
        boolean stop = false;
        do {
            // Ordered by lastModified (changed).
            // If the parameter lastHarvested is set, harvest medias until we found a media that was last modified before
            //     the lastHarvested parameter.
            // "http://localhost:9090/jsonapi/media/image?include=thumbnail&sort=-changed&page[limit]=100&page[offset]=0"
            String url = String.format("%s/jsonapi/media/%s?include=%s&sort=-changed&page[limit]=%d&page[offset]=%d",
                this.drupalUrl, this.drupalMediaType, this.getSafeDrupalPreviewImageField(), INDEX_PAGE_SIZE, page * INDEX_PAGE_SIZE);

            mediaFound = 0;
            String responseStr = null;
            try {
                responseStr = EntityUtils.harvestGetURL(url);
            } catch(Exception ex) {
                LOGGER.warn(String.format("Exception occurred while requesting a page of Drupal medias. Media type: %s",  this.drupalMediaType), ex);
            }
            if (responseStr != null && !responseStr.isEmpty()) {
                JSONObject jsonResponse = new JSONObject(responseStr);

                JSONArray jsonMedias = jsonResponse.optJSONArray("data");
                JSONArray jsonIncluded = jsonResponse.optJSONArray("included");

                mediaFound = jsonMedias == null ? 0 : jsonMedias.length();

                for (int i=0; i<mediaFound; i++) {
                    count++;

                    JSONObject jsonApiMedia = jsonMedias.optJSONObject(i);
                    DrupalMedia drupalMedia = new DrupalMedia(this.getIndex(), jsonApiMedia);

                    drupalMedia.setTitle(DrupalMediaIndexer.getDrupalTitle(jsonApiMedia, DrupalMediaIndexer.this.drupalTitleField));
                    drupalMedia.setDocument(DrupalMediaIndexer.getDrupalDescription(jsonApiMedia, DrupalMediaIndexer.this.drupalDescriptionField));

                    DrupalMediaIndexerThread thread = new DrupalMediaIndexerThread(
                        client, drupalMedia, jsonApiMedia, jsonIncluded, usedThumbnails, page+1, i+1, mediaFound);

                    threadPool.execute(thread);

                    // NOTE: Drupal last modified date (aka changed date) are rounded to seconds,
                    //     and can be a bit off. Use a 10s margin for safety.
                    if (!fullHarvest && lastHarvested != null && drupalMedia.getLastModified() < lastHarvested + 10000) {
                        stop = true;
                        break;
                    }
                }
            }
            page++;
        } while(!stop && mediaFound == INDEX_PAGE_SIZE);

        threadPool.shutdown();
        try {
            threadPool.awaitTermination(1, TimeUnit.HOURS);
        } catch(InterruptedException ex) {
            LOGGER.error(String.format("The DrupalMedia indexation for media type %s was interrupted",
                    this.drupalMediaType), ex);
        }

        // Only cleanup when we are doing a full harvest
        if (fullHarvest) {
            this.cleanUp(client, harvestStart, usedThumbnails, String.format("Drupal media of type %s", this.drupalMediaType));
            return count;
        }

        return null;
    }

    private static String getDrupalTitle(JSONObject jsonApiMedia, String drupalTitleField) {
        if (drupalTitleField == null || drupalTitleField.isEmpty()) {
            return null;
        }
        JSONObject jsonAttributes = jsonApiMedia == null ? null : jsonApiMedia.optJSONObject("attributes");
        return jsonAttributes == null ? null : jsonAttributes.optString(drupalTitleField, null);
    }

    private static String getDrupalDescription(JSONObject jsonApiMedia, String drupalDescriptionField) {
        if (drupalDescriptionField == null || drupalDescriptionField.isEmpty()) {
            return null;
        }
        JSONObject jsonAttributes = jsonApiMedia == null ? null : jsonApiMedia.optJSONObject("attributes");
        JSONObject jsonDesc = jsonAttributes == null ? null : jsonAttributes.optJSONObject(drupalDescriptionField);
        return jsonDesc == null ? null :
            EntityUtils.extractHTMLTextContent(jsonDesc.optString("processed", null));
    }

    private static String getPreviewImageUUID(JSONObject jsonApiMedia, String previewImageField) {
        JSONObject jsonRelationships = jsonApiMedia == null ? null : jsonApiMedia.optJSONObject("relationships");
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

    public String getDrupalMediaType() {
        return this.drupalMediaType;
    }

    public void setDrupalMediaType(String drupalMediaType) {
        this.drupalMediaType = drupalMediaType;
    }

    public String getDrupalPreviewImageField() {
        return this.drupalPreviewImageField;
    }
    public String getSafeDrupalPreviewImageField() {
        return this.drupalPreviewImageField == null ? "thumbnail" : this.drupalPreviewImageField;
    }

    public void setDrupalPreviewImageField(String drupalPreviewImageField) {
        this.drupalPreviewImageField = drupalPreviewImageField;
    }

    public String getDrupalTitleField() {
        return this.drupalTitleField;
    }

    public void setDrupalTitleField(String drupalTitleField) {
        this.drupalTitleField = drupalTitleField;
    }

    public String getDrupalDescriptionField() {
        return this.drupalDescriptionField;
    }

    public void setDrupalDescriptionField(String drupalDescriptionField) {
        this.drupalDescriptionField = drupalDescriptionField;
    }

    public class DrupalMediaIndexerThread extends Thread {
        private final ESClient client;
        private final DrupalMedia drupalMedia;
        private final JSONObject jsonApiMedia;
        private final JSONArray jsonIncluded;
        private final Set<String> usedThumbnails;
        private final int page;
        private final int current;
        private final int total;

        public DrupalMediaIndexerThread(
                ESClient client,
                DrupalMedia drupalMedia,
                JSONObject jsonApiMedia,
                JSONArray jsonIncluded,
                Set<String> usedThumbnails,
                int page, int current, int total
        ) {

            this.client = client;
            this.drupalMedia = drupalMedia;
            this.jsonApiMedia = jsonApiMedia;
            this.jsonIncluded = jsonIncluded;
            this.usedThumbnails = usedThumbnails;
            this.page = page;
            this.current = current;
            this.total = total;
        }

        @Override
        public void run() {
            // Thumbnail (aka preview image)
            URL baseUrl = DrupalMedia.getDrupalBaseUrl(this.jsonApiMedia);
            String previewImageField = DrupalMediaIndexer.this.getSafeDrupalPreviewImageField();
            if (baseUrl != null && previewImageField != null) {
                String previewImageUUID = DrupalMediaIndexer.getPreviewImageUUID(this.jsonApiMedia, previewImageField);
                if (previewImageUUID != null) {
                    String previewImageRelativePath = DrupalMediaIndexer.findPreviewImageRelativePath(previewImageUUID, this.jsonIncluded);
                    if (previewImageRelativePath != null) {
                        URL thumbnailUrl = null;
                        try {
                            thumbnailUrl = new URL(baseUrl, previewImageRelativePath);
                        } catch(Exception ex) {
                            LOGGER.warn(String.format("Exception occurred while creating a thumbnail URL for Drupal media: %s, media type: %s",
                                    this.drupalMedia.getId(), DrupalMediaIndexer.this.drupalMediaType), ex);
                        }
                        this.drupalMedia.setThumbnailUrl(thumbnailUrl);

                        // Create the thumbnail if it's missing or outdated
                        DrupalMedia oldMedia = DrupalMediaIndexer.this.safeGet(this.client, DrupalMedia.class, this.drupalMedia.getId());
                        if (this.drupalMedia.isThumbnailOutdated(oldMedia, DrupalMediaIndexer.this.getSafeThumbnailTTL(), DrupalMediaIndexer.this.getSafeBrokenThumbnailTTL())) {
                            try {
                                File cachedThumbnailFile = ImageCache.cache(thumbnailUrl, DrupalMediaIndexer.this.getIndex(), this.drupalMedia.getId());
                                if (cachedThumbnailFile != null) {
                                    this.drupalMedia.setCachedThumbnailFilename(cachedThumbnailFile.getName());
                                }
                            } catch(Exception ex) {
                                LOGGER.warn(String.format("Exception occurred while creating a thumbnail for Drupal media: %s, media type: %s",
                                        this.drupalMedia.getId(), DrupalMediaIndexer.this.drupalMediaType), ex);
                            }
                            this.drupalMedia.setThumbnailLastIndexed(System.currentTimeMillis());
                        } else {
                            this.drupalMedia.useCachedThumbnail(oldMedia);
                        }
                    }
                }
            }

            if (this.usedThumbnails != null) {
                String thumbnailFilename = drupalMedia.getCachedThumbnailFilename();
                if (thumbnailFilename != null) {
                    this.usedThumbnails.add(thumbnailFilename);
                }
            }

            try {
                IndexResponse indexResponse = DrupalMediaIndexer.this.index(this.client, this.drupalMedia);

                // NOTE: We don't know how many medias (or pages of medias) there is.
                //     We index until we reach the bottom of the barrel...
                LOGGER.debug(String.format("[Page %d: %d/%d] Indexing drupal media ID: %s, index response status: %s",
                        this.page, this.current, this.total,
                        this.drupalMedia.getMid(),
                        indexResponse.result()));
            } catch(Exception ex) {
                LOGGER.warn(String.format("Exception occurred while indexing a Drupal media: %s, media type: %s", this.drupalMedia.getId(), DrupalMediaIndexer.this.drupalMediaType), ex);
            }

        }
    }
}
