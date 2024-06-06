package au.gov.aims.eatlas.searchengine;

public class MockHttpClient extends HttpClient {

    private static MockHttpClient instance;

    public static MockHttpClient getInstance() {
        if (instance == null) {
            instance = new MockHttpClient();
        }
        return instance;
    }

}
