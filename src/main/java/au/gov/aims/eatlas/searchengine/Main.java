/*
 *  Copyright (C) 2020 Australian Institute of Marine Science
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
package au.gov.aims.eatlas.searchengine;

import au.gov.aims.eatlas.searchengine.admin.SearchEngineConfig;
import au.gov.aims.eatlas.searchengine.client.SearchUtils;
import au.gov.aims.eatlas.searchengine.index.AtlasMapperIndexer;
import au.gov.aims.eatlas.searchengine.index.DrupalExternalLinkNodeIndexer;
import au.gov.aims.eatlas.searchengine.index.DrupalMediaIndexer;
import au.gov.aims.eatlas.searchengine.index.DrupalNodeIndexer;
import au.gov.aims.eatlas.searchengine.index.GeoNetworkIndexer;
import au.gov.aims.eatlas.searchengine.rest.Index;
import au.gov.aims.eatlas.searchengine.rest.Search;
import au.gov.aims.eatlas.searchengine.search.SearchResults;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.apache.log4j.Logger;
import org.elasticsearch.client.RestClient;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Main {
    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());

    public static void main(String... args) throws Exception {
        boolean fullHarvest = true;

        File configFile = new File("/var/lib/tomcat9/conf/Catalina/data/eatlas-search-engine/eatlas_search_engine.json");

        SearchEngineConfig config = SearchEngineConfig.createInstance(configFile, "eatlas_search_engine_devel.json");

        // Re-index everything
        //Index.internalReindex(fullHarvest);


        // Re-index individual indexes

        // TODO Implement LEMMATIZATION (mice => mouse, foot => feet, tooth => teeth, etc):
        //     https://apprize.best/data/elasticsearch_1/23.html
        // TODO Fix JUnit tests
        // TODO Implement UI - Configure, re-index button, try search

        DrupalNodeIndexer drupalNodeIndexer = (DrupalNodeIndexer)config.getIndexer("eatlas_article");
        //Index.internalReindex(drupalNodeIndexer, fullHarvest);

        DrupalMediaIndexer drupalMediaIndexer = (DrupalMediaIndexer)config.getIndexer("eatlas_image");
        //Index.internalReindex(drupalMediaIndexer, fullHarvest);

        DrupalExternalLinkNodeIndexer drupalExternalLinkNodeIndexer = (DrupalExternalLinkNodeIndexer)config.getIndexer("eatlas_external_link");
        //Index.internalReindex(drupalExternalLinkNodeIndexer, fullHarvest);

        GeoNetworkIndexer geoNetworkIndexer = (GeoNetworkIndexer)config.getIndexer("eatlas_metadata");
        //Index.internalReindex(geoNetworkIndexer, fullHarvest);

        AtlasMapperIndexer atlasMapperIndexer = (AtlasMapperIndexer)config.getIndexer("eatlas_layer");
        Index.internalReindex(atlasMapperIndexer, fullHarvest, null);


        //Main.testElasticsearchClient();


        // Test search

        String searchQuery = "reef";

        List<String> idx = new ArrayList<String>();
        //idx.add("eatlas_article");
        idx.add("eatlas_image");
        //idx.add("eatlas_external_link");
        //idx.add("eatlas_metadata");
        //idx.add("eatlas_layer");

        SearchResults results = Search.paginationSearch(searchQuery, 0, 100, idx, null);
        System.out.println(results.toJSON().toString(2));

    }

    private static void testElasticsearchClient() throws IOException {
        RestClient restClient = SearchUtils.buildRestClient();

        // Create the transport with a Jackson mapper
        ElasticsearchTransport transport = new RestClientTransport(
                restClient, new JacksonJsonpMapper());

        // And create the API client
        ElasticsearchClient rawClient = new ElasticsearchClient(transport);

        restClient.close();
        transport.close();
        rawClient.shutdown();
    }
}
