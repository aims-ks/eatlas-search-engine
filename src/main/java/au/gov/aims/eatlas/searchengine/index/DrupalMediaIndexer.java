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
import au.gov.aims.eatlas.searchengine.entity.DrupalMedia;
import org.json.JSONObject;

import java.util.List;
import java.util.Map;

public class DrupalMediaIndexer extends AbstractDrupalEntityIndexer<DrupalMedia> {
    private static final String DEFAULT_PREVIEW_IMAGE_FIELD = "thumbnail";

    private String drupalTitleField;

    /**
     * index: eatlas-image
     * drupalUrl: http://localhost:9090
     * drupalVersion: 9.0
     * drupalMediaType: image
     */
    public DrupalMediaIndexer(
            HttpClient httpClient,
            String index,
            String indexName,
            String drupalUrl,
            String drupalPublicUrl,
            String drupalVersion,
            String drupalMediaType,
            String drupalPreviewImageField,
            String drupalTitleField,
            String drupalIndexedFields,
            String drupalGeoJSONField
    ) {

        super(httpClient, index, indexName, drupalUrl, drupalPublicUrl, drupalVersion, "media", drupalMediaType,
                (drupalPreviewImageField == null || drupalPreviewImageField.isEmpty()) ? DEFAULT_PREVIEW_IMAGE_FIELD : drupalPreviewImageField,
                drupalIndexedFields, drupalGeoJSONField);

        this.drupalTitleField = drupalTitleField;
    }

    public static DrupalMediaIndexer fromJSON(HttpClient httpClient, String index, String indexName, JSONObject json) {
        if (json == null || json.isEmpty()) {
            return null;
        }

        return new DrupalMediaIndexer(
            httpClient, index, indexName,
            json.optString("drupalUrl", null),
            json.optString("drupalPublicUrl", null),
            json.optString("drupalVersion", null),
            json.optString("drupalMediaType", null),
            json.optString("drupalPreviewImageField", null),
            json.optString("drupalTitleField", null),
            json.optString("drupalIndexedFields", null),
            json.optString("drupalGeoJSONField", null));
    }

    public JSONObject toJSON() {
        return this.getJsonBase()
            .put("drupalMediaType", this.getDrupalBundleId())
            .put("drupalTitleField", this.drupalTitleField);
    }

    @Override
    public String getHarvestSort(boolean fullHarvest) {
        return fullHarvest ? "drupal_internal__mid" : "-changed,drupal_internal__mid";
    }

    @Override
    public DrupalMedia load(JSONObject json, AbstractLogger logger) {
        return DrupalMedia.load(json, logger);
    }

    @Override
    public boolean supportsIndexLatest() {
        return true;
    }

    @Override
    public DrupalMedia createDrupalEntity(JSONObject jsonApiMedia, Map<String, JSONObject> jsonIncluded, AbstractLogger logger) {
        DrupalMedia drupalMedia = new DrupalMedia(this, jsonApiMedia, logger);

        if (jsonApiMedia == null) {
            return drupalMedia;
        }

        drupalMedia.setTitle(this.parseDrupalTitle(jsonApiMedia, this.getDrupalTitleField()));

        List<String> textChunks = this.getIndexedFieldsContent(jsonApiMedia, jsonIncluded);
        drupalMedia.setDocument(String.join(" ", textChunks));

        return drupalMedia;

    }

    @Override
    public DrupalMedia getIndexedDrupalEntity(SearchClient searchClient, String id, AbstractLogger logger) {
        return this.safeGet(searchClient, DrupalMedia.class, id, logger);
    }

    private String parseDrupalTitle(JSONObject jsonApiMedia, String drupalTitleField) {
        if (drupalTitleField == null || drupalTitleField.isEmpty()) {
            return null;
        }
        JSONObject jsonAttributes = jsonApiMedia == null ? null : jsonApiMedia.optJSONObject("attributes");
        return jsonAttributes == null ? null : jsonAttributes.optString(drupalTitleField, null);
    }

    public String getDrupalTitleField() {
        return this.drupalTitleField;
    }

    public void setDrupalTitleField(String drupalTitleField) {
        this.drupalTitleField = drupalTitleField;
    }
}
