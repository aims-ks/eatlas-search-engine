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
import org.apache.http.client.utils.URIBuilder;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Set;

public class DrupalNodeIndexer extends AbstractDrupalEntityIndexer<DrupalNode> {
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

    public String getDrupalPrepressField() {
        return this.drupalPrepressField;
    }

    public void setDrupalPrepressField(String drupalPrepressField) {
        this.drupalPrepressField = drupalPrepressField;
    }

    public class DrupalNodeIndexerThread extends AbstractDrupalEntityIndexerThread {
        public DrupalNodeIndexerThread(
                SearchClient client,
                Messages messages,
                DrupalNode drupalNode,
                JSONObject jsonApiNode,
                JSONArray jsonIncluded,
                Set<String> usedThumbnails,
                int page, int current, int pageTotal
        ) {
            super(client, messages, drupalNode, jsonApiNode, jsonIncluded, usedThumbnails, page, current, pageTotal);
        }

        public DrupalNode getIndexedDrupalEntity() {
            return DrupalNodeIndexer.this.safeGet(this.getClient(), DrupalNode.class, this.getDrupalEntity().getId(), this.getMessages());
        }
    }
}
