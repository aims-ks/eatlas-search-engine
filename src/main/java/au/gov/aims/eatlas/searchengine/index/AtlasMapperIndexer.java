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
import au.gov.aims.eatlas.searchengine.entity.AtlasMapperLayer;
import au.gov.aims.eatlas.searchengine.entity.EntityUtils;
import org.apache.http.HttpHost;
import org.apache.log4j.Logger;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.json.JSONObject;

import java.io.IOException;

public class AtlasMapperIndexer extends AbstractIndexer<AtlasMapperLayer> {
    private static final Logger LOGGER = Logger.getLogger(AtlasMapperIndexer.class.getName());

    private String atlasMapperClientUrl;
    private String atlasMapperVersion;

    public AtlasMapperLayer load(JSONObject json) {
        return AtlasMapperLayer.load(json);
    }

    /**
     * index: eatlas_layer
     * atlasMapperClientUrl: https://maps.eatlas.org.au
     * atlasMapperVersion: 2.2.0
     */
    public AtlasMapperIndexer(String index, String atlasMapperClientUrl, String atlasMapperVersion) {
        super(index);
        this.atlasMapperClientUrl = atlasMapperClientUrl;
        this.atlasMapperVersion = atlasMapperVersion;
    }

    @Override
    public void harvest() throws IOException, InterruptedException {
        // Get the map of datasources
        // "https://maps.eatlas.org.au/config/main.json"
        String mainUrlStr = String.format("%s/config/main.json", this.atlasMapperClientUrl);
        String mainResponseStr = EntityUtils.harvestGetURL(mainUrlStr);
        if (mainResponseStr == null || mainResponseStr.isEmpty()) {
            return;
        }
        JSONObject jsonMainConfig = new JSONObject(mainResponseStr);

        // Get the list of layers
        // "https://maps.eatlas.org.au/config/layers.json"
        String layersUrlStr = String.format("%s/config/layers.json", this.atlasMapperClientUrl);
        String layersResponseStr = EntityUtils.harvestGetURL(layersUrlStr);
        if (layersResponseStr == null || layersResponseStr.isEmpty()) {
            return;
        }
        JSONObject jsonLayersConfig = new JSONObject(layersResponseStr);

        try (ESClient client = new ESRestHighLevelClient(new RestHighLevelClient(
                RestClient.builder(
                        new HttpHost("localhost", 9200, "http"),
                        new HttpHost("localhost", 9300, "http"))))) {

            for (String atlasMapperLayerId : jsonLayersConfig.keySet()) {
                JSONObject jsonLayer = jsonLayersConfig.optJSONObject(atlasMapperLayerId);

                AtlasMapperLayer layerEntity = new AtlasMapperLayer(
                        this.getIndex(), this.atlasMapperClientUrl, atlasMapperLayerId, jsonLayer, jsonMainConfig);
                IndexResponse indexResponse = this.index(client, layerEntity);

                LOGGER.debug(String.format("Indexing AtlasMapper layer ID: %s, status: %d",
                        atlasMapperLayerId,
                        indexResponse.status().getStatus()));
            }
        }
    }

    public String getAtlasMapperClientUrl() {
        return this.atlasMapperClientUrl;
    }

    public void setAtlasMapperClientUrl(String atlasMapperClientUrl) {
        this.atlasMapperClientUrl = atlasMapperClientUrl;
    }

    public String getAtlasMapperVersion() {
        return this.atlasMapperVersion;
    }

    public void setAtlasMapperVersion(String atlasMapperVersion) {
        this.atlasMapperVersion = atlasMapperVersion;
    }
}
