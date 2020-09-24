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
import org.apache.http.HttpHost;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class Main {

    public static void main(String... args) throws IOException {
        // https://www.elastic.co/guide/en/elasticsearch/client/java-rest/7.8/java-rest-high-document-index.html
        try (ESClient client = new ESRestHighLevelClient(new RestHighLevelClient(
                RestClient.builder(
                        new HttpHost("localhost", 9200, "http"),
                        new HttpHost("localhost", 9201, "http"))))) {

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
}
