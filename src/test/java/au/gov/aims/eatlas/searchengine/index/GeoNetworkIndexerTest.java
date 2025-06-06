package au.gov.aims.eatlas.searchengine.index;

import au.gov.aims.eatlas.searchengine.MockHttpClient;
import au.gov.aims.eatlas.searchengine.logger.ConsoleLogger;
import au.gov.aims.eatlas.searchengine.logger.AbstractLogger;
import au.gov.aims.eatlas.searchengine.rest.Search;
import au.gov.aims.eatlas.searchengine.search.IndexSummary;
import au.gov.aims.eatlas.searchengine.search.SearchResult;
import au.gov.aims.eatlas.searchengine.search.SearchResults;
import au.gov.aims.eatlas.searchengine.search.Summary;
import co.elastic.clients.elasticsearch._types.HealthStatus;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class GeoNetworkIndexerTest extends IndexerTestBase {

    @Test
    public void testIndexMetadataRecords() throws Exception {
        try (MockSearchClient searchClient = this.createMockSearchClient()) {
            MockHttpClient mockHttpClient = this.getMockHttpClient();

            // Search page
            mockHttpClient.addGetUrl("https://domain.com/geonetwork/srv/eng/xml.search?from=1", "metadataRecords/search.xml");
            // Metadata records
            mockHttpClient.addGetUrl("https://domain.com/geonetwork/srv/eng/xml.metadata.get?uuid=09ac8e36-5d65-40f9-9bb7-c32a0dd9f24f", "metadataRecords/records/09ac8e36-5d65-40f9-9bb7-c32a0dd9f24f.xml");
            mockHttpClient.addGetUrl("https://domain.com/geonetwork/srv/eng/xml.metadata.get?uuid=61a4bac5-79d1-4c1f-9358-a7bb587e07df", "metadataRecords/records/61a4bac5-79d1-4c1f-9358-a7bb587e07df.xml");
            mockHttpClient.addGetUrl("https://domain.com/geonetwork/srv/eng/xml.metadata.get?uuid=356e7b3c-1508-432e-9d85-263ec8a67cef", "metadataRecords/records/356e7b3c-1508-432e-9d85-263ec8a67cef.xml");
            mockHttpClient.addGetUrl("https://domain.com/geonetwork/srv/eng/xml.metadata.get?uuid=a2a8f9c0-d7bc-4fae-b9b1-ccebfa642068", "metadataRecords/records/a2a8f9c0-d7bc-4fae-b9b1-ccebfa642068.xml");
            mockHttpClient.addGetUrl("https://domain.com/geonetwork/srv/eng/xml.metadata.get?uuid=e9a43553-dbe4-40e2-9d3a-aa200f9e2277", "metadataRecords/records/e9a43553-dbe4-40e2-9d3a-aa200f9e2277.xml");
            mockHttpClient.addGetUrl("https://domain.com/geonetwork/srv/eng/xml.metadata.get?uuid=f6636322-28d9-47fe-878d-0e70cc7c6920", "metadataRecords/records/f6636322-28d9-47fe-878d-0e70cc7c6920.xml");
            // Preview images
            mockHttpClient.addGetUrl("https://domain.com/geonetwork/srv/api/records/f6636322-28d9-47fe-878d-0e70cc7c6920/attachments/Preview-image.png", "metadataRecords/previews/preview.png");
            mockHttpClient.addGetUrl("https://domain.com/geonetwork/srv/api/records/61a4bac5-79d1-4c1f-9358-a7bb587e07df/attachments/Mean_par8_2008.png", "metadataRecords/previews/preview.png");
            mockHttpClient.addGetUrl("https://domain.com/geonetwork/srv/api/records/356e7b3c-1508-432e-9d85-263ec8a67cef/attachments/example_raster_plot.png", "metadataRecords/previews/preview.png");
            mockHttpClient.addGetUrl("https://domain.com/geonetwork/srv/api/records/e9a43553-dbe4-40e2-9d3a-aa200f9e2277/attachments/products__ncanimate__ereefs__gbr1_2-0__fresh-water-exposure_monthly_map_monthly_2019-02_townsville-3_-2.35.png", "metadataRecords/previews/preview.png");
            mockHttpClient.addGetUrl("https://domain.com/geonetwork/srv/api/records/a2a8f9c0-d7bc-4fae-b9b1-ccebfa642068/attachments/preview-image-recruits-on-disks.jpg", "metadataRecords/previews/preview.jpg");

            Assertions.assertEquals(HealthStatus.Green, searchClient.getHealthStatus(), "The Elastic Search engine health status is not Green before starting the test.");

            String index = "metadata_records";
            AbstractLogger logger = ConsoleLogger.getInstance();

            searchClient.createIndex(index);

            // Find the indexer, defined in the config file
            GeoNetworkIndexer geoNetworkIndexer =
                    (GeoNetworkIndexer)this.getConfig().getIndexer(index);

            geoNetworkIndexer.internalIndex(searchClient, null, logger);

            // Wait for ElasticSearch to finish its indexation
            searchClient.refresh(index);

            SearchResults results = null;
            try {
                String q = ""; // Search for an empty string, to get all the blocks
                Integer start = 0;
                Integer hits = 50;
                String wkt = null; // No geographic filtering
                List<String> idx = List.of(index);
                List<SortOptions> sortOptionsList = new ArrayList<>();
                SortOptions sortOption = SortOptions.of(so -> so.field(f -> f.field("publishedOn").order(SortOrder.Desc)));
                sortOptionsList.add(sortOption);

                results = Search.paginationSearch(searchClient, q, start, hits, wkt, sortOptionsList, idx, null, logger);

                Summary searchSummary = results.getSummary();

                // Check indexes in the summary.
                Map<String, IndexSummary> indexSummaryMap = searchSummary.getIndexSummaries();
                Assertions.assertEquals(1, indexSummaryMap.size(),
                        "Wrong number if index summary.");
                Assertions.assertTrue(indexSummaryMap.containsKey(index),
                        String.format("Missing index from the search summary: %s", index));

                IndexSummary layersIndexSummary = searchSummary.getIndexSummary(index);

                Assertions.assertEquals(6, layersIndexSummary.getHits(),
                        "Wrong number of search result in the index summary.");

                Assertions.assertEquals(6, searchSummary.getHits(),
                        "Wrong total number of search result in the index summary.");
                
                List<SearchResult> searchResultList = results.getSearchResults();
                Assertions.assertEquals("a2a8f9c0-d7bc-4fae-b9b1-ccebfa642068", searchResultList.get(0).getId(),
                        "Wrong order of search results.");

            } catch(Exception ex) {
                Assertions.fail("Exception thrown while testing the Search API.", ex);
            }

            Assertions.assertEquals(HealthStatus.Green, searchClient.getHealthStatus(), "The Elastic Search engine health status is not Green after the test.");
        }
    }
}
