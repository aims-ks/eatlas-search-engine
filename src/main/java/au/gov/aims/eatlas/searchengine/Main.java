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

import au.gov.aims.eatlas.searchengine.client.ESClient;
import au.gov.aims.eatlas.searchengine.client.ESRestHighLevelClient;
import au.gov.aims.eatlas.searchengine.entity.DrupalNode;
import au.gov.aims.eatlas.searchengine.entity.EntityUtils;
import au.gov.aims.eatlas.searchengine.entity.ExternalLink;
import au.gov.aims.eatlas.searchengine.index.IndexUtils;
import org.apache.http.HttpHost;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Main {

    public static void main(String... args) throws IOException {
        //Main.testElasticSearch();
        //Main.loadDrupalArticles();
        Main.loadExternalLinks();
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
    private static void loadDrupalArticles() throws IOException {
        String index = "eatlas_article";

        // URL to get first 10 last modified articles (using Drupal core module JSON:API)
        String url = "http://localhost:9090/jsonapi/node/article?include=field_image&sort=-changed&page[limit]=10&page[offset]=0";


        try (ESClient client = new ESRestHighLevelClient(new RestHighLevelClient(
                RestClient.builder(
                        new HttpHost("localhost", 9200, "http"),
                        new HttpHost("localhost", 9300, "http"))))) {

            String responseStr = EntityUtils.harvestURL(url);
            JSONObject jsonResponse = new JSONObject(responseStr);

            JSONArray jsonArticles = jsonResponse.optJSONArray("data");
            JSONArray jsonIncluded = jsonResponse.optJSONArray("included");

            if (jsonArticles != null) {
                for (int i=0; i<jsonArticles.length(); i++) {
                    DrupalNode drupalNode = new DrupalNode(jsonArticles.optJSONObject(i), jsonIncluded);

                    IndexRequest indexRequest = new IndexRequest(index)
                        .id(drupalNode.getId())
                        .source(IndexUtils.JSONObjectToMap(drupalNode.toJSON()));

                    IndexResponse indexResponse = client.index(indexRequest);

                    System.out.println(String.format("Indexing node ID: %d, status: %d", drupalNode.getNid(), indexResponse.status().getStatus()));
                }
            }
        }
    }

    private static void loadExternalLinks() throws IOException {
        String index = "eatlas_extlink";

        List<ExternalLink> externalLinks = new ArrayList<ExternalLink>();
        externalLinks.add(new ExternalLink(
            "http://www.csiro.au/connie2/",
            "https://eatlas.org.au/sites/default/files/styles/square_thumbnail/public/eatlas/external-links/connie2-hydrodynamic-modelling.png?itok=gVbD37jD",
            "Connie 2 Online interactive hydrodynamic modelling",
            EntityUtils.harvestURLText("http://www.csiro.au/connie2/")
        ));

        externalLinks.add(new ExternalLink(
            "https://doi.org/10.1002/aqc.3115",
            "https://eatlas.org.au/sites/default/files/styles/square_thumbnail/public/eatlas/external-links/GBRMPA-GBRMP-zoning.jpg?itok=XNuZPHhB",
            "Marine zoning revisited: How decades of zoning the Great Barrier Reef has evolved as an effective spatial planning approach for marine ecosystem based management",
            EntityUtils.harvestURLText("https://doi.org/10.1002/aqc.3115")
        ));

        externalLinks.add(new ExternalLink(
            "https://doi.org/10.1371/journal.pone.0221855",
            "https://eatlas.org.au/sites/default/files/styles/square_thumbnail/public/eatlas/external-links/journal.pone_.0221855.g003.PNG?itok=TiczR9En",
            "Preferences and perceptions of the recreational spearfishery of the Great Barrier Reef - Paper",
            EntityUtils.harvestURLText("https://doi.org/10.1371/journal.pone.0221855")
        ));

        // PDF
        externalLinks.add(new ExternalLink(
            "https://research.csiro.au/seltmp/wp-content/uploads/sites/214/2019/06/SELTMP-ResidentsChangesReport-May2019.pdf",
            "https://eatlas.org.au/sites/default/files/styles/square_thumbnail/public/eatlas/external-links/SELTMP-ResidentsChangesReport-May2019.jpg?itok=EANyhXRR",
            "Changes among coastal residents of the Great Barrier Reef region from 2013 to 2017 - Social and Economic Long Term Monitoring Program (SELTMP)",
            EntityUtils.harvestURLText("https://research.csiro.au/seltmp/wp-content/uploads/sites/214/2019/06/SELTMP-ResidentsChangesReport-May2019.pdf")
        ));

        externalLinks.add(new ExternalLink(
            "http://marine.ga.gov.au",
            "https://eatlas.org.au/sites/default/files/styles/square_thumbnail/public/eatlas/external-links/AusSeabed-marine-data-portal-preview.jpg?itok=4yFzhXHd",
            "AusSeabed Marine Data Discovery portal",
            EntityUtils.harvestURLText("http://marine.ga.gov.au")
        ));

        externalLinks.add(new ExternalLink(
            "http://www.seagrasswatch.org/id_seagrass.html",
            "https://eatlas.org.au/sites/default/files/styles/square_thumbnail/public/shared/nerp-te/5-3/Seagrass%20Maggie%20Jan%20081.jpg?itok=cqHnl8-D",
            "Tropical Seagrass Identification (Seagrass-Watch)",
            EntityUtils.harvestURLText("http://www.seagrasswatch.org/id_seagrass.html")
        ));

        try (ESClient client = new ESRestHighLevelClient(new RestHighLevelClient(
                RestClient.builder(
                        new HttpHost("localhost", 9200, "http"),
                        new HttpHost("localhost", 9300, "http"))))) {

            for (ExternalLink externalLink : externalLinks) {

                IndexRequest indexRequest = new IndexRequest(index)
                    .id(externalLink.getId())
                    .source(IndexUtils.JSONObjectToMap(externalLink.toJSON()));

                IndexResponse indexResponse = client.index(indexRequest);

                System.out.println(String.format("Indexing URL: %s, status: %d", externalLink.getId(), indexResponse.status().getStatus()));
            }
        }
    }
}
