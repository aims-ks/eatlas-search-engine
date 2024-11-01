package au.gov.aims.eatlas.searchengine.index;

import au.gov.aims.eatlas.searchengine.MockHttpClient;
import au.gov.aims.eatlas.searchengine.logger.ConsoleLogger;
import au.gov.aims.eatlas.searchengine.logger.AbstractLogger;
import au.gov.aims.eatlas.searchengine.rest.Search;
import au.gov.aims.eatlas.searchengine.search.IndexSummary;
import au.gov.aims.eatlas.searchengine.search.SearchResults;
import au.gov.aims.eatlas.searchengine.search.Summary;
import co.elastic.clients.elasticsearch._types.HealthStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

public class DrupalMediaIndexerTest extends IndexerTestBase {

    @Test
    public void testIndexImages() throws Exception {
        try (MockSearchClient searchClient = this.createMockSearchClient()) {
            MockHttpClient mockHttpClient = this.getMockHttpClient();

            mockHttpClient.addGetUrl("https://domain.com/jsonapi/media/image?sort=-changed&page%5Blimit%5D=50&page%5Boffset%5D=0&filter%5Bstatus%5D=1", "drupalImageFiles/jsonapi/media/image.json");

            mockHttpClient.addGetUrl("https://domain.com/jsonapi/media/image/7b99cf2d-1539-413f-8e97-fc3e91610367?include=thumbnail&filter%5Bstatus%5D=1", "drupalImageFiles/jsonapi/media/image/7b99cf2d-1539-413f-8e97-fc3e91610367.json");
            mockHttpClient.addGetUrl("https://domain.com/jsonapi/media/image/47ce7ed4-bb0b-4dcb-9f3a-8b9c41917dd4?include=thumbnail&filter%5Bstatus%5D=1", "drupalImageFiles/jsonapi/media/image/47ce7ed4-bb0b-4dcb-9f3a-8b9c41917dd4.json");
            mockHttpClient.addGetUrl("https://domain.com/jsonapi/media/image/e3727cb7-0a17-465d-8dd7-cac5d2b10e47?include=thumbnail&filter%5Bstatus%5D=1", "drupalImageFiles/jsonapi/media/image/e3727cb7-0a17-465d-8dd7-cac5d2b10e47.json");
            mockHttpClient.addGetUrl("https://domain.com/jsonapi/media/image/eae1f7db-bf10-4be5-957d-ece17b2b1ae7?include=thumbnail&filter%5Bstatus%5D=1", "drupalImageFiles/jsonapi/media/image/eae1f7db-bf10-4be5-957d-ece17b2b1ae7.json");
            mockHttpClient.addGetUrl("https://domain.com/jsonapi/media/image/f4c8e050-b15b-424a-a90b-bca294922f9e?include=thumbnail&filter%5Bstatus%5D=1", "drupalImageFiles/jsonapi/media/image/f4c8e050-b15b-424a-a90b-bca294922f9e.json");

            Assertions.assertEquals(HealthStatus.Green, searchClient.getHealthStatus(), "The Elastic Search engine health status is not Green before starting the test.");

            String index = "images";
            AbstractLogger logger = ConsoleLogger.getInstance();

            searchClient.createIndex(index);

            // Find the indexer, defined in the config file
            DrupalMediaIndexer drupalMediaIndexer =
                    (DrupalMediaIndexer)this.getConfig().getIndexer(index);

            drupalMediaIndexer.internalIndex(searchClient, null, logger);

            // Wait for ElasticSearch to finish its indexation
            searchClient.refresh(index);

            SearchResults results = null;
            try {
                String q = ""; // Search for an empty string, to get all the blocks
                Integer start = 0;
                Integer hits = 50;
                String wkt = null; // No geographic filtering
                List<String> idx = List.of(index);

                results = Search.paginationSearch(searchClient, q, start, hits, wkt, idx, null, logger);

                Summary searchSummary = results.getSummary();

                // Check indexes in the summary.
                Map<String, IndexSummary> indexSummaryMap = searchSummary.getIndexSummaries();
                Assertions.assertEquals(1, indexSummaryMap.size(),
                        "Wrong number if index summary.");
                Assertions.assertTrue(indexSummaryMap.containsKey(index),
                        String.format("Missing index from the search summary: %s", index));

                IndexSummary layersIndexSummary = searchSummary.getIndexSummary(index);

                Assertions.assertEquals(5, layersIndexSummary.getHits(),
                        "Wrong number of search result in the index summary.");

                Assertions.assertEquals(5, searchSummary.getHits(),
                        "Wrong total number of search result in the index summary.");

            } catch(Exception ex) {
                Assertions.fail("Exception thrown while testing the Search API.", ex);
            }

            Assertions.assertEquals(HealthStatus.Green, searchClient.getHealthStatus(), "The Elastic Search engine health status is not Green after the test.");
        }
    }
}
