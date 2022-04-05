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
import au.gov.aims.eatlas.searchengine.client.ESClient;
import au.gov.aims.eatlas.searchengine.client.ESRestHighLevelClient;
import au.gov.aims.eatlas.searchengine.entity.Entity;
import au.gov.aims.eatlas.searchengine.entity.ExternalLink;
import au.gov.aims.eatlas.searchengine.rest.Index;
import au.gov.aims.eatlas.searchengine.rest.Search;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.CountResponse;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.apache.log4j.Logger;
import org.elasticsearch.client.RestClient;

import java.io.File;
import java.io.IOException;
import java.net.URL;

public class Main {
    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());

    public static void main(String... args) throws Exception {
        boolean fullHarvest = true;

        File configFile = new File("/var/lib/tomcat9/conf/Catalina/data/eatlas-search-engine/eatlas_search_engine.json");

        SearchEngineConfig config = SearchEngineConfig.createInstance(configFile, "eatlas_search_engine_devel.json");
        Index.internalReindex(config, fullHarvest);


        //Main.loadDummyExternalLinks(15000);

        //Main.doNothing();
    }

    private static void doNothing() throws IOException {
        // Create the low-level client
        RestClient restClient = RestClient.builder(
                new HttpHost("localhost", 9200, "http"),
                new HttpHost("localhost", 9300, "http")
            ).build();

        // Create the transport with a Jackson mapper
        ElasticsearchTransport transport = new RestClientTransport(
            restClient, new JacksonJsonpMapper());

        // And create the API client
        ElasticsearchClient rawClient = new ElasticsearchClient(transport);

        rawClient._transport().close();
        rawClient.shutdown();
    }

    private static void loadDummyExternalLinks(int count) throws IOException {
        String index = "eatlas_dummy";

        // Create the low-level client
        RestClient restClient = RestClient.builder(
                new HttpHost("localhost", 9200, "http"),
                new HttpHost("localhost", 9300, "http")
            ).build();

        // Create the transport with a Jackson mapper
        ElasticsearchTransport transport = new RestClientTransport(
            restClient, new JacksonJsonpMapper());

        // And create the API client
        ElasticsearchClient rawClient = new ElasticsearchClient(transport);

        try(ESClient client = new ESRestHighLevelClient(rawClient)) {

            CountResponse countResponseBefore = client.count(Search.getSearchSummaryRequest("Lorem", index));
            System.out.println("Count before: " + countResponseBefore.count());

            for (int i=0; i<count; i++) {
                ExternalLink externalLink = new ExternalLink(
                    index,
                    String.format("http://www.domain.com/result/%d", i),
                    String.format("Dummy link number: %d", i)
                );
                externalLink.setThumbnailUrl(new URL("https://www.google.com/logos/doodles/2020/december-holidays-days-2-30-6753651837108830.3-law.gif"));
                externalLink.setDocument(
                    "<p>Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum.</p>" +
                    "<p>Sed ut perspiciatis unde omnis iste natus error sit voluptatem accusantium doloremque laudantium, totam rem aperiam, eaque ipsa quae ab illo inventore veritatis et quasi architecto beatae vitae dicta sunt explicabo. Nemo enim ipsam voluptatem quia voluptas sit aspernatur aut odit aut fugit, sed quia consequuntur magni dolores eos qui ratione voluptatem sequi nesciunt. Neque porro quisquam est, qui dolorem ipsum quia dolor sit amet, consectetur, adipisci velit, sed quia non numquam eius modi tempora incidunt ut labore et dolore magnam aliquam quaerat voluptatem. Ut enim ad minima veniam, quis nostrum exercitationem ullam corporis suscipit laboriosam, nisi ut aliquid ex ea commodi consequatur? Quis autem vel eum iure reprehenderit qui in ea voluptate velit esse quam nihil molestiae consequatur, vel illum qui dolorem eum fugiat quo voluptas nulla pariatur?</p>");

                IndexRequest<Entity> indexRequest = new IndexRequest.Builder<Entity>()
                        .index(index)
                        .id(externalLink.getId())
                        .document(externalLink)
                        .build();

                IndexResponse indexResponse = client.index(indexRequest);

                System.out.println(String.format("Indexing dummy URL: %s, status: %s", externalLink.getId(), indexResponse.result()));
            }

            CountResponse countResponseAfter = client.count(Search.getSearchSummaryRequest("Lorem", index));
            System.out.println("Count after: " + countResponseAfter.count());
        }
    }
}
