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
import co.elastic.clients.elasticsearch.indices.DeleteIndexResponse;
import co.elastic.clients.elasticsearch.indices.RefreshResponse;

import java.io.IOException;
import java.util.List;

/**
 * Simple interface to unify RestHighLevelClient with ESSingleNodeTestCase.
 *     They both use different client which are incompatible.
 *     The only way to write testable code is to have a
 *     common client interface so the code can work in test
 *     environment and with a real ElasticSearch engine.
 */
public interface SearchClient extends AutoCloseable {
    boolean indexExists(String indexName) throws IOException;
    CreateIndexResponse createIndex(String indexName) throws IOException;

    List<String> listIndexes() throws IOException;
    HealthStatus getHealthStatus() throws IOException;
    DeleteIndexResponse deleteIndex(String indexName) throws IOException;
    void deleteOrphanIndexes(List<String> activeIndexes) throws IOException;

    <E extends Entity> IndexResponse index(IndexRequest<E> indexRequest) throws IOException;
    <E extends Entity> GetResponse<E> get(GetRequest getRequest, Class<E> entityClass) throws IOException;
    // Search needs to work with any Entity types
    SearchResponse<Entity> search(SearchRequest searchRequest) throws IOException;
    CountResponse count(CountRequest countRequest) throws IOException;
    DeleteByQueryResponse deleteByQuery(DeleteByQueryRequest deleteRequest) throws IOException;

    RefreshResponse refresh(String ... indices) throws IOException;

    void close() throws IOException;
}
