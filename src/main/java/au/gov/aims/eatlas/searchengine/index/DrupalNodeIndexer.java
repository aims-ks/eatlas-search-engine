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
import au.gov.aims.eatlas.searchengine.entity.EntityUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
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

        JSONObject jsonAttributes = jsonApiNode.optJSONObject("attributes");

        // Indexed fields (i.e. body)
        List<String> textChunks = new ArrayList<>();
        List<String> indexedFields = AbstractDrupalEntityIndexer.splitIndexedFields(this.getDrupalIndexedFields());
        if (!indexedFields.isEmpty()) {
            for (String indexedField : indexedFields) {
                // Standard body field
                if ("body".equals(indexedField)) {
                    JSONObject jsonBody = jsonAttributes == null ? null : jsonAttributes.optJSONObject("body");
                    if (jsonBody != null) {
                        textChunks.add(EntityUtils.extractHTMLTextContent(jsonBody.optString("processed", null)));
                    }
                } else {
                    // Other fields
                    String fieldType = AbstractDrupalEntityIndexer.getFieldType(jsonApiNode, indexedField);
                    if (fieldType != null) {
                        if ("paragraph".equals(fieldType)) {
                            // The field is type Paragraphs.
                            // It contains an array of Paragraph fields.
                            // Loop through each paragraph field, extract the texts and add them to the list of chunks.
                            List<String> paragraphsTexts = this.parseParagraphsField(jsonApiNode, jsonIncluded, indexedField);
                            if (paragraphsTexts != null && !paragraphsTexts.isEmpty()) {
                                textChunks.addAll(paragraphsTexts);
                            }
                        }
                    }
                }
            }
        }
        drupalNode.setDocument(String.join(" ", textChunks));

        return drupalNode;
    }

    private List<String> parseParagraphsField(JSONObject jsonApiNode, Map<String, JSONObject> jsonIncluded, String field) {
        List<String> texts = new ArrayList<>();

        // Returns: relationships.field_preview.data.id
        JSONObject jsonRelationships = jsonApiNode.optJSONObject("relationships");
        JSONObject jsonRelField = jsonRelationships == null ? null : jsonRelationships.optJSONObject(field);
        JSONArray jsonRelFieldDataArray = jsonRelField == null ? null : jsonRelField.optJSONArray("data");
        if (jsonRelFieldDataArray != null) {
            for (int i=0; i<jsonRelFieldDataArray.length(); i++) {
                JSONObject jsonRelFieldData = jsonRelFieldDataArray.optJSONObject(i);
                String jsonRelFieldDataUUID = jsonRelFieldData == null ? null : jsonRelFieldData.optString("id", null);
                List<String> paragraphTexts = this.parseParagraphFromJsonIncluded(jsonRelFieldDataUUID, jsonIncluded);
                if (paragraphTexts != null && !paragraphTexts.isEmpty()) {
                    texts.addAll(paragraphTexts);
                }
            }
        }

        return texts;
    }

    private List<String> parseParagraphFromJsonIncluded(String paragraphUUID, Map<String, JSONObject> jsonIncluded) {
        List<String> texts = new ArrayList<>();

        JSONObject jsonParagraph = jsonIncluded.get(paragraphUUID);
        if (jsonParagraph != null) {
            JSONObject jsonParAttributes = jsonParagraph.optJSONObject("attributes");
            if (jsonParAttributes != null) {
                for (String key : jsonParAttributes.keySet()) {
                    if (key != null && key.startsWith("field_")) {
                        List<String> paragraphFieldTexts = this.parseParagraphFieldTexts(jsonParAttributes.opt(key));
                        if (paragraphFieldTexts != null && !paragraphFieldTexts.isEmpty()) {
                            texts.addAll(paragraphFieldTexts);
                        }
                    }
                }
            }
        }

        return texts;
    }

    private List<String> parseParagraphFieldTexts(Object paragraphFieldObj) {
        List<String> paragraphTexts = new ArrayList<>();

        if (paragraphFieldObj instanceof JSONArray) {
            JSONArray paragraphFieldArray = (JSONArray)paragraphFieldObj;
            for (int i=0; i<paragraphFieldArray.length(); i++) {
                Object paragraphFieldSubObj = paragraphFieldArray.opt(i);
                paragraphTexts.addAll(this.parseParagraphFieldTexts(paragraphFieldSubObj));
            }

        } else if (paragraphFieldObj instanceof JSONObject) {
            JSONObject paragraphField = (JSONObject)paragraphFieldObj;
            String ckeditorText = paragraphField.optString("processed", null);
            if (ckeditorText != null) {
                paragraphTexts.add(EntityUtils.extractHTMLTextContent(ckeditorText));
            }

        } else if (paragraphFieldObj instanceof String) {
            paragraphTexts.add((String)paragraphFieldObj);
        }

        return paragraphTexts;
    }

    @Override
    public DrupalNode getIndexedDrupalEntity(SearchClient client, String id, Messages messages) {
        return this.safeGet(client, DrupalNode.class, id, messages);
    }
}
