package au.gov.aims.eatlas.searchengine;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.HealthStatus;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * This test was created to experiment with the "Testcontainers" framework library.
 */
@Testcontainers
public class ToDeleteTemporaryTest {

    public static String getElasticsearchVersion() {
        // Load the Elastic Search version found in the test.properties file,
        // after being substituted by Maven with the actual version number found in the POM.
        try (InputStream input = ToDeleteTemporaryTest.class.getClassLoader()
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
        new ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch:" + ToDeleteTemporaryTest.getElasticsearchVersion())
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

    private ElasticsearchClient createElasticsearchClient() {
        // Retrieve the dynamically assigned HTTP host address from Testcontainers
        String elasticsearchHttpHostAddress = elasticsearchContainer.getHttpHostAddress();

        // Create an instance of the RestClient
        RestClient restClient = RestClient.builder(
                HttpHost.create(elasticsearchHttpHostAddress)
        ).build();

        // Create the ElasticsearchClient using the RestClient
        RestClientTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
        return new ElasticsearchClient(transport);
    }

    @Test
    public void testElasticsearchConnection() throws Exception {
        ElasticsearchClient client = createElasticsearchClient();
        HealthStatus status = client.cluster().health().status();
        System.out.println("Cluster health status: " + status);
    }
}
