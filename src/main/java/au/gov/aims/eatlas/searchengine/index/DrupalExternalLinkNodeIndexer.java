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
import au.gov.aims.eatlas.searchengine.entity.EntityUtils;
import au.gov.aims.eatlas.searchengine.entity.ExternalLink;
import au.gov.aims.eatlas.searchengine.rest.ImageCache;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.locationtech.jts.io.ParseException;

import java.io.File;
import java.net.URL;
import java.util.Set;

public class DrupalExternalLinkNodeIndexer extends AbstractDrupalEntityIndexer<ExternalLink> {
    private static final Logger LOGGER = Logger.getLogger(DrupalExternalLinkNodeIndexer.class.getName());

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
            .put("drupalNodeType", this.getDrupalBundleId())
            .put("drupalExternalUrlField", this.drupalExternalUrlField)
            .put("drupalContentOverwriteField", this.drupalContentOverwriteField);
    }

    @Override
    public ExternalLink load(JSONObject json, Messages messages) {
        return ExternalLink.load(json, messages);
    }

    @Override
    protected ExternalLink harvestEntity(SearchClient client, String id, Messages messages) {
        // TODO Implement
        messages.addMessage(Messages.Level.ERROR, "RE-INDEX NOT IMPLEMENTED");
        return null;
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
        super(index, drupalUrl, drupalVersion, "node", drupalNodeType, drupalPreviewImageField);
        this.drupalExternalUrlField = drupalExternalUrlField;
        this.drupalContentOverwriteField = drupalContentOverwriteField;
    }

    @Override
    public boolean supportsIndexLatest() {
        return true;
    }

    @Override
    public ExternalLink createDrupalEntity(JSONObject jsonApiNode, Messages messages) {
        return new ExternalLink(this.getIndex(), jsonApiNode, messages);
    }

    @Override
    public Thread createIndexerThread(
            SearchClient client,
            Messages messages,
            ExternalLink externalLink,
            JSONObject jsonApiNode,
            JSONArray jsonIncluded,
            Set<String> usedThumbnails,
            int page, int current, int nodeFound) {

        return new DrupalExternalLinkNodeIndexerThread(
            client, messages, externalLink, jsonApiNode, jsonIncluded, usedThumbnails, page, current, nodeFound);
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

    // NOTE: This Thread class does NOT extends AbstractDrupalEntityIndexerThread.
    //     It's getting its content by harvesting the URL found in the node.
    //     It's very different from node / media indexation.
    public class DrupalExternalLinkNodeIndexerThread extends Thread {
        private final SearchClient client;
        private final Messages messages;
        private final ExternalLink externalLink;
        private final JSONObject jsonApiNode;
        private final JSONArray jsonIncluded;

        private final Set<String> usedThumbnails;
        private final int page;
        private final int current;
        private final int pageTotal;

        public DrupalExternalLinkNodeIndexerThread(
                SearchClient client,
                Messages messages,
                ExternalLink externalLink,
                JSONObject jsonApiNode,
                JSONArray jsonIncluded,
                Set<String> usedThumbnails,
                int page, int current, int pageTotal
        ) {
            this.client = client;
            this.messages = messages;
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
                        this.messages.addMessage(Messages.Level.WARNING, String.format("Invalid URL found for Drupal node external link %s, id: %s",
                                DrupalExternalLinkNodeIndexer.this.getDrupalBundleId(),
                                this.externalLink.getId()), ex);
                    }

                    String content = null;

                    // If there is a content overwrite, don't even attempt to download the URL
                    String contentOverwrite = DrupalExternalLinkNodeIndexer.getDrupalContentOverwrite(this.jsonApiNode, DrupalExternalLinkNodeIndexer.this.drupalContentOverwriteField);
                    if (contentOverwrite != null && !contentOverwrite.isEmpty()) {
                        content = contentOverwrite;
                    } else {

                        // Download the text content of the URL
                        try {
                            content = EntityUtils.harvestURLText(externalLinkStr, this.messages);
                        } catch (Exception ex) {
                            this.messages.addMessage(Messages.Level.WARNING, String.format("Exception occurred while harvesting URL for Drupal node external link %s, id: %s. URL %s",
                                    DrupalExternalLinkNodeIndexer.this.getDrupalBundleId(),
                                    this.externalLink.getId(),
                                    externalLinkStr), ex);
                        }
                    }

                    if (content != null) {
                        this.externalLink.setDocument(content);


                        // TODO Implement WKT
                        String wkt = "BBOX (142.5, 153.0, -10.5, -22.5)";
                        try {
                            this.externalLink.setWktAndArea(wkt);
                        } catch(ParseException ex) {
                            Messages.Message message = messages.addMessage(Messages.Level.WARNING, "Invalid WKT", ex);
                            message.addDetail(wkt);
                        }

                        // Overwrite fields to make the results look more like an external link
                        this.externalLink.setLink(externalLink);

                        // Thumbnail (aka preview image)
                        URL baseUrl = ExternalLink.getDrupalBaseUrl(this.jsonApiNode, this.messages);
                        if (baseUrl != null && DrupalExternalLinkNodeIndexer.this.getDrupalPreviewImageField() != null) {
                            String previewImageUUID = AbstractDrupalEntityIndexer.getPreviewImageUUID(this.jsonApiNode, DrupalExternalLinkNodeIndexer.this.getDrupalPreviewImageField());
                            if (previewImageUUID != null) {
                                String previewImageRelativePath = AbstractDrupalEntityIndexer.findPreviewImageRelativePath(previewImageUUID, this.jsonIncluded);
                                if (previewImageRelativePath != null) {
                                    URL thumbnailUrl = null;
                                    try {
                                        thumbnailUrl = new URL(baseUrl, previewImageRelativePath);
                                    } catch(Exception ex) {
                                        this.messages.addMessage(Messages.Level.WARNING, String.format("Exception occurred while creating a thumbnail URL for Drupal node external link %s, id: %s",
                                                DrupalExternalLinkNodeIndexer.this.getDrupalBundleId(),
                                                this.externalLink.getId()), ex);
                                    }
                                    this.externalLink.setThumbnailUrl(thumbnailUrl);

                                    // Create the thumbnail if it's missing or outdated
                                    ExternalLink oldExternalLink = DrupalExternalLinkNodeIndexer.this.safeGet(this.client, ExternalLink.class, this.externalLink.getId(), this.messages);
                                    if (this.externalLink.isThumbnailOutdated(oldExternalLink, DrupalExternalLinkNodeIndexer.this.getSafeThumbnailTTL(), DrupalExternalLinkNodeIndexer.this.getSafeBrokenThumbnailTTL(), this.messages)) {
                                        try {
                                            File cachedThumbnailFile = ImageCache.cache(thumbnailUrl, DrupalExternalLinkNodeIndexer.this.getIndex(), this.externalLink.getId(), this.messages);
                                            if (cachedThumbnailFile != null) {
                                                this.externalLink.setCachedThumbnailFilename(cachedThumbnailFile.getName());
                                            }
                                        } catch(Exception ex) {
                                            this.messages.addMessage(Messages.Level.WARNING, String.format("Exception occurred while creating a thumbnail for Drupal node external link %s, id: %s",
                                                    DrupalExternalLinkNodeIndexer.this.getDrupalBundleId(),
                                                    this.externalLink.getId()), ex);
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
                            IndexResponse indexResponse = DrupalExternalLinkNodeIndexer.this.indexEntity(client, this.externalLink, this.messages);

                            LOGGER.debug(String.format("[Page %d: %d/%d] Indexing Drupal node external link %s, id: %s, URL: %s, index response status: %s",
                                    this.page, this.current, this.pageTotal,
                                    DrupalExternalLinkNodeIndexer.this.getDrupalBundleId(),
                                    this.externalLink.getNid(),
                                    externalLinkStr,
                                    indexResponse.result()));
                        } catch(Exception ex) {
                            this.messages.addMessage(Messages.Level.WARNING, String.format("Exception occurred while indexing a Drupal node external link %s, id: %s, URL: %s",
                                    DrupalExternalLinkNodeIndexer.this.getDrupalBundleId(),
                                    this.externalLink.getId(),
                                    externalLinkStr), ex);
                        }
                    }
                }
            }

            DrupalExternalLinkNodeIndexer.this.incrementCompleted();
        }
    }
}
