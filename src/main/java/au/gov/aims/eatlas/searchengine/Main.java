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
import au.gov.aims.eatlas.searchengine.entity.ExternalLink;
import au.gov.aims.eatlas.searchengine.index.AtlasMapperIndexer;
import au.gov.aims.eatlas.searchengine.index.DrupalNodeIndexer;
import au.gov.aims.eatlas.searchengine.index.ExternalLinkIndexer;
import au.gov.aims.eatlas.searchengine.index.GeoNetworkIndexer;
import au.gov.aims.eatlas.searchengine.index.IndexUtils;
import au.gov.aims.eatlas.searchengine.rest.Index;
import org.apache.http.HttpHost;
import org.apache.log4j.Logger;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class Main {
    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());

    public static void main(String... args) throws Exception {
        File configFile = new File("/tmp/eatlas_search_engine.json");
        //configFile.delete();
        SearchEngineConfig config = SearchEngineConfig.createInstance(configFile, "eatlas_search_engine_devel.json");

        Index.internalReindex(config, true);

        // TODO Create a default config file so it won't start with nothing

        //Main.testElasticSearch();
        //Main.loadDummyExternalLinks(15000);

        //Main.loadExternalLinks();

        // 2 hours
        //Main.loadAtlasMapperLayers("https://maps.eatlas.org.au", System.currentTimeMillis() - (4 * DAY_MS));
        //Main.loadAtlasMapperLayers("https://maps.eatlas.org.au", null);

        //Main.loadDrupalArticles(System.currentTimeMillis() - DAY_MS);
        //Main.loadDrupalArticles(null);

        //Main.loadGeoNetworkRecords("https://eatlas.org.au/geonetwork", System.currentTimeMillis() - (7 * DAY_MS));
        //Main.loadGeoNetworkRecords("https://eatlas.org.au/geonetwork", null);
    }

    private static void testElasticSearch() throws IOException {
        // https://www.elastic.co/guide/en/elasticsearch/client/java-rest/7.8/java-rest-high-document-index.html
        try (ESClient client = new ESRestHighLevelClient(new RestHighLevelClient(
                RestClient.builder(
                        new HttpHost("localhost", 9200, "http"),
                        new HttpHost("localhost", 9300, "http"))))) {

            Map<String, Object> jsonMap = new HashMap<>();
            jsonMap.put("user", "kimchy");
            jsonMap.put("postDate", new Date());
            jsonMap.put("message", "trying out Elasticsearch");

            IndexRequest indexRequest = new IndexRequest("posts")
                .id("1")
                .source(jsonMap);

            IndexResponse indexResponse = client.index(indexRequest);

            String index = indexResponse.getIndex();
            String id = indexResponse.getId();

            System.out.println("index: " + index);
            System.out.println("id: " + id);
        }
    }

    /**
     * Load the first 10 Drupal article into elasticsearch index "eatlas-article"
     * NOTE: The eAtlas website must be running:
     *   $ cd Desktop/projects/eAtlas-redesign/2020-Drupal9/
     *   $ docker-compose up
     */
    private static void loadDrupalArticles(Long lastHarvest) throws Exception {
        String index = "eatlas_article";

        DrupalNodeIndexer drupalNodeIndex = new DrupalNodeIndexer(
            index, "http://localhost:9090", "9", "article", "field_image");

        drupalNodeIndex.setLastIndexed(lastHarvest);
        drupalNodeIndex.harvest(false);
    }

    private static void loadExternalLinks() throws Exception {
        String index = "eatlas_extlink";

        ExternalLinkIndexer eAtlasExternalLinkIndexer = new ExternalLinkIndexer(index);
        eAtlasExternalLinkIndexer.addExternalLink(
            "http://www.csiro.au/connie2/",
            "https://eatlas.org.au/sites/default/files/styles/square_thumbnail/public/eatlas/external-links/connie2-hydrodynamic-modelling.png?itok=gVbD37jD",
            "Connie 2 Online interactive hydrodynamic modelling"
        );
        eAtlasExternalLinkIndexer.addExternalLink(
            "https://doi.org/10.1002/aqc.3115",
            "https://eatlas.org.au/sites/default/files/styles/square_thumbnail/public/eatlas/external-links/GBRMPA-GBRMP-zoning.jpg?itok=XNuZPHhB",
            "Marine zoning revisited: How decades of zoning the Great Barrier Reef has evolved as an effective spatial planning approach for marine ecosystem based management"
        );
        eAtlasExternalLinkIndexer.addExternalLink(
            "https://doi.org/10.1371/journal.pone.0221855",
            "https://eatlas.org.au/sites/default/files/styles/square_thumbnail/public/eatlas/external-links/journal.pone_.0221855.g003.PNG?itok=TiczR9En",
            "Preferences and perceptions of the recreational spearfishery of the Great Barrier Reef - Paper"
        );

        // PDF
        eAtlasExternalLinkIndexer.addExternalLink(
            "https://research.csiro.au/seltmp/wp-content/uploads/sites/214/2019/06/SELTMP-ResidentsChangesReport-May2019.pdf",
            "https://eatlas.org.au/sites/default/files/styles/square_thumbnail/public/eatlas/external-links/SELTMP-ResidentsChangesReport-May2019.jpg?itok=EANyhXRR",
            "Changes among coastal residents of the Great Barrier Reef region from 2013 to 2017 - Social and Economic Long Term Monitoring Program (SELTMP)"
        );

        eAtlasExternalLinkIndexer.addExternalLink(
            "http://marine.ga.gov.au",
            "https://eatlas.org.au/sites/default/files/styles/square_thumbnail/public/eatlas/external-links/AusSeabed-marine-data-portal-preview.jpg?itok=4yFzhXHd",
            "AusSeabed Marine Data Discovery portal"
        );
        eAtlasExternalLinkIndexer.addExternalLink(
            "http://www.seagrasswatch.org/id_seagrass.html",
            "https://eatlas.org.au/sites/default/files/styles/square_thumbnail/public/shared/nerp-te/5-3/Seagrass%20Maggie%20Jan%20081.jpg?itok=cqHnl8-D",
            "Tropical Seagrass Identification (Seagrass-Watch)"
        );

        Long lastHarvest = null;
        eAtlasExternalLinkIndexer.setLastIndexed(lastHarvest);
        eAtlasExternalLinkIndexer.harvest(false);
    }

    private static void loadGeoNetworkRecords(String geoNetworkUrl, Long lastHarvest) throws Exception {
        String index = "eatlas_metadata";
        GeoNetworkIndexer geoNetworkIndex = new GeoNetworkIndexer(index, geoNetworkUrl, "3.0");

        geoNetworkIndex.setLastIndexed(lastHarvest);
        geoNetworkIndex.harvest(false);
    }

    private static void loadAtlasMapperLayers(String atlasMapperClientUrl, Long lastHarvest) throws Exception {
        String index = "eatlas_layer";
        AtlasMapperIndexer atlasMapperIndexer = new AtlasMapperIndexer(index, atlasMapperClientUrl, "2.2.0");

        atlasMapperIndexer.setLastIndexed(lastHarvest);
        atlasMapperIndexer.harvest(false);
    }

    private static void loadDummyExternalLinks(int count) throws IOException {
        String index = "eatlas_dummy";

        try (ESClient client = new ESRestHighLevelClient(new RestHighLevelClient(
                RestClient.builder(
                        new HttpHost("localhost", 9200, "http"),
                        new HttpHost("localhost", 9300, "http"))))) {

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

                IndexRequest indexRequest = new IndexRequest(index)
                    .id(externalLink.getId())
                    .source(IndexUtils.JSONObjectToMap(externalLink.toJSON()));

                IndexResponse indexResponse = client.index(indexRequest);

                System.out.println(String.format("Indexing dummy URL: %s, status: %d", externalLink.getId(), indexResponse.status().getStatus()));
            }
        }
    }
}
