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
import au.gov.aims.eatlas.searchengine.admin.rest.Messages;
import au.gov.aims.eatlas.searchengine.client.SearchClient;
import au.gov.aims.eatlas.searchengine.client.ESClient;
import au.gov.aims.eatlas.searchengine.client.SearchUtils;
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
import org.elasticsearch.client.RestClient;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.Set;

public abstract class AbstractIndexer<E extends Entity> {
    private boolean enabled;
    private String index;
    private Long thumbnailTTL; // TTL, in days
    private Long brokenThumbnailTTL; // TTL, in days

    // Variable related to running threads
    private IndexerThread indexerThread;
    private Long total;
    private long completed;
    private long indexed;

    public AbstractIndexer(String index) {
        this.index = index;
    }

    protected abstract void internalIndex(SearchClient client, Long lastIndexed, Messages messages);
    public abstract Entity load(JSONObject json, Messages messages);
    public abstract JSONObject toJSON();

    public boolean validate() {
        if (this.index == null || this.index.isEmpty()) {
            return false;
        }
        return true;
    }

    public boolean supportsIndexLatest() {
        return false;
    }

    public boolean isRunning() {
        return this.indexerThread != null && this.indexerThread.isAlive();
    }

    public Double getProgress() {
        if (!this.isRunning()) {
            return 1.0;
        }
        if (this.total == null || this.total == 0) {
            return null;
        }
        return ((double)this.completed) / this.total;
    }

    public Long getTotal() {
        return this.total;
    }

    public void setTotal(Long total) {
        this.total = total;
    }

    // Called when an indexation thread completes, to update the indexation progress.
    protected synchronized void incrementCompleted() {
        this.completed++;
    }
    private synchronized void incrementIndexed() {
        this.indexed++;
    }

    public IndexerState getState() {
        SearchEngineState searchEngineState = SearchEngineState.getInstance();
        return searchEngineState.getOrAddIndexerState(this.index);
    }

    // If full is true, reindex everything.
    // If not, only index what have changed since last indexation.
    public synchronized void index(boolean full, Messages messages) throws IOException {
        if (!this.isRunning()) {
            this.total = null;
            this.completed = 0;
            this.indexed = 0;
            this.indexerThread = new IndexerThread(full, messages);
            this.indexerThread.start();
        }
    }

    public void refreshCount(SearchClient client) throws IOException {
        IndexerState state = this.getState();

        CountRequest countRequest = new CountRequest.Builder().index(this.getIndex()).build();
        CountResponse countResponse = client.count(countRequest);
        state.setCount(countResponse.count());
    }

    protected JSONObject getJsonBase() {
        return new JSONObject()
            .put("type", this.getType())
            .put("enabled", this.isEnabled())
            .put("index", this.index)
            .put("thumbnailTTL", this.thumbnailTTL)
            .put("brokenThumbnailTTL", this.brokenThumbnailTTL);
    }

    public static AbstractIndexer fromJSON(JSONObject json, Messages messages) {
        if (json == null) {
            return null;
        }

        String type = json.optString("type");
        if (type == null) {
            messages.addMessage(Messages.Level.WARNING,
                    String.format("Invalid indexer JSON Object. Property \"type\" missing.%n%s", json.toString(2)));
            return null;
        }

        String index = json.optString("index", null);
        if (index == null) {
            messages.addMessage(Messages.Level.WARNING,
                    String.format("Invalid indexer JSON Object. Property \"index\" missing.%n%s", json.toString(2)));
            return null;
        }

        SearchEngineState searchEngineState = SearchEngineState.getInstance();

        // TODO Use annotation to find indexer
        AbstractIndexer indexer = null;
        switch(type) {
            case "AtlasMapperIndexer":
                indexer = AtlasMapperIndexer.fromJSON(index, json);
                break;

            case "DrupalNodeIndexer":
                indexer = DrupalNodeIndexer.fromJSON(index, json);
                break;

            case "DrupalExternalLinkNodeIndexer":
                indexer = DrupalExternalLinkNodeIndexer.fromJSON(index, json);
                break;

            case "DrupalMediaIndexer":
                indexer = DrupalMediaIndexer.fromJSON(index, json);
                break;

            case "GeoNetworkIndexer":
                indexer = GeoNetworkIndexer.fromJSON(index, json);
                break;

            default:
                messages.addMessage(Messages.Level.WARNING,
                        String.format("Unsupported indexer type: %s%n%s", type, json.toString(2)));
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

    public IndexResponse indexEntity(SearchClient client, E entity) throws IOException {
        return this.indexEntity(client, entity, true);
    }
    public IndexResponse indexEntity(SearchClient client, E entity, boolean incrementIndexedCount) throws IOException {
        entity.setLastIndexed(System.currentTimeMillis());
        IndexResponse indexResponse = client.index(this.getIndexRequest(entity));
        if (incrementIndexedCount) {
            this.incrementIndexed();
        }
        return indexResponse;
    }

    // Only called with complete reindex
    public void cleanUp(SearchClient client, long lastIndexed, Set<String> usedThumbnails, String entityDisplayName, Messages messages) {
        long deletedIndexedItems = this.deleteOldIndexedItems(client, lastIndexed, messages);
        if (deletedIndexedItems > 0) {
            messages.addMessage(Messages.Level.INFO,
                    String.format("Deleted %d indexed %s",
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
            messages.addMessage(Messages.Level.WARNING,
                    String.format("Exception occurred while refreshing the search index: %s", this.index), ex);
        }

        long deletedThumbnails = this.deleteOldThumbnails(usedThumbnails, messages);
        if (deletedThumbnails > 0) {
            messages.addMessage(Messages.Level.INFO,
                    String.format("Deleted %d cached thumbnail for %s",
                    deletedThumbnails, entityDisplayName));
        }
    }

    private long deleteOldIndexedItems(SearchClient client, long lastIndexed, Messages messages) {
        DeleteByQueryRequest deleteRequest = this.getDeleteOldItemsRequest(lastIndexed);

        // Delete old records
        Long deleted = null;
        try {
            DeleteByQueryResponse response = client.deleteByQuery(deleteRequest);

            if (response != null) {
                deleted = response.deleted();
            }
        } catch(Exception ex) {
            messages.addMessage(Messages.Level.ERROR,
                    String.format("Exception occurred while deleting old indexed entities in search index: %s", this.index), ex);
        }

        return deleted == null ? 0L : deleted;
    }

    private long deleteOldThumbnails(Set<String> usedThumbnails, Messages messages) {
        File cacheDirectory = ImageCache.getCacheDirectory(this.getIndex(), messages);

        // Loop through each thumbnail files and delete the ones that are unused
        if (cacheDirectory != null && cacheDirectory.isDirectory()) {
            return this.deleteOldThumbnailsRecursive(cacheDirectory, usedThumbnails, messages);
        }

        return 0;
    }
    private long deleteOldThumbnailsRecursive(File dir, Set<String> usedThumbnails, Messages messages) {
        long deleted = 0;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleted += deleteOldThumbnailsRecursive(file, usedThumbnails, messages);
                } else if (file.isFile()) {
                    String thumbnailName = file.getName();
                    if (!usedThumbnails.contains(thumbnailName)) {
                        if (file.delete()) {
                            deleted++;
                        } else {
                            messages.addMessage(Messages.Level.ERROR,
                                    String.format("Can't delete old thumbnail: %s",
                                    file.toString()));
                        }
                    }
                }
            }
        }

        return deleted;
    }

    public E get(SearchClient client, Class<E> entityClass, String id) throws IOException {
        return AbstractIndexer.get(client, entityClass, this.index, id);
    }

    public E safeGet(SearchClient client, Class<E> entityClass, String id, Messages messages) {
        try {
            return this.get(client, entityClass, id);
        } catch(Exception ex) {
            // Should not happen
            messages.addMessage(Messages.Level.WARNING,
                    String.format("Exception occurred while looking for item ID \"%s\" in the search index.",
                    id), ex);
        }

        return null;
    }

    public static <E extends Entity> E get(SearchClient client, Class<E> entityClass, String index, String id) throws IOException {
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

    public class IndexerThread extends Thread {
        private final boolean fullIndex;
        private Messages messages;

        public IndexerThread(boolean fullIndex, Messages messages) {
            this.fullIndex = fullIndex;
            this.messages = messages;
        }

        @Override
        public void run() {
            // Wait 1 second, to be sure the progress system have time to be initialised.
            // NOTE: The client (browser) won't know the indexation process had occurred
            //     if it starts and completes in less than 1 second, between 2 checks.
            //     Also, the user might think the indexation process is broken if no visual cue is shown
            //     after pressing the button.
            //     Note that the client (browser) checks for indexation progress every second.
            //     This 1sec "initialisation" delay allow the client to register that an indexation process
            //     is in progress. It also shows a short initialisation animation in the progress bar,
            //     giving some visual cue to the user.
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ex) {
                messages.addMessage(Messages.Level.ERROR, "The indexation was interrupted", ex);
            }

            long lastIndexedStarts = System.currentTimeMillis();
            IndexerState state = AbstractIndexer.this.getState();
            String index = AbstractIndexer.this.getIndex();

            try(
                    RestClient restClient = SearchUtils.buildRestClient();

                    // Create the transport with a Jackson mapper
                    ElasticsearchTransport transport = new RestClientTransport(
                            restClient, new JacksonJsonpMapper());

                    // And create the API client
                    SearchClient client = new ESClient(new ElasticsearchClient(transport))
            ) {
                client.createIndex(index);
                AbstractIndexer.this.internalIndex(client, this.fullIndex ? null : state.getLastIndexed(), this.messages);
                AbstractIndexer.this.refreshCount(client);

                long indexed = AbstractIndexer.this.indexed;
                if (!this.fullIndex && indexed == 0) {
                    messages.addMessage(Messages.Level.INFO, String.format("Index %s is up to date.", index));
                } else {
                    messages.addMessage(Messages.Level.INFO, String.format("Index %s %d document indexed.", index, indexed));
                }

                state.setLastIndexed(lastIndexedStarts);
            } catch(Exception ex) {
                this.messages.addMessage(Messages.Level.ERROR,
                        String.format("An error occurred during the indexation of %s", index), ex);
            }

            long lastIndexedEnds = System.currentTimeMillis();
            state.setLastIndexRuntime(lastIndexedEnds - lastIndexedStarts);

            try {
                SearchEngineState.getInstance().save();
            } catch(Exception ex) {
                this.messages.addMessage(Messages.Level.ERROR,
                        String.format("An error occurred while saving the index state for %s", index), ex);
            }
        }
    }
}
