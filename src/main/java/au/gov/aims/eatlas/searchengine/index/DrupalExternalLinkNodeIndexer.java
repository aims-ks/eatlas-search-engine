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
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URL;

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
    protected boolean parseJsonDrupalEntity(SearchClient client, JSONObject jsonApiNode, JSONArray jsonIncluded, ExternalLink externalLink, Messages messages) {
        if (this.drupalExternalUrlField != null) {
            String externalLinkStr = DrupalExternalLinkNodeIndexer.getExternalLink(jsonApiNode, this.drupalExternalUrlField);
            if (externalLinkStr != null && !externalLinkStr.isEmpty()) {
                URL externalLinkUrl = null;
                try {
                    externalLinkUrl = new URL(externalLinkStr);
                } catch(Exception ex) {
                    messages.addMessage(Messages.Level.WARNING, String.format("Invalid URL found for Drupal node external link %s, id: %s",
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
                        content = EntityUtils.harvestURLText(externalLinkStr, messages);
                    } catch (Exception ex) {
                        messages.addMessage(Messages.Level.WARNING, String.format("Exception occurred while harvesting URL for Drupal node external link %s, id: %s. URL %s",
                                this.getDrupalBundleId(),
                                externalLink.getId(),
                                externalLinkStr), ex);
                    }
                }

                if (content != null) {
                    externalLink.setDocument(content);

                    // Overwrite fields to make the results look more like an external link
                    externalLink.setLink(externalLinkUrl);

                    return super.parseJsonDrupalEntity(client, jsonApiNode, jsonIncluded, externalLink, messages);
                }
            }
        }

        return false;
    }

    @Override
    public ExternalLink getIndexedDrupalEntity(SearchClient client, String id, Messages messages) {
        return this.safeGet(client, ExternalLink.class, id, messages);
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
}
