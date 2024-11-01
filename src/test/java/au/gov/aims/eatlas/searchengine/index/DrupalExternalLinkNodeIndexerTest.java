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

public class DrupalExternalLinkNodeIndexerTest extends IndexerTestBase {

    @Test
    public void testIndexLinks() throws Exception {
        try (MockSearchClient searchClient = this.createMockSearchClient()) {
            MockHttpClient mockHttpClient = this.getMockHttpClient();

            mockHttpClient.addGetUrl("https://domain.com/jsonapi/node/external_link?sort=-changed&page%5Blimit%5D=50&page%5Boffset%5D=0&filter%5Bstatus%5D=1", "drupalLinkFiles/jsonapi/node/external_link.json");

            Assertions.assertEquals(HealthStatus.Green, searchClient.getHealthStatus(), "The Elastic Search engine health status is not Green before starting the test.");

            String index = "links";
            AbstractLogger logger = ConsoleLogger.getInstance();

            searchClient.createIndex(index);

            // Find the indexer, defined in the config file
            DrupalExternalLinkNodeIndexer drupalExternalLinkNodeIndexer =
                    (DrupalExternalLinkNodeIndexer)this.getConfig().getIndexer(index);

            drupalExternalLinkNodeIndexer.internalIndex(searchClient, null, logger);

            // Wait for ElasticSearch to finish its indexation
            searchClient.refresh(index);

            SearchResults results = null;
            try {
                String q = ""; // Search for an empty string, to get all the blocks
                Integer start = 0;
                Integer hits = 50; // There is only 2 documents in the index
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

                Assertions.assertEquals(2, layersIndexSummary.getHits(),
                        "Wrong number of search result in the index summary.");

                Assertions.assertEquals(2, searchSummary.getHits(),
                        "Wrong total number of search result in the index summary.");

            } catch(Exception ex) {
                Assertions.fail("Exception thrown while testing the Search API.", ex);
            }

            Assertions.assertEquals(HealthStatus.Green, searchClient.getHealthStatus(), "The Elastic Search engine health status is not Green after the test.");
        }
    }
}
