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

import au.gov.aims.eatlas.searchengine.HttpClient;
import au.gov.aims.eatlas.searchengine.admin.SearchEngineConfig;
import au.gov.aims.eatlas.searchengine.admin.SearchEngineState;
import au.gov.aims.eatlas.searchengine.logger.AbstractLogger;
import au.gov.aims.eatlas.searchengine.client.SearchClient;
import au.gov.aims.eatlas.searchengine.entity.Entity;
import au.gov.aims.eatlas.searchengine.logger.FileLogger;
import au.gov.aims.eatlas.searchengine.logger.Level;
import au.gov.aims.eatlas.searchengine.logger.Message;
import au.gov.aims.eatlas.searchengine.logger.ConsoleLogger;
import au.gov.aims.eatlas.searchengine.rest.ImageCache;
import co.elastic.clients.elasticsearch._types.Conflicts;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.query_dsl.DateRangeQuery;
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
import org.json.JSONObject;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;

import java.io.File;
import java.io.IOException;
import java.util.Set;

public abstract class AbstractIndexer<E extends Entity> {
    public static final String WHOLE_WORLD_WKT = "BBOX (-180, 180, 90, -90)";
    // WKT used when the indexed document does not have a defined WKT.
    public static final String DEFAULT_WKT = WHOLE_WORLD_WKT;

    private HttpClient httpClient;
    private boolean enabled;
    private String index;
    private Long thumbnailTTL; // TTL, in days
    private Long brokenThumbnailTTL; // TTL, in days

    // Variable related to running threads
    private IndexerThread indexerThread;
    private Long total;
    private long completed;
    private long indexed;

    private AbstractLogger fileLogger;

    public AbstractIndexer(HttpClient httpClient, String index) {
        this.httpClient = httpClient;
        this.index = index;
    }

    public void initFileLogger(String logCacheDirStr) {
        File logCacheDir = logCacheDirStr == null ? null : new File(logCacheDirStr);

        this.fileLogger = null;
        if (logCacheDir != null) {
            logCacheDir.mkdirs();

            if (logCacheDir.exists() && logCacheDir.canWrite()) {
                File logFile = new File(logCacheDir, index + ".log");
                FileLogger fileLogger = new FileLogger(logFile);
                fileLogger.load();
                this.fileLogger = fileLogger;
            }
        }

        // Fallback logger
        if (this.fileLogger == null) {
            this.fileLogger = ConsoleLogger.getInstance();
            if (logCacheDirStr != null) {
                this.fileLogger.addMessage(Level.ERROR,
                    String.format("Can not access the log cache directory: %s", logCacheDirStr));
            }
        }
    }

    protected abstract void internalIndex(SearchClient searchClient, Long lastIndexed, AbstractLogger logger);
    protected abstract E harvestEntity(SearchClient searchClient, String id, AbstractLogger logger);
    public abstract E load(JSONObject json, AbstractLogger logger);
    public abstract JSONObject toJSON();

    public AbstractLogger getFileLogger() {
        return this.fileLogger;
    }

    public HttpClient getHttpClient() {
        return this.httpClient;
    }

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
    public synchronized void index(SearchClient searchClient, boolean full, AbstractLogger logger) throws IOException {
        if (!this.isRunning()) {
            this.total = null;
            this.completed = 0;
            this.indexed = 0;

            this.indexerThread = new IndexerThread(searchClient, full, logger);
            this.indexerThread.start();
        }
    }

    public void refreshCount(SearchClient searchClient) throws IOException {
        IndexerState state = this.getState();

        CountRequest countRequest = new CountRequest.Builder()
                .index(this.getIndex())
                .ignoreUnavailable(true)
                .build();

        CountResponse countResponse = searchClient.count(countRequest);
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

    public static AbstractIndexer<?> fromJSON(HttpClient httpClient, JSONObject json, SearchEngineConfig config, AbstractLogger logger) {
        if (json == null) {
            return null;
        }

        String type = json.optString("type", null);
        if (type == null) {
            logger.addMessage(Level.WARNING,
                    String.format("Invalid indexer JSON Object. Property \"type\" missing.%n%s", json.toString(2)));
            return null;
        }

        String index = json.optString("index", null);
        if (index == null) {
            logger.addMessage(Level.WARNING,
                    String.format("Invalid indexer JSON Object. Property \"index\" missing.%n%s", json.toString(2)));
            return null;
        }

        SearchEngineState searchEngineState = SearchEngineState.getInstance();

        AbstractIndexer<?> indexer = null;
        switch(type) {
            case "AtlasMapperIndexer":
                indexer = AtlasMapperIndexer.fromJSON(httpClient, index, json);
                break;

            case "DrupalNodeIndexer":
                indexer = DrupalNodeIndexer.fromJSON(httpClient, index, json);
                break;

            case "DrupalExternalLinkNodeIndexer":
                indexer = DrupalExternalLinkNodeIndexer.fromJSON(httpClient, index, json);
                break;

            case "DrupalBlockIndexer":
                indexer = DrupalBlockIndexer.fromJSON(httpClient, index, json);
                break;

            case "DrupalMediaIndexer":
                indexer = DrupalMediaIndexer.fromJSON(httpClient, index, json);
                break;

            case "GeoNetworkIndexer":
                indexer = GeoNetworkIndexer.fromJSON(httpClient, index, json);
                break;

            case "GeoNetworkCswIndexer":
                indexer = GeoNetworkCswIndexer.fromJSON(httpClient, index, json);
                break;

            default:
                logger.addMessage(Level.WARNING,
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

        String logCacheDirStr = config.getLogCacheDirectory();
        indexer.initFileLogger(logCacheDirStr);

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

    public IndexResponse reindex(SearchClient searchClient, String id, AbstractLogger logger) throws IOException {
        IndexResponse indexResponse = null;

        searchClient.createIndex(index);
        E entity = this.harvestEntity(searchClient, id, logger);
        if (entity != null) {
            indexResponse = this.indexEntity(searchClient, entity, false, logger);
        }

        return indexResponse;
    }

    public IndexResponse indexEntity(SearchClient searchClient, E entity, AbstractLogger logger) throws IOException {
        return this.indexEntity(searchClient, entity, true, logger);
    }
    public IndexResponse indexEntity(SearchClient searchClient, E entity, boolean incrementIndexedCount, AbstractLogger logger) throws IOException {
        entity.setLastIndexed(System.currentTimeMillis());

        IndexResponse indexResponse = null;

        // Fix geometry
        String originalWkt = entity.getWkt();
        if (originalWkt == null || originalWkt.isEmpty()) {
            originalWkt = null;
            entity.setWktAndAttributes(null, null, null);
        } else {
            try {
                entity.setWktAndAttributes(WktUtils.fixWkt(originalWkt));
            } catch(ParseException ex) {
                // The Reader may throw an exception.
                // We assume the WKT is parsable by JTS since it was generated using the JTS library.
                Message messageObj = logger.addMessage(Level.WARNING, String.format("Document ID: %s. WKT is not parsable.", entity.getId()), ex);
                messageObj.addDetail(String.format("Invalid WKT: %s", originalWkt));
            }
        }

        try {
            indexResponse = searchClient.index(this.getIndexRequest(entity));
        } catch(ElasticsearchException ex) {
            String message = ex.getMessage();
            if (originalWkt != null && message != null && message.contains("failed to parse field [wkt] of type")) {
                // Fallback to the BBox of the WKT geometry
                indexResponse = this.indexEntityBboxFallback(searchClient, entity, originalWkt, logger);
            } else {
                throw ex;
            }
        }

        if (incrementIndexedCount) {
            this.incrementIndexed();
        }
        return indexResponse;
    }

    // Fallback to the BBox of the WKT geometry
    private IndexResponse indexEntityBboxFallback(SearchClient searchClient, E entity, String originalWkt, AbstractLogger logger) throws IOException {
        IndexResponse indexResponse = null;

        String newWkt = null;

        // The following process is computationally expensive,
        // but it should almost never happen.
        try {
            WKTReader reader = new WKTReader();
            Geometry geometry = reader.read(originalWkt);
            Geometry bbox = geometry.getEnvelope();
            newWkt = WktUtils.WKT_WRITER.write(bbox);
        } catch (Exception ex) {
            // The Reader may throw an exception.
            // We assume the WKT is parsable by JTS since it was generated using the JTS library.
            Message messageObj = logger.addMessage(Level.WARNING, String.format("Document ID: %s. WKT is not parsable.", entity.getId()), ex);
            messageObj.addDetail(String.format("Invalid WKT: %s", originalWkt));
        }

        if (newWkt == null || newWkt.isEmpty()) {
            // Fallback to the BBox of whole world
            indexResponse = this.indexEntityWholeWorldFallback(searchClient, entity, entity.getWkt(), logger);
        } else {
            try {
                entity.setWktAndAttributes(newWkt);
                indexResponse = searchClient.index(this.getIndexRequest(entity));

                // The Geometry bbox is valid.
                // Send a warning message regarding the original invalid WKT
                Message messageObj = logger.addMessage(Level.WARNING, String.format("Document ID: %s. Unsupported WKT. Fall back to bounding box of the geometry.", entity.getId()));
                messageObj.addDetail(String.format("Invalid WKT: %s", originalWkt));
                messageObj.addDetail(String.format("Replaced with: %s", newWkt));
            } catch(ParseException ex) {
                indexResponse = this.indexEntityWholeWorldFallback(searchClient, entity, entity.getWkt(), logger);
            } catch(ElasticsearchException ex) {
                String message = ex.getMessage();
                if (message != null && message.contains("failed to parse field [wkt] of type")) {
                    // Fallback to the BBox of whole world
                    indexResponse = this.indexEntityWholeWorldFallback(searchClient, entity, entity.getWkt(), logger);
                } else {
                    throw ex;
                }
            }
        }

        return indexResponse;
    }

    // Fallback to the BBox of whole world
    private IndexResponse indexEntityWholeWorldFallback(SearchClient searchClient, E entity, String originalWkt, AbstractLogger logger) throws IOException {
        IndexResponse indexResponse = null;
        String newWkt = AbstractIndexer.WHOLE_WORLD_WKT;
        try {
            entity.setWktAndAttributes(newWkt);
            indexResponse = searchClient.index(this.getIndexRequest(entity));

            Message messageObj = logger.addMessage(Level.WARNING, String.format("Document ID: %s. Unsupported WKT. Fall back to whole world.", entity.getId()));
            messageObj.addDetail(String.format("Invalid WKT: %s", originalWkt));
            messageObj.addDetail(String.format("Replaced with: %s", newWkt));

        } catch (ParseException ex) {
            entity.setWktAndAttributes(null, null, null);
            indexResponse = searchClient.index(this.getIndexRequest(entity));

            Message message = logger.addMessage(Level.WARNING, "Invalid WKT", ex);
            message.addDetail(String.format("Invalid WKT: %s", originalWkt));
            message.addDetail(String.format("Invalid replacement WKT: %s", newWkt));
        }

        return indexResponse;
    }

    // Only called with complete reindex
    public void cleanUp(SearchClient searchClient, long lastIndexed, Set<String> usedThumbnails, String entityDisplayName, AbstractLogger logger) {
        long deletedIndexedItems = this.deleteOldIndexedItems(searchClient, lastIndexed, logger);
        if (deletedIndexedItems > 0) {
            logger.addMessage(Level.INFO,
                    String.format("Deleted %d indexed %s",
                    deletedIndexedItems, entityDisplayName));
        }

        // Refresh ElasticSearch indexes, to be sure the
        // search engine won't return deleted records.
        // NOTE: This need to be done before deleting thumbnails
        //     otherwise the search engine could return old records
        //     which refers to thumbnails that doesn't exist any more.
        try {
            searchClient.refresh(this.index);
        } catch(Exception ex) {
            logger.addMessage(Level.WARNING,
                    String.format("Exception occurred while refreshing the search index: %s", this.index), ex);
        }

        long deletedThumbnails = this.deleteOldThumbnails(usedThumbnails, logger);
        if (deletedThumbnails > 0) {
            logger.addMessage(Level.INFO,
                    String.format("Deleted %d cached thumbnail for %s",
                    deletedThumbnails, entityDisplayName));
        }
    }

    private long deleteOldIndexedItems(SearchClient searchClient, long lastIndexed, AbstractLogger logger) {
        DeleteByQueryRequest deleteRequest = this.getDeleteOldItemsRequest(lastIndexed);

        // Delete old records
        Long deleted = null;
        try {
            DeleteByQueryResponse response = searchClient.deleteByQuery(deleteRequest);

            if (response != null) {
                deleted = response.deleted();
            }
        } catch(Exception ex) {
            logger.addMessage(Level.ERROR,
                    String.format("Exception occurred while deleting old indexed entities in search index: %s", this.index), ex);
        }

        return deleted == null ? 0L : deleted;
    }

    private long deleteOldThumbnails(Set<String> usedThumbnails, AbstractLogger logger) {
        File cacheDirectory = ImageCache.getCacheDirectory(this.getIndex(), logger);

        // Loop through each thumbnail files and delete the ones that are unused
        if (cacheDirectory != null && cacheDirectory.isDirectory()) {
            return this.deleteOldThumbnailsRecursive(cacheDirectory, usedThumbnails, logger);
        }

        return 0;
    }
    private long deleteOldThumbnailsRecursive(File dir, Set<String> usedThumbnails, AbstractLogger logger) {
        long deleted = 0;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleted += deleteOldThumbnailsRecursive(file, usedThumbnails, logger);
                } else if (file.isFile()) {
                    String thumbnailName = file.getName();
                    if (!usedThumbnails.contains(thumbnailName)) {
                        if (file.delete()) {
                            deleted++;
                        } else {
                            logger.addMessage(Level.ERROR,
                                    String.format("Can't delete old thumbnail: %s",
                                    file.toString()));
                        }
                    }
                }
            }
        }

        return deleted;
    }

    public E get(SearchClient searchClient, Class<E> entityClass, String id) throws IOException {
        return AbstractIndexer.get(searchClient, entityClass, this.index, id);
    }

    public E safeGet(SearchClient searchClient, Class<E> entityClass, String id, AbstractLogger logger) {
        try {
            return this.get(searchClient, entityClass, id);
        } catch(Exception ex) {
            // Should not happen
            logger.addMessage(Level.WARNING,
                    String.format("Exception occurred while looking for item ID \"%s\" in the search index.",
                    id), ex);
        }

        return null;
    }

    public static <E extends Entity> E get(SearchClient searchClient, Class<E> entityClass, String index, String id) throws IOException {
        // Jackson instantiate the Entity using EntityDeserializer.
        // NOTE: The EntityDeserializer uses the SearchEngineConfig to find the proper indexer for the given index ID,
        //   then the EntityDeserializer uses the "load" method from the indexer to instantiate the Entity.
        GetResponse<E> response = searchClient.get(AbstractIndexer.getGetRequest(index, id), entityClass);
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
                        .date(
                                new DateRangeQuery.Builder()
                                        .field("lastIndexed")
                                        .lt("" + olderThanLastIndexed)
                                        .build()
                        ).build()
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
        private final SearchClient searchClient;
        private final boolean fullIndex;
        private final AbstractLogger logger;

        public IndexerThread(SearchClient searchClient, boolean fullIndex, AbstractLogger logger) {
            this.searchClient = searchClient;
            this.fullIndex = fullIndex;
            this.logger = logger;
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
                this.logger.addMessage(Level.ERROR, "The indexation was interrupted", ex);
            }

            long lastIndexedStarts = System.currentTimeMillis();
            IndexerState state = AbstractIndexer.this.getState();
            String index = AbstractIndexer.this.getIndex();

            boolean fullIndexation = this.fullIndex || state.getLastIndexed() == null;

            try {
                this.searchClient.createIndex(index);
                AbstractIndexer.this.internalIndex(this.searchClient, fullIndexation ? null : state.getLastIndexed(), this.logger);
                AbstractIndexer.this.refreshCount(this.searchClient);

                long indexed = AbstractIndexer.this.indexed;
                if (!fullIndexation && indexed == 0) {
                    this.logger.addMessage(Level.INFO, String.format("Index %s is up to date.", index));
                } else {
                    this.logger.addMessage(Level.INFO, String.format("Index %s %d document indexed.", index, indexed));
                }

                state.setLastIndexed(lastIndexedStarts);
            } catch(Exception ex) {
                this.logger.addMessage(Level.ERROR,
                        String.format("An error occurred during the indexation of %s", index), ex);
            }

            long lastIndexedEnds = System.currentTimeMillis();

            // Only save the indexation runtime when it's doing a full reindexation.
            // "Index latest" should always only take a few seconds. It's not worth recording the runtime.
            if (fullIndexation) {
                state.setLastIndexRuntime(lastIndexedEnds - lastIndexedStarts);
            }

            try {
                SearchEngineState.getInstance().save();
            } catch(Exception ex) {
                this.logger.addMessage(Level.ERROR,
                        String.format("An error occurred while saving the index state for %s", index), ex);
            }

            this.logger.save();
        }
    }
}
