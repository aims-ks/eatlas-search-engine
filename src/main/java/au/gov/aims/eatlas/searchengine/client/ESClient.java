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

import au.gov.aims.eatlas.searchengine.admin.SearchEngineConfig;
import au.gov.aims.eatlas.searchengine.entity.Entity;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.HealthStatus;
import co.elastic.clients.elasticsearch._types.analysis.Analyzer;
import co.elastic.clients.elasticsearch._types.analysis.CustomAnalyzer;
import co.elastic.clients.elasticsearch._types.mapping.DateProperty;
import co.elastic.clients.elasticsearch._types.mapping.DoubleNumberProperty;
import co.elastic.clients.elasticsearch._types.mapping.GeoShapeProperty;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch._types.mapping.TextProperty;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import co.elastic.clients.elasticsearch.cat.IndicesResponse;
import co.elastic.clients.elasticsearch.cat.indices.IndicesRecord;
import co.elastic.clients.elasticsearch.cluster.HealthResponse;
import co.elastic.clients.elasticsearch.core.CountRequest;
import co.elastic.clients.elasticsearch.core.CountResponse;
import co.elastic.clients.elasticsearch.core.DeleteByQueryRequest;
import co.elastic.clients.elasticsearch.core.DeleteByQueryResponse;
import co.elastic.clients.elasticsearch.core.DeleteRequest;
import co.elastic.clients.elasticsearch.core.DeleteResponse;
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
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.endpoints.BooleanResponse;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.elasticsearch.client.RestClient;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;

public class ESClient implements SearchClient {
    private static SearchClient instance;

    private final RestClient restClient;
    private final ElasticsearchTransport transport;
    private final ElasticsearchClient elasticsearchClient;

    public ESClient() throws MalformedURLException {
        this(SearchUtils.buildRestClient());
    }

    public ESClient(RestClient restClient) {
        this.restClient = restClient;
        JacksonJsonpMapper jacksonJsonpMapper = new JacksonJsonpMapper();
        // Add support for Java 8 time types (needed for LocalDate publishedOn field)
        ObjectMapper mapper = jacksonJsonpMapper.objectMapper();
        mapper.registerModule(new JavaTimeModule());

        this.transport = new RestClientTransport(this.restClient, jacksonJsonpMapper);
        this.elasticsearchClient = new ElasticsearchClient(transport);
    }

    public static SearchClient getInstance() throws MalformedURLException {
        if (instance == null) {
            instance = new ESClient();
        }
        return instance;
    }

    @Override
    public boolean indexExists(String indexName) throws IOException {
        ExistsRequest existsRequest = new ExistsRequest.Builder().index(indexName).build();
        BooleanResponse existsResponse = this.elasticsearchClient.indices().exists(existsRequest);
        return existsResponse.value();
    }

    @Override
    public List<String> listIndexes() throws IOException {
        IndicesResponse indicesResponse = this.elasticsearchClient.cat().indices();

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
    public HealthStatus getHealthStatus() throws IOException {
        HealthResponse healthResponse = this.elasticsearchClient.cluster().health();
        return healthResponse.status();
    }

    public boolean isHealthy() {
        try {
            HealthStatus status = this.getHealthStatus();
            return !"red".equals(status.jsonValue());
        } catch(Exception ex) {
            // The health status request crashed.
            // Probably because Elastic Search is not running.
            return false;
        }
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

    @Override
    public DeleteIndexResponse deleteIndex(String indexName) throws IOException {
        if (this.indexExists(indexName)) {
            DeleteIndexRequest deleteIndexRequest = new DeleteIndexRequest.Builder().index(indexName).build();
            return this.elasticsearchClient.indices().delete(deleteIndexRequest);
        }
        return null;
    }

    @Override
    public CreateIndexResponse createIndex(String indexName) throws IOException {
        if (!this.indexExists(indexName)) {
            SearchEngineConfig config = SearchEngineConfig.getInstance();
            String nbShards = String.valueOf(config.getElasticSearchNumberOfShards());
            String nbReplicas = String.valueOf(config.getElasticSearchNumberOfReplicas());

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
                            .properties("publishedOn", new Property.Builder()
                                    .date(new DateProperty.Builder()
                                            .format("yyyy-MM-dd")
                                            .store(true)
                                            .build())
                                    .build())

                            .properties("wkt", new Property.Builder()
                                    .geoShape(new GeoShapeProperty.Builder()
                                            .ignoreZValue(true)
                                            .coerce(true) // Automatically close polygons
                                            //.ignoreMalformed(true) // Enable if indexation struggle with malformed WKT
                                            //.orientation(GeoOrientation.Left) // Default: right (counter-clockwise)
                                            .build())
                                    .build())

                            .properties("wktArea", new Property.Builder()
                                    .double_(new DoubleNumberProperty.Builder()
                                            .build())
                                    .build())

                            // Using Shape instead of GeoShape because of the following bug:
                            //   https://github.com/elastic/elasticsearch/issues/89059
                            /*
                            .properties("wkt", new Property.Builder()
                                    .shape(new ShapeProperty.Builder()
                                            .ignoreZValue(true)
                                            .coerce(true) // Automatically close polygons
                                            //.ignoreMalformed(true) // Enable if indexation struggle with malformed WKT
                                            .orientation(GeoOrientation.Left) // Default: right (counter-clockwise)
                                            .build())
                                    .build())
                            */
                            .build())
                    .settings(new IndexSettings.Builder()
                            // Set number of shards to 1 and the number of replica to 0,
                            // to be able to run on a single-node ElasticSearch instance.
                            .numberOfShards(nbShards)
                            .numberOfReplicas(nbReplicas)
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

            return this.elasticsearchClient.indices().create(createIndexRequest);
        }

        return null;
    }

    @Override
    public <E extends Entity> IndexResponse index(IndexRequest<E> indexRequest) throws IOException {
        return this.elasticsearchClient.index(indexRequest);
    }

    @Override
    public <E extends Entity> GetResponse<E> get(GetRequest getRequest, Class<E> entityClass) throws IOException {
        return this.elasticsearchClient.get(getRequest, entityClass);
    }

    @Override
    public SearchResponse<Entity> search(SearchRequest searchRequest) throws IOException {
        return this.elasticsearchClient.search(searchRequest, Entity.class);
    }

    @Override
    public CountResponse count(CountRequest countRequest) throws IOException {
        return this.elasticsearchClient.count(countRequest);
    }

    @Override
    public DeleteResponse delete(DeleteRequest deleteRequest) throws IOException {
        return this.elasticsearchClient.delete(deleteRequest);
    }

    @Override
    public DeleteByQueryResponse deleteByQuery(DeleteByQueryRequest deleteRequest) throws IOException {
        return this.elasticsearchClient.deleteByQuery(deleteRequest);
    }

    @Override
    public RefreshResponse refresh(String ... indices) throws IOException {
        return this.elasticsearchClient.indices().refresh(new RefreshRequest.Builder().index(List.of(indices)).build());
    }

    @Override
    public void close() throws IOException {
        this.elasticsearchClient.shutdown();
        this.transport.close();
        this.restClient.close();
    }
}
