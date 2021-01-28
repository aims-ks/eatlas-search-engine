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
import au.gov.aims.eatlas.searchengine.client.ESRestHighLevelClient;
import au.gov.aims.eatlas.searchengine.entity.EntityUtils;
import au.gov.aims.eatlas.searchengine.entity.ExternalLink;
import org.apache.http.HttpHost;
import org.apache.log4j.Logger;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ExternalLinkIndexer extends AbstractIndexer<ExternalLink> {
    private static final Logger LOGGER = Logger.getLogger(ExternalLinkIndexer.class.getName());

    // List of "title, url, thumbnail"
    private List<ExternalLinkEntry> externalLinkEntries;

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
    public void harvest(Long lastHarvested) throws IOException, InterruptedException {
        // There is no reliable way to know if a website was modified since last indexation.
        // Therefore, the lastHarvested parameter is ignored.
        // Always perform a full harvest.

        try (ESClient client = new ESRestHighLevelClient(new RestHighLevelClient(
                RestClient.builder(
                        new HttpHost("localhost", 9200, "http"),
                        new HttpHost("localhost", 9300, "http"))))) {

            long harvestStart = System.currentTimeMillis();

            if (this.externalLinkEntries != null && !this.externalLinkEntries.isEmpty()) {
                for (ExternalLinkEntry externalLinkEntry : this.externalLinkEntries) {
                    String url = externalLinkEntry.url;
                    if (url != null && !url.isEmpty()) {
                        ExternalLink entity = new ExternalLink(this.getIndex(), url, externalLinkEntry.thumbnail, externalLinkEntry.title);
                        ExternalLink oldEntity = this.get(client, entity.getId());
                        if (oldEntity != null) {
                            oldEntity.deleteThumbnail();
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

                            IndexResponse indexResponse = this.index(client, entity);

                            LOGGER.debug(String.format("Indexing external URL: %s, status: %d",
                                    entity.getId(),
                                    indexResponse.status().getStatus()));
                        }
                    }
                }
            }

            this.cleanUp(client, harvestStart, "external URL");
        }
    }

    public static class ExternalLinkEntry {
        private String title;
        private String url;
        private String thumbnail;

        public ExternalLinkEntry(String url, String thumbnail, String title) {
            this.title = title;
            this.url = url;
            this.thumbnail = thumbnail;
        }

        public String getTitle() {
            return this.title;
        }

        public void setTitle(String title) {
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
    }
}
