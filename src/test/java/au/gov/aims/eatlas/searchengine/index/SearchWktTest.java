package au.gov.aims.eatlas.searchengine.index;

import au.gov.aims.eatlas.searchengine.MockHttpClient;
import au.gov.aims.eatlas.searchengine.admin.SearchEngineConfig;
import au.gov.aims.eatlas.searchengine.admin.rest.Messages;
import au.gov.aims.eatlas.searchengine.entity.GeoNetworkRecord;
import co.elastic.clients.elasticsearch._types.HealthStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.io.ParseException;

import java.io.IOException;

/**
 * Create WKT using this online tool:
 *   https://clydedacruz.github.io/openstreetmap-wkt-playground/
 */
public class SearchWktTest extends IndexerTestBase {

    @Test
    public void testSearchWkt() throws Exception {
        SearchEngineConfig config = SearchEngineConfig.getInstance();
        Messages messages = Messages.getInstance(null);
        MockHttpClient mockHttpClient = MockHttpClient.getInstance();
        try (MockSearchClient searchClient = this.createMockSearchClient()) {
            Assertions.assertEquals(HealthStatus.Green, searchClient.getHealthStatus(), "The Elastic Search engine health status is not Green before starting the test.");

            String metadataRecordIndex = "junit_records";
            String layersIndex = "junit_layers";
            String imagesIndex = "junit_images";

            // Index mix content
            this.indexMetadataRecords(metadataRecordIndex, config, searchClient, mockHttpClient, messages);
            this.indexLayers(layersIndex, config, searchClient, mockHttpClient, messages);
            this.indexImages(imagesIndex, config, searchClient, mockHttpClient, messages);

            // TODO Search with WKT

            Assertions.assertEquals(HealthStatus.Green, searchClient.getHealthStatus(), "The Elastic Search engine health status is not Green after the test.");
        }
    }

    private void indexMetadataRecords(String index, SearchEngineConfig config, MockSearchClient searchClient, MockHttpClient mockHttpClient, Messages messages) throws ParseException, IOException {
        searchClient.createIndex(index);
        GeoNetworkIndexer indexer = new GeoNetworkIndexer(mockHttpClient, index, "http://domain.com/geonetwork", "3.0");

        // Add the indexer to the SearchEngineConfig, so the EntityDeserializer (Jackson)
        //   can serialise / deserialise the Entity.
        config.addIndexer(indexer);


        // TODO Add a few records
        GeoNetworkRecord australiaRecord = new GeoNetworkRecord(index, "00000000-0000-0000-0000-000000000000", "iso19115-3.2018", "3.0");
        australiaRecord.setTitle("Australia record");
        australiaRecord.setDocument("Record that covers whole of Australia.");
        australiaRecord.setWktAndAttributes("POLYGON((131.8 -7.7,112.5 -21.3,114.3 -36.9,147 -44.6,156.8 -28,143.1 -9.4,131.8 -7.7))");
        indexer.indexEntity(searchClient, australiaRecord, messages);

        GeoNetworkRecord qldRecord = new GeoNetworkRecord(index, "00000000-0000-0000-0000-000000000001", "iso19115-3.2018", "3.0");
        qldRecord.setTitle("Queensland record");
        qldRecord.setDocument("Record that covers whole of Queensland.");
        qldRecord.setWktAndAttributes("POLYGON((137.9 -16.4,137.9 -26,141.1 -26,141.1 -29,154.7 -29.2,149.9 -20.8,142.4 -10.3,140.7 -16.6,137.9 -16.4))");
        indexer.indexEntity(searchClient, qldRecord, messages);



        // Wait for ElasticSearch to finish its indexation
        searchClient.refresh(index);
    }

    private void indexLayers(String index, SearchEngineConfig config, MockSearchClient searchClient, MockHttpClient mockHttpClient, Messages messages) throws IOException {
        searchClient.createIndex(index);
        AtlasMapperIndexer indexer = new AtlasMapperIndexer(mockHttpClient, index, "http://domain.com/atlasmapper", "1.0", "http://domain.com/geoserver");

        // Add the indexer to the SearchEngineConfig, so the EntityDeserializer (Jackson)
        //   can serialise / deserialise the Entity.
        config.addIndexer(indexer);

        // TODO Add a few layers

        // Wait for ElasticSearch to finish its indexation
        searchClient.refresh(index);
    }

    private void indexImages(String index, SearchEngineConfig config, MockSearchClient searchClient, MockHttpClient mockHttpClient, Messages messages) throws IOException {
        searchClient.createIndex(index);
        DrupalMediaIndexer indexer = new DrupalMediaIndexer(mockHttpClient, index, "http://domain.com", "11.0", "image", "field_preview", "title", "body", "field_wkt");

        // Add the indexer to the SearchEngineConfig, so the EntityDeserializer (Jackson)
        //   can serialise / deserialise the Entity.
        config.addIndexer(indexer);

        // TODO Add a few images

        // Wait for ElasticSearch to finish its indexation
        searchClient.refresh(index);
    }
}
