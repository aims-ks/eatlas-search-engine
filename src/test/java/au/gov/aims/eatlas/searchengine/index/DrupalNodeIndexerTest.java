package au.gov.aims.eatlas.searchengine.index;

import au.gov.aims.eatlas.searchengine.MockHttpClient;
import au.gov.aims.eatlas.searchengine.admin.rest.Messages;
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

public class DrupalNodeIndexerTest extends IndexerTestBase {

    @Test
    public void testIndexArticles() throws Exception {
        try (MockSearchClient searchClient = this.createMockSearchClient()) {
            MockHttpClient mockHttpClient = this.getMockHttpClient();

            Map<String, String> urlMap = new HashMap<>();
            urlMap.put("https://domain.com/jsonapi/node/article?sort=-changed&page%5Blimit%5D=50&page%5Boffset%5D=0&filter%5Bstatus%5D=1", "drupalArticleFiles/jsonapi/node/article.json");
            mockHttpClient.setUrlMap(urlMap);

            Assertions.assertEquals(HealthStatus.Green, searchClient.getHealthStatus(), "The Elastic Search engine health status is not Green before starting the test.");

            String index = "articles";
            Messages messages = Messages.getInstance(null);

            searchClient.createIndex(index);

            // Find the indexer, defined in the config file
            DrupalNodeIndexer drupalNodeIndexer =
                    (DrupalNodeIndexer)this.getConfig().getIndexer(index);

            drupalNodeIndexer.internalIndex(searchClient, null, messages);

            // Wait for ElasticSearch to finish its indexation
            searchClient.refresh(index);

            SearchResults results = null;
            try {
                String q = ""; // Search for an empty string, to get all the blocks
                Integer start = 0;
                Integer hits = 50;
                String wkt = null; // No geographic filtering
                List<String> idx = List.of(index);

                results = Search.paginationSearch(searchClient, q, start, hits, wkt, idx, null, messages);

                Summary searchSummary = results.getSummary();

                // Check indexes in the summary.
                Map<String, IndexSummary> indexSummaryMap = searchSummary.getIndexSummaries();
                Assertions.assertEquals(1, indexSummaryMap.size(),
                        "Wrong number if index summary.");
                Assertions.assertTrue(indexSummaryMap.containsKey(index),
                        String.format("Missing index from the search summary: %s", index));

                IndexSummary layersIndexSummary = searchSummary.getIndexSummary(index);

                Assertions.assertEquals(50, layersIndexSummary.getHits(),
                        "Wrong number of search result in the index summary.");

            } catch(Exception ex) {
                Assertions.fail("Exception thrown while testing the Search API.", ex);
            }

            Assertions.assertEquals(HealthStatus.Green, searchClient.getHealthStatus(), "The Elastic Search engine health status is not Green after the test.");
        }
    }
}
