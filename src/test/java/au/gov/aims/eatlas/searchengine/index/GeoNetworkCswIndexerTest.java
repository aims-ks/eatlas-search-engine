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

import java.util.List;
import java.util.Map;

public class GeoNetworkCswIndexerTest extends IndexerTestBase {

    // TODO Categories: use "!name" to harvest records without given category

    /**
     * Test the indexation of GeoNetwork records
     * using the CSW API.
     */
    @Test
    public void testIndexCswMetadataRecords() throws Exception {
        try (MockSearchClient searchClient = this.createMockSearchClient()) {
            MockHttpClient mockHttpClient = this.getMockHttpClient();

            // Setup the mockHttpClient
            String geoNetworkUrl = "https://domain.com/geonetwork/srv/eng/csw";
            // Data for the request, with "%d" parameter for startPosition.
            String dataTemplate = "<?xml version=\"1.0\"?><GetRecords xmlns=\"http://www.opengis.net/cat/csw/2.0.2\" xmlns:ogc=\"http://www.opengis.net/ogc\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" service=\"CSW\" version=\"2.0.2\" resultType=\"results\" startPosition=\"%d\" maxRecords=\"10\" outputSchema=\"http://standards.iso.org/iso/19115/-3/mdb/2.0\" xsi:schemaLocation=\"http://www.opengis.net/cat/csw/2.0.2 http://schemas.opengis.net/csw/2.0.2/CSW-discovery.xsd\"><Query typeNames=\"mdb:MD_Metadata\"><ElementSetName>full</ElementSetName><ogc:SortBy xmlns:ogc=\"http://www.opengis.net/ogc\"><ogc:SortProperty><ogc:PropertyName>Identifier</ogc:PropertyName><ogc:SortOrder>ASC</ogc:SortOrder></ogc:SortProperty></ogc:SortBy></Query></GetRecords>";

            // Search pages
            mockHttpClient.addPostUrl(geoNetworkUrl,
                String.format(dataTemplate, 1),
                "cswMetadataRecords/responses/geonetwork-csw-records_page1.xml");

            mockHttpClient.addPostUrl(geoNetworkUrl,
                String.format(dataTemplate, 11),
                "cswMetadataRecords/responses/geonetwork-csw-records_page2.xml");

            mockHttpClient.addPostUrl(geoNetworkUrl,
                String.format(dataTemplate, 21),
                "cswMetadataRecords/responses/geonetwork-csw-records_page3.xml");

            Assertions.assertEquals(HealthStatus.Green, searchClient.getHealthStatus(), "The Elastic Search engine health status is not Green before starting the test.");

            String index = "csw_metadata_records_all";
            Messages messages = Messages.getInstance(null);

            searchClient.createIndex(index);

            // Find the indexer, defined in the config file
            GeoNetworkCswIndexer geoNetworkCswIndexer =
                    (GeoNetworkCswIndexer)this.getConfig().getIndexer(index);

            geoNetworkCswIndexer.internalIndex(searchClient, null, messages);

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

                Assertions.assertEquals(30, layersIndexSummary.getHits(),
                        "Wrong number of search result in the index summary.");

                Assertions.assertEquals(30, searchSummary.getHits(),
                        "Wrong total number of search result in the index summary.");

            } catch(Exception ex) {
                Assertions.fail("Exception thrown while testing the Search API.", ex);
            }

            Assertions.assertEquals(HealthStatus.Green, searchClient.getHealthStatus(), "The Elastic Search engine health status is not Green after the test.");
        }
    }

    /**
     * Test the indexation of a single GeoNetwork record
     * using the CSW API.
     */
    @Test
    public void testIndexCswSingleMetadataRecord() throws Exception {
        try (MockSearchClient searchClient = this.createMockSearchClient()) {
            MockHttpClient mockHttpClient = this.getMockHttpClient();

            // Setup the mockHttpClient
            String metadataRecordUUID = "00713afb-28fd-4878-96da-431d16944732";
            String geoNetworkUrl = "https://domain.com/geonetwork/srv/eng/csw";
            // Data for the request, with "%s" parameter for the record UUID.
            String dataTemplate = "<?xml version=\"1.0\"?><GetRecords xmlns=\"http://www.opengis.net/cat/csw/2.0.2\" xmlns:ogc=\"http://www.opengis.net/ogc\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" service=\"CSW\" version=\"2.0.2\" resultType=\"results\" startPosition=\"1\" maxRecords=\"1\" outputSchema=\"http://standards.iso.org/iso/19115/-3/mdb/2.0\" xsi:schemaLocation=\"http://www.opengis.net/cat/csw/2.0.2 http://schemas.opengis.net/csw/2.0.2/CSW-discovery.xsd\"><Query typeNames=\"mdb:MD_Metadata\"><ElementSetName>full</ElementSetName><Constraint version=\"1.1.0\"><ogc:Filter><ogc:PropertyIsEqualTo><ogc:PropertyName>Identifier</ogc:PropertyName><ogc:Literal>%s</ogc:Literal></ogc:PropertyIsEqualTo></ogc:Filter></Constraint></Query></GetRecords>";

            // Metadata record page
            mockHttpClient.addPostUrl(geoNetworkUrl,
                String.format(dataTemplate, metadataRecordUUID),
                String.format("cswMetadataRecords/responses/geonetwork-csw-record_%s.xml", metadataRecordUUID));

            Assertions.assertEquals(HealthStatus.Green, searchClient.getHealthStatus(), "The Elastic Search engine health status is not Green before starting the test.");

            String index = "csw_metadata_records_single";
            Messages messages = Messages.getInstance(null);

            searchClient.createIndex(index);

            // Find the indexer, defined in the config file
            GeoNetworkCswIndexer geoNetworkCswIndexer =
                    (GeoNetworkCswIndexer)this.getConfig().getIndexer(index);

            // Index single record
            geoNetworkCswIndexer.harvestEntity(searchClient, metadataRecordUUID, messages);

            // Wait for ElasticSearch to finish its indexation
            searchClient.refresh(index);

            // Check if the record is in the index
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

                Assertions.assertEquals(1, layersIndexSummary.getHits(),
                        "Wrong number of search result in the index summary.");

                Assertions.assertEquals(1, searchSummary.getHits(),
                        "Wrong total number of search result in the index summary.");

            } catch(Exception ex) {
                Assertions.fail("Exception thrown while testing the Search API.", ex);
            }

            Assertions.assertEquals(HealthStatus.Green, searchClient.getHealthStatus(), "The Elastic Search engine health status is not Green after the test.");
        }
    }
}
