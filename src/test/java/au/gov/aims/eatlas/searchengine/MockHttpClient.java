package au.gov.aims.eatlas.searchengine;

import au.gov.aims.eatlas.searchengine.admin.rest.Messages;
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

    private Map<String, String> urlMap = new HashMap<>();

    public static MockHttpClient getInstance() {
        if (instance == null) {
            instance = new MockHttpClient();
        }
        return instance;
    }

    public void setUrlMap(Map<String, String> urlMap) {
        this.urlMap = urlMap;
    }

    @Override
    public Response getRequest(String url, Messages messages) throws IOException, InterruptedException {
        if (!this.urlMap.containsKey(url)) {
            // Return 404
            return this.notFound("Unsupported URL. Add the URL to the getMockupUrlMap: " + url);
        }

        String resourcePath = this.urlMap.get(url);

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
