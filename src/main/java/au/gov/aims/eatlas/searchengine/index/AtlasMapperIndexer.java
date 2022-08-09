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

import au.gov.aims.eatlas.searchengine.admin.rest.Messages;
import au.gov.aims.eatlas.searchengine.client.SearchClient;
import au.gov.aims.eatlas.searchengine.entity.AtlasMapperLayer;
import au.gov.aims.eatlas.searchengine.entity.EntityUtils;
import au.gov.aims.eatlas.searchengine.rest.ImageCache;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import org.apache.log4j.Logger;
import org.glassfish.jersey.uri.UriComponent;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Connection;

import javax.ws.rs.core.MultivaluedMap;
import java.io.File;
import java.net.URI;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class AtlasMapperIndexer extends AbstractIndexer<AtlasMapperLayer> {
    private static final Logger LOGGER = Logger.getLogger(AtlasMapperIndexer.class.getName());
    private static final int THREAD_POOL_SIZE = 10;
    private static final int THUMBNAIL_MAX_WIDTH = 300;
    private static final int THUMBNAIL_MAX_HEIGHT = 200;
    private static final float THUMBNAIL_MARGIN = 0.1f; // Margin, in percentage

    // Example: https://maps.eatlas.org.au
    private String atlasMapperClientUrl;
    private String atlasMapperVersion;
    private String baseLayerUrl;

    public static AtlasMapperIndexer fromJSON(String index, JSONObject json) {
        if (json == null || json.isEmpty()) {
            return null;
        }

        return new AtlasMapperIndexer(
            index,
            json.optString("atlasMapperClientUrl", null),
            json.optString("atlasMapperVersion", null),
            json.optString("baseLayerUrl", null));
    }

    public JSONObject toJSON() {
        return this.getJsonBase()
            .put("atlasMapperClientUrl", this.atlasMapperClientUrl)
            .put("atlasMapperVersion", this.atlasMapperVersion)
            .put("baseLayerUrl", this.baseLayerUrl);
    }

    @Override
    public boolean validate() {
        if (!super.validate()) {
            return false;
        }
        if (this.atlasMapperClientUrl == null || this.atlasMapperClientUrl.isEmpty()) {
            return false;
        }
        return true;
    }

    @Override
    public AtlasMapperLayer load(JSONObject json, Messages messages) {
        return AtlasMapperLayer.load(json, messages);
    }

    @Override
    protected AtlasMapperLayer harvestEntity(SearchClient client, String layerId, Messages messages) {
        // If we have a layerInfoService, use that to get info about the layer.
        // If not, pull the whole list of layer and find the info about that single layer.

        String mainUrlStr = String.format("%s/config/main.json", this.atlasMapperClientUrl);
        JsonResponse mainResponse = AtlasMapperIndexer.getJsonResponse(mainUrlStr, messages);
        if (mainResponse == null) {
            return null;
        }
        JSONObject jsonMainConfig = mainResponse.getJson();

        // Example: /atlasmapper/public/layersInfo.jsp
        String layerInfoServiceUrl = jsonMainConfig.optString("layerInfoServiceUrl", null);
        String clientId = jsonMainConfig.optString("clientId", null);

        JSONObject jsonLayer = null;
        if (layerInfoServiceUrl != null && !layerInfoServiceUrl.isEmpty()) {
            // Get the layer info using the layer info service.
            // The service is much faster than requesting the list of all layers.
            String layerInfoUrlStr = String.format("%s%s?client=%s&layerIds=%s",
                        this.atlasMapperClientUrl, layerInfoServiceUrl, clientId, layerId);

            JsonResponse layerInfoResponse = AtlasMapperIndexer.getJsonResponse(layerInfoUrlStr, messages);
            if (layerInfoResponse != null) {
                JSONObject jsonLayerResponse = layerInfoResponse.getJson();
                if (jsonLayerResponse != null) {
                    JSONObject jsonLayerData = jsonLayerResponse.optJSONObject("data");
                    if (jsonLayerData != null) {
                        jsonLayer = jsonLayerData.optJSONObject(layerId);
                    }
                }
            }

        } else {
            // Get the layer info from the list of all layers.
            // The list can be pretty long, that might take a while...
            String layersUrlStr = String.format("%s/config/layers.json", this.atlasMapperClientUrl);
            JsonResponse layersResponse = AtlasMapperIndexer.getJsonResponse(layersUrlStr, messages);
            if (layersResponse != null) {
                JSONObject jsonLayers = layersResponse.getJson();
                jsonLayer = jsonLayers.optJSONObject(layerId);
            }
        }

        AtlasMapperLayer layerEntity = new AtlasMapperLayer(
                this.getIndex(), this.atlasMapperClientUrl, layerId,
                jsonLayer, jsonMainConfig, messages);

        AtlasMapperIndexer.updateThumbnail(
                layerId, this.getIndex(), jsonLayer,
                this.baseLayerUrl, jsonMainConfig, layerEntity, messages);

        return layerEntity;
    }

    /**
     * index: eatlas_layer
     * atlasMapperClientUrl: https://maps.eatlas.org.au
     * atlasMapperVersion: 2.2.0
     */
    public AtlasMapperIndexer(String index, String atlasMapperClientUrl, String atlasMapperVersion, String baseLayerUrl) {
        super(index);
        this.atlasMapperClientUrl = atlasMapperClientUrl;
        this.atlasMapperVersion = atlasMapperVersion;
        this.baseLayerUrl = baseLayerUrl;
    }

    @Override
    protected void internalIndex(SearchClient client, Long lastHarvested, Messages messages) {
        // There is no way to get last modified layers from AtlasMapper.
        // Therefore, we only perform a harvest if the JSON files are more recent than lastHarvested.

        // Get the main configuration file, containing the map of data sources
        // "https://maps.eatlas.org.au/config/main.json"
        String mainUrlStr = String.format("%s/config/main.json", this.atlasMapperClientUrl);
        JsonResponse mainResponse = AtlasMapperIndexer.getJsonResponse(mainUrlStr, messages);
        if (mainResponse == null) {
            return;
        }
        Long mainLastModified = mainResponse.getLastModified();

        // Get the list of layers
        // "https://maps.eatlas.org.au/config/layers.json"
        String layersUrlStr = String.format("%s/config/layers.json", this.atlasMapperClientUrl);
        JsonResponse layersResponse = AtlasMapperIndexer.getJsonResponse(layersUrlStr, messages);
        if (layersResponse == null) {
            return;
        }
        Long layersLastModified = layersResponse.getLastModified();

        if (lastHarvested != null) {
            // If a file have no last modified in the header,
            // we can't tell if the index is outdated. Let's assume it is.
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
                    return;
                }
            }
        }

        JSONObject jsonMainConfig = mainResponse.getJson();
        JSONObject jsonLayersConfig = layersResponse.getJson();

        long harvestStart = System.currentTimeMillis();

        Set<String> usedThumbnails = Collections.synchronizedSet(new HashSet<String>());
        this.setTotal((long)jsonLayersConfig.length());
        int current = 0;

        ThreadPoolExecutor threadPool = (ThreadPoolExecutor) Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        for (String atlasMapperLayerId : jsonLayersConfig.keySet()) {
            current++;

            Thread thread = new AtlasMapperIndexerThread(
                    client, messages, atlasMapperLayerId, jsonMainConfig, jsonLayersConfig,
                    this.getBaseLayerUrl(), usedThumbnails, current);

            threadPool.execute(thread);
        }

        threadPool.shutdown();
        try {
            threadPool.awaitTermination(1, TimeUnit.HOURS);
        } catch(InterruptedException ex) {
            messages.addMessage(Messages.Level.ERROR, "The AtlasMapper layers indexation was interrupted", ex);
        }

        // Delete old thumbnails older than the TTL (1 month old)
        this.cleanUp(client, harvestStart, usedThumbnails, "AtlasMapper layer", messages);
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

    public String getBaseLayerUrl() {
        return this.baseLayerUrl;
    }

    public void setBaseLayerUrl(String baseLayerUrl) {
        this.baseLayerUrl = baseLayerUrl;
    }


    // Static methods, use by AtlasMapperIndexerThread

    public static URL getLayerUrl(JSONObject dataSource, JSONObject jsonLayer, JSONArray bbox) throws Exception {
        // Parse bbox
        BboxInfo bboxInfo = BboxInfo.parse(bbox);
        return AtlasMapperIndexer.getLayerUrl(dataSource, jsonLayer, bboxInfo);
    }

    public static URL getLayerUrl(JSONObject dataSource, JSONObject jsonLayer, BboxInfo bboxInfo) throws Exception {
        if (AtlasMapperLayer.isWMS(dataSource)) {
            return AtlasMapperIndexer.getWMSLayerUrl(dataSource, jsonLayer, bboxInfo);
        }

        return null;
    }

    public static URL getWMSLayerUrl(JSONObject dataSource, JSONObject jsonLayer, BboxInfo bboxInfo) throws Exception {
        if (dataSource == null || jsonLayer == null || bboxInfo == null) {
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
        queryMap.put("BBOX", bboxInfo.getBbox());
        queryMap.put("WIDTH", bboxInfo.getWidth());
        queryMap.put("HEIGHT", bboxInfo.getHeight());

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

    public static void updateThumbnail(
            String layerId, String index,
            JSONObject jsonLayer,
            String baseLayerUrl,
            JSONObject jsonMainConfig,
            AtlasMapperLayer layerEntity,
            Messages messages) {

        try {
            File cachedThumbnailFile = AtlasMapperIndexer.createLayerThumbnail(
                    layerId, index, jsonLayer, baseLayerUrl, jsonMainConfig, messages);

            if (cachedThumbnailFile != null) {
                layerEntity.setCachedThumbnailFilename(cachedThumbnailFile.getName());
            }
        } catch(Exception ex) {
            messages.addMessage(Messages.Level.WARNING,
                    String.format("Exception occurred while creating a thumbnail image for AtlasMapper layer: %s",
                    layerEntity.getId()), ex);
        }
        layerEntity.setThumbnailLastIndexed(System.currentTimeMillis());
    }

    private static File createLayerThumbnail(
            String layerId, String index,
            JSONObject jsonLayer,
            String baseLayerUrlStr,
            JSONObject jsonMainConfig,
            Messages messages) throws Exception {

        if (layerId == null || jsonMainConfig == null) {
            return null;
        }

        // Get list of data source
        JSONObject dataSources = jsonMainConfig.optJSONObject("dataSources");

        // Get layer configuration
        if (jsonLayer == null || dataSources == null) {
            return null;
        }

        // Get layer data source
        JSONObject dataSource = AtlasMapperLayer.getDataSourceConfig(jsonLayer, jsonMainConfig);

        JSONArray bbox = jsonLayer.optJSONArray("layerBoundingBox");
        BboxInfo bboxInfo = BboxInfo.parse(bbox);

        // Get URL of the layer
        URL layerUrl = AtlasMapperIndexer.getLayerUrl(dataSource, jsonLayer, bboxInfo);

        boolean isBaseLayer = jsonLayer.optBoolean("isBaseLayer", false);

        URL baseLayerUrl = null;
        if (bboxInfo != null && !isBaseLayer) {
            // Get URL of the base layer
            baseLayerUrl = new URL(baseLayerUrlStr.replace("{BBOX}", bboxInfo.getBbox())
                .replace("{WIDTH}", bboxInfo.getWidth())
                .replace("{HEIGHT}", bboxInfo.getHeight()));
        }

        // If layer is a base layer (or can't find a WMS base layer), simply call cache with the layer URL.
        if (baseLayerUrl == null) {
            return ImageCache.cache(layerUrl, index, layerId, messages);
        }

        // Combine layers and cache them.
        return ImageCache.cacheLayer(baseLayerUrl, layerUrl, index, layerId, messages);
    }

    public static JsonResponse getJsonResponse(String url, Messages messages) {
        Connection.Response response = null;
        try {
            response = EntityUtils.jsoupExecuteWithRetry(url, messages);
        } catch(Exception ex) {
            messages.addMessage(Messages.Level.ERROR, String.format("Exception occurred while downloading the AtlasMapper configuration file: %s",
                    url), ex);
            return null;
        }
        Long lastModified = IndexUtils.parseHttpLastModifiedHeader(response, messages);

        String responseStr = response.body();
        JSONObject json = null;
        if (responseStr != null && !responseStr.isEmpty()) {
            json = new JSONObject(responseStr);
        }

        return new JsonResponse(json, lastModified);
    }

    private static class BboxInfo {
        private String bbox;
        private String width;
        private String height;

        public static BboxInfo parse(JSONArray bbox) {
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

            BboxInfo bboxInfo = new BboxInfo();
            bboxInfo.width = "" + width;
            bboxInfo.height = "" + height;
            bboxInfo.bbox = String.format("%.12f,%.12f,%.12f,%.12f", west, south, east, north);

            return bboxInfo;
        }

        public String getBbox() {
            return bbox;
        }

        public String getWidth() {
            return width;
        }

        public String getHeight() {
            return height;
        }
    }

    private static class JsonResponse {
        private Long lastModified;
        private JSONObject json;

        public JsonResponse(JSONObject json, Long lastModified) {
            this.lastModified = lastModified;
            this.json = json;
        }

        public Long getLastModified() {
            return this.lastModified;
        }

        public JSONObject getJson() {
            return this.json;
        }
    }

    public class AtlasMapperIndexerThread extends Thread {
        private final SearchClient client;
        private final Messages messages;
        private final String atlasMapperLayerId;
        private final JSONObject jsonMainConfig;
        private final JSONObject jsonLayersConfig;
        private final String baseLayerUrl;
        private final Set<String> usedThumbnails;
        private final int current;

        public AtlasMapperIndexerThread(
                SearchClient client,
                Messages messages,
                String atlasMapperLayerId,
                JSONObject jsonMainConfig,
                JSONObject jsonLayersConfig,
                String baseLayerUrl,
                Set<String> usedThumbnails,
                int current
        ) {
            this.client = client;
            this.messages = messages;
            this.atlasMapperLayerId = atlasMapperLayerId;
            this.jsonMainConfig = jsonMainConfig;
            this.jsonLayersConfig = jsonLayersConfig;
            this.baseLayerUrl = baseLayerUrl;
            this.usedThumbnails = usedThumbnails;
            this.current = current;
        }

        @Override
        public void run() {
            JSONObject jsonLayer = this.jsonLayersConfig.optJSONObject(this.atlasMapperLayerId);

            AtlasMapperLayer layerEntity = new AtlasMapperLayer(
                    AtlasMapperIndexer.this.getIndex(), AtlasMapperIndexer.this.atlasMapperClientUrl,
                    this.atlasMapperLayerId, jsonLayer, this.jsonMainConfig, this.messages);

            // Create the thumbnail if it's missing or outdated
            AtlasMapperLayer oldLayer = AtlasMapperIndexer.this.safeGet(this.client, AtlasMapperLayer.class, this.atlasMapperLayerId, this.messages);
            if (layerEntity.isThumbnailOutdated(oldLayer, AtlasMapperIndexer.this.getSafeThumbnailTTL(), AtlasMapperIndexer.this.getSafeBrokenThumbnailTTL(), this.messages)) {
                AtlasMapperIndexer.updateThumbnail(
                        this.atlasMapperLayerId, AtlasMapperIndexer.this.getIndex(),
                        jsonLayer, this.baseLayerUrl, this.jsonMainConfig,
                        layerEntity, this.messages);
            } else {
                layerEntity.useCachedThumbnail(oldLayer);
            }

            String thumbnailFilename = layerEntity.getCachedThumbnailFilename();
            if (thumbnailFilename != null) {
                this.usedThumbnails.add(thumbnailFilename);
            }

            try {
                IndexResponse indexResponse = AtlasMapperIndexer.this.indexEntity(this.client, layerEntity, this.messages);

                LOGGER.debug(String.format("[%d/%d] Indexing AtlasMapper layer ID: %s, index response status: %s",
                        this.current, AtlasMapperIndexer.this.getTotal(),
                        this.atlasMapperLayerId,
                        indexResponse.result()));
            } catch(Exception ex) {
                this.messages.addMessage(Messages.Level.WARNING,
                        String.format("Exception occurred while indexing an AtlasMapper layer: %s", this.atlasMapperLayerId), ex);
            }

            AtlasMapperIndexer.this.incrementCompleted();
        }
    }
}
