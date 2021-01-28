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
import au.gov.aims.eatlas.searchengine.entity.DrupalNode;
import au.gov.aims.eatlas.searchengine.entity.EntityUtils;
import org.apache.http.HttpHost;
import org.apache.log4j.Logger;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;

public class DrupalNodeIndexer extends AbstractIndexer<DrupalNode> {
    private static final Logger LOGGER = Logger.getLogger(DrupalNodeIndexer.class.getName());

    // Number of Drupal node to index per page.
    //     Larger number = less request, more RAM
    private static final int INDEX_PAGE_SIZE = 100;

    private String drupalUrl;
    private String drupalVersion;
    private String drupalNodeType;
    private String drupalPreviewImageField;

    public DrupalNode load(JSONObject json) {
        return DrupalNode.load(json);
    }

    /**
     * index: eatlas-article
     * drupalUrl: http://localhost:9090
     * drupalVersion: 9.0
     * drupalNodeType: article
     */
    public DrupalNodeIndexer(String index, String drupalUrl, String drupalVersion, String drupalNodeType, String drupalPreviewImageField) {
        super(index);
        this.drupalUrl = drupalUrl;
        this.drupalVersion = drupalVersion;
        this.drupalNodeType = drupalNodeType;
        this.drupalPreviewImageField = drupalPreviewImageField;
    }

    @Override
    public void harvest(Long lastIndexed) throws IOException, InterruptedException {
        try (ESClient client = new ESRestHighLevelClient(new RestHighLevelClient(
                RestClient.builder(
                        new HttpHost("localhost", 9200, "http"),
                        new HttpHost("localhost", 9300, "http"))))) {

            long harvestStart = System.currentTimeMillis();

            int nodeFound, page = 0;
            do {
                // "http://localhost:9090/jsonapi/node/article?include=field_image&sort=-changed&page[limit]=100&page[offset]=0"
                String url = String.format("%s/jsonapi/node/%s?include=%s&sort=-changed&page[limit]=%d&page[offset]=%d",
                    this.drupalUrl, this.drupalNodeType, this.drupalPreviewImageField, INDEX_PAGE_SIZE, page * INDEX_PAGE_SIZE);

                nodeFound = 0;
                String responseStr = EntityUtils.harvestGetURL(url);
                if (responseStr != null && !responseStr.isEmpty()) {
                    JSONObject jsonResponse = new JSONObject(responseStr);

                    JSONArray jsonNodes = jsonResponse.optJSONArray("data");
                    JSONArray jsonIncluded = jsonResponse.optJSONArray("included");

                    nodeFound = jsonNodes == null ? 0 : jsonNodes.length();

                    for (int i=0; i<nodeFound; i++) {
                        DrupalNode drupalNode = new DrupalNode(this.getIndex(), jsonNodes.optJSONObject(i), jsonIncluded, this.drupalPreviewImageField);
                        DrupalNode oldNode = this.get(client, drupalNode.getId());
                        if (oldNode != null) {
                            oldNode.deleteThumbnail();
                        }

                        IndexResponse indexResponse = this.index(client, drupalNode);

                        LOGGER.debug(String.format("Indexing drupal nodes page %d node ID: %s, status: %d",
                                page,
                                drupalNode.getNid(),
                                indexResponse.status().getStatus()));
                    }
                }
                page++;
            } while(nodeFound == INDEX_PAGE_SIZE);

            if (lastIndexed == null) {
                this.cleanUp(client, harvestStart, String.format("Drupal node of type %s", this.drupalNodeType));
            }
        }
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

    public String getDrupalNodeType() {
        return this.drupalNodeType;
    }

    public void setDrupalNodeType(String drupalNodeType) {
        this.drupalNodeType = drupalNodeType;
    }
}
