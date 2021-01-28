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

import org.elasticsearch.action.admin.indices.refresh.RefreshResponse;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.core.CountRequest;
import org.elasticsearch.client.core.CountResponse;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.elasticsearch.index.reindex.DeleteByQueryRequest;

import java.io.IOException;

/**
 * Simple interface to unify RestHighLevelClient with ESSingleNodeTestCase.
 *     They both use different client which are incompatible.
 *     The only way to write testable code is to have a
 *     common client interface so the code can work in test
 *     environment and with a real ElasticSearch engine.
 */
public interface ESClient extends AutoCloseable {
    IndexResponse index(IndexRequest indexRequest) throws IOException;
    GetResponse get(GetRequest getRequest) throws IOException;
    SearchResponse search(SearchRequest searchRequest) throws IOException;
    CountResponse count(CountRequest countRequest) throws IOException;
    BulkByScrollResponse deleteByQuery(DeleteByQueryRequest deleteRequest) throws IOException;

    RefreshResponse refresh(String ... indices) throws IOException;

    void close() throws IOException;
}
