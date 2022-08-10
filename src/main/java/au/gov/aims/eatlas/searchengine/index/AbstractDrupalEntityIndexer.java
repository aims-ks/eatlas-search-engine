/*
 *  Copyright (C) 2022 Australian Institute of Marine Science
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

import au.gov.aims.eatlas.searchengine.admin.rest.Messages;
import au.gov.aims.eatlas.searchengine.client.SearchClient;
import au.gov.aims.eatlas.searchengine.entity.DrupalNode;
import au.gov.aims.eatlas.searchengine.entity.Entity;
import au.gov.aims.eatlas.searchengine.entity.EntityUtils;
import au.gov.aims.eatlas.searchengine.rest.ImageCache;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import org.apache.http.client.utils.URIBuilder;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public abstract class AbstractDrupalEntityIndexer<E extends Entity> extends AbstractIndexer<E> {
    private static final Logger LOGGER = Logger.getLogger(AbstractDrupalEntityIndexer.class.getName());
    private static final int THREAD_POOL_SIZE = 10;

    // Number of Drupal entity to index per page.
    //     Larger number = less request, more RAM
    private static final int INDEX_PAGE_SIZE = 100;

    private String drupalUrl;
    private String drupalVersion;
    private String drupalEntityType; // Entity type. Example: node, media, user, etc
    private String drupalBundleId; // Content type (node type) or media type. Example: article, image, etc
    private String drupalPreviewImageField;

    public AbstractDrupalEntityIndexer(
            String index,
            String drupalUrl,
            String drupalVersion,
            String drupalEntityType,
            String drupalBundleId,
            String drupalPreviewImageField) {

        super(index);
        this.drupalUrl = drupalUrl;
        this.drupalVersion = drupalVersion;
        this.drupalEntityType = drupalEntityType;
        this.drupalBundleId = drupalBundleId;
        this.drupalPreviewImageField = drupalPreviewImageField;
    }

    public abstract E createDrupalEntity(JSONObject jsonApiEntity, Messages messages);
    public abstract E getIndexedDrupalEntity(SearchClient client, String id, Messages messages);

    protected JSONObject getJsonBase() {
        return super.getJsonBase()
            .put("drupalUrl", this.drupalUrl)
            .put("drupalVersion", this.drupalVersion)
            .put("drupalPreviewImageField", this.drupalPreviewImageField);
    }

    @Override
    public boolean validate() {
        if (!super.validate()) {
            return false;
        }
        if (this.drupalUrl == null || this.drupalUrl.isEmpty()) {
            return false;
        }
        if (this.drupalEntityType == null || this.drupalEntityType.isEmpty()) {
            return false;
        }
        if (this.drupalBundleId == null || this.drupalBundleId.isEmpty()) {
            return false;
        }
        return true;
    }

    @Override
    protected E harvestEntity(SearchClient client, String entityUUID, Messages messages) {
        URIBuilder uriBuilder = this.buildDrupalApiEntityUrl(entityUUID, messages);
        if (uriBuilder == null) {
            return null;
        }

        String url;
        try {
            url = uriBuilder.build().toURL().toString();
        } catch(Exception ex) {
            // Should not happen
            messages.addMessage(Messages.Level.ERROR,
                    String.format("Invalid Drupal URL. Exception occurred while building a URL starting with: %s", this.getDrupalApiUrlBase()), ex);
            return null;
        }

        String responseStr = null;
        try {
            responseStr = EntityUtils.harvestGetURL(url, messages);
        } catch(Exception ex) {
            messages.addMessage(Messages.Level.WARNING, String.format("Exception occurred while requesting the Drupal %s, type: %s, UUID: %s",
                    this.getDrupalEntityType(), this.getDrupalBundleId(), entityUUID), ex);
        }

        if (responseStr != null && !responseStr.isEmpty()) {
            JSONObject jsonResponse = new JSONObject(responseStr);

            JSONArray jsonErrors = jsonResponse.optJSONArray("errors");
            if (jsonErrors != null && !jsonErrors.isEmpty()) {
                this.handleDrupalApiErrors(jsonErrors, messages);
            } else {
                JSONObject jsonApiEntity = jsonResponse.optJSONObject("data");
                JSONArray jsonIncluded = jsonResponse.optJSONArray("included");

                E drupalEntity = this.createDrupalEntity(jsonApiEntity, messages);

                if (this.parseJsonDrupalEntity(client, jsonApiEntity, jsonIncluded, drupalEntity, messages)) {
                    return drupalEntity;
                }
            }
        }

        return null;
    }

    // Overwrite in sub-classes when more work needs to be done.
    // Return false to prevent the entity from been indexed.
    protected boolean parseJsonDrupalEntity(SearchClient client, JSONObject jsonApiEntity, JSONArray jsonIncluded, E drupalEntity, Messages messages) {
        this.updateThumbnail(client, jsonApiEntity, jsonIncluded, drupalEntity, messages);
        return true;
    }

    @Override
    protected void internalIndex(SearchClient client, Long lastHarvested, Messages messages) {
        boolean fullHarvest = lastHarvested == null;
        long harvestStart = System.currentTimeMillis();

        Set<String> usedThumbnails = null;
        if (fullHarvest) {
            usedThumbnails = Collections.synchronizedSet(new HashSet<String>());

            // There is no easy way to know how many entities needs indexing.
            // Use the total number of entities we have in the index, by looking at the last indexed count in the state.
            IndexerState state = this.getState();
            Long total = null;
            if (state != null) {
                total = state.getCount();
            }
            this.setTotal(total);
        }

        ThreadPoolExecutor threadPool = (ThreadPoolExecutor) Executors.newFixedThreadPool(THREAD_POOL_SIZE);

        long totalFound = 0;
        int entityFound, page = 0;
        boolean stop = false;
        boolean crashed = false;
        do {
            URIBuilder uriBuilder = this.buildDrupalApiPageUrl(page, messages);
            if (uriBuilder == null) {
                return;
            }

            String url;
            try {
                url = uriBuilder.build().toURL().toString();
            } catch(Exception ex) {
                // Should not happen
                messages.addMessage(Messages.Level.ERROR,
                        String.format("Invalid Drupal URL. Exception occurred while building a URL starting with: %s", this.getDrupalApiUrlBase()), ex);
                return;
            }

            entityFound = 0;
            String responseStr = null;
            try {
                responseStr = EntityUtils.harvestGetURL(url, messages);
            } catch(Exception ex) {
                if (!crashed) {
                    messages.addMessage(Messages.Level.WARNING, String.format("Exception occurred while requesting a page of Drupal %s, type: %s",
                            this.getDrupalEntityType(), this.getDrupalBundleId()), ex);
                }
                crashed = true;
            }
            if (responseStr != null && !responseStr.isEmpty()) {
                JSONObject jsonResponse = new JSONObject(responseStr);

                JSONArray jsonErrors = jsonResponse.optJSONArray("errors");
                if (jsonErrors != null && !jsonErrors.isEmpty()) {
                    this.handleDrupalApiErrors(jsonErrors, messages);
                    crashed = true;
                } else {
                    JSONArray jsonEntities = jsonResponse.optJSONArray("data");
                    JSONArray jsonIncluded = jsonResponse.optJSONArray("included");

                    entityFound = jsonEntities == null ? 0 : jsonEntities.length();
                    totalFound += entityFound;
                    if (fullHarvest) {
                        if (this.getTotal() != null && this.getTotal() < totalFound) {
                            this.setTotal(totalFound);
                        }
                    } else {
                        this.setTotal(totalFound);
                    }

                    for (int i=0; i<entityFound; i++) {
                        JSONObject jsonApiEntity = jsonEntities.optJSONObject(i);

                        E drupalEntity = this.createDrupalEntity(jsonApiEntity, messages);

                        // NOTE: Drupal last modified date (aka changed date) are rounded to second,
                        //     and can be a bit off. Use a 10s margin for safety.
                        if (!fullHarvest && lastHarvested != null && drupalEntity.getLastModified() < lastHarvested + 10000) {
                            stop = true;
                            break;
                        }

                        Thread thread = new DrupalEntityIndexerThread(
                            client, messages, drupalEntity, jsonApiEntity, jsonIncluded, usedThumbnails, page+1, i+1, entityFound);

                        threadPool.execute(thread);
                    }
                }
            }
            page++;

        // while:
        //     !stop: Stop explicitly set to true, because we found an entity that was not modified since last harvest.
        //     !crashed: An exception occurred or an error message was sent by Drupal.
        //     entityFound == INDEX_PAGE_SIZE: A page of results contains less entity than requested.
        } while(!stop && !crashed && entityFound == INDEX_PAGE_SIZE);

        threadPool.shutdown();
        try {
            threadPool.awaitTermination(1, TimeUnit.HOURS);
        } catch(InterruptedException ex) {
            messages.addMessage(Messages.Level.ERROR, String.format("The indexation for %s type %s was interrupted",
                    this.getDrupalEntityType(), this.getDrupalBundleId()), ex);
        }

        // Only cleanup when we are doing a full harvest
        if (!crashed && fullHarvest) {
            this.cleanUp(client, harvestStart, usedThumbnails, String.format("Drupal %s type %s",
                    this.getDrupalEntityType(), this.getDrupalBundleId()), messages);
        }
    }

    public String getDrupalApiUrlBase() {
        return String.format("%s/jsonapi/%s/%s", this.getDrupalUrl(), this.getDrupalEntityType(), this.getDrupalBundleId());
    }

    public URIBuilder buildDrupalApiEntityUrl(String entityUUID, Messages messages) {
        String urlBase = this.getDrupalApiUrlBase();
        URIBuilder uriBuilder;
        try {
            uriBuilder = new URIBuilder(String.format("%s/%s", urlBase, entityUUID));
        } catch(URISyntaxException ex) {
            messages.addMessage(Messages.Level.ERROR,
                    String.format("Invalid Drupal URL. Exception occurred while building the URL: %s", urlBase), ex);
            return null;
        }
        uriBuilder.setParameter("include", this.drupalPreviewImageField);
        uriBuilder.setParameter("filter[status]", "1");

        return uriBuilder;
    }

    public URIBuilder buildDrupalApiPageUrl(int page, Messages messages) {
        // Ordered by lastModified (changed).
        // If the parameter lastHarvested is set, harvest entities (nodes)
        //     until we found an entity that was last modified before the lastHarvested parameter.
        // Example:
        //     "http://localhost:9090/jsonapi/node/article?include=field_image&sort=-changed&page[limit]=100&page[offset]=0&filter[status]=1&filter[field_prepress]=0"
        //     "http://localhost:9090/jsonapi/media/image?include=thumbnail&sort=-changed&page[limit]=100&page[offset]=0&filter[status]=1&filter[field_private_media_page]=0"
        // Filter out unpublished entities (only useful when logged in): filter[status]=1
        String urlBase = this.getDrupalApiUrlBase();
        URIBuilder uriBuilder;
        try {
            uriBuilder = new URIBuilder(urlBase);
        } catch(URISyntaxException ex) {
            messages.addMessage(Messages.Level.ERROR,
                    String.format("Invalid Drupal URL. Exception occurred while building the URL: %s", urlBase), ex);
            return null;
        }
        uriBuilder.setParameter("include", this.drupalPreviewImageField);
        uriBuilder.setParameter("sort", "-changed");
        uriBuilder.setParameter("page[limit]", String.format("%d", INDEX_PAGE_SIZE));
        uriBuilder.setParameter("page[offset]", String.format("%d", page * INDEX_PAGE_SIZE));
        uriBuilder.setParameter("filter[status]", "1");

        return uriBuilder;
    }

    public void handleDrupalApiErrors(JSONArray jsonErrors, Messages messages) {
        // Handle errors returned by Drupal.
        for (int i=0; i<jsonErrors.length(); i++) {
            JSONObject jsonError = jsonErrors.optJSONObject(i);
            String errorTitle = jsonError.optString("title", "Untitled error");
            String errorDetail = jsonError.optString("detail", "No details");

            messages.addMessage(Messages.Level.ERROR,
                    String.format("An error occurred during the indexation of %s type %s - %s: %s",
                            this.getDrupalEntityType(), this.getDrupalBundleId(),
                            errorTitle, errorDetail));
        }
    }

    public static String getPreviewImageUUID(JSONObject jsonApiEntity, String previewImageField) {
        if (previewImageField == null || previewImageField.isEmpty()) {
            return null;
        }
        JSONObject jsonRelationships = jsonApiEntity == null ? null : jsonApiEntity.optJSONObject("relationships");
        JSONObject jsonRelFieldImage = jsonRelationships == null ? null : jsonRelationships.optJSONObject(previewImageField);
        JSONObject jsonRelFieldImageData = jsonRelFieldImage == null ? null : jsonRelFieldImage.optJSONObject("data");
        return jsonRelFieldImageData == null ? null : jsonRelFieldImageData.optString("id", null);
    }

    public static String findPreviewImageRelativePath(String imageUUID, JSONArray included) {
        if (imageUUID == null || included == null) {
            return null;
        }

        for (int i=0; i < included.length(); i++) {
            JSONObject jsonInclude = included.optJSONObject(i);
            String includeId = jsonInclude == null ? null : jsonInclude.optString("id", null);
            if (imageUUID.equals(includeId)) {
                JSONObject jsonIncludeAttributes = jsonInclude == null ? null : jsonInclude.optJSONObject("attributes");
                JSONObject jsonIncludeAttributesUri = jsonIncludeAttributes == null ? null : jsonIncludeAttributes.optJSONObject("uri");
                return jsonIncludeAttributesUri == null ? null : jsonIncludeAttributesUri.optString("url", null);
            }
        }

        return null;
    }

    public String getDrupalUrl() {
        return this.drupalUrl;
    }

    public void setDrupalUrl(String drupalUrl) {
        this.drupalUrl = drupalUrl;
    }

    public String getDrupalVersion() {
        return this.drupalVersion;
    }

    public void setDrupalVersion(String drupalVersion) {
        this.drupalVersion = drupalVersion;
    }

    public String getDrupalEntityType() {
        return this.drupalEntityType;
    }

    public void setDrupalEntityType(String drupalEntityType) {
        this.drupalEntityType = drupalEntityType;
    }

    public String getDrupalBundleId() {
        return this.drupalBundleId;
    }

    public void setDrupalBundleId(String drupalBundleId) {
        this.drupalBundleId = drupalBundleId;
    }

    public String getDrupalPreviewImageField() {
        return this.drupalPreviewImageField;
    }

    public void setDrupalPreviewImageField(String drupalPreviewImageField) {
        this.drupalPreviewImageField = drupalPreviewImageField;
    }

    public void updateThumbnail(SearchClient client, JSONObject jsonApiEntity, JSONArray jsonIncluded, E drupalEntity, Messages messages) {
        URL baseUrl = DrupalNode.getDrupalBaseUrl(jsonApiEntity, messages);
        if (baseUrl != null && this.getDrupalPreviewImageField() != null) {
            String previewImageUUID = AbstractDrupalEntityIndexer.getPreviewImageUUID(jsonApiEntity, this.getDrupalPreviewImageField());
            if (previewImageUUID != null) {
                String previewImageRelativePath = AbstractDrupalEntityIndexer.findPreviewImageRelativePath(previewImageUUID, jsonIncluded);
                if (previewImageRelativePath != null) {
                    URL thumbnailUrl = null;
                    try {
                        thumbnailUrl = new URL(baseUrl, previewImageRelativePath);
                    } catch(Exception ex) {
                        messages.addMessage(Messages.Level.WARNING,
                                String.format("Exception occurred while creating a thumbnail URL for Drupal %s type %s, id: %s",
                                        this.getDrupalEntityType(),
                                        this.getDrupalBundleId(),
                                        drupalEntity.getId()), ex);
                    }
                    drupalEntity.setThumbnailUrl(thumbnailUrl);

                    // Create the thumbnail if it's missing or outdated
                    E oldEntity = this.getIndexedDrupalEntity(client, drupalEntity.getId(), messages);
                    if (drupalEntity.isThumbnailOutdated(oldEntity, this.getSafeThumbnailTTL(), this.getSafeBrokenThumbnailTTL(), messages)) {
                        try {
                            File cachedThumbnailFile = ImageCache.cache(thumbnailUrl, this.getIndex(), drupalEntity.getId(), messages);
                            if (cachedThumbnailFile != null) {
                                drupalEntity.setCachedThumbnailFilename(cachedThumbnailFile.getName());
                            }
                        } catch(Exception ex) {
                            messages.addMessage(Messages.Level.WARNING,
                                    String.format("Exception occurred while creating a thumbnail for Drupal %s type %s, id: %s",
                                            AbstractDrupalEntityIndexer.this.getDrupalEntityType(),
                                            AbstractDrupalEntityIndexer.this.getDrupalBundleId(),
                                            drupalEntity.getId()), ex);
                        }
                        drupalEntity.setThumbnailLastIndexed(System.currentTimeMillis());
                    } else {
                        drupalEntity.useCachedThumbnail(oldEntity);
                    }
                }
            }
        }
    }

    // Thread class, which does typical entity indexing (node, media, etc).
    // It can be extended in the indexer and used as a starting point.
    public class DrupalEntityIndexerThread extends Thread {
        private final SearchClient client;
        private final Messages messages;
        private final E drupalEntity;
        private final JSONObject jsonApiEntity;
        private final JSONArray jsonIncluded;
        private final Set<String> usedThumbnails;
        private final int page;
        private final int current;
        private final int pageTotal;

        public DrupalEntityIndexerThread(
                SearchClient client,
                Messages messages,
                E drupalEntity,
                JSONObject jsonApiEntity,
                JSONArray jsonIncluded,
                Set<String> usedThumbnails,
                int page, int current, int pageTotal
        ) {
            this.client = client;
            this.messages = messages;
            this.drupalEntity = drupalEntity;
            this.jsonApiEntity = jsonApiEntity;
            this.jsonIncluded = jsonIncluded;
            this.usedThumbnails = usedThumbnails;
            this.page = page;
            this.current = current;
            this.pageTotal = pageTotal;
        }

        public SearchClient getClient() {
            return this.client;
        }

        public Messages getMessages() {
            return this.messages;
        }

        public E getDrupalEntity() {
            return this.drupalEntity;
        }

        @Override
        public void run() {
            if (AbstractDrupalEntityIndexer.this.parseJsonDrupalEntity(this.client, this.jsonApiEntity, this.jsonIncluded, this.drupalEntity, this.messages)) {
                if (this.usedThumbnails != null) {
                    String thumbnailFilename = this.drupalEntity.getCachedThumbnailFilename();
                    if (thumbnailFilename != null) {
                        this.usedThumbnails.add(thumbnailFilename);
                    }
                }

                try {
                    IndexResponse indexResponse = AbstractDrupalEntityIndexer.this.indexEntity(this.client, this.drupalEntity, this.messages);

                    // NOTE: We don't know how many entities (or pages of entities) there is.
                    //     We index until we reach the bottom of the barrel...
                    LOGGER.debug(String.format("[Page %d: %d/%d] Indexing Drupal %s type %s, id: %s, index response status: %s",
                            this.page, this.current, this.pageTotal,
                            AbstractDrupalEntityIndexer.this.getDrupalEntityType(),
                            AbstractDrupalEntityIndexer.this.getDrupalBundleId(),
                            this.drupalEntity.getId(),
                            indexResponse.result()));
                } catch(Exception ex) {
                    this.messages.addMessage(Messages.Level.WARNING,
                            String.format("Exception occurred while indexing a Drupal %s type %s, id: %s",
                                    AbstractDrupalEntityIndexer.this.getDrupalEntityType(),
                                    AbstractDrupalEntityIndexer.this.getDrupalBundleId(),
                                    this.drupalEntity.getId()), ex);
                }
            }

            AbstractDrupalEntityIndexer.this.incrementCompleted();
        }
    }
}
