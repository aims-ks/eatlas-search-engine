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

import au.gov.aims.eatlas.searchengine.client.ESClient;
import au.gov.aims.eatlas.searchengine.entity.Entity;
import au.gov.aims.eatlas.searchengine.rest.ImageCache;
import org.apache.log4j.Logger;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.elasticsearch.index.reindex.DeleteByQueryRequest;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.Map;

// TODO Move search outside. Search should be allowed to be run against any number of indexes at once
public abstract class AbstractIndexer<E extends Entity> {
    private static final Logger LOGGER = Logger.getLogger(AbstractIndexer.class.getName());

    private String index;

    public AbstractIndexer(String index) {
        this.index = index;
    }

    public abstract void harvest(Long lastIndexed) throws Exception;
    public abstract E load(JSONObject json);

    public String getIndex() {
        return this.index;
    }

    public IndexResponse index(ESClient client, E entity) throws IOException {
        entity.setLastIndexed(System.currentTimeMillis());
        return client.index(this.getIndexRequest(entity));
    }

    public void cleanUp(ESClient client, long lastIndexed, String entityDisplayName) throws IOException {
        long deletedIndexedItems = this.deleteOldIndexedItems(client, lastIndexed);
        if (deletedIndexedItems > 0) {
            LOGGER.info(String.format("Deleted %d indexed %s",
                    deletedIndexedItems, entityDisplayName));
        }

        long deletedThumbnails = this.deleteOldThumbnails(lastIndexed);
        if (deletedThumbnails > 0) {
            LOGGER.info(String.format("Deleted %d cached %s thumbnail",
                    deletedThumbnails, entityDisplayName));
        }
    }

    private long deleteOldIndexedItems(ESClient client, long lastIndexed) throws IOException {
        DeleteByQueryRequest deleteRequest = this.getDeleteOldItemsRequest(lastIndexed);
        BulkByScrollResponse response = client.deleteByQuery(deleteRequest);

        if (response != null) {
            return response.getDeleted();
        }

        return 0;
    }

    private long deleteOldThumbnails(long lastIndexed) throws IOException {
        File cacheDirectory = ImageCache.getCacheDirectory(this.getIndex());

        if (cacheDirectory != null && cacheDirectory.isDirectory()) {
            // NOTE: File timestamps are often rounded to seconds, and can be a bit off.
            //     Use a 10s margin for safety.
            return this.deleteOldThumbnailsRecursive(cacheDirectory, lastIndexed - 10000);
        }

        return 0;
    }
    private long deleteOldThumbnailsRecursive(File dir, long lastIndexed) throws IOException {
        long deleted = 0;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleted += deleteOldThumbnailsRecursive(file, lastIndexed);
                } else if (file.isFile()) {
                    if (file.lastModified() < lastIndexed) {
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
}
