package au.gov.aims.eatlas.searchengine.index;

import org.json.JSONObject;

public class AmpsaNetworkBlockIndexer extends DrupalBlockIndexer {

    public static AmpsaNetworkBlockIndexer fromJSON(String index, JSONObject json) {
        if (json == null || json.isEmpty()) {
            return null;
        }

        return new AmpsaNetworkBlockIndexer(
                index,
                json.optString("drupalUrl", null),
                json.optString("drupalVersion", null),
                json.optString("drupalBlockType", null),
                json.optString("drupalPreviewImageField", null),
                json.optString("drupalIndexedFields", null),
                json.optString("drupalGeoJSONField", null));
    }

    public JSONObject toJSON() {
        return super.toJSON();
    }

    public AmpsaNetworkBlockIndexer(
            String index,
            String drupalUrl,
            String drupalVersion,
            String drupalBlockType,
            String drupalPreviewImageField,
            String drupalIndexedFields,
            String drupalGeoJSONField) {

        super(index, drupalUrl, drupalVersion, drupalBlockType, drupalPreviewImageField, drupalIndexedFields, drupalGeoJSONField);
    }
}
