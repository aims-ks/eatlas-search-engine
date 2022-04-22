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
package au.gov.aims.eatlas.searchengine.index;

import au.gov.aims.eatlas.searchengine.admin.SearchEngineConfig;
import au.gov.aims.eatlas.searchengine.admin.SearchEngineState;
import au.gov.aims.eatlas.searchengine.client.ESClient;
import au.gov.aims.eatlas.searchengine.client.ESRestHighLevelClient;
import au.gov.aims.eatlas.searchengine.entity.Entity;
import au.gov.aims.eatlas.searchengine.rest.ImageCache;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Conflicts;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import co.elastic.clients.elasticsearch.core.CountRequest;
import co.elastic.clients.elasticsearch.core.CountResponse;
import co.elastic.clients.elasticsearch.core.DeleteByQueryRequest;
import co.elastic.clients.elasticsearch.core.DeleteByQueryResponse;
import co.elastic.clients.elasticsearch.core.GetRequest;
import co.elastic.clients.elasticsearch.core.GetResponse;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import co.elastic.clients.json.JsonData;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.apache.log4j.Logger;
import org.elasticsearch.client.RestClient;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.Set;

// TODO Move search outside. Search should be allowed to be run against any number of indexes at once
public abstract class AbstractIndexer<E extends Entity> {
    private static final Logger LOGGER = Logger.getLogger(AbstractIndexer.class.getName());

    private boolean enabled;
    private String index;
    private Long thumbnailTTL; // TTL, in days
    private Long brokenThumbnailTTL; // TTL, in days

    private final IndexerState state;

    public AbstractIndexer(String index, IndexerState state) {
        this.index = index;
        this.state = state;
    }

    protected abstract Long internalHarvest(ESClient client, Long lastIndexed);
    public abstract Entity load(JSONObject json);
    public abstract JSONObject toJSON();

    public boolean supportsIndexLatest() {
        return false;
    }

    public IndexerState getState() {
        return this.state;
    }

    // If full is true, re-index everything.
    // If not, only index what have changed since last indexation.
    public void harvest(boolean full) throws Exception {
        long lastIndexedStarts = System.currentTimeMillis();

        try(
            // Create the low-level client
            RestClient restClient = RestClient.builder(
                    new HttpHost("localhost", 9200, "http"),
                    new HttpHost("localhost", 9300, "http")
                ).build();

            // Create the transport with a Jackson mapper
            ElasticsearchTransport transport = new RestClientTransport(
                restClient, new JacksonJsonpMapper());

            // And create the API client
            ESClient client = new ESRestHighLevelClient(new ElasticsearchClient(transport))
        ) {
            // TODO - DELETE
            //System.out.println("DELETE INDEX [" + this.getIndex() + "]: " + ((ESRestHighLevelClient)client).deleteIndex(this.getIndex()));

            client.createIndex(this.getIndex());
            Long count = this.internalHarvest(client, full ? null : this.state.getLastIndexed());
            if (count == null) {
                CountRequest countRequest = new CountRequest.Builder().index(this.getIndex()).build();
                CountResponse countResponse = client.count(countRequest);
                count = countResponse.count();
            }
            this.state.setCount(count);
        }

        long lastIndexedEnds = System.currentTimeMillis();
        this.state.setLastIndexed(lastIndexedStarts);
        this.state.setLastIndexRuntime(lastIndexedEnds - lastIndexedStarts);
    }

    protected JSONObject getJsonBase() {
        return new JSONObject()
            .put("type", this.getType())
            .put("enabled", this.isEnabled())
            .put("index", this.index)
            .put("thumbnailTTL", this.thumbnailTTL)
            .put("brokenThumbnailTTL", this.brokenThumbnailTTL);
    }

    public static AbstractIndexer fromJSON(JSONObject json) {
        if (json == null) {
            return null;
        }

        String type = json.optString("type");
        if (type == null) {
            LOGGER.warn(String.format("Invalid indexer JSON Object. Property \"type\" missing.%n%s", json.toString(2)));
            return null;
        }

        String index = json.optString("index", null);
        if (index == null) {
            LOGGER.warn(String.format("Invalid indexer JSON Object. Property \"index\" missing.%n%s", json.toString(2)));
            return null;
        }

        SearchEngineState searchEngineState = SearchEngineState.getInstance();

        // TODO Use annotation to find indexer
        IndexerState state = null;
        AbstractIndexer indexer = null;
        switch(type) {
            case "AtlasMapperIndexer":
                state = searchEngineState.getOrAddIndexerState(index);
                indexer = AtlasMapperIndexer.fromJSON(index, state, json);
                break;

            case "DrupalNodeIndexer":
                state = searchEngineState.getOrAddIndexerState(index);
                indexer = DrupalNodeIndexer.fromJSON(index, state, json);
                break;

            case "DrupalExternalLinkNodeIndexer":
                state = searchEngineState.getOrAddIndexerState(index);
                indexer = DrupalExternalLinkNodeIndexer.fromJSON(index, state, json);
                break;

            case "DrupalMediaIndexer":
                state = searchEngineState.getOrAddIndexerState(index);
                indexer = DrupalMediaIndexer.fromJSON(index, state, json);
                break;

            case "GeoNetworkIndexer":
                state = searchEngineState.getOrAddIndexerState(index);
                indexer = GeoNetworkIndexer.fromJSON(index, state, json);
                break;

            default:
                LOGGER.warn(String.format("Unsupported indexer type: %s%n%s", type, json.toString(2)));
                return null;
        }

        if (indexer == null) {
            return null;
        }

        indexer.enabled = json.optBoolean("enabled", true);

        Long thumbnailTTL = null;
        if (json.has("thumbnailTTL")) {
            thumbnailTTL = json.optLong("thumbnailTTL", -1);
        }
        indexer.thumbnailTTL = thumbnailTTL;

        Long brokenThumbnailTTL = null;
        if (json.has("brokenThumbnailTTL")) {
            brokenThumbnailTTL = json.optLong("brokenThumbnailTTL", -1);
        }
        indexer.brokenThumbnailTTL = brokenThumbnailTTL;

        return indexer;
    }

    public String getIndex() {
        return this.index;
    }

    public void setIndex(String index) {
        this.index = index;
    }

    public String getType() {
        return this.getClass().getSimpleName();
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Long getThumbnailTTL() {
        return this.thumbnailTTL;
    }
    public long getSafeThumbnailTTL() {
        return this.thumbnailTTL == null ? SearchEngineConfig.getInstance().getGlobalThumbnailTTL() : this.thumbnailTTL;
    }

    public void setThumbnailTTL(Long thumbnailTTL) {
        this.thumbnailTTL = thumbnailTTL;
    }

    public Long getBrokenThumbnailTTL() {
        return this.brokenThumbnailTTL;
    }
    public long getSafeBrokenThumbnailTTL() {
        return this.brokenThumbnailTTL == null ? SearchEngineConfig.getInstance().getGlobalBrokenThumbnailTTL() : this.brokenThumbnailTTL;
    }

    public void setBrokenThumbnailTTL(Long brokenThumbnailTTL) {
        this.brokenThumbnailTTL = brokenThumbnailTTL;
    }

    public IndexResponse index(ESClient client, E entity) throws IOException {
        entity.setLastIndexed(System.currentTimeMillis());
        return client.index(this.getIndexRequest(entity));
    }

    // Only called with complete re-index
    public void cleanUp(ESClient client, long lastIndexed, Set<String> usedThumbnails, String entityDisplayName) {
        long deletedIndexedItems = this.deleteOldIndexedItems(client, lastIndexed);
        if (deletedIndexedItems > 0) {
            LOGGER.info(String.format("Deleted %d indexed %s",
                    deletedIndexedItems, entityDisplayName));
        }

        // Refresh ElasticSearch indexes, to be sure the
        // search engine won't return deleted records.
        // NOTE: This need to be done before deleting thumbnails
        //     otherwise the search engine could return old records
        //     which refers to thumbnails that doesn't exist anymore.
        try {
            client.refresh(this.index);
        } catch(Exception ex) {
            LOGGER.error(String.format("Exception occurred while refreshing the search index: %s", this.index), ex);
        }

        long deletedThumbnails = this.deleteOldThumbnails(usedThumbnails);
        if (deletedThumbnails > 0) {
            LOGGER.info(String.format("Deleted %d cached thumbnail for %s",
                    deletedThumbnails, entityDisplayName));
        }
    }

    private long deleteOldIndexedItems(ESClient client, long lastIndexed) {
        DeleteByQueryRequest deleteRequest = this.getDeleteOldItemsRequest(lastIndexed);

        // Delete old records
        Long deleted = null;
        try {
            DeleteByQueryResponse response = client.deleteByQuery(deleteRequest);

            if (response != null) {
                deleted = response.deleted();
            }
        } catch(Exception ex) {
            LOGGER.error(String.format("Exception occurred while deleting old indexed entities in search index: %s", this.index), ex);
        }

        return deleted == null ? 0L : deleted;
    }

    private long deleteOldThumbnails(Set<String> usedThumbnails) {
        File cacheDirectory = ImageCache.getCacheDirectory(this.getIndex());

        // Loop through each thumbnail files and delete the ones that are unused
        if (cacheDirectory != null && cacheDirectory.isDirectory()) {
            return this.deleteOldThumbnailsRecursive(cacheDirectory, usedThumbnails);
        }

        return 0;
    }
    private long deleteOldThumbnailsRecursive(File dir, Set<String> usedThumbnails) {
        long deleted = 0;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleted += deleteOldThumbnailsRecursive(file, usedThumbnails);
                } else if (file.isFile()) {
                    String thumbnailName = file.getName();
                    if (!usedThumbnails.contains(thumbnailName)) {
                        if (file.delete()) {
                            deleted++;
                        } else {
                            LOGGER.error(String.format("Can't delete old thumbnail: %s",
                                    file.toString()));
                        }
                    }
                }
            }
        }

        return deleted;
    }

    public E get(ESClient client, Class<E> entityClass, String id) throws IOException {
        return AbstractIndexer.get(client, entityClass, this.index, id);
    }

    public E safeGet(ESClient client, Class<E> entityClass, String id) {
        try {
            return this.get(client, entityClass, id);
        } catch(Exception ex) {
            // Should not happen
            LOGGER.warn(String.format("Exception occurred while looking for item ID \"%s\" in the search index.",
                    id), ex);
        }

        return null;
    }

    public static <E extends Entity> E get(ESClient client, Class<E> entityClass, String index, String id) throws IOException {
        // TODO: Entity is abstract! Jackson can't instantiate it! Use Generics
        GetResponse<E> response = client.get(AbstractIndexer.getGetRequest(index, id), entityClass);
        if (response == null) {
            return null;
        }

        return response.source();
    }

    // Low level

    public IndexRequest<E> getIndexRequest(E entity) {
        return new IndexRequest.Builder<E>()
                .index(this.getIndex())
                .id(entity.getId())
                .document(entity)
                .build();
    }

    public static GetRequest getGetRequest(String index, String id) {
        return new GetRequest.Builder()
                .index(index)
                .id(id)
                .build();
    }

    public DeleteByQueryRequest getDeleteOldItemsRequest(long olderThanLastIndexed) {
        Query query = new Query.Builder()
                .range(QueryBuilders.range()
                        .field("lastIndexed")
                        .lt(JsonData.of(olderThanLastIndexed))
                        .build()
                )
                .build();

        return new DeleteByQueryRequest.Builder()
                .index(this.index)
                .query(query)
                .refresh(true)
                .conflicts(Conflicts.Proceed)
                .build();
    }

    @Override
    public String toString() {
        return this.toJSON().toString(2);
    }
}
