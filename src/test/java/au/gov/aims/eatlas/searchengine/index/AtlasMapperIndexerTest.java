package au.gov.aims.eatlas.searchengine.index;

import au.gov.aims.eatlas.searchengine.MockHttpClient;
import au.gov.aims.eatlas.searchengine.admin.rest.Messages;
import au.gov.aims.eatlas.searchengine.client.SearchClient;
import au.gov.aims.eatlas.searchengine.rest.Search;
import au.gov.aims.eatlas.searchengine.search.IndexSummary;
import au.gov.aims.eatlas.searchengine.search.SearchResults;
import au.gov.aims.eatlas.searchengine.search.Summary;
import co.elastic.clients.elasticsearch._types.HealthStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AtlasMapperIndexerTest extends IndexerTestBase {

    @Test
    public void testIndexLayers() throws Exception {
        try (SearchClient client = this.createElasticsearchClient()) {
            MockHttpClient mockHttpClient = this.getMockHttpClient();

            Map<String, String> urlMap = new HashMap<>();
            urlMap.put("https://domain.com/config/main.json", "atlasmapperFiles/config/main.json");
            urlMap.put("https://domain.com/config/layers.json", "atlasmapperFiles/config/layers.json");
            mockHttpClient.setUrlMap(urlMap);

            Assertions.assertEquals(HealthStatus.Green, client.getHealthStatus(), "The Elastic Search engine health status is not Green before starting the test.");

            String index = "atlasmapper";
            Messages messages = Messages.getInstance(null);

            client.createIndex(index);

            // Find the indexer, defined in the config file
            AtlasMapperIndexer atlasMapperIndexer =
                    (AtlasMapperIndexer)this.getConfig().getIndexer(index);

            atlasMapperIndexer.internalIndex(client, null, messages);

            // Wait for ElasticSearch to finish its indexation
            client.refresh(index);

            // Check indexed documents
            SearchResults results = null;
            try {
                String q = "coral"; // Search for the word "coral", present in multiple layers
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

                Assertions.assertEquals(144, layersIndexSummary.getHits(),
                        "Wrong number of search result in the index summary.");

            } catch(Exception ex) {
                Assertions.fail("Exception thrown while testing the Search API.", ex);
            }

            Assertions.assertEquals(HealthStatus.Green, client.getHealthStatus(), "The Elastic Search engine health status is not Green after the test.");
        }
    }
}
