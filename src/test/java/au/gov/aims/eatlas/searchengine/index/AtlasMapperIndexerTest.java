package au.gov.aims.eatlas.searchengine.index;

import au.gov.aims.eatlas.searchengine.admin.rest.Messages;
import au.gov.aims.eatlas.searchengine.client.SearchClient;
import au.gov.aims.eatlas.searchengine.rest.Search;
import au.gov.aims.eatlas.searchengine.search.IndexSummary;
import au.gov.aims.eatlas.searchengine.search.SearchResults;
import au.gov.aims.eatlas.searchengine.search.Summary;
import co.elastic.clients.elasticsearch._types.HealthStatus;
import org.json.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class AtlasMapperIndexerTest extends IndexerTestBase {
    @Test
    public void testIndexLayers() throws IOException {
        try (SearchClient client = this.createElasticsearchClient()) {
            Assertions.assertEquals(HealthStatus.Green, client.getHealthStatus(), "The Elastic Search engine health status is not Green before starting the test.");

            String index = "atlasmapper"; // TODO Define index
            Messages messages = Messages.getInstance(null);

            client.createIndex(index);

            // Find the indexer, defined in the config file
            AtlasMapperIndexer atlasMapperIndexer =
                    (AtlasMapperIndexer)this.getConfig().getIndexer(index);

            // Get AtlasMapper config files
            String mainConfigPath = "atlasmapperFiles/main.json";
            String mainConfigText = IndexerTestBase.getResourceFileContent(mainConfigPath);
            Assertions.assertNotNull(mainConfigText, String.format("AtlasMapper main config is null: %s", mainConfigPath));
            JSONObject jsonMainConfig = new JSONObject(mainConfigText);
            Assertions.assertNotNull(jsonMainConfig, String.format("AtlasMapper main config is empty: %s", mainConfigPath));

            String layersConfigPath = "atlasmapperFiles/layers.json";
            String layersConfigText = IndexerTestBase.getResourceFileContent(layersConfigPath);
            Assertions.assertNotNull(layersConfigText, String.format("AtlasMapper layers config is null: %s", layersConfigPath));
            JSONObject jsonLayersConfig = new JSONObject(layersConfigText);
            Assertions.assertNotNull(layersConfigText, String.format("AtlasMapper layers config is empty: %s", layersConfigPath));

            atlasMapperIndexer.indexLayers(client, messages, jsonMainConfig, jsonLayersConfig, false);

            // Wait for ElasticSearch to finish its indexation
            client.refresh(index);

            // TODO CHECK
            SearchResults results = null;
            try {
                String q = "of"; // Search for the word "of", present in both external link entities
                Integer start = 0;
                Integer hits = 50; // There is only 2 documents in the index
                String wkt = null; // No geographic filtering
                List<String> idx = List.of(index);

                results = Search.paginationSearch(client, q, start, hits, wkt, idx, null, messages);

                Summary searchSummary = results.getSummary();

                // Check indexes in the summary.
                Map<String, IndexSummary> indexSummaryMap = searchSummary.getIndexSummaries();
                Assertions.assertEquals(1, indexSummaryMap.size(),
                        "Wrong number if index summary.");
                Assertions.assertTrue(indexSummaryMap.containsKey(index),
                        String.format("Missing index from the search summary: %s", index));

                IndexSummary layersIndexSummary = searchSummary.getIndexSummary(index);

                Assertions.assertEquals(1523, layersIndexSummary.getHits(),
                        "Wrong number of search result in the index summary.");

            } catch(Exception ex) {
                Assertions.fail("Exception thrown while testing the Search API.", ex);
            }

            Assertions.assertEquals(HealthStatus.Green, client.getHealthStatus(), "The Elastic Search engine health status is not Green after the test.");
        }
    }
}
