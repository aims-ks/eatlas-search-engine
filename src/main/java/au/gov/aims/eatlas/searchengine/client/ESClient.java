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
import co.elastic.clients.elasticsearch._types.analysis.Analyzer;
import co.elastic.clients.elasticsearch._types.analysis.CustomAnalyzer;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch._types.mapping.TextProperty;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import co.elastic.clients.elasticsearch.cat.IndicesResponse;
import co.elastic.clients.elasticsearch.cat.indices.IndicesRecord;
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
import co.elastic.clients.elasticsearch.indices.DeleteIndexRequest;
import co.elastic.clients.elasticsearch.indices.DeleteIndexResponse;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.elasticsearch.indices.IndexSettings;
import co.elastic.clients.elasticsearch.indices.IndexSettingsAnalysis;
import co.elastic.clients.elasticsearch.indices.RefreshRequest;
import co.elastic.clients.elasticsearch.indices.RefreshResponse;
import co.elastic.clients.transport.endpoints.BooleanResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ESClient implements SearchClient {
    private final ElasticsearchClient client;

    public ESClient(ElasticsearchClient client) {
        this.client = client;
    }

    @Override
    public boolean indexExists(String indexName) throws IOException {
        ExistsRequest existsRequest = new ExistsRequest.Builder().index(indexName).build();
        BooleanResponse existsResponse = this.client.indices().exists(existsRequest);
        return existsResponse.value();
    }

    @Override
    public List<String> listIndexes() throws IOException {
        IndicesResponse indicesResponse = this.client.cat().indices();

        List<String> indexes = new ArrayList<>();
        if (indicesResponse != null) {
            for (IndicesRecord indicesRecord : indicesResponse.valueBody()) {
                String index = indicesRecord.index();
                if (index != null && !index.isEmpty()) {
                    indexes.add(index);
                }
            }
        }

        return indexes;
    }

    @Override
    public void deleteOrphanIndexes(List<String> activeIndexes) throws IOException {
        boolean noActiveIndexes = activeIndexes == null || activeIndexes.isEmpty();
        List<String> indexes = this.listIndexes();
        if (indexes != null && !indexes.isEmpty()) {
            for (String index : indexes) {
                if (noActiveIndexes || !activeIndexes.contains(index)) {
                    this.deleteIndex(index);
                }
            }
        }
    }

    public DeleteIndexResponse deleteIndex(String indexName) throws IOException {
        if (this.indexExists(indexName)) {
            DeleteIndexRequest deleteIndexRequest = new DeleteIndexRequest.Builder().index(indexName).build();
            return this.client.indices().delete(deleteIndexRequest);
        }
        return null;
    }

    @Override
    public CreateIndexResponse createIndex(String indexName) throws IOException {
        if (!this.indexExists(indexName)) {
            // A setting that works, but is probably overkill
            /*
            new IndexSettings.Builder()
                    .analysis(new IndexSettingsAnalysis.Builder()
                            .analyzer("english_analyser", new Analyzer.Builder()
                                    .custom(new CustomAnalyzer.Builder()
                                            .tokenizer("standard")
                                            // IMPORTANT: The order of the filters matters.
                                            .filter("asciifolding", "lowercase", "possessive_english_stemmer", "english_stemmer")
                                            .build())
                                    .build())
                            // Available stemmer algo (aka language)
                            // https://www.elastic.co/guide/en/elasticsearch/reference/current/analysis-stemmer-tokenfilter.html
                            .filter("english_stemmer", new TokenFilter.Builder()
                                    .definition(new TokenFilterDefinition.Builder()
                                            .stemmer(new StemmerTokenFilter.Builder()
                                                    .language("english")
                                                    .build())
                                            .build())
                                    .build())
                            .filter("possessive_english_stemmer", new TokenFilter.Builder()
                                    .definition(new TokenFilterDefinition.Builder()
                                            .stemmer(new StemmerTokenFilter.Builder()
                                                    .language("possessive_english")
                                                    .build())
                                            .build())
                                    .build())
                            .build())
                    .build();
            */

            // Create an index with English Stemming:
            //     https://www.elastic.co/guide/en/elasticsearch/reference/current/stemming.html

            // JSON configuration, which I used as a guide to implement this stemmer:
            //     https://stackoverflow.com/questions/27204606/stemming-and-highlighting-for-phrase-search
            CreateIndexRequest createIndexRequest = new CreateIndexRequest.Builder()
                    .index(indexName)
                    .mappings(new TypeMapping.Builder()
                            .properties("document", new Property.Builder()
                                    .text(new TextProperty.Builder()
                                            .analyzer("english_analyser")
                                            .store(true)
                                            .build())
                                    .build())
                            .properties("title", new Property.Builder()
                                    .text(new TextProperty.Builder()
                                            .analyzer("english_analyser")
                                            .store(true)
                                            .build())
                                    .build())
                            .build())
                    .settings(new IndexSettings.Builder()
                            .analysis(new IndexSettingsAnalysis.Builder()
                                    .analyzer("english_analyser", new Analyzer.Builder()
                                            .custom(new CustomAnalyzer.Builder()
                                                    .tokenizer("classic")
                                                    // IMPORTANT: The order of the filters matters.
                                                    .filter("asciifolding", "lowercase", "classic", "kstem")
                                                    .build())
                                            .build())
                                    .build())
                            .build())
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
        this.client.shutdown();
    }
}
