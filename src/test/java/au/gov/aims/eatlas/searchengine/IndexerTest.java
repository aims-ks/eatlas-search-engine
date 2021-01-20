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
import au.gov.aims.eatlas.searchengine.entity.EntityUtils;
import au.gov.aims.eatlas.searchengine.entity.ExternalLink;
import au.gov.aims.eatlas.searchengine.index.AbstractIndex;
import au.gov.aims.eatlas.searchengine.index.ExternalLinkIndex;
import au.gov.aims.eatlas.searchengine.rest.Search;
import au.gov.aims.eatlas.searchengine.search.SearchResult;
import org.apache.commons.io.IOUtils;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.test.ESIntegTestCase;
import org.elasticsearch.test.ESSingleNodeTestCase;
import org.elasticsearch.test.hamcrest.ElasticsearchAssertions;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// https://mincong.io/2019/11/24/essinglenodetestcase/
// https://discuss.elastic.co/t/is-the-highlevelrestclient-no-supported-in-the-java-testing-framework/218083/3
// https://www.javacodegeeks.com/2017/03/elasticsearch-java-developers-elasticsearch-java.html
@ESIntegTestCase.ClusterScope(numDataNodes = 3)
public class IndexerTest extends ESSingleNodeTestCase {

    @BeforeClass
    public static void beforeClass() {
        // ESIntegTestCase (or com.carrotsearch.randomizedtesting.RandomizedRunner)
        // is changing the Locale to random stuff, making it really hard to read logs.
        Locale.setDefault(Locale.forLanguageTag("en_AU"));
    }

    @Before
    public void setUpCatalog() {
        this.createIndex("catalog");

        this.ensureGreen("catalog");
    }

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
        ClassLoader classLoader = IndexerTest.class.getClassLoader();
        String index = "eatlas_extlink";

        ExternalLinkIndex seagrassWatchLinkIndex = new ExternalLinkIndex(
            index,
            "https://www.seagrasswatch.org/idseagrass/",
            null,
            "Tropical Seagrass Identification (Seagrass-Watch)"
        );
        String seagrassWatchLinkText = EntityUtils.extractHTMLTextContent(IOUtils.resourceToString(
                "externalLinks/seagrasswatch.html", StandardCharsets.UTF_8, classLoader));
        ExternalLink seagrassWatchLink = new ExternalLink(
                seagrassWatchLinkIndex.getUrl(), seagrassWatchLinkIndex.getThumbnail(),
                seagrassWatchLinkIndex.getTitle(), seagrassWatchLinkText);

        ExternalLinkIndex coralsOfTheWorldLinkIndex = new ExternalLinkIndex(
            index,
            "http://www.coralsoftheworld.org/",
            null,
            "Corals of the World (AIMS)"
        );
        String coralsOfTheWorldLinkText = EntityUtils.extractHTMLTextContent(IOUtils.resourceToString(
                "externalLinks/coralsoftheworld.html", StandardCharsets.UTF_8, classLoader));
        ExternalLink coralsOfTheWorldLink = new ExternalLink(
                coralsOfTheWorldLinkIndex.getUrl(), coralsOfTheWorldLinkIndex.getThumbnail(),
                coralsOfTheWorldLinkIndex.getTitle(), coralsOfTheWorldLinkText);

        try (ESClient client = new ESTestClient(super.node().client())) {
            seagrassWatchLinkIndex.index(client, seagrassWatchLink);
            coralsOfTheWorldLinkIndex.index(client, coralsOfTheWorldLink);

            // Wait for ElasticSearch to finish its indexation
            client.refresh(index);

            JSONObject jsonLink = AbstractIndex.get(client, index, "http://www.coralsoftheworld.org/");

            // Verify the link retrieved from the index
            Assert.assertNotNull("Link retrieved from the search index is null", jsonLink);
            Assert.assertEquals("Link retrieved from the search index has wrong title",
                    "Corals of the World (AIMS)", jsonLink.optString("title", null));

            // Check the search
            List<SearchResult> searchResults = Search.search(client, "of", 0, 10, index);
            Assert.assertEquals("Wrong number of search result", 2, searchResults.size());

            int found = 0;
            for (SearchResult searchResult : searchResults) {
                Assert.assertNotNull("Link found with index search is null", searchResult);

                String id = searchResult.getId();
                String title = searchResult.getTitle();

                List<String> highlights = searchResult.getHighlights();
                String highlight = String.join(" ", highlights);

                switch (id) {
                    case "http://www.coralsoftheworld.org/":
                        found++;
                        Assert.assertEquals(String.format("Link %s found with index search has wrong title", id),
                            "Corals of the World (AIMS)", title);

                        Assert.assertTrue(String.format("Link %s found with index search has unexpected highlight: %s", id, highlight),
                            highlight.contains("Donate Go Toggle navigation Corals <strong>of</strong> the World"));
                        break;

                    case "https://www.seagrasswatch.org/idseagrass/":
                        found++;
                        Assert.assertEquals(String.format("Link %s found with index search has wrong title", id),
                            "Tropical Seagrass Identification (Seagrass-Watch)", title);

                        Assert.assertTrue(String.format("Link %s found with index search has unexpected highlight: %s", id, highlight),
                            highlight.contains("From the advice <strong>of</strong> Dr Don Les"));
                        break;

                    default:
                        Assert.fail(String.format("Unexpected ID found: %s", id));
                }
            }

            Assert.assertEquals("Some of the external links was not found.", 2, found);
        }
    }
}
