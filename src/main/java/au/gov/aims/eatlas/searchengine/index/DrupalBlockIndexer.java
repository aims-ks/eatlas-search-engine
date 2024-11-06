package au.gov.aims.eatlas.searchengine.index;

import au.gov.aims.eatlas.searchengine.HttpClient;
import au.gov.aims.eatlas.searchengine.logger.AbstractLogger;
import au.gov.aims.eatlas.searchengine.client.SearchClient;
import au.gov.aims.eatlas.searchengine.entity.DrupalBlock;
import org.json.JSONObject;

import java.util.List;
import java.util.Map;

public class DrupalBlockIndexer extends AbstractDrupalEntityIndexer<DrupalBlock> {

    /**
     * index: eatlas-block
     * drupalUrl: http://localhost:9090
     * drupalVersion: 9.0
     * drupalNodeType: article
     */
    public DrupalBlockIndexer(
            HttpClient httpClient,
            String index,
            String indexName,
            String drupalUrl,
            String drupalVersion,
            String drupalBlockType,
            String drupalPreviewImageField,
            String drupalIndexedFields,
            String drupalGeoJSONField) {

        super(httpClient, index, indexName, drupalUrl, drupalVersion, "block_content", drupalBlockType, drupalPreviewImageField, drupalIndexedFields, drupalGeoJSONField);
    }

    public static DrupalBlockIndexer fromJSON(HttpClient httpClient, String index, String indexName, JSONObject json) {
        if (json == null || json.isEmpty()) {
            return null;
        }

        return new DrupalBlockIndexer(
            httpClient, index, indexName,
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
    public DrupalBlock load(JSONObject json, AbstractLogger logger) {
        return DrupalBlock.load(json, logger);
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
    public DrupalBlock createDrupalEntity(JSONObject jsonApiBlock, Map<String, JSONObject> jsonIncluded, AbstractLogger logger) {
        DrupalBlock drupalBlock = new DrupalBlock(this.getIndex(), jsonApiBlock, logger);

        if (jsonApiBlock == null) {
            return drupalBlock;
        }

        List<String> textChunks = this.getIndexedFieldsContent(jsonApiBlock, jsonIncluded);
        drupalBlock.setDocument(String.join(" ", textChunks));

        return drupalBlock;
    }

    @Override
    public DrupalBlock getIndexedDrupalEntity(SearchClient searchClient, String id, AbstractLogger logger) {
        return this.safeGet(searchClient, DrupalBlock.class, id, logger);
    }
}
