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
import org.json.JSONObject;

import java.util.List;
import java.util.Map;

public class DrupalNodeIndexer extends AbstractDrupalEntityIndexer<DrupalNode> {

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
            json.optString("drupalIndexedFields", null),
            json.optString("drupalWktField", null));
    }

    public JSONObject toJSON() {
        return this.getJsonBase()
            .put("drupalNodeType", this.getDrupalBundleId());
    }

    @Override
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
            String drupalIndexedFields,
            String drupalWktField) {

        super(index, drupalUrl, drupalVersion, "node", drupalNodeType, drupalPreviewImageField, drupalIndexedFields, drupalWktField);
    }

    @Override
    public boolean supportsIndexLatest() {
        return true;
    }

    @Override
    public DrupalNode createDrupalEntity(JSONObject jsonApiNode, Map<String, JSONObject> jsonIncluded, Messages messages) {
        DrupalNode drupalNode = new DrupalNode(this.getIndex(), jsonApiNode, messages);

        if (jsonApiNode == null) {
            return drupalNode;
        }

        List<String> textChunks = this.getIndexedFieldsContent(jsonApiNode, jsonIncluded);
        drupalNode.setDocument(String.join(" ", textChunks));

        return drupalNode;
    }

    @Override
    public DrupalNode getIndexedDrupalEntity(SearchClient client, String id, Messages messages) {
        return this.safeGet(client, DrupalNode.class, id, messages);
    }
}
