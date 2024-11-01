package au.gov.aims.eatlas.searchengine;

import au.gov.aims.eatlas.searchengine.logger.AbstractLogger;
import au.gov.aims.eatlas.searchengine.index.IndexerTest;
import org.apache.http.entity.ContentType;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class MockHttpClient extends HttpClient {

    private static MockHttpClient instance;

    private Map<MockHttpRequest, String> requestMap = new HashMap<>();

    public static MockHttpClient getInstance() {
        if (instance == null) {
            instance = new MockHttpClient();
        }
        return instance;
    }

    public void addGetUrl(String url, String resourcePath) {
        Map<MockHttpRequest, String> requestMap = this.getRequestMap();
        requestMap.put(new MockHttpRequest(url), resourcePath);
    }

    public void addPostUrl(String url, String data, String resourcePath) {
        Map<MockHttpRequest, String> requestMap = this.getRequestMap();
        requestMap.put(new MockHttpRequest(url, data), resourcePath);
    }

    public Map<MockHttpRequest, String> getRequestMap() {
        if (this.requestMap == null) {
            this.requestMap = new HashMap<MockHttpRequest, String>();
        }
        return this.requestMap;
    }

    @Override
    public Response getRequest(String url, AbstractLogger logger) throws IOException, InterruptedException {
        MockHttpRequest urlRequest = new MockHttpRequest(url);
        if (!this.requestMap.containsKey(urlRequest)) {
            // Return 404
            return this.notFound(String.format("Unsupported URL. Add the GET URL to the MockHttpClient URL map: %s", url));
        }

        return this.getResourceResponse(this.requestMap.get(urlRequest));
    }

    @Override
    public Response postXmlRequest(String url, String requestBody, AbstractLogger logger) throws IOException, InterruptedException {
        MockHttpRequest urlRequest = new MockHttpRequest(url, requestBody);
        if (!this.requestMap.containsKey(urlRequest)) {
            // Return 404
            return this.notFound(String.format("Unsupported URL. Add the POST URL to the MockHttpClient URL map: %s%n" +
                "with data:%n%s",
                url, requestBody));
        }

        return this.getResourceResponse(this.requestMap.get(urlRequest));
    }

    private Response getResourceResponse(String resourcePath) throws IOException {
        URL resourceUrl = IndexerTest.class.getClassLoader().getResource(resourcePath);
        if (resourceUrl == null) {
            return this.notFound("Resource not found: " + resourcePath);
        }
        try (InputStream inputStream = resourceUrl.openStream()) {
            if (inputStream == null) {
                return this.notFound("Resource not found: " + resourcePath);
            }

            URI uri;
            try {
                uri = resourceUrl.toURI();
            } catch (URISyntaxException e) {
                return this.notFound("Resource URL could not be converted to a URI: " + resourcePath);
            }
            Path path = Paths.get(uri);

            byte[] content = inputStream.readAllBytes();

            String contentTypeStr = Files.probeContentType(path);
            ContentType contentType = contentTypeStr == null ? null :
                    ContentType.parse(contentTypeStr);

            return new Response(
                    200,
                    content,
                    contentType,
                    Files.getLastModifiedTime(path).toMillis()
            );
        }
    }

    private Response notFound(String errorMessage) {
        return new Response(404, errorMessage.getBytes(StandardCharsets.UTF_8), null, null);
    }
}
