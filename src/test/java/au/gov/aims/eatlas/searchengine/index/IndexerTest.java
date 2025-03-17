package au.gov.aims.eatlas.searchengine.index;

import au.gov.aims.eatlas.searchengine.HttpClient;
import au.gov.aims.eatlas.searchengine.MockHttpClient;
import au.gov.aims.eatlas.searchengine.logger.ConsoleLogger;
import au.gov.aims.eatlas.searchengine.logger.AbstractLogger;
import au.gov.aims.eatlas.searchengine.entity.ExternalLink;
import au.gov.aims.eatlas.searchengine.entity.GeoNetworkRecord;
import au.gov.aims.eatlas.searchengine.rest.Search;
import au.gov.aims.eatlas.searchengine.search.IndexSummary;
import au.gov.aims.eatlas.searchengine.search.SearchResult;
import au.gov.aims.eatlas.searchengine.search.SearchResults;
import au.gov.aims.eatlas.searchengine.search.Summary;
import co.elastic.clients.elasticsearch._types.HealthStatus;
import co.elastic.clients.elasticsearch._types.Result;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch.core.CountRequest;
import co.elastic.clients.elasticsearch.core.CountResponse;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import org.apache.commons.io.IOUtils;
import org.json.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Basic tests to make sure the ElasticSearch container works,
 * and integrates well with the Search Engine.
 */
public class IndexerTest extends IndexerTestBase {

    @Test
    public void testMockHttpClient() throws Exception {
        MockHttpClient mockHttpClient = this.getMockHttpClient();
        AbstractLogger logger = ConsoleLogger.getInstance();

        mockHttpClient.addGetUrl("https://www.hpwmxatrfsjcebqvdgnukz.com/coralsoftheworld", "externalLinks/coralsoftheworld.html");

        HttpClient.Response response = mockHttpClient.getRequest("https://www.hpwmxatrfsjcebqvdgnukz.com/coralsoftheworld", logger);
        Assertions.assertNotNull(response, "Response is null");
        String responseStr = response.body();
        Assertions.assertNotNull(responseStr, "Response body is null");
        Assertions.assertTrue(responseStr.contains("<title>Corals of the World</title>"), "The response doesn't contain the expected HTML title.");
    }

    @Test
    public void testElasticsearchConnection() throws Exception {
        try (MockSearchClient searchClient = this.createMockSearchClient()) {
            Assertions.assertEquals(HealthStatus.Green, searchClient.getHealthStatus(), "The Elastic Search engine health status is not Green.");
        }
    }

    @Test
    public void testEmptyIndexIsEmpty() throws Exception {
        try (MockSearchClient searchClient = this.createMockSearchClient()) {
            Assertions.assertEquals(HealthStatus.Green, searchClient.getHealthStatus(), "The Elastic Search engine health status is not Green before starting the test.");

            CountResponse countResponse = searchClient.count(new CountRequest.Builder()
                .index("donotexist")
                .ignoreUnavailable(true)
                .build());

            Assertions.assertEquals(0, countResponse.count(), "Number of document in the \"donotexist\" index is not 0.");

            Assertions.assertEquals(HealthStatus.Green, searchClient.getHealthStatus(), "The Elastic Search engine health status is not Green after the test.");
        }
    }

    /**
     * Test indexing a document, using the Elastic Search client.
     */
    @Test
    public void testIndex() throws IOException {
        try (MockSearchClient searchClient = this.createMockSearchClient()) {
            Assertions.assertEquals(HealthStatus.Green, searchClient.getHealthStatus(), "The Elastic Search engine health status is not Green before starting the test.");

            GeoNetworkRecord record = new GeoNetworkRecord(
                "metadata",
                "00000000-0000-0000-0000-000000000000",
                "iso19115-3.2018",
                "3.0"
            );

            String index = "records";
            searchClient.createIndex(index);

            IndexRequest<GeoNetworkRecord> indexRequest = new IndexRequest.Builder<GeoNetworkRecord>()
                .index(index)
                .id(record.getId())
                .document(record)
                .build();

            IndexResponse indexResponse = searchClient.index(indexRequest);

            Result result = indexResponse.result();

            Assertions.assertEquals(Result.Created, result, "Unexpected result.");
            Assertions.assertEquals(HealthStatus.Green, searchClient.getHealthStatus(), "The Elastic Search engine health status is not Green after the test.");
        }
    }

    /**
     * Test indexing a document, using a Search Engine indexer.
     */
    @Test
    public void testIndexExternalLinks() throws IOException {
        // Get the JSON object from the resource files
        // NOTE: JSON Documents extracted from Drupal's JSON API:
        //   http://localhost:1022/jsonapi/node/external_link
        String coralsoftheworldJsonPath = "externalLinks/coralsoftheworld.json";
        JSONObject coralsoftheworldJson = null;
        try (InputStream coralsoftheworldJsonInputStream = IndexerTest.class.getClassLoader().getResourceAsStream(coralsoftheworldJsonPath)) {
            if (coralsoftheworldJsonInputStream != null) {
                String coralsoftheworldJsonTextContent = IOUtils.toString(coralsoftheworldJsonInputStream, StandardCharsets.UTF_8);
                coralsoftheworldJson = new JSONObject(coralsoftheworldJsonTextContent);
            }
        }
        Assertions.assertNotNull(coralsoftheworldJson, String.format("Can not find the test resource file: %s", coralsoftheworldJsonPath));
        String coralsoftheworldId = coralsoftheworldJson.optString("id", null);

        String seagrasswatchJsonPath = "externalLinks/seagrasswatch.json";
        JSONObject seagrasswatchJson = null;
        try (InputStream seagrasswatchJsonInputStream = IndexerTest.class.getClassLoader().getResourceAsStream(seagrasswatchJsonPath)) {
            if (seagrasswatchJsonInputStream != null) {
                String seagrasswatchJsonTextContent = IOUtils.toString(seagrasswatchJsonInputStream, StandardCharsets.UTF_8);
                seagrasswatchJson = new JSONObject(seagrasswatchJsonTextContent);
            }
        }
        Assertions.assertNotNull(seagrasswatchJson, String.format("Can not find the test resource file: %s", seagrasswatchJsonPath));
        String seagrasswatchId = seagrasswatchJson.optString("id", null);

        // Get the HTML content from the test resource (do not rely on Internet)
        String coralsoftheworldHtmlPath = "externalLinks/coralsoftheworld.html";
        String coralsoftheworldTextContent = null;
        try (InputStream coralsoftheworldHtmlInputStream = IndexerTest.class.getClassLoader().getResourceAsStream(coralsoftheworldHtmlPath)) {
            if (coralsoftheworldHtmlInputStream != null) {
                String coralsoftheworldHtml = IOUtils.toString(coralsoftheworldHtmlInputStream, StandardCharsets.UTF_8);
                coralsoftheworldTextContent = HttpClient.extractHTMLTextContent(coralsoftheworldHtml);
            }
        }
        Assertions.assertNotNull(coralsoftheworldTextContent, String.format("Can not find the test resource file: %s", coralsoftheworldHtmlPath));

        String seagrasswatchHtmlPath = "externalLinks/seagrasswatch.html";
        String seagrasswatchTextContent = null;
        try (InputStream seagrasswatchHtmlInputStream = IndexerTest.class.getClassLoader().getResourceAsStream(seagrasswatchHtmlPath)) {
            if (seagrasswatchHtmlInputStream != null) {
                String seagrasswatchHtml = IOUtils.toString(seagrasswatchHtmlInputStream, StandardCharsets.UTF_8);
                seagrasswatchTextContent = HttpClient.extractHTMLTextContent(seagrasswatchHtml);
            }
        }
        Assertions.assertNotNull(seagrasswatchTextContent, String.format("Can not find the test resource file: %s", seagrasswatchHtmlPath));

        try (MockSearchClient searchClient = this.createMockSearchClient()) {
            Assertions.assertEquals(HealthStatus.Green, searchClient.getHealthStatus(), "The Elastic Search engine health status is not Green before starting the test.");
            String index = "links";
            AbstractLogger logger = ConsoleLogger.getInstance();

            searchClient.createIndex(index);

            // Find the indexer, defined in the config file
            DrupalExternalLinkNodeIndexer drupalExternalLinkIndexer =
                (DrupalExternalLinkNodeIndexer)this.getConfig().getIndexer(index);

            // Create the Entities to index (with content overwrite)
            ExternalLink coralsoftheworldLink = new ExternalLink(
                drupalExternalLinkIndexer,
                coralsoftheworldJson,
                logger
            );
            coralsoftheworldLink.setDocument(coralsoftheworldTextContent);

            ExternalLink seagrasswatchLink = new ExternalLink(
                drupalExternalLinkIndexer,
                seagrasswatchJson,
                logger
            );
            seagrasswatchLink.setDocument(seagrasswatchTextContent);

            // Index the entities
            drupalExternalLinkIndexer.indexEntity(searchClient, coralsoftheworldLink, logger);
            drupalExternalLinkIndexer.indexEntity(searchClient, seagrasswatchLink, logger);


            // Wait for ElasticSearch to finish its indexation
            searchClient.refresh(index);


            /* *************** */
            /* Test indexation */
            /* *************** */

            // Retrieve indexed documents directly
            ExternalLink coralsoftheworldIndexedLink = drupalExternalLinkIndexer.get(
                searchClient, ExternalLink.class, coralsoftheworldId);

            ExternalLink seagrasswatchIndexedLink = drupalExternalLinkIndexer.get(
                searchClient, ExternalLink.class, seagrasswatchId);


            // Check indexed values

            // Verify the link retrieved from the index
            Assertions.assertNotNull(coralsoftheworldIndexedLink, "CoralsOfTheWorld link retrieved from the search index is null");
            Assertions.assertNotNull(seagrasswatchIndexedLink, "SeagrassWatch link retrieved from the search index is null");

            Assertions.assertEquals("Corals of the World (AIMS)", coralsoftheworldIndexedLink.getTitle(),
                "CoralsOfTheWorld link retrieved from the search index has wrong title");
            Assertions.assertEquals("Tropical Seagrass Identification (Seagrass-Watch)", seagrasswatchIndexedLink.getTitle(),
                "SeagrassWatch link retrieved from the search index has wrong title");


            /* *************** */
            /* Test the search */
            /* *************** */

            SearchResults results = null;
            try {
                String q = "of"; // Search for the word "of", present in both external link entities
                Integer start = 0;
                Integer hits = 50; // There is only 2 documents in the index
                String wkt = null; // No geographic filtering
                List<String> idx = List.of(index, "metadata");
                List<String> fidx = List.of(index);
                List<SortOptions> sortOptionsList = new ArrayList<>();

                results = Search.paginationSearch(searchClient, q, start, hits, wkt, sortOptionsList, idx, null, logger);

                Summary searchSummary = results.getSummary();

                // Check indexes in the summary.
                Map<String, IndexSummary> indexSummaryMap = searchSummary.getIndexSummaries();
                Assertions.assertEquals(1, indexSummaryMap.size(),
                    "Wrong number if index summary.");
                Assertions.assertTrue(indexSummaryMap.containsKey(index),
                    String.format("Missing index from the search summary: %s", index));

                IndexSummary linksIndexSummary = searchSummary.getIndexSummary(index);

                Assertions.assertEquals(2, linksIndexSummary.getHits(),
                    "Wrong number of search result in the index summary.");


                List<SearchResult> searchResultList = results.getSearchResults();
                Assertions.assertEquals(2, searchResultList.size(),
                    "Wrong number of search result in the result list.");

                for (SearchResult searchResult : searchResultList) {
                    Assertions.assertNotNull(searchResult, "External link entity found with index search is null");

                    String id = searchResult.getId();
                    ExternalLink externalLinkEntity = (ExternalLink)searchResult.getEntity();

                    String title = externalLinkEntity.getTitle();
                    Assertions.assertNotNull(title,
                        String.format("Search result id %s do not contain a title.", id));
                    Assertions.assertFalse(title.isEmpty(),
                        String.format("Search result id %s contains an empty title.", id));

                    Assertions.assertNotNull(externalLinkEntity,
                        String.format("Search result %s do not contain an entity.", title));

                    List<String> highlights = searchResult.getHighlights();
                    Assertions.assertNotNull(highlights,
                        String.format("Search result %s do not contain any highlight.", title));

                    String highlight = String.join(" -- ", highlights);

                    if (coralsoftheworldId.equals(id)) {
                        Assertions.assertEquals("Corals of the World (AIMS)", title,
                            String.format("Link %s found with index search has wrong title", title));

                        Assertions.assertTrue(highlight.contains("Overview <strong class=\"search-highlight\">of</strong>"),
                            String.format("Link %s found with index search has unexpected highlight: %s", title, highlight));

                        Assertions.assertTrue(highlight.contains("timing <strong class=\"search-highlight\">of</strong> releases."),
                            String.format("Link %s found with index search has unexpected highlight: %s", title, highlight));

                    } else if (seagrasswatchId.equals(id)) {
                        Assertions.assertEquals("Tropical Seagrass Identification (Seagrass-Watch)", title,
                            String.format("Link %s found with index search has wrong title", title));

                        Assertions.assertTrue(highlight.contains("From the advice <strong class=\"search-highlight\">of</strong> Dr Don Les"),
                            String.format("Link %s found with index search has unexpected highlight: %s", title, highlight));

                        Assertions.assertTrue(highlight.contains("the shapes and sizes <strong class=\"search-highlight\">of</strong> leaves"),
                            String.format("Link %s found with index search has unexpected highlight: %s", title, highlight));

                    } else {
                        Assertions.fail(String.format("Unexpected ID found: %s", id));
                    }
                }

            } catch(Exception ex) {
                Assertions.fail("Exception thrown while testing the Search API.", ex);
            }

            Assertions.assertEquals(HealthStatus.Green, searchClient.getHealthStatus(), "The Elastic Search engine health status is not Green after the test.");
        }
    }

}
