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
import co.elastic.clients.elasticsearch.indices.RefreshRequest;
import co.elastic.clients.elasticsearch.indices.RefreshResponse;

import java.io.IOException;
import java.util.Arrays;

public class ESRestHighLevelClient implements ESClient {
    private final ElasticsearchClient client;

    public ESRestHighLevelClient(ElasticsearchClient client) {
        this.client = client;
    }

    @Override
    public IndexResponse index(IndexRequest<Entity> indexRequest) throws IOException {
        return this.client.index(indexRequest);
    }

    @Override
    public GetResponse<Entity> get(GetRequest getRequest) throws IOException {
        return this.client.get(getRequest, Entity.class);
    }

    @Override
    public SearchResponse<Entity> search(SearchRequest searchRequest) throws IOException {
        return this.client.search(searchRequest, Entity.class);
    }

    @Override
    public CountResponse count(CountRequest countRequest) throws IOException {
        return this.client.count(countRequest);
    }

    @Override
    public DeleteByQueryResponse deleteByQuery(DeleteByQueryRequest deleteRequest) throws IOException {
        return this.client.deleteByQuery(deleteRequest);
    }

    @Override
    public RefreshResponse refresh(String ... indices) throws IOException {
        return this.client.indices().refresh(new RefreshRequest.Builder().index(Arrays.asList(indices)).build());
    }

    @Override
    public void close() throws IOException {
        this.client._transport().close();
        this.client.shutdown();
    }
}
