package au.gov.aims.eatlas.searchengine.index;

import au.gov.aims.eatlas.searchengine.client.ESClient;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

public class MockSearchClient extends ESClient {

    public MockSearchClient(ElasticsearchContainer elasticsearchContainer) {
        super(
            RestClient.builder(
                HttpHost.create(elasticsearchContainer.getHttpHostAddress())
            ).build());
    }
}
