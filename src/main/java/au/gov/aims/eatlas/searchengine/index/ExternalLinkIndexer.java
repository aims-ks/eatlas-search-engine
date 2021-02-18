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
import au.gov.aims.eatlas.searchengine.entity.EntityUtils;
import au.gov.aims.eatlas.searchengine.entity.ExternalLink;
import au.gov.aims.eatlas.searchengine.rest.ImageCache;
import org.apache.log4j.Logger;
import org.elasticsearch.action.index.IndexResponse;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ExternalLinkIndexer extends AbstractIndexer<ExternalLink> {
    private static final Logger LOGGER = Logger.getLogger(ExternalLinkIndexer.class.getName());

    // List of "title, url, thumbnail"
    private List<ExternalLinkEntry> externalLinkEntries;

    public static ExternalLinkIndexer fromJSON(String index, JSONObject json) {
        if (json == null || json.isEmpty()) {
            return null;
        }

        ExternalLinkIndexer indexer = new ExternalLinkIndexer(index);

        JSONArray jsonExternalLinkEntries = json.optJSONArray("externalLinkEntries");
        if (jsonExternalLinkEntries != null && !jsonExternalLinkEntries.isEmpty()) {
            for (int i=0; i<jsonExternalLinkEntries.length(); i++) {
                JSONObject jsonExternalLinkEntry = jsonExternalLinkEntries.optJSONObject(i);
                ExternalLinkEntry externalLinkEntry = ExternalLinkEntry.fromJSON(jsonExternalLinkEntry);
                if (externalLinkEntry != null) {
                    indexer.addExternalLink(externalLinkEntry);
                }
            }
        }

        return indexer;
    }

    public JSONObject toJSON() {
        JSONArray jsonExternalLinkEntries = new JSONArray();
        if (this.externalLinkEntries != null && !externalLinkEntries.isEmpty()) {
            for (ExternalLinkEntry externalLinkEntry : this.externalLinkEntries) {
                jsonExternalLinkEntries.put(externalLinkEntry.toJSON());
            }
        }

        return this.getJsonBase()
            .put("externalLinkEntries", jsonExternalLinkEntries);
    }

    public ExternalLink load(JSONObject json) {
        return ExternalLink.load(json);
    }

    /**
     * index: eatlas_extlink
     * url: http://www.csiro.au/connie2/
     */
    public ExternalLinkIndexer(String index) {
        super(index);
    }

    public void addExternalLink(String url, String thumbnail, String title) {
        this.addExternalLink(new ExternalLinkEntry(url, thumbnail, title));
    }
    public void addExternalLink(ExternalLinkEntry externalLinkEntry) {
        if (this.externalLinkEntries == null) {
            this.externalLinkEntries = new ArrayList<ExternalLinkEntry>();
        }

        this.externalLinkEntries.add(externalLinkEntry);
    }

    public List<ExternalLinkEntry> getExternalLinkEntries() {
        return this.externalLinkEntries;
    }

    /**
     * NOTE: Harvest for external links is a bit special
     *   since there is always only one entity to harvest.
     */
    @Override
    protected void internalHarvest(ESClient client, Long lastHarvested) {
        // There is no reliable way to know if a website was modified since last indexation.
        // Therefore, the lastHarvested parameter is ignored.
        // Always perform a full harvest.

        long harvestStart = System.currentTimeMillis();

        Set<String> usedThumbnails = new HashSet<String>();
        if (this.externalLinkEntries != null && !this.externalLinkEntries.isEmpty()) {
            int total = this.externalLinkEntries.size();
            int current = 0;
            for (ExternalLinkEntry externalLinkEntry : this.externalLinkEntries) {
                current++;
                String url = externalLinkEntry.url;
                if (url != null && !url.isEmpty()) {
                    ExternalLink entity = new ExternalLink(this.getIndex(), url, externalLinkEntry.title);

                    URL thumbnailUrl = null;
                    String thumbnailUrlStr = externalLinkEntry.thumbnail;
                    if (thumbnailUrlStr != null) {
                        try {
                            thumbnailUrl = new URL(thumbnailUrlStr);
                        } catch(Exception ex) {
                            LOGGER.error(String.format("Invalid thumbnail URL found for external link: %s%nThumbnail URL: %s",
                                    entity.getId(), thumbnailUrlStr), ex);
                        }
                    }
                    entity.setThumbnailUrl(thumbnailUrl);

                    // Create the thumbnail if it's missing or outdated
                    ExternalLink oldEntity = this.safeGet(client, entity.getId());
                    if (entity.isThumbnailOutdated(oldEntity, this.getThumbnailTTL())) {
                        try {
                            File cachedThumbnailFile = ImageCache.cache(thumbnailUrl, this.getIndex(), null);
                            if (cachedThumbnailFile != null) {
                                entity.setCachedThumbnailFilename(cachedThumbnailFile.getName());
                            }
                        } catch(Exception ex) {
                            LOGGER.warn(String.format("Exception occurred while creating a thumbnail for external link: %s",
                                    entity.getId()), ex);
                        }
                        entity.setThumbnailLastIndexed(System.currentTimeMillis());
                    } else {
                        entity.useCachedThumbnail(oldEntity);
                    }

                    String thumbnailFilename = entity.getCachedThumbnailFilename();
                    if (thumbnailFilename != null) {
                        usedThumbnails.add(thumbnailFilename);
                    }

                    String responseStr = null;
                    try {
                        responseStr = EntityUtils.harvestURLText(url);
                    } catch (Exception ex) {
                        LOGGER.error(String.format("Exception occurred while harvesting the external URL: %s",
                                url), ex);
                    }

                    if (responseStr != null) {
                        entity.setDocument(responseStr);

                        try {
                            IndexResponse indexResponse = this.index(client, entity);

                            LOGGER.debug(String.format("[%d/%d] Indexing external URL: %s, status: %d",
                                    current, total,
                                    entity.getId(),
                                    indexResponse.status().getStatus()));
                        } catch(Exception ex) {
                            LOGGER.warn(String.format("Exception occurred while indexing an external URL: %s", url), ex);
                        }
                    }
                }
            }
        }

        this.cleanUp(client, harvestStart, usedThumbnails, "external URL");
    }

    public static class ExternalLinkEntry {
        private String url;
        private String thumbnail;
        private String title;

        public ExternalLinkEntry(String url, String thumbnail, String title) {
            this.url = url;
            this.thumbnail = thumbnail;
            this.title = title;
        }

        public String getUrl() {
            return this.url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getThumbnail() {
            return this.thumbnail;
        }

        public void setThumbnail(String thumbnail) {
            this.thumbnail = thumbnail;
        }

        public String getTitle() {
            return this.title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public static ExternalLinkEntry fromJSON(JSONObject json) {
            if (json == null || json.isEmpty()) {
                return null;
            }

            return new ExternalLinkEntry(
                json.optString("url", null),
                json.optString("thumbnail", null),
                json.optString("title", null));
        }

        public JSONObject toJSON() {
            return new JSONObject()
                .put("url", this.url)
                .put("thumbnail", this.thumbnail)
                .put("title", this.title);
        }

        @Override
        public String toString() {
            return this.toJSON().toString(2);
        }
    }
}
