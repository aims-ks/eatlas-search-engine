package au.gov.aims.eatlas.searchengine.index;

import au.gov.aims.eatlas.searchengine.admin.SearchEngineConfig;
import au.gov.aims.eatlas.searchengine.admin.rest.Messages;
import au.gov.aims.eatlas.searchengine.client.ESClient;
import au.gov.aims.eatlas.searchengine.client.SearchClient;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.BeforeAll;
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
import java.util.Properties;

@Testcontainers
public abstract class IndexerTestBase {

    private static SearchEngineConfig config;

    public SearchEngineConfig getConfig() {
        return IndexerTestBase.config;
    }

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
                    .waitingFor(Wait.forHttp("/_cluster/health?wait_for_status=green&timeout=1m")
                            .forPort(9200) // This refers to the internal port of the container
                            .forStatusCode(200));

    public SearchClient createElasticsearchClient() {
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

    public static String getResourceFileContent(String resourcePath) throws IOException {
        try (InputStream is = IndexerTestBase.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (is != null) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        }

        return null;
    }
}
