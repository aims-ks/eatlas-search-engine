package au.gov.aims.eatlas.searchengine;

import au.gov.aims.eatlas.searchengine.admin.SearchEngineConfig;
import au.gov.aims.eatlas.searchengine.admin.rest.Messages;
import au.gov.aims.eatlas.searchengine.client.ESClient;
import au.gov.aims.eatlas.searchengine.client.SearchClient;
import au.gov.aims.eatlas.searchengine.entity.EntityUtils;
import au.gov.aims.eatlas.searchengine.entity.ExternalLink;
import au.gov.aims.eatlas.searchengine.entity.GeoNetworkRecord;
import au.gov.aims.eatlas.searchengine.index.DrupalExternalLinkNodeIndexer;
import au.gov.aims.eatlas.searchengine.rest.Search;
import au.gov.aims.eatlas.searchengine.search.IndexSummary;
import au.gov.aims.eatlas.searchengine.search.SearchResult;
import au.gov.aims.eatlas.searchengine.search.SearchResults;
import au.gov.aims.eatlas.searchengine.search.Summary;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.HealthStatus;
import co.elastic.clients.elasticsearch._types.Result;
import co.elastic.clients.elasticsearch.core.CountRequest;
import co.elastic.clients.elasticsearch.core.CountResponse;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.json.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;

@Testcontainers
public class IndexerTest {

    private static SearchEngineConfig config;

    @BeforeAll
    public static void setup() throws Exception {
        URL resourceUrl = IndexerTest.class.getClassLoader().getResource("config/eatlas_search_engine.json");
        if (resourceUrl == null) {
            throw new FileNotFoundException("Could not find the Search Engine config file for tests");
        }
        File configFile = new File(resourceUrl.getFile());
        Messages messages = Messages.getInstance(null);

        config = SearchEngineConfig.createInstance(configFile, "eatlas_search_engine_devel.json", messages);
    }

    public static String getElasticsearchVersion() {
        // Load the Elastic Search version found in the test.properties file,
        // after being substituted by Maven with the actual version number found in the POM.
        try (InputStream input = IndexerTest.class.getClassLoader()
                .getResourceAsStream("test.properties")) {
            Properties prop = new Properties();
            prop.load(input);
            return prop.getProperty("elasticsearch.version");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Container
    private static final ElasticsearchContainer elasticsearchContainer =
        new ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch:" + IndexerTest.getElasticsearchVersion())
            // Restrict to single node (for testing purposes)
            .withEnv("discovery.type", "single-node")
            // Restrict memory usage
            .withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m")
            // Disable HTTPS
            .withEnv("xpack.security.enabled", "false")
            .withEnv("xpack.security.http.ssl.enabled", "false")
            .waitingFor(Wait.forHttp("/_cluster/health")
            .forPort(9200) // This refers to the internal port of the container
            .forStatusCode(200));

    private SearchClient createElasticsearchClient() {
        // Retrieve the dynamically assigned HTTP host address from Testcontainers
        String elasticsearchHttpHostAddress = elasticsearchContainer.getHttpHostAddress();

        // Create an instance of the RestClient
        RestClient restClient = RestClient.builder(
                HttpHost.create(elasticsearchHttpHostAddress)
        ).build();

        // Create the ElasticsearchClient using the RestClient
        RestClientTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
        return new ESClient(new ElasticsearchClient(transport));
    }

    @Test
    public void testElasticsearchConnection() throws Exception {
        try (SearchClient client = this.createElasticsearchClient()) {
            HealthStatus status = client.getHealthStatus();

            Assertions.assertEquals(HealthStatus.Green, status, "The Elastic Search engine health status is not Green.");
        }
    }

    @Test
    public void testEmptyCatalogHasNoBooks() throws Exception {
        try (SearchClient client = this.createElasticsearchClient()) {
            CountResponse countResponse = client.count(new CountRequest.Builder()
                .index("donotexist")
                .ignoreUnavailable(true)
                .build());

            Assertions.assertEquals(0, countResponse.count(), "Number of document in the donotexist index is not 0.");
        }
    }

    /**
     * Test indexing a document, using the Elastic Search client.
     */
    @Test
    public void testIndex() throws IOException {
        try (SearchClient client = this.createElasticsearchClient()) {
            GeoNetworkRecord record = new GeoNetworkRecord(
                "metadata",
                "00000000-0000-0000-0000-000000000000",
                "iso19115-3.2018",
                "3.0"
            );

            IndexRequest<GeoNetworkRecord> indexRequest = new IndexRequest.Builder<GeoNetworkRecord>()
                .index("records")
                .id(record.getId())
                .document(record)
                .build();

            IndexResponse indexResponse = client.index(indexRequest);

            Result result = indexResponse.result();

            Assertions.assertEquals(Result.Created, result, "Unexpected result.");
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
                coralsoftheworldTextContent = EntityUtils.extractHTMLTextContent(coralsoftheworldHtml);
            }
        }
        Assertions.assertNotNull(coralsoftheworldTextContent, String.format("Can not find the test resource file: %s", coralsoftheworldHtmlPath));

        String seagrasswatchHtmlPath = "externalLinks/seagrasswatch.html";
        String seagrasswatchTextContent = null;
        try (InputStream seagrasswatchHtmlInputStream = IndexerTest.class.getClassLoader().getResourceAsStream(seagrasswatchHtmlPath)) {
            if (seagrasswatchHtmlInputStream != null) {
                String seagrasswatchHtml = IOUtils.toString(seagrasswatchHtmlInputStream, StandardCharsets.UTF_8);
                seagrasswatchTextContent = EntityUtils.extractHTMLTextContent(seagrasswatchHtml);
            }
        }
        Assertions.assertNotNull(seagrasswatchTextContent, String.format("Can not find the test resource file: %s", seagrasswatchHtmlPath));

        try (SearchClient client = this.createElasticsearchClient()) {
            String index = "links";
            Messages messages = Messages.getInstance(null);

            // Find the indexer, defined in the config file
            DrupalExternalLinkNodeIndexer drupalExternalLinkIndexer =
                (DrupalExternalLinkNodeIndexer)config.getIndexer(index);

            // Create the Entities to index (with content overwrite)
            ExternalLink coralsoftheworldLink = new ExternalLink(
                index,
                coralsoftheworldJson,
                messages
            );
            coralsoftheworldLink.setDocument(coralsoftheworldTextContent);

            ExternalLink seagrasswatchLink = new ExternalLink(
                index,
                seagrasswatchJson,
                messages
            );
            seagrasswatchLink.setDocument(seagrasswatchTextContent);

            // Index the entities
            drupalExternalLinkIndexer.indexEntity(client, coralsoftheworldLink, messages);
            drupalExternalLinkIndexer.indexEntity(client, seagrasswatchLink, messages);


            // Wait for ElasticSearch to finish its indexation
            client.refresh(index);


            /* *************** */
            /* Test indexation */
            /* *************** */

            // Retrieve indexed documents directly
            ExternalLink coralsoftheworldIndexedLink = drupalExternalLinkIndexer.get(
                client, ExternalLink.class, coralsoftheworldId);

            ExternalLink seagrasswatchIndexedLink = drupalExternalLinkIndexer.get(
                client, ExternalLink.class, seagrasswatchId);


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
                List<String> idx = Arrays.asList(index, "metadata");
                List<String> fidx = Arrays.asList(index);


                results = Search.paginationSearch(client, q, start, hits, wkt, idx, fidx, messages);

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

        }
    }

}
