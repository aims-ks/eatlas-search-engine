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
package au.gov.aims.eatlas.searchengine.entity;

import au.gov.aims.eatlas.searchengine.admin.rest.Messages;
import au.gov.aims.eatlas.searchengine.client.ESClient;
import au.gov.aims.eatlas.searchengine.client.SearchClient;
import au.gov.aims.eatlas.searchengine.index.GeoNetworkIndexer;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Paths;

public class GeoShapeTestManual {

    // Attempting to isolate and resolve the GeoShape bug
    @Test
    public void testGeoShapeBug() throws Exception {
        String index = "unit_test";
        GeoNetworkRecord record = new GeoNetworkRecord(index);
        record.setId("00000000-0000-0000-0000-000000000000");
        record.setTitle("Dummy record");
        record.setDocument("Dummy record content.");

        Messages messages = Messages.getInstance(null);

        // Polygon
        // 2                      3
        //   *------------------*
        //   |                  |
        //   *--------*---------*
        // 1          5           4

        // BAD
        //record.setWkt("POLYGON ((-175 -31, -171 36, 171 39, 175 -30, -32 -34, -175 -31))");
        //record.setWkt("POLYGON ((-175 -2, -175 0, 175 0, 175 -1, -32 -5, -175 -2))");
        //record.setWkt("POLYGON ((-150 -1, -150 0, 175 0, -32 -4, -150 -1))");

        // GOOD
        //record.setWkt("POLYGON ((-175 -31, -171 36, 171 39, 175 -31, -32 -34, -175 -31))");

        // Bug isolation
        // Polygon
        // 2                      3
        //   *------------------*
        //   |                /
        //   *--------------*
        // 1                 4
        /*
        West boundary
        GOOD
        record.setWkt("POLYGON ((-146 -1, -146 0, 175 0, -32 -4, -146 -1))");
        BAD
        record.setWkt("POLYGON ((-33 -1, -33 0, 175 0, -32 -1, -33 -1))");

        East boundary
        GOOD
        record.setWkt("POLYGON ((-147 -1, -147 0, 174 0, -32 -4, -147 -1))");
        BAD
        record.setWkt("POLYGON ((-147 -1, -147 0, 175 0, -32 -4, -147 -1))");

        South boundary
        Bad
        record.setWkt("POLYGON ((-147 -1, -147 0, 175 0, -32 -1, -147 -1))");
        */

        /*
        GOOD
        record.setWkt("POLYGON ((-12 -1, -12 0, 169 0, 0 -1, -12 -1))");
        Bad
        record.setWkt("POLYGON ((-12 -1, -12 0, 169 0, -1 -1, -12 -1))");
        record.setWkt("POLYGON ((-12 9, -12 10, 169 10, -1 9, -12 9))");
        record.setWkt("POLYGON ((-2 9, -2 10, 179 10, -1 9, -2 9))");
        */

        //this.setWkt(record, "POLYGON ((-91 9, 0 80, 90 10, -90 9, -91 9))");
        //this.setWkt(record, "POLYGON ((-175 -31, -171 36, 171 39, 175 -30, -32 -34, -175 -31))");
        record.setWkt("POLYGON ((-175.4736328125 -31.245117187500004, -171.2548828125 36.9580078125, 171.8701171875 39.0673828125, 175.3857421875 -30.5419921875, -32.0361328125 -34.0576171875, -175.4736328125 -31.245117187500004))");

        // Indexation
        GeoNetworkIndexer indexer = new GeoNetworkIndexer(index, "http://domain.com/geonetwork", "3.0");
        try(
                RestClient restClient = RestClient.builder(
                    new HttpHost[]{
                        new HttpHost("localhost", 9200, "http"),
                        new HttpHost("localhost", 9300, "http")
                    }
                ).build();

                // Create the transport with a Jackson mapper
                ElasticsearchTransport transport = new RestClientTransport(
                        restClient, new JacksonJsonpMapper());

                // And create the API client
                SearchClient client = new ESClient(new ElasticsearchClient(transport))
        ) {
            client.createIndex(index);

            indexer.indexEntity(client, record, messages);
        }
    }

    @Test
    public void testNaturalEarthData() throws Exception {
        String index = "unit_test";
        GeoNetworkRecord record = new GeoNetworkRecord(index);
        record.setId("00000000-0000-0000-0000-000000000000");
        record.setTitle("Dummy record");
        record.setDocument("Dummy record content.");

        Messages messages = Messages.getInstance(null);

        try {
            String bigWkt = new String(Files.readAllBytes(Paths.get(GeoNetworkRecord.class.getClassLoader().getResource("ne_10m.wkt").toURI())));
            record.setWkt(bigWkt);
        } catch(Exception ex) {
            ex.printStackTrace();
        }

        // Indexation
        GeoNetworkIndexer indexer = new GeoNetworkIndexer(index, "http://domain.com/geonetwork", "3.0");
        try(
                RestClient restClient = RestClient.builder(
                    new HttpHost[]{
                        new HttpHost("localhost", 9200, "http"),
                        new HttpHost("localhost", 9300, "http")
                    }
                ).build();

                // Create the transport with a Jackson mapper
                ElasticsearchTransport transport = new RestClientTransport(
                        restClient, new JacksonJsonpMapper());

                // And create the API client
                SearchClient client = new ESClient(new ElasticsearchClient(transport))
        ) {
            client.createIndex(index);

            indexer.indexEntity(client, record, messages);
        }
    }

    @Test
    public void testCAPAD() throws Exception {
        String index = "unit_test";
        GeoNetworkRecord record = new GeoNetworkRecord(index);
        record.setId("00000000-0000-0000-0000-000000000000");
        record.setTitle("Dummy CAPAD record");
        record.setDocument("Dummy CAPAD record content.");

        Messages messages = Messages.getInstance(null);

        try {
            String bigWkt = new String(Files.readAllBytes(Paths.get(GeoNetworkRecord.class.getClassLoader().getResource("capad_2020.wkt").toURI())));
            record.setWkt(bigWkt);
        } catch(Exception ex) {
            ex.printStackTrace();
        }

        // Indexation
        GeoNetworkIndexer indexer = new GeoNetworkIndexer(index, "http://domain.com/geonetwork", "3.0");
        try(
                RestClient restClient = RestClient.builder(
                    new HttpHost[]{
                        new HttpHost("localhost", 9200, "http"),
                        new HttpHost("localhost", 9300, "http")
                    }
                ).build();

                // Create the transport with a Jackson mapper
                ElasticsearchTransport transport = new RestClientTransport(
                        restClient, new JacksonJsonpMapper());

                // And create the API client
                SearchClient client = new ESClient(new ElasticsearchClient(transport))
        ) {
            client.createIndex(index);

            indexer.indexEntity(client, record, messages);
        }
    }

}
