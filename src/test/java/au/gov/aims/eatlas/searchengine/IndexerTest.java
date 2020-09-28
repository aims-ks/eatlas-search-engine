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
import au.gov.aims.eatlas.searchengine.client.ESTestClient;
import au.gov.aims.eatlas.searchengine.entity.ExternalLink;
import au.gov.aims.eatlas.searchengine.index.ExternalLinks;
import au.gov.aims.eatlas.searchengine.index.SearchResult;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.test.ESIntegTestCase;
import org.elasticsearch.test.ESSingleNodeTestCase;
import org.elasticsearch.test.hamcrest.ElasticsearchAssertions;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// https://mincong.io/2019/11/24/essinglenodetestcase/
// https://discuss.elastic.co/t/is-the-highlevelrestclient-no-supported-in-the-java-testing-framework/218083/3
// https://www.javacodegeeks.com/2017/03/elasticsearch-java-developers-elasticsearch-java.html
@ESIntegTestCase.ClusterScope(numDataNodes = 3)
public class IndexerTest extends ESSingleNodeTestCase {

/*
    @BeforeClass
    public static void blah() {
        // ESIntegTestCase (or com.carrotsearch.randomizedtesting.RandomizedRunner)
        // is changing the Locale to random stuff, making it really hard to read logs.
        Locale.setDefault(Locale.forLanguageTag("en_AU"));
    }

    @Before
    public void setUpCatalog() throws IOException {
        CreateIndexResponse createIndexResponse = this.admin()
            .indices()
            .prepareCreate("catalog")
            .get();

        ElasticsearchAssertions.assertAcked(createIndexResponse);
        this.ensureGreen("catalog");
    }

    @After
    public void tearDownCatalog() throws IOException, InterruptedException {
        ESIntegTestCase.cluster().wipeIndices("catalog");
    }
*/

    @Test
    public void testEmptyCatalogHasNoBooks() {
        final SearchResponse response = client()
            .prepareSearch("catalog")
            .setSearchType(SearchType.DFS_QUERY_THEN_FETCH)
            .setQuery(QueryBuilders.matchAllQuery())
            .setFetchSource(false)
            .get();

        ElasticsearchAssertions.assertNoSearchHits(response);
    }

    @Test
    public void testIndex() throws IOException {
        try (ESClient client = new ESTestClient(super.node().client())) {
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

    @Test
    public void testIndexExternalLinks() throws IOException {
        List<ExternalLink> links = new ArrayList<>();

        links.add(new ExternalLink(
            "Tropical Seagrass Identification (Seagrass-Watch)",
            "https://www.seagrasswatch.org/idseagrass/"
        ));
        links.add(new ExternalLink(
            "Corals of the World (AIMS)",
            "http://www.coralsoftheworld.org/"
        ));

        // Harvest
        for (ExternalLink link : links) {
            link.harvestHtmlContent();
            link.extractTextContent();
        }

        try (ESClient client = new ESTestClient(super.node().client())) {
            ExternalLinks externalLinks = new ExternalLinks();

            for (ExternalLink link : links) {
                externalLinks.index(client, link);
            }
            // Wait for ElasticSearch to finish its indexation
            client.refresh(externalLinks.getIndex());

            ExternalLink link = externalLinks.get(client, "http://www.coralsoftheworld.org/");

            System.out.println(link.toString());

            List<SearchResult> searchResults = externalLinks.search(client, "textContent", "of", 0, 10);

            System.out.println("foundLinks:");
            for (SearchResult searchResult : searchResults) {
                link = externalLinks.get(client, searchResult.getId());
                System.out.println(searchResult.getHighlight());
                System.out.println(link.toString());
            }
        }
    }
}
