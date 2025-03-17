/*
 *  Copyright (C) 2022 Australian Institute of Marine Science
 *
 *  Contact: Gael Lafond <g.lafond@aims.gov.au>
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package au.gov.aims.eatlas.searchengine.index;

import au.gov.aims.eatlas.searchengine.MockHttpClient;
import au.gov.aims.eatlas.searchengine.admin.SearchEngineConfig;
import au.gov.aims.eatlas.searchengine.logger.ConsoleLogger;
import au.gov.aims.eatlas.searchengine.logger.AbstractLogger;
import au.gov.aims.eatlas.searchengine.entity.GeoNetworkRecord;
import co.elastic.clients.elasticsearch._types.HealthStatus;
import co.elastic.clients.elasticsearch._types.Result;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class GeoShapeIndexationTest extends IndexerTestBase {

    @Test
    public void testGeoShapeIndexation() throws Exception {
        MockHttpClient mockHttpClient = MockHttpClient.getInstance();
        String index = "unit_test";

        GeoNetworkIndexer indexer = new GeoNetworkIndexer(mockHttpClient, index, index, "http://eatlas/geonetwork", "http://domain.com/geonetwork", "3.0");

        GeoNetworkRecord record = new GeoNetworkRecord(indexer, "00000000-0000-0000-0000-000000000000", "iso19115-3.2018", "3.0");
        record.setTitle("Dummy record");
        record.setDocument("Dummy record content.");

        record.setWktAndAttributes("POLYGON ((-175.4736328125 -31.245117187500004, -171.2548828125 36.9580078125, 171.8701171875 39.0673828125, 175.3857421875 -30.5419921875, -32.0361328125 -34.0576171875, -175.4736328125 -31.245117187500004))");

        SearchEngineConfig config = SearchEngineConfig.getInstance();
        // Add the indexer to the SearchEngineConfig, so the EntityDeserializer (Jackson)
        //   can serialise / deserialise the Entity.
        config.addIndexer(indexer);

        // Indexation
        AbstractLogger logger = ConsoleLogger.getInstance();
        try (MockSearchClient searchClient = this.createMockSearchClient()) {
            Assertions.assertEquals(HealthStatus.Green, searchClient.getHealthStatus(), "The Elastic Search engine health status is not Green before starting the test.");

            searchClient.createIndex(index);

            // Index the GeoNetworkRecord
            IndexResponse indexResponse = indexer.indexEntity(searchClient, record, logger);
            // Check the response to make sure the record was properly indexed.
            Assertions.assertNotNull(indexResponse, "The indexer returned NULL for the indexed document.");
            Assertions.assertEquals(Result.Created, indexResponse.result(), "Wrong index response type.");
            Assertions.assertEquals(index, indexResponse.index());
            Assertions.assertEquals("00000000-0000-0000-0000-000000000000", indexResponse.id());

            // Wait for ElasticSearch to finish its indexation
            searchClient.refresh(index);

            // Get the record from the index
            GeoNetworkRecord foundRecord = indexer.get(searchClient, GeoNetworkRecord.class, "00000000-0000-0000-0000-000000000000");
            // Check the record
            Assertions.assertNotNull(foundRecord, "Indexed record can not be found in the index.");
            Assertions.assertEquals(24453.259, foundRecord.getWktArea(), 0.001, "Wrong WKT area.");

            Assertions.assertEquals(HealthStatus.Green, searchClient.getHealthStatus(), "The Elastic Search engine health status is not Green after the test.");
        }

        Assertions.assertTrue(logger.isEmpty());
    }
}
