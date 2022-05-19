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

import au.gov.aims.eatlas.searchengine.admin.rest.Messages;
import au.gov.aims.eatlas.searchengine.client.SearchClient;
import au.gov.aims.eatlas.searchengine.entity.DrupalMedia;
import au.gov.aims.eatlas.searchengine.entity.EntityUtils;
import au.gov.aims.eatlas.searchengine.rest.ImageCache;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import org.apache.http.client.utils.URIBuilder;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.net.URL;
import java.util.Set;

public class DrupalMediaIndexer extends DrupalEntityIndexer<DrupalMedia> {
    private static final Logger LOGGER = Logger.getLogger(DrupalMediaIndexer.class.getName());
    private static final String DEFAULT_PREVIEW_IMAGE_FIELD = "thumbnail";

    private String drupalTitleField;
    private String drupalDescriptionField;
    private String drupalPrivateMediaField;

    public static DrupalMediaIndexer fromJSON(String index, JSONObject json) {
        if (json == null || json.isEmpty()) {
            return null;
        }

        return new DrupalMediaIndexer(
            index,
            json.optString("drupalUrl", null),
            json.optString("drupalVersion", null),
            json.optString("drupalMediaType", null),
            json.optString("drupalPreviewImageField", null),
            json.optString("drupalTitleField", null),
            json.optString("drupalDescriptionField", null),
            json.optString("drupalPrivateMediaField", null));
    }

    public JSONObject toJSON() {
        return this.getJsonBase()
            .put("drupalMediaType", this.getDrupalBundleId())
            .put("drupalTitleField", this.drupalTitleField)
            .put("drupalDescriptionField", this.drupalDescriptionField)
            .put("drupalPrivateMediaField", this.drupalPrivateMediaField);
    }

    public DrupalMedia load(JSONObject json, Messages messages) {
        return DrupalMedia.load(json, messages);
    }

    /**
     * index: eatlas-image
     * drupalUrl: http://localhost:9090
     * drupalVersion: 9.0
     * drupalMediaType: image
     */
    public DrupalMediaIndexer(
            String index,
            String drupalUrl,
            String drupalVersion,
            String drupalMediaType,
            String drupalPreviewImageField,
            String drupalTitleField,
            String drupalDescriptionField,
            String drupalPrivateMediaField
    ) {

        super(index, drupalUrl, drupalVersion, "media", drupalMediaType,
                (drupalPreviewImageField == null || drupalPreviewImageField.isEmpty()) ? DEFAULT_PREVIEW_IMAGE_FIELD : drupalPreviewImageField);
        this.drupalTitleField = drupalTitleField;
        this.drupalDescriptionField = drupalDescriptionField;
        this.drupalPrivateMediaField = drupalPrivateMediaField;
    }

    @Override
    public boolean supportsIndexLatest() {
        return true;
    }

    @Override
    public URIBuilder buildDrupalApiUrl(int page, Messages messages) {
        URIBuilder uriBuilder = super.buildDrupalApiUrl(page, messages);
        if (uriBuilder != null) {
            if (this.drupalPrivateMediaField != null && !this.drupalPrivateMediaField.isEmpty()) {
                uriBuilder.setParameter(String.format("filter[%s]", this.drupalPrivateMediaField), "0");
            }
        }

        return uriBuilder;
    }

    @Override
    public DrupalMedia createDrupalEntity(JSONObject jsonApiMedia, Messages messages) {
        return new DrupalMedia(this.getIndex(), jsonApiMedia, messages);
    }

    @Override
    public Thread createIndexerThread(
            SearchClient client,
            Messages messages,
            DrupalMedia drupalMedia,
            JSONObject jsonApiMedia,
            JSONArray jsonIncluded,
            Set<String> usedThumbnails,
            int page, int current, int mediaFound) {

        return new DrupalMediaIndexerThread(
            client, messages, drupalMedia, jsonApiMedia, jsonIncluded, usedThumbnails, page, current, mediaFound);
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

    public String getDrupalPrivateMediaField() {
        return this.drupalPrivateMediaField;
    }

    public void setDrupalPrivateMediaField(String drupalPrivateMediaField) {
        this.drupalPrivateMediaField = drupalPrivateMediaField;
    }

    public class DrupalMediaIndexerThread extends Thread {
        private final SearchClient client;
        private final Messages messages;
        private final DrupalMedia drupalMedia;
        private final JSONObject jsonApiMedia;
        private final JSONArray jsonIncluded;
        private final Set<String> usedThumbnails;
        private final int page;
        private final int current;
        private final int pageTotal;

        public DrupalMediaIndexerThread(
                SearchClient client,
                Messages messages,
                DrupalMedia drupalMedia,
                JSONObject jsonApiMedia,
                JSONArray jsonIncluded,
                Set<String> usedThumbnails,
                int page, int current, int pageTotal
        ) {

            this.client = client;
            this.messages = messages;
            this.drupalMedia = drupalMedia;
            this.jsonApiMedia = jsonApiMedia;
            this.jsonIncluded = jsonIncluded;
            this.usedThumbnails = usedThumbnails;
            this.page = page;
            this.current = current;
            this.pageTotal = pageTotal;
        }

        @Override
        public void run() {
            // Thumbnail (aka preview image)
            URL baseUrl = DrupalMedia.getDrupalBaseUrl(this.jsonApiMedia, this.messages);
            String previewImageField = DrupalMediaIndexer.this.getDrupalPreviewImageField();
            if (baseUrl != null && previewImageField != null) {
                String previewImageUUID = DrupalMediaIndexer.getPreviewImageUUID(this.jsonApiMedia, previewImageField);
                if (previewImageUUID != null) {
                    String previewImageRelativePath = DrupalMediaIndexer.findPreviewImageRelativePath(previewImageUUID, this.jsonIncluded);
                    if (previewImageRelativePath != null) {
                        URL thumbnailUrl = null;
                        try {
                            thumbnailUrl = new URL(baseUrl, previewImageRelativePath);
                        } catch(Exception ex) {
                            messages.addMessage(Messages.Level.WARNING, String.format("Exception occurred while creating a thumbnail URL for Drupal media: %s, media type: %s",
                                    this.drupalMedia.getId(), DrupalMediaIndexer.this.getDrupalBundleId()), ex);
                        }
                        this.drupalMedia.setThumbnailUrl(thumbnailUrl);

                        // Create the thumbnail if it's missing or outdated
                        DrupalMedia oldMedia = DrupalMediaIndexer.this.safeGet(this.client, DrupalMedia.class, this.drupalMedia.getId(), this.messages);
                        if (this.drupalMedia.isThumbnailOutdated(oldMedia, DrupalMediaIndexer.this.getSafeThumbnailTTL(), DrupalMediaIndexer.this.getSafeBrokenThumbnailTTL(), this.messages)) {
                            try {
                                File cachedThumbnailFile = ImageCache.cache(thumbnailUrl, DrupalMediaIndexer.this.getIndex(), this.drupalMedia.getId(), this.messages);
                                if (cachedThumbnailFile != null) {
                                    this.drupalMedia.setCachedThumbnailFilename(cachedThumbnailFile.getName());
                                }
                            } catch(Exception ex) {
                                messages.addMessage(Messages.Level.WARNING, String.format("Exception occurred while creating a thumbnail for Drupal media: %s, media type: %s",
                                        this.drupalMedia.getId(), DrupalMediaIndexer.this.getDrupalBundleId()), ex);
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
                IndexResponse indexResponse = DrupalMediaIndexer.this.indexEntity(this.client, this.drupalMedia);

                // NOTE: We don't know how many medias (or pages of medias) there is.
                //     We index until we reach the bottom of the barrel...
                LOGGER.debug(String.format("[Page %d: %d/%d] Indexing drupal media ID: %s, index response status: %s",
                        this.page, this.current, this.pageTotal,
                        this.drupalMedia.getMid(),
                        indexResponse.result()));
            } catch(Exception ex) {
                messages.addMessage(Messages.Level.WARNING, String.format("Exception occurred while indexing a Drupal media: %s, media type: %s",
                        this.drupalMedia.getId(), DrupalMediaIndexer.this.getDrupalBundleId()), ex);
            }

            DrupalMediaIndexer.this.incrementCompleted();
        }
    }
}
