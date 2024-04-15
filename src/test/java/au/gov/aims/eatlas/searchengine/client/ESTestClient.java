/*
 *  Copyright (C) 2020 Australian Institute of Marine Science
 *
 *  Contact: Gael Lafond <g.lafond@aims.gov.au>
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package au.gov.aims.eatlas.searchengine.client;

import au.gov.aims.eatlas.searchengine.entity.Entity;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.HealthStatus;
import co.elastic.clients.elasticsearch.core.CountRequest;
import co.elastic.clients.elasticsearch.core.CountResponse;
import co.elastic.clients.elasticsearch.core.DeleteByQueryRequest;
import co.elastic.clients.elasticsearch.core.DeleteByQueryResponse;
import co.elastic.clients.elasticsearch.core.GetRequest;
import co.elastic.clients.elasticsearch.core.GetResponse;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.indices.CreateIndexResponse;
import co.elastic.clients.elasticsearch.indices.RefreshResponse;

import java.io.IOException;
import java.util.List;

/**
 * NOTE: Elastic Search used to provide a testing environment.
 * I could not find that environment in the latest version of Elastic Search.
 * Tests are currently disabled until I can figure out how to do this.
 */
public class ESTestClient implements SearchClient {
    // https://github.com/elastic/elasticsearch-java/blob/main/java-client/src/test/java/co/elastic/clients/elasticsearch/end_to_end/RequestTest.java
    private ElasticsearchClient client;

    public ESTestClient(ElasticsearchClient client) {
        this.client = client;
    }

    @Override
    public List<String> listIndexes() throws IOException {
        return null; // TODO
    }

    @Override
    public HealthStatus getHealthStatus() throws IOException {
        return null; // TODO
    }

    @Override
    public void deleteOrphanIndexes(List<String> activeIndexes) throws IOException {
        // TODO
    }

    @Override
    public boolean indexExists(String indexName) throws IOException {
        return false; // TODO
    }

    @Override
    public CreateIndexResponse createIndex(String indexName) throws IOException {
        return null;// TODO
    }

    @Override
    public <E extends Entity> IndexResponse index(IndexRequest<E> indexRequest) throws IOException {
        return null;//this.client.index(indexRequest).actionGet();
    }

    @Override
    public <E extends Entity> GetResponse<E> get(GetRequest getRequest, Class<E> entityClass) throws IOException {
        return null;//this.client.get(getRequest).actionGet();
    }

    @Override
    public SearchResponse<Entity> search(SearchRequest searchRequest) throws IOException {
        return null;//this.client.search(searchRequest).actionGet();
    }

    @Override
    public CountResponse count(CountRequest countRequest) throws IOException {
        // Elasticsearch test framework have no support for this.
        throw new UnsupportedOperationException("Elasticsearch test framework doesn't support count");
    }

    @Override
    public DeleteByQueryResponse deleteByQuery(DeleteByQueryRequest deleteRequest) throws IOException {
        throw new UnsupportedOperationException("Elasticsearch test framework doesn't support deleteByQuery");
    }

    @Override
    public RefreshResponse refresh(String ... indices) throws IOException {
        return null;//this.client.execute(RefreshAction.INSTANCE, new RefreshRequest(indices)).actionGet();
    }

    @Override
    public void close() throws IOException {
        this.client.shutdown();
    }
}
