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

import au.gov.aims.eatlas.searchengine.HttpClient;
import au.gov.aims.eatlas.searchengine.logger.AbstractLogger;
import au.gov.aims.eatlas.searchengine.client.SearchClient;
import au.gov.aims.eatlas.searchengine.entity.ExternalLink;
import au.gov.aims.eatlas.searchengine.logger.Level;
import org.json.JSONObject;

import java.net.URL;
import java.util.Map;

public class DrupalExternalLinkNodeIndexer extends AbstractDrupalEntityIndexer<ExternalLink> {
    private String drupalExternalUrlField;
    private String drupalContentOverwriteField;

    /**
     * index: eatlas-ext-links
     * drupalUrl: http://localhost:9090
     * drupalVersion: 9.0
     * drupalNodeType: article
     */
    public DrupalExternalLinkNodeIndexer(
            HttpClient httpClient,
            String index,
            String indexName,
            String drupalUrl,
            String drupalPublicUrl,
            String drupalVersion,
            String drupalNodeType,
            String drupalPreviewImageField,
            String drupalExternalUrlField,
            String drupalContentOverwriteField,
            String drupalGeoJSONField
    ) {
        super(httpClient, index, indexName, drupalUrl, drupalPublicUrl, drupalVersion, "node", drupalNodeType, drupalPreviewImageField, null, drupalGeoJSONField);
        this.drupalExternalUrlField = drupalExternalUrlField;
        this.drupalContentOverwriteField = drupalContentOverwriteField;
    }

    public static DrupalExternalLinkNodeIndexer fromJSON(HttpClient httpClient, String index, String indexName, JSONObject json) {
        if (json == null || json.isEmpty()) {
            return null;
        }

        return new DrupalExternalLinkNodeIndexer(
            httpClient, index, indexName,
            json.optString("drupalUrl", null),
            json.optString("drupalPublicUrl", null),
            json.optString("drupalVersion", null),
            json.optString("drupalNodeType", null),
            json.optString("drupalPreviewImageField", null),
            json.optString("drupalExternalUrlField", null),
            json.optString("drupalContentOverwriteField", null),
            json.optString("drupalGeoJSONField", null));
    }

    public JSONObject toJSON() {
        return this.getJsonBase()
            .put("drupalNodeType", this.getDrupalBundleId())
            .put("drupalExternalUrlField", this.drupalExternalUrlField)
            .put("drupalContentOverwriteField", this.drupalContentOverwriteField);
    }

    @Override
    public ExternalLink load(JSONObject json, AbstractLogger logger) {
        return ExternalLink.load(json, logger);
    }

    @Override
    public String getHarvestSort(boolean fullHarvest) {
        return fullHarvest ? "drupal_internal__nid" : "-changed,drupal_internal__nid";
    }

    @Override
    public boolean supportsIndexLatest() {
        return true;
    }

    @Override
    public ExternalLink createDrupalEntity(JSONObject jsonApiNode, Map<String, JSONObject> jsonIncluded, AbstractLogger logger) {
        return new ExternalLink(this, jsonApiNode, logger);
    }

    @Override
    protected boolean parseJsonDrupalEntity(SearchClient searchClient, JSONObject jsonApiNode, Map<String, JSONObject> jsonIncluded, ExternalLink externalLink, AbstractLogger logger) {
        HttpClient httpClient = this.getHttpClient();

        if (this.drupalExternalUrlField != null) {
            String externalLinkStr = DrupalExternalLinkNodeIndexer.getExternalLink(jsonApiNode, this.drupalExternalUrlField);
            if (externalLinkStr != null && !externalLinkStr.isEmpty()) {
                URL externalLinkUrl = null;
                try {
                    externalLinkUrl = new URL(externalLinkStr);
                } catch(Exception ex) {
                    logger.addMessage(Level.WARNING, String.format("Invalid URL found for Drupal node external link %s, id: %s",
                            this.getDrupalBundleId(),
                            externalLink.getId()), ex);
                }

                String content = null;

                // If there is a content overwrite, don't even attempt to download the URL
                String contentOverwrite = DrupalExternalLinkNodeIndexer.getDrupalContentOverwrite(jsonApiNode, this.drupalContentOverwriteField);
                if (contentOverwrite != null && !contentOverwrite.isEmpty()) {
                    content = contentOverwrite;
                } else {

                    // Download the text content of the URL
                    try {
                        HttpClient.Response response = httpClient.getRequest(externalLinkStr, logger);
                        content = response.extractText();
                    } catch (Exception ex) {
                        logger.addMessage(Level.WARNING, String.format("Exception occurred while harvesting URL for Drupal node external link %s, id: %s. URL %s",
                                this.getDrupalBundleId(),
                                externalLink.getId(),
                                externalLinkStr), ex);
                    }
                }

                if (content != null) {
                    externalLink.setDocument(content);

                    // Overwrite fields to make the results look more like an external link
                    externalLink.setLink(externalLinkUrl);

                    return super.parseJsonDrupalEntity(searchClient, jsonApiNode, jsonIncluded, externalLink, logger);
                }
            }
        }

        return false;
    }

    @Override
    public ExternalLink getIndexedDrupalEntity(SearchClient searchClient, String id, AbstractLogger logger) {
        return this.safeGet(searchClient, ExternalLink.class, id, logger);
    }

    private static String getExternalLink(JSONObject jsonApiNode, String externalLinkField) {
        if (externalLinkField == null || externalLinkField.isEmpty()) {
            return null;
        }
        JSONObject jsonAttributes = jsonApiNode == null ? null : jsonApiNode.optJSONObject("attributes");
        if (jsonAttributes == null) {
            return null;
        }
        JSONObject jsonExternalLink = jsonAttributes.optJSONObject(externalLinkField);
        if (jsonExternalLink == null) {
            // URL provided as a String
            return jsonAttributes.optString(externalLinkField, null);
        }

        // URL provided as a URL field
        return jsonExternalLink.optString("uri", null);
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
}
