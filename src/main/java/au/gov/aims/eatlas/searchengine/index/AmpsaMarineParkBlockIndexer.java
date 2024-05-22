package au.gov.aims.eatlas.searchengine.index;

import org.json.JSONObject;

public class AmpsaMarineParkBlockIndexer extends DrupalBlockIndexer {

    private String drupalParentField;

    public static AmpsaMarineParkBlockIndexer fromJSON(String index, JSONObject json) {
        if (json == null || json.isEmpty()) {
            return null;
        }

        return new AmpsaMarineParkBlockIndexer(
                index,
                json.optString("drupalUrl", null),
                json.optString("drupalVersion", null),
                json.optString("drupalBlockType", null),
                json.optString("drupalParentField", null),
                json.optString("drupalPreviewImageField", null),
                json.optString("drupalIndexedFields", null),
                json.optString("drupalGeoJSONField", null));
    }

    public JSONObject toJSON() {
        return super.toJSON()
                .put("drupalParentField", this.drupalParentField);
    }

    public AmpsaMarineParkBlockIndexer(
            String index,
            String drupalUrl,
            String drupalVersion,
            String drupalBlockType,
            String drupalParentField,
            String drupalPreviewImageField,
            String drupalIndexedFields,
            String drupalGeoJSONField) {

        super(index, drupalUrl, drupalVersion, drupalBlockType, drupalPreviewImageField, drupalIndexedFields, drupalGeoJSONField);
        this.drupalParentField = drupalParentField;
    }

    public String getDrupalParentField() {
        return this.drupalParentField;
    }

    public void setDrupalParentField(String drupalParentField) {
        this.drupalParentField = drupalParentField;
    }
}
