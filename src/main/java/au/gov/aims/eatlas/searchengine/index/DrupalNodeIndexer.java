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

import au.gov.aims.eatlas.searchengine.HttpClient;
import au.gov.aims.eatlas.searchengine.logger.AbstractLogger;
import au.gov.aims.eatlas.searchengine.client.SearchClient;
import au.gov.aims.eatlas.searchengine.entity.DrupalNode;
import org.json.JSONObject;

import java.util.List;
import java.util.Map;

public class DrupalNodeIndexer extends AbstractDrupalEntityIndexer<DrupalNode> {

    /**
     * index: eatlas-article
     * drupalUrl: http://localhost:9090
     * drupalVersion: 9.0
     * drupalNodeType: article
     */
    public DrupalNodeIndexer(
            HttpClient httpClient,
            String index,
            String indexName,
            String drupalUrl,
            String drupalVersion,
            String drupalNodeType,
            String drupalPreviewImageField,
            String drupalIndexedFields,
            String drupalGeoJSONField) {

        super(httpClient, index, indexName, drupalUrl, drupalVersion, "node", drupalNodeType, drupalPreviewImageField, drupalIndexedFields, drupalGeoJSONField);
    }

    public static DrupalNodeIndexer fromJSON(HttpClient httpClient, String index, String indexName, JSONObject json) {
        if (json == null || json.isEmpty()) {
            return null;
        }

        return new DrupalNodeIndexer(
            httpClient, index, indexName,
            json.optString("drupalUrl", null),
            json.optString("drupalVersion", null),
            json.optString("drupalNodeType", null),
            json.optString("drupalPreviewImageField", null),
            json.optString("drupalIndexedFields", null),
            json.optString("drupalGeoJSONField", null));
    }

    public JSONObject toJSON() {
        return this.getJsonBase()
            .put("drupalNodeType", this.getDrupalBundleId());
    }

    @Override
    public DrupalNode load(JSONObject json, AbstractLogger logger) {
        return DrupalNode.load(json, logger);
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
    public DrupalNode createDrupalEntity(JSONObject jsonApiNode, Map<String, JSONObject> jsonIncluded, AbstractLogger logger) {
        DrupalNode drupalNode = new DrupalNode(this.getIndex(), jsonApiNode, logger);

        if (jsonApiNode == null) {
            return drupalNode;
        }

        List<String> textChunks = this.getIndexedFieldsContent(jsonApiNode, jsonIncluded);
        drupalNode.setDocument(String.join(" ", textChunks));

        return drupalNode;
    }

    @Override
    public DrupalNode getIndexedDrupalEntity(SearchClient searchClient, String id, AbstractLogger logger) {
        return this.safeGet(searchClient, DrupalNode.class, id, logger);
    }
}
