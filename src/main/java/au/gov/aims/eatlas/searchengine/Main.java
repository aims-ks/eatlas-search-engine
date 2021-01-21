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
import au.gov.aims.eatlas.searchengine.entity.GeoNetworkRecord;
import au.gov.aims.eatlas.searchengine.index.IndexUtils;
import org.apache.http.HttpHost;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Connection;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Main {

    public static void main(String... args) throws Exception {
        //Main.testElasticSearch();
        //Main.loadDummyExternalLinks(15000);

        //Main.loadDrupalArticles();
        //Main.loadExternalLinks();
        Main.loadGeoNetworkRecords("https://eatlas.org.au/geonetwork");
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
        String url = "http://localhost:9090/jsonapi/node/article?include=field_image&sort=-changed&page[limit]=100&page[offset]=0";

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
            "Connie 2 Online interactive hydrodynamic modelling"
        ));

        externalLinks.add(new ExternalLink(
            "https://doi.org/10.1002/aqc.3115",
            "https://eatlas.org.au/sites/default/files/styles/square_thumbnail/public/eatlas/external-links/GBRMPA-GBRMP-zoning.jpg?itok=XNuZPHhB",
            "Marine zoning revisited: How decades of zoning the Great Barrier Reef has evolved as an effective spatial planning approach for marine ecosystem based management"
        ));

        externalLinks.add(new ExternalLink(
            "https://doi.org/10.1371/journal.pone.0221855",
            "https://eatlas.org.au/sites/default/files/styles/square_thumbnail/public/eatlas/external-links/journal.pone_.0221855.g003.PNG?itok=TiczR9En",
            "Preferences and perceptions of the recreational spearfishery of the Great Barrier Reef - Paper"
        ));

        // PDF
        externalLinks.add(new ExternalLink(
            "https://research.csiro.au/seltmp/wp-content/uploads/sites/214/2019/06/SELTMP-ResidentsChangesReport-May2019.pdf",
            "https://eatlas.org.au/sites/default/files/styles/square_thumbnail/public/eatlas/external-links/SELTMP-ResidentsChangesReport-May2019.jpg?itok=EANyhXRR",
            "Changes among coastal residents of the Great Barrier Reef region from 2013 to 2017 - Social and Economic Long Term Monitoring Program (SELTMP)"
        ));

        externalLinks.add(new ExternalLink(
            "http://marine.ga.gov.au",
            "https://eatlas.org.au/sites/default/files/styles/square_thumbnail/public/eatlas/external-links/AusSeabed-marine-data-portal-preview.jpg?itok=4yFzhXHd",
            "AusSeabed Marine Data Discovery portal"
        ));

        externalLinks.add(new ExternalLink(
            "http://www.seagrasswatch.org/id_seagrass.html",
            "https://eatlas.org.au/sites/default/files/styles/square_thumbnail/public/shared/nerp-te/5-3/Seagrass%20Maggie%20Jan%20081.jpg?itok=cqHnl8-D",
            "Tropical Seagrass Identification (Seagrass-Watch)"
        ));

        try (ESClient client = new ESRestHighLevelClient(new RestHighLevelClient(
                RestClient.builder(
                        new HttpHost("localhost", 9200, "http"),
                        new HttpHost("localhost", 9300, "http"))))) {

            for (ExternalLink externalLink : externalLinks) {
                externalLink.setDocument(EntityUtils.harvestURLText(externalLink.getLink().toString()));

                IndexRequest indexRequest = new IndexRequest(index)
                    .id(externalLink.getId())
                    .source(IndexUtils.JSONObjectToMap(externalLink.toJSON()));

                IndexResponse indexResponse = client.index(indexRequest);

                System.out.println(String.format("Indexing URL: %s, status: %d", externalLink.getId(), indexResponse.status().getStatus()));
            }
        }
    }

    private static void loadGeoNetworkRecords(String geoNetworkUrl) throws IOException, ParserConfigurationException, SAXException {
        String index = "eatlas_metadata";

        // https://geonetwork-opensource.org/manuals/2.10.4/eng/developer/xml_services/metadata_xml_search_retrieve.html
        String url = String.format("%s/srv/eng/xml.search", geoNetworkUrl);

        String responseStr = EntityUtils.harvestURL(url);

        // JDOM tutorial:
        //     https://www.tutorialspoint.com/java_xml/java_dom_parse_document.htm
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();

        try (ByteArrayInputStream input = new ByteArrayInputStream(
            responseStr.getBytes(StandardCharsets.UTF_8))) {

            Document document = builder.parse(input);
            // Fix the document, if needed
            document.getDocumentElement().normalize();

            Element root = document.getDocumentElement();

            NodeList metadataRecordList = root.getElementsByTagName("metadata");

            try (ESClient client = new ESRestHighLevelClient(new RestHighLevelClient(
                    RestClient.builder(
                            new HttpHost("localhost", 9200, "http"),
                            new HttpHost("localhost", 9300, "http"))))) {

                for (int i=0; i<metadataRecordList.getLength(); i++) {
                    Node metadataRecordNode = metadataRecordList.item(i);

                    if (metadataRecordNode.getNodeType() == Node.ELEMENT_NODE) {
                        Element metadataRecordElement = (Element) metadataRecordNode;
                        Node metadataRecordInfoNode = metadataRecordElement.getElementsByTagName("geonet:info").item(0);
                        if (metadataRecordInfoNode.getNodeType() == Node.ELEMENT_NODE) {
                            Element metadataRecordInfoElement = (Element) metadataRecordInfoNode;
                            Node metadataRecordUUIDNode = metadataRecordInfoElement.getElementsByTagName("uuid").item(0);

                            String metadataRecordUUID = metadataRecordUUIDNode.getTextContent();
                            Main.loadGeoNetworkRecord(client, index, builder, geoNetworkUrl, metadataRecordUUID);
                        }
                    }

// TODO Delete when the parser is done
if (i >= 2) break;
                }
            }
        }
    }

    private static void loadGeoNetworkRecord(ESClient client, String index, DocumentBuilder builder, String geoNetworkUrl, String metadataRecordUUID) throws IOException, ParserConfigurationException, SAXException {
        String url = String.format("%s/srv/eng/xml.metadata.get", geoNetworkUrl);
        String metadataRecordUrl = String.format("%s/srv/eng/metadata.show?uuid=%s", geoNetworkUrl, metadataRecordUUID);

        String responseStr = EntityUtils.getJsoupConnection(url)
                .data("uuid", metadataRecordUUID)
                .method(Connection.Method.POST)
                .execute()
                .body();

        System.out.println(String.format("METADATA RECORD UUID %s:%n%s", metadataRecordUUID, responseStr));

        try (ByteArrayInputStream input = new ByteArrayInputStream(
            responseStr.getBytes(StandardCharsets.UTF_8))) {

            Document document = builder.parse(input);
            // Fix the document, if needed
            document.getDocumentElement().normalize();

            GeoNetworkRecord geoNetworkRecord = new GeoNetworkRecord(metadataRecordUUID, metadataRecordUrl, document.getDocumentElement());

            // TODO Uncomment when parser is ready
            /*
            IndexRequest indexRequest = new IndexRequest(index)
                .id(geoNetworkRecord.getId())
                .source(IndexUtils.JSONObjectToMap(geoNetworkRecord.toJSON()));

            IndexResponse indexResponse = client.index(indexRequest);

            System.out.println(String.format("Indexing GeoNetwork metadata record: %s, status: %d", geoNetworkRecord.getId(), indexResponse.status().getStatus()));
            */
        } catch(Exception ex) {
            System.out.println(String.format("Invalid metadata record UUID: %s", metadataRecordUUID));
        }
    }

    private static void loadDummyExternalLinks(int count) throws IOException {
        String index = "eatlas_dummy";

        try (ESClient client = new ESRestHighLevelClient(new RestHighLevelClient(
                RestClient.builder(
                        new HttpHost("localhost", 9200, "http"),
                        new HttpHost("localhost", 9300, "http"))))) {

            for (int i=0; i<count; i++) {
                ExternalLink externalLink = new ExternalLink(
                    String.format("http://www.domain.com/result/%d", i),
                    "https://www.google.com/logos/doodles/2020/december-holidays-days-2-30-6753651837108830.3-law.gif",
                    String.format("Dummy link number: %d", i)
                );
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
