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
import au.gov.aims.eatlas.searchengine.entity.AtlasMapperLayer;
import au.gov.aims.eatlas.searchengine.entity.EntityUtils;
import au.gov.aims.eatlas.searchengine.rest.ImageCache;
import org.apache.log4j.Logger;
import org.elasticsearch.action.index.IndexResponse;
import org.glassfish.jersey.uri.UriComponent;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Connection;

import javax.ws.rs.core.MultivaluedMap;
import java.io.File;
import java.net.URI;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class AtlasMapperIndexer extends AbstractIndexer<AtlasMapperLayer> {
    private static final Logger LOGGER = Logger.getLogger(AtlasMapperIndexer.class.getName());
    private static final int THUMBNAIL_MAX_WIDTH = 300;
    private static final int THUMBNAIL_MAX_HEIGHT = 200;
    private static final float THUMBNAIL_MARGIN = 0.1f; // Margin, in percentage

    private String atlasMapperClientUrl;
    private String atlasMapperVersion;

    public static AtlasMapperIndexer fromJSON(String index, JSONObject json) {
        if (json == null || json.isEmpty()) {
            return null;
        }

        return new AtlasMapperIndexer(
            index,
            json.optString("atlasMapperClientUrl", null),
            json.optString("atlasMapperVersion", null));
    }

    public JSONObject toJSON() {
        return this.getJsonBase()
            .put("atlasMapperClientUrl", this.atlasMapperClientUrl)
            .put("atlasMapperVersion", this.atlasMapperVersion);
    }

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
    protected void internalHarvest(ESClient client, Long lastHarvested) {
        // There is no way to get last modified layers from AtlasMapper.
        // Therefore, we only perform an harvest if the JSON files are more recent than lastHarvested.

        // Get the main configuration file, containing the map of data sources
        // "https://maps.eatlas.org.au/config/main.json"
        String mainUrlStr = String.format("%s/config/main.json", this.atlasMapperClientUrl);

        Connection.Response mainResponse = null;
        try {
            mainResponse = EntityUtils.jsoupExecuteWithRetry(mainUrlStr);
        } catch(Exception ex) {
            LOGGER.error(String.format("Exception occurred while downloading the AtlasMapper main configuration file: %s",
                    mainUrlStr), ex);
            return;
        }
        Long mainLastModified = IndexUtils.parseHttpLastModifiedHeader(mainResponse);

        // Get the list of layers
        // "https://maps.eatlas.org.au/config/layers.json"
        String layersUrlStr = String.format("%s/config/layers.json", this.atlasMapperClientUrl);

        Connection.Response layersResponse = null;
        try {
            layersResponse = EntityUtils.jsoupExecuteWithRetry(layersUrlStr);
        } catch(Exception ex) {
            LOGGER.error(String.format("Exception occurred while downloading the AtlasMapper layers file: %s",
                    layersUrlStr), ex);
            return;
        }
        Long layersLastModified = IndexUtils.parseHttpLastModifiedHeader(layersResponse);

        if (lastHarvested != null) {
            // If a file have no last modified in the header,
            // we can't tell if the index is outdated. Lets assume it is.
            if (mainLastModified != null && layersLastModified != null) {
                boolean indexOutDated = false;
                if (mainLastModified > lastHarvested - 10000) {
                    indexOutDated = true;
                }
                if (layersLastModified > lastHarvested - 10000) {
                    indexOutDated = true;
                }

                // The index is more recent than both files.
                // We can skip the harvest.
                if (!indexOutDated) {
                    LOGGER.info(String.format("Index %s is up to date.", this.getIndex()));
                    return;
                }
            }
        }

        String mainResponseStr = mainResponse.body();
        if (mainResponseStr == null || mainResponseStr.isEmpty()) {
            return;
        }
        JSONObject jsonMainConfig = new JSONObject(mainResponseStr);

        String layersResponseStr = layersResponse.body();
        if (layersResponseStr == null || layersResponseStr.isEmpty()) {
            return;
        }
        JSONObject jsonLayersConfig = new JSONObject(layersResponseStr);

        long harvestStart = System.currentTimeMillis();

        // Get URL of the base layer
        String baseLayerId = AtlasMapperLayer.getWMSBaseLayer(jsonMainConfig, jsonLayersConfig);

        Set<String> usedThumbnails = new HashSet<String>();
        int total = jsonLayersConfig.length();
        int current = 0;
        for (String atlasMapperLayerId : jsonLayersConfig.keySet()) {
            current++;
            JSONObject jsonLayer = jsonLayersConfig.optJSONObject(atlasMapperLayerId);

            AtlasMapperLayer layerEntity = new AtlasMapperLayer(
                    this.getIndex(), this.atlasMapperClientUrl, atlasMapperLayerId, jsonLayer, jsonMainConfig);

            // Create the thumbnail if it's missing or outdated
            AtlasMapperLayer oldLayer = this.safeGet(client, atlasMapperLayerId);
            if (layerEntity.isThumbnailOutdated(oldLayer, this.getThumbnailTTL(), this.getBrokenThumbnailTTL())) {
                try {
                    File cachedThumbnailFile = this.createLayerThumbnail(atlasMapperLayerId, baseLayerId, jsonLayersConfig, jsonMainConfig);
                    if (cachedThumbnailFile != null) {
                        layerEntity.setCachedThumbnailFilename(cachedThumbnailFile.getName());
                    }
                } catch(Exception ex) {
                    LOGGER.warn(String.format("Exception occurred while creating a thumbnail image for AtlasMapper layer: %s",
                            atlasMapperLayerId), ex);
                }
                layerEntity.setThumbnailLastIndexed(System.currentTimeMillis());
            } else {
                layerEntity.useCachedThumbnail(oldLayer);
            }

            String thumbnailFilename = layerEntity.getCachedThumbnailFilename();
            if (thumbnailFilename != null) {
                usedThumbnails.add(thumbnailFilename);
            }

            try {
                IndexResponse indexResponse = this.index(client, layerEntity);

                LOGGER.debug(String.format("[%d/%d] Indexing AtlasMapper layer ID: %s, status: %d",
                        current, total,
                        atlasMapperLayerId,
                        indexResponse.status().getStatus()));
            } catch(Exception ex) {
                LOGGER.warn(String.format("Exception occurred while indexing an AtlasMapper layer: %s", atlasMapperLayerId), ex);
            }
        }

        // Delete old thumbnails older than the TTL (1 month old)
        this.cleanUp(client, harvestStart, usedThumbnails, "AtlasMapper layer");
    }

    private File createLayerThumbnail(String atlasMapperLayerId, String baseLayerId, JSONObject jsonLayersConfig, JSONObject jsonMainConfig) throws Exception {
        if (atlasMapperLayerId == null || jsonLayersConfig == null || jsonMainConfig == null) {
            return null;
        }

        // Get list of data source
        JSONObject dataSources = jsonMainConfig.optJSONObject("dataSources");

        // Get layer configuration
        JSONObject jsonLayer = jsonLayersConfig.optJSONObject(atlasMapperLayerId);
        if (jsonLayer == null || dataSources == null) {
            return null;
        }

        // Get layer data source
        JSONObject dataSource = AtlasMapperLayer.getDataSourceConfig(jsonLayer, jsonMainConfig);

        JSONArray bbox = jsonLayer.optJSONArray("layerBoundingBox");

        // Get URL of the layer
        URL layerUrl = this.getLayerUrl(dataSource, jsonLayer, bbox);

        boolean isBaseLayer = jsonLayer.optBoolean("isBaseLayer", false);

        URL baseLayerUrl = null;
        if (!isBaseLayer) {
            // Get URL of the base layer
            JSONObject jsonBaseLayer = baseLayerId == null ? null : jsonLayersConfig.optJSONObject(baseLayerId);
            String baseLayerDataSourceId = jsonBaseLayer == null ? null : jsonBaseLayer.optString("dataSourceId", null);
            JSONObject baseLayerDataSource = dataSources.optJSONObject(baseLayerDataSourceId);

            baseLayerUrl = this.getLayerUrl(baseLayerDataSource, jsonBaseLayer, bbox);
        }

        // If layer is a base layer (or can't find a WMS base layer), simply call cache with the layer URL.
        if (baseLayerUrl == null) {
            return ImageCache.cache(layerUrl, this.getIndex(), atlasMapperLayerId);
        }

        // Combine layers and cache them.
        return ImageCache.cacheLayer(baseLayerUrl, layerUrl, this.getIndex(), atlasMapperLayerId);
    }

    private URL getLayerUrl(JSONObject dataSource, JSONObject jsonLayer, JSONArray bbox) throws Exception {
        if (dataSource == null || jsonLayer == null) {
            return null;
        }

        if (AtlasMapperLayer.isWMS(dataSource)) {
            return this.getWMSLayerUrl(dataSource, jsonLayer, bbox);
        }

        return null;
    }

    private URL getWMSLayerUrl(JSONObject dataSource, JSONObject jsonLayer, JSONArray bbox) throws Exception {
        if (dataSource == null || jsonLayer == null) {
            return null;
        }

        String layerName = jsonLayer.optString("layerName", null);
        boolean isBaseLayer = jsonLayer.optBoolean("isBaseLayer", false);
        String serviceUrlStr = jsonLayer.optString("serviceUrl", null);
        if (serviceUrlStr == null) {
            serviceUrlStr = dataSource.optString("serviceUrl", null);
        }

        String wmsVersion = jsonLayer.optString("wmsVersion", null);
        if (wmsVersion == null) {
            wmsVersion = dataSource.optString("wmsVersion", null);
        }
        if (wmsVersion == null) {
            wmsVersion = "1.1.1";
        }

        URI serviceUri = URI.create(serviceUrlStr);

        // Get query parameters map
        MultivaluedMap<String, String> queryMultiMap = UriComponent.decodeQuery(serviceUri, true);
        Map<String, String> queryMap = new HashMap<String, String>();
        if (queryMultiMap != null && !queryMultiMap.isEmpty()) {
            for (String key : queryMultiMap.keySet()) {
                queryMap.put(key.toUpperCase(), queryMultiMap.getFirst(key));
            }
        }

        String format = "image/png";
        String transparent = "true";
        if (isBaseLayer) {
            format = "image/jpeg";
            transparent = "false";
        }

        // Parse bbox
        float west = -180;
        float south = -90;
        float east = 180;
        float north = 90;
        if (bbox != null) {
            west = bbox.optFloat(0, west);
            south = bbox.optFloat(1, south);
            east = bbox.optFloat(2, east);
            north = bbox.optFloat(3, north);

            if (west > east) {
                float temp = east;
                east = west;
                west = temp;
            }

            if (south > north) {
                float temp = south;
                south = north;
                north = temp;
            }

            // If bbox have ridiculous values, don't even try...
            if (west < -200 || west > 200) { return null; }
            if (south < -100 || south > 100) { return null; }
            if (east < -200 || east > 200) { return null; }
            if (north < -100 || north > 100) { return null; }

            // Fix very small bbox (single point)
            float _bboxWidth = east - west;
            float _bboxHeight = north - south;
            if (_bboxWidth < 0.01) {
                west -= 0.005;
                east += 0.005;
            }
            if (_bboxHeight < 0.01) {
                south -= 0.005;
                north += 0.005;
            }

            // Add layer margin
            west = west - _bboxWidth * THUMBNAIL_MARGIN;
            south = south - _bboxHeight * THUMBNAIL_MARGIN;
            east = east + _bboxWidth * THUMBNAIL_MARGIN;
            north = north + _bboxHeight * THUMBNAIL_MARGIN;

            // Fix out-of-bounds bbox
            if (west < -180) { west = -180; }
            if (west > 180) { west = 180; }
            if (south < -90) { south = -90; }
            if (south > 90) { south = 90; }
            if (east < -180) { east = -180; }
            if (east > 180) { east = 180; }
            if (north < -90) { north = -90; }
            if (north > 90) { north = 90; }
        }

        float bboxWidth = east - west;
        float bboxHeight = north - south;

        // Calculate width x height, respecting max width and max height,
        //     and respecting layer aspect ratio.
        // It's hard to visualise, but the math are very simple.

        float bboxRatio = bboxWidth / bboxHeight;
        float thumbnailRatio = (float)THUMBNAIL_MAX_WIDTH / THUMBNAIL_MAX_HEIGHT;

        int width = THUMBNAIL_MAX_WIDTH;
        int height = THUMBNAIL_MAX_HEIGHT;
        if (bboxRatio > thumbnailRatio) {
            height = (int)(THUMBNAIL_MAX_WIDTH / bboxRatio);
        } else {
            width = (int)(THUMBNAIL_MAX_HEIGHT * bboxRatio);
        }


        // Set query parameters for the WMS GetMap query
        queryMap.put("SERVICE", "WMS");
        queryMap.put("REQUEST", "GetMap");
        queryMap.put("LAYERS", layerName);
        queryMap.put("STYLES", ""); // Needed for THREDDS
        queryMap.put("FORMAT", format);
        queryMap.put("TRANSPARENT", transparent);
        queryMap.put("VERSION", wmsVersion);
        queryMap.put("SRS", "EPSG:4326");
        // CRS is only necessary with WMS 1.3.0, but some WMS server do not interpret "version" properly.
        queryMap.put("CRS", "EPSG:4326");
        queryMap.put("BBOX", String.format("%.12f,%.12f,%.12f,%.12f", west, south, east, north));
        queryMap.put("WIDTH", "" + width);
        queryMap.put("HEIGHT", "" + height);

        StringBuilder querySb = new StringBuilder();
        for (Map.Entry<String, String> queryParameter : queryMap.entrySet()) {
            if (querySb.length() > 0) {
                querySb.append("&");
            }
            querySb
                .append(UriComponent.encode(queryParameter.getKey(), UriComponent.Type.QUERY_PARAM))
                .append("=")
                .append(UriComponent.encode(queryParameter.getValue(), UriComponent.Type.QUERY_PARAM));
        }

        return new URL(serviceUri.getScheme(), serviceUri.getHost(), serviceUri.getPort(),
            serviceUri.getPath() + "?" + querySb.toString());
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
