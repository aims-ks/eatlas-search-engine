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
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.parser.Parser;
import org.junit.jupiter.api.BeforeAll;
import org.mockito.ArgumentMatchers;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

@Testcontainers
public abstract class IndexerTestBase {

    private static SearchEngineConfig config;

    public SearchEngineConfig getConfig() {
        return IndexerTestBase.config;
    }

    protected Map<String, String> getMockupUrlMap() {
        return new HashMap<String, String>();
    }

    protected MockedStatic<Jsoup> getMockedJsoup() throws NoSuchMethodException {
        Map<String, String> urlMap = this.getMockupUrlMap();

        // Overwrite the Jsoup class
        MockedStatic<Jsoup> mockedJsoup = Mockito.mockStatic(Jsoup.class);

        // Make sure the method Jsoup.parse() still works.
        mockedJsoup.when(() -> Jsoup.parse(ArgumentMatchers.anyString())).thenAnswer(invocation -> {
            String content = invocation.getArgument(0);
            return Parser.parse(content, "");
        });

        mockedJsoup.when(() -> Jsoup.connect(ArgumentMatchers.anyString())).thenAnswer((Answer<Connection>) invocation -> {
            String url = invocation.getArgument(0);
            if (!urlMap.containsKey(url)) {
                throw new IOException("Unsupported URL. Add the URL to the getMockupUrlMap: " + url);
            }

            // Mockup the JSoup connection
            // NOTE: Setup all the connection methods used by:
            //   EntityUtils.getJsoupConnection()
            Connection mockConnection = Mockito.mock(Connection.class);
            Mockito.when(mockConnection.timeout(Mockito.anyInt())).thenReturn(mockConnection);
            Mockito.when(mockConnection.ignoreHttpErrors(Mockito.anyBoolean())).thenReturn(mockConnection);
            Mockito.when(mockConnection.ignoreContentType(Mockito.anyBoolean())).thenReturn(mockConnection);
            Mockito.when(mockConnection.maxBodySize(Mockito.anyInt())).thenReturn(mockConnection);
            Mockito.when(mockConnection.sslSocketFactory(Mockito.any())).thenReturn(mockConnection);

            // Mockup the JSoup response
            Connection.Response mockResponse = this.createMockResponse(urlMap.get(url));
            Mockito.when(mockConnection.execute()).thenReturn(mockResponse);
            Mockito.when(mockConnection.response()).thenReturn(mockResponse);
            return mockConnection;
        });
        return mockedJsoup;
    }

    private Connection.Response createMockResponse(String resourcePath) throws IOException {
        URL resourceUrl = IndexerTest.class.getClassLoader().getResource(resourcePath);
        if (resourceUrl == null) {
            throw new IOException("Resource not found: " + resourcePath);
        }
        try (InputStream inputStream = resourceUrl.openStream()) {
            if (inputStream == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }
            String content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            Connection.Response mockResponse = Mockito.mock(Connection.Response.class);
            Mockito.when(mockResponse.statusCode()).thenReturn(200);
            Mockito.when(mockResponse.body()).thenReturn(content);

            Mockito.when(mockResponse.header(Mockito.anyString())).thenAnswer(invocation -> {
                String headerName = invocation.getArgument(0);
                if ("Last-Modified".equalsIgnoreCase(headerName)) {
                    try {
                        URI uri = resourceUrl.toURI();
                        Path path = Paths.get(uri);
                        BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);
                        Instant lastModifiedTime = attributes.lastModifiedTime().toInstant();
                        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("E, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH);
                        ZonedDateTime zonedDateTime = lastModifiedTime.atZone(ZoneId.of("UTC"));
                        return zonedDateTime.format(formatter);
                    } catch (IOException ex) {
                        // Can not get the file last modified. Fallback to "null"...
                        return null;
                    }
                }
                // Other headers not supported yet...
                return null;
            });

            return mockResponse;
        }
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
