package au.gov.aims.eatlas.searchengine.index;

import au.gov.aims.eatlas.searchengine.admin.rest.Messages;
import au.gov.aims.eatlas.searchengine.client.SearchClient;
import au.gov.aims.eatlas.searchengine.entity.DrupalBlock;
import org.json.JSONObject;

import java.util.List;
import java.util.Map;

public class DrupalBlockIndexer extends AbstractDrupalEntityIndexer<DrupalBlock> {

    public static DrupalBlockIndexer fromJSON(String index, JSONObject json) {
        if (json == null || json.isEmpty()) {
            return null;
        }

        return new DrupalBlockIndexer(
            index,
            json.optString("drupalUrl", null),
            json.optString("drupalVersion", null),
            json.optString("drupalBlockType", null),
            json.optString("drupalPreviewImageField", null),
            json.optString("drupalIndexedFields", null),
            json.optString("drupalGeoJSONField", null));
    }

    public JSONObject toJSON() {
        return this.getJsonBase()
            .put("drupalBlockType", this.getDrupalBundleId());
    }

    @Override
    public DrupalBlock load(JSONObject json, Messages messages) {
        return DrupalBlock.load(json, messages);
    }

    /**
     * index: eatlas-article
     * drupalUrl: http://localhost:9090
     * drupalVersion: 9.0
     * drupalNodeType: article
     */
    public DrupalBlockIndexer(
            String index,
            String drupalUrl,
            String drupalVersion,
            String drupalBlockType,
            String drupalPreviewImageField,
            String drupalIndexedFields,
            String drupalGeoJSONField) {

        super(index, drupalUrl, drupalVersion, "block_content", drupalBlockType, drupalPreviewImageField, drupalIndexedFields, drupalGeoJSONField);
    }

    @Override
    public String getHarvestSort(boolean fullHarvest) {
        return fullHarvest ? "drupal_internal__id" : "-changed,drupal_internal__id";
    }

    @Override
    public boolean supportsIndexLatest() {
        return true;
    }

    @Override
    public DrupalBlock createDrupalEntity(JSONObject jsonApiBlock, Map<String, JSONObject> jsonIncluded, Messages messages) {
        DrupalBlock drupalBlock = new DrupalBlock(this.getIndex(), jsonApiBlock, messages);

        if (jsonApiBlock == null) {
            return drupalBlock;
        }

        List<String> textChunks = this.getIndexedFieldsContent(jsonApiBlock, jsonIncluded);
        drupalBlock.setDocument(String.join(" ", textChunks));

        return drupalBlock;
    }

    @Override
    public DrupalBlock getIndexedDrupalEntity(SearchClient client, String id, Messages messages) {
        return this.safeGet(client, DrupalBlock.class, id, messages);
    }
}
