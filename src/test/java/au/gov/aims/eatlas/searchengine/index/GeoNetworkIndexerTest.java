package au.gov.aims.eatlas.searchengine.index;

import au.gov.aims.eatlas.searchengine.admin.rest.Messages;
import au.gov.aims.eatlas.searchengine.client.SearchClient;
import au.gov.aims.eatlas.searchengine.rest.Search;
import au.gov.aims.eatlas.searchengine.search.IndexSummary;
import au.gov.aims.eatlas.searchengine.search.SearchResults;
import au.gov.aims.eatlas.searchengine.search.Summary;
import co.elastic.clients.elasticsearch._types.HealthStatus;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Map;

public class GeoNetworkIndexerTest extends IndexerTestBase {

    @Override
    protected Map<String, String> getMockupUrlMap() {
        // TODO Change URLs in metadata records (eAtlas), add URLs for record preview images
        Map<String, String> urlMap = super.getMockupUrlMap();
        urlMap.put("https://domain.com/geonetwork/srv/eng/xml.search?from=1", "metadataRecords/srv/eng/xml.search_from_1");
        urlMap.put("https://domain.com/geonetwork/srv/eng/xml.metadata.get?uuid=09ac8e36-5d65-40f9-9bb7-c32a0dd9f24f", "metadataRecords/srv/eng/xml.metadata.get_uuid_09ac8e36-5d65-40f9-9bb7-c32a0dd9f24f");
        urlMap.put("https://domain.com/geonetwork/srv/eng/xml.metadata.get?uuid=61a4bac5-79d1-4c1f-9358-a7bb587e07df", "metadataRecords/srv/eng/xml.metadata.get_uuid_61a4bac5-79d1-4c1f-9358-a7bb587e07df");
        urlMap.put("https://domain.com/geonetwork/srv/eng/xml.metadata.get?uuid=356e7b3c-1508-432e-9d85-263ec8a67cef", "metadataRecords/srv/eng/xml.metadata.get_uuid_356e7b3c-1508-432e-9d85-263ec8a67cef");
        urlMap.put("https://domain.com/geonetwork/srv/eng/xml.metadata.get?uuid=a2a8f9c0-d7bc-4fae-b9b1-ccebfa642068", "metadataRecords/srv/eng/xml.metadata.get_uuid_a2a8f9c0-d7bc-4fae-b9b1-ccebfa642068");
        urlMap.put("https://domain.com/geonetwork/srv/eng/xml.metadata.get?uuid=e9a43553-dbe4-40e2-9d3a-aa200f9e2277", "metadataRecords/srv/eng/xml.metadata.get_uuid_e9a43553-dbe4-40e2-9d3a-aa200f9e2277");
        urlMap.put("https://domain.com/geonetwork/srv/eng/xml.metadata.get?uuid=f6636322-28d9-47fe-878d-0e70cc7c6920", "metadataRecords/srv/eng/xml.metadata.get_uuid_f6636322-28d9-47fe-878d-0e70cc7c6920");
        return urlMap;
    }

    @Test
    public void testIndexMetadataRecords() throws Exception {
        try (
                MockedStatic<Jsoup> mockedJsoup = this.getMockedJsoup();
                SearchClient client = this.createElasticsearchClient()
        ) {
            Assertions.assertEquals(HealthStatus.Green, client.getHealthStatus(), "The Elastic Search engine health status is not Green before starting the test.");

            GeoNetworkIndexer.disableMultiThread();

            String index = "metadata_records";
            Messages messages = Messages.getInstance(null);

            client.createIndex(index);

            // Find the indexer, defined in the config file
            GeoNetworkIndexer geoNetworkIndexer =
                    (GeoNetworkIndexer)this.getConfig().getIndexer(index);

            geoNetworkIndexer.internalIndex(client, null, messages);

            // Wait for ElasticSearch to finish its indexation
            client.refresh(index);

            SearchResults results = null;
            try {
                String q = ""; // Search for an empty string, to get all the blocks
                Integer start = 0;
                Integer hits = 50;
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

                Assertions.assertEquals(6, layersIndexSummary.getHits(),
                        "Wrong number of search result in the index summary.");

            } catch(Exception ex) {
                Assertions.fail("Exception thrown while testing the Search API.", ex);
            }

            Assertions.assertEquals(HealthStatus.Green, client.getHealthStatus(), "The Elastic Search engine health status is not Green after the test.");
        }
    }
}
