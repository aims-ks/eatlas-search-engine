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
import au.gov.aims.eatlas.searchengine.entity.Entity;
import au.gov.aims.eatlas.searchengine.entity.EntityUtils;
import org.apache.http.client.utils.URIBuilder;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public abstract class DrupalEntityIndexer<E extends Entity> extends AbstractIndexer<E> {
    private static final int THREAD_POOL_SIZE = 10;

    // Number of Drupal entity to index per page.
    //     Larger number = less request, more RAM
    private static final int INDEX_PAGE_SIZE = 100;

    private String drupalUrl;
    private String drupalVersion;
    private String drupalEntityType; // Entity type. Example: node, media, user, etc
    private String drupalBundleId; // Content type (node type) or media type. Example: article, image, etc
    private String drupalPreviewImageField;

    public DrupalEntityIndexer(
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

    public abstract Thread createIndexerThread(
            SearchClient client,
            Messages messages,
            E drupalEntity,
            JSONObject jsonApiEntity,
            JSONArray jsonIncluded,
            Set<String> usedThumbnails,
            int page, int current, int entityFound);

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
    protected void internalIndex(SearchClient client, Long lastHarvested, Messages messages) {
        boolean fullHarvest = lastHarvested == null;
        long harvestStart = System.currentTimeMillis();

        Set<String> usedThumbnails = null;
        if (fullHarvest) {
            usedThumbnails = Collections.synchronizedSet(new HashSet<String>());

            // There is no easy way to know how many nodes needs indexing.
            // Use the total number of nodes we have in the index, by looking at the number in the state.
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
            URIBuilder uriBuilder = buildDrupalApiUrl(page, messages);
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

                        Thread thread = this.createIndexerThread(
                            client, messages, drupalEntity, jsonApiEntity, jsonIncluded, usedThumbnails, page+1, i+1, entityFound);

                        threadPool.execute(thread);
                    }
                }
            }
            page++;

        // while:
        //     !stop: Stop explicitly set to true, because we found a node that was not modified since last harvest.
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

    public URIBuilder buildDrupalApiUrl(int page, Messages messages) {
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
}
