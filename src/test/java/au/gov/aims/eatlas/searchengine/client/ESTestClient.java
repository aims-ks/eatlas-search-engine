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

import org.elasticsearch.action.admin.indices.refresh.RefreshAction;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.admin.indices.refresh.RefreshResponse;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.core.CountRequest;
import org.elasticsearch.client.core.CountResponse;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.elasticsearch.index.reindex.DeleteByQueryRequest;

import java.io.IOException;

public class ESTestClient implements ESClient {
    private Client client;

    public ESTestClient(Client client) {
        this.client = client;
    }

    @Override
    public IndexResponse index(IndexRequest indexRequest) throws IOException {
        return this.client.index(indexRequest).actionGet();
    }

    @Override
    public GetResponse get(GetRequest getRequest) throws IOException {
        return this.client.get(getRequest).actionGet();
    }

    @Override
    public SearchResponse search(SearchRequest searchRequest) throws IOException {
        return this.client.search(searchRequest).actionGet();
    }

    @Override
    public CountResponse count(CountRequest countRequest) throws IOException {
        // Elasticsearch test framework have no support for this.
        throw new UnsupportedOperationException("Elasticsearch test framework doesn't support count");
    }

    @Override
    public BulkByScrollResponse deleteByQuery(DeleteByQueryRequest deleteRequest) throws IOException {
        throw new UnsupportedOperationException("Elasticsearch test framework doesn't support deleteByQuery");
    }

    @Override
    public RefreshResponse refresh(String ... indices) throws IOException {
        return this.client.execute(RefreshAction.INSTANCE, new RefreshRequest(indices)).actionGet();
    }

    @Override
    public void close() throws IOException {
        this.client.close();
    }
}
