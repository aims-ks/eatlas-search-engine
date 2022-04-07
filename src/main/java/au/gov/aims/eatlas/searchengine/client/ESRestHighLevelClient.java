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
import au.gov.aims.eatlas.searchengine.entity.EntityUtils;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.analysis.Analyzer;
import co.elastic.clients.elasticsearch._types.analysis.StandardAnalyzer;
import co.elastic.clients.elasticsearch._types.analysis.TokenFilter;
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
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.CreateIndexResponse;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.elasticsearch.indices.IndexSettingBlocks;
import co.elastic.clients.elasticsearch.indices.IndexSettings;
import co.elastic.clients.elasticsearch.indices.IndexSettingsAnalysis;
import co.elastic.clients.elasticsearch.indices.RefreshRequest;
import co.elastic.clients.elasticsearch.indices.RefreshResponse;
import co.elastic.clients.transport.endpoints.BooleanResponse;

import java.io.IOException;
import java.util.Arrays;

public class ESRestHighLevelClient implements ESClient {
    private final ElasticsearchClient client;

    public ESRestHighLevelClient(ElasticsearchClient client) {
        this.client = client;
    }

    @Override
    public boolean indexExists(String indexName) throws IOException {
        ExistsRequest existsRequest = new ExistsRequest.Builder().index(indexName).build();
        BooleanResponse existsResponse = this.client.indices().exists(existsRequest);
        return existsResponse.value();
    }

    @Override
    public CreateIndexResponse createIndex(String indexName) throws IOException {
        // TODO Setup English Stemming
        // https://www.elastic.co/guide/en/elasticsearch/reference/current/stemming.html

        if (!this.indexExists(indexName)) {
            CreateIndexRequest createIndexRequest = new CreateIndexRequest.Builder()
                    .index(indexName)
    //                .settings(IndexSettings) // TODO There should be pre-configured settings for English Stemming
    /*
                    .settings(new IndexSettings.Builder()
                            .analysis(new IndexSettingsAnalysis.Builder()
                                    .analyzer("english_analyser", new Analyzer.Builder()
                                            .standard(new StandardAnalyzer.Builder().build())
                                            .build())
                                    .filter("english_stemmer", new TokenFilter.Builder()
                                            .
                                            .build())
                                    .build())
                            .build())
    */
                    .build();

            return this.client.indices().create(createIndexRequest);
        }

        return null;
    }

    @Override
    public <E extends Entity> IndexResponse index(IndexRequest<E> indexRequest) throws IOException {
        return this.client.index(indexRequest);
    }

    @Override
    public <E extends Entity> GetResponse<E> get(GetRequest getRequest, Class<E> entityClass) throws IOException {
        return this.client.get(getRequest, entityClass);
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
