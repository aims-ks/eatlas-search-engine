package au.gov.aims.eatlas.searchengine;

import au.gov.aims.eatlas.searchengine.admin.SearchEngineConfig;
import au.gov.aims.eatlas.searchengine.admin.rest.Messages;
import au.gov.aims.eatlas.searchengine.client.ESClient;
import au.gov.aims.eatlas.searchengine.client.SearchClient;
import au.gov.aims.eatlas.searchengine.entity.GeoNetworkRecord;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.HealthStatus;
import co.elastic.clients.elasticsearch._types.Result;
import co.elastic.clients.elasticsearch.core.CountRequest;
import co.elastic.clients.elasticsearch.core.CountResponse;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
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

    @Test
    public void testIndex() throws IOException {
        try (SearchClient client = this.createElasticsearchClient()) {
            GeoNetworkRecord record = new GeoNetworkRecord(
                "records",
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

    @Test
    public void testIndexExternalLinks() throws IOException {
/*
        ClassLoader classLoader = IndexerTest.class.getClassLoader();
        String index = "eatlas_extlink";

        ExternalLinkIndexer eAtlasExternalLinkIndexer = new ExternalLinkIndexer(index);

        eAtlasExternalLinkIndexer.addExternalLink(
            "https://www.seagrasswatch.org/idseagrass/",
            null,
            "Tropical Seagrass Identification (Seagrass-Watch)"
        );
        eAtlasExternalLinkIndexer.addExternalLink(
            "http://www.coralsoftheworld.org/",
            null,
            "Corals of the World (AIMS)"
        );

        // Fake the harvest
        try (ESClient client = new ESTestClient(super.node().client())) {
            for (ExternalLinkIndexer.ExternalLinkEntry externalLinkEntry : eAtlasExternalLinkIndexer.getExternalLinkEntries()) {
                ExternalLink entity = new ExternalLink(index, externalLinkEntry.getUrl(), externalLinkEntry.getTitle());

                switch (externalLinkEntry.getUrl()) {
                    case "https://www.seagrasswatch.org/idseagrass/":
                        entity.setDocument(EntityUtils.extractHTMLTextContent(IOUtils.resourceToString(
                                "externalLinks/seagrasswatch.html", StandardCharsets.UTF_8, classLoader)));
                        break;

                    case "http://www.coralsoftheworld.org/":
                        entity.setDocument(EntityUtils.extractHTMLTextContent(IOUtils.resourceToString(
                                "externalLinks/coralsoftheworld.html", StandardCharsets.UTF_8, classLoader)));
                        break;
                }

                IndexResponse indexResponse = eAtlasExternalLinkIndexer.index(client, entity);

                LOGGER.debug(String.format("Indexing external URL: %s, status: %d",
                        entity.getId(),
                        indexResponse.status().getStatus()));
            }

            // Wait for ElasticSearch to finish its indexation
            client.refresh(index);

            JSONObject jsonLink = AbstractIndexer.get(client, index, "http://www.coralsoftheworld.org/");

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
                JSONObject searchResultEntity = searchResult.getEntity();
                Assert.assertNotNull(String.format("Search result id %s do not contain a JSON entity.", id), searchResultEntity);
                String title = searchResultEntity.optString("title", null);

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
*/
    }

}
