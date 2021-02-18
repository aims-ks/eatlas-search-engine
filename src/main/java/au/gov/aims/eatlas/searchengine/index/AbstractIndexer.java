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
import au.gov.aims.eatlas.searchengine.client.ESClient;
import au.gov.aims.eatlas.searchengine.client.ESRestHighLevelClient;
import au.gov.aims.eatlas.searchengine.entity.Entity;
import au.gov.aims.eatlas.searchengine.rest.ImageCache;
import org.apache.http.HttpHost;
import org.apache.log4j.Logger;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.elasticsearch.index.reindex.DeleteByQueryRequest;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Set;

// TODO Move search outside. Search should be allowed to be run against any number of indexes at once
public abstract class AbstractIndexer<E extends Entity> {
    private static final Logger LOGGER = Logger.getLogger(AbstractIndexer.class.getName());

    private boolean enabled;
    private String index;
    private Long lastIndexed;
    private Long lastIndexElapse;
    private Long thumbnailTTL; // TTL, in days

    public AbstractIndexer(String index) {
        this.index = index;
    }

    protected abstract void internalHarvest(ESClient client, Long lastIndexed);
    public abstract E load(JSONObject json);
    public abstract JSONObject toJSON();

    // If full is true, re-index everything.
    // If not, only index what have changed since last indexation.
    public void harvest(boolean full) throws Exception {
        long lastIndexedStarts = System.currentTimeMillis();

        try (ESClient client = new ESRestHighLevelClient(new RestHighLevelClient(
                RestClient.builder(
                        new HttpHost("localhost", 9200, "http"),
                        new HttpHost("localhost", 9300, "http"))))) {

            this.internalHarvest(client, full ? null : this.lastIndexed);
        }

        long lastIndexedEnds = System.currentTimeMillis();
        this.lastIndexed = lastIndexedStarts;
        this.lastIndexElapse = lastIndexedEnds - lastIndexedStarts;
    }

    protected JSONObject getJsonBase() {
        return new JSONObject()
            .put("class", this.getClass().getSimpleName())
            .put("enabled", this.isEnabled())
            .put("index", this.index)
            .put("lastIndexed", this.lastIndexed)
            .put("lastIndexElapse", this.lastIndexElapse)
            .put("thumbnailTTL", this.thumbnailTTL);
    }

    public static AbstractIndexer fromJSON(JSONObject json) {
        if (json == null) {
            return null;
        }

        String className = json.optString("class");
        if (className == null) {
            LOGGER.warn(String.format("Invalid indexer JSON Object. Property \"class\" missing.%n%s", json.toString(2)));
            return null;
        }

        String index = json.optString("index", null);
        if (index == null) {
            LOGGER.warn(String.format("Invalid indexer JSON Object. Property \"index\" missing.%n%s", json.toString(2)));
            return null;
        }

        AbstractIndexer indexer = null;
        switch(className) {
            case "AtlasMapperIndexer":
                indexer = AtlasMapperIndexer.fromJSON(index, json);
                break;

            case "DrupalNodeIndexer":
                indexer = DrupalNodeIndexer.fromJSON(index, json);
                break;

            case "ExternalLinkIndexer":
                indexer = ExternalLinkIndexer.fromJSON(index, json);
                break;

            case "GeoNetworkIndexer":
                indexer = GeoNetworkIndexer.fromJSON(index, json);
                break;

            default:
                LOGGER.warn(String.format("Unsupported indexer class: %s%n%s", className, json.toString(2)));
                return null;
        }

        if (indexer == null) {
            return null;
        }

        indexer.enabled = json.optBoolean("enabled", true);

        Long lastIndexed = null;
        if (json.has("lastIndexed")) {
            lastIndexed = json.optLong("lastIndexed", -1);
        }
        indexer.lastIndexed = lastIndexed;

        Long lastIndexElapse = null;
        if (json.has("lastIndexElapse")) {
            lastIndexElapse = json.optLong("lastIndexElapse", -1);
        }
        indexer.lastIndexElapse = lastIndexElapse;

        Long thumbnailTTL = null;
        if (json.has("thumbnailTTL")) {
            thumbnailTTL = json.optLong("thumbnailTTL", -1);
        }
        indexer.thumbnailTTL = thumbnailTTL;

        return indexer;
    }

    public String getIndex() {
        return this.index;
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public void setLastIndexed(Long lastIndexed) {
        this.lastIndexed = lastIndexed;
    }

    public Long getLastIndexed() {
        return this.lastIndexed;
    }

    public Long getLastIndexElapse() {
        return this.lastIndexElapse;
    }

    public long getThumbnailTTL() {
        return this.thumbnailTTL == null ? SearchEngineConfig.getInstance().getGlobalThumbnailTTL() : this.thumbnailTTL;
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
            LOGGER.info(String.format("Deleted %d cached %s thumbnail",
                    deletedThumbnails, entityDisplayName));
        }
    }

    private long deleteOldIndexedItems(ESClient client, long lastIndexed) {
        DeleteByQueryRequest deleteRequest = this.getDeleteOldItemsRequest(lastIndexed);

        // Delete old records
        long deleted = 0;
        try {
            BulkByScrollResponse response = client.deleteByQuery(deleteRequest);

            if (response != null) {
                deleted = response.getDeleted();
            }
        } catch(Exception ex) {
            LOGGER.error(String.format("Exception occurred while deleting old indexed entities in search index: %s", this.index), ex);
        }

        return deleted;
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

    public E get(ESClient client, String id) throws IOException {
        JSONObject jsonEntity = AbstractIndexer.get(client, this.index, id);
        if (jsonEntity != null) {
            return this.load(jsonEntity);
        }
        return null;
    }

    public E safeGet(ESClient client, String id) {
        try {
            return this.get(client, id);
        } catch(Exception ex) {
            // Should not happen
            LOGGER.warn(String.format("Exception occurred while looking for item ID \"%s\" in the search index.",
                    id), ex);
        }

        return null;
    }

    public static JSONObject get(ESClient client, String index, String id) throws IOException {
        GetResponse response = client.get(AbstractIndexer.getGetRequest(index, id));
        if (response == null) {
            return null;
        }

        Map<String, Object> sourceMap = response.getSource();
        if (sourceMap == null || sourceMap.isEmpty()) {
            return null;
        }

        return new JSONObject(response.getSource());
    }

    // Low level

    public IndexRequest getIndexRequest(E entity) {
        return new IndexRequest(this.getIndex())
            .id(entity.getId())
            .source(IndexUtils.JSONObjectToMap(entity.toJSON()));
    }

    public static GetRequest getGetRequest(String index, String id) {
        return new GetRequest(index)
            .id(id);
    }

    public DeleteByQueryRequest getDeleteOldItemsRequest(long olderThanLastIndexed) {
        // https://www.elastic.co/guide/en/elasticsearch/client/java-rest/master/java-rest-high-document-delete-by-query.html
        DeleteByQueryRequest deleteRequest = new DeleteByQueryRequest(this.index)
            .setQuery(QueryBuilders.rangeQuery("lastIndexed").lt(olderThanLastIndexed))
            .setRefresh(true);

        // Set "proceed" on version conflict
        deleteRequest.setConflicts("proceed");

        return deleteRequest;
    }

    @Override
    public String toString() {
        return this.toJSON().toString(2);
    }
}
