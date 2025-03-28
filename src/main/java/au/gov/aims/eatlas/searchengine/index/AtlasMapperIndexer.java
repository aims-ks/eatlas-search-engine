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

import au.gov.aims.eatlas.searchengine.HttpClient;
import au.gov.aims.eatlas.searchengine.admin.SearchEngineConfig;
import au.gov.aims.eatlas.searchengine.logger.AbstractLogger;
import au.gov.aims.eatlas.searchengine.client.SearchClient;
import au.gov.aims.eatlas.searchengine.entity.AtlasMapperLayer;
import au.gov.aims.eatlas.searchengine.logger.Level;
import au.gov.aims.eatlas.searchengine.rest.ImageCache;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import jakarta.ws.rs.core.MultivaluedMap;
import org.glassfish.jersey.uri.UriComponent;
import org.json.JSONArray;
import org.json.JSONObject;

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
    // Be kind on GeoServer, do not suffocate it...
    private static final int THREAD_POOL_SIZE = 2;
    private static final int REQUEST_DELAY_MS = 500; // Delay between requests, in milliseconds

    private static final int THUMBNAIL_REQUEST_TIMEOUT = 10000; // 10 seconds
    private static final float THUMBNAIL_MARGIN = 0.25f; // Margin, in percentage

    // Example: https://maps.eatlas.org.au
    private String atlasMapperClientUrl;
    private String atlasMapperVersion;
    private String baseLayerUrl;

    /**
     * index: eatlas_layer
     * atlasMapperClientUrl: https://maps.eatlas.org.au
     * atlasMapperVersion: 2.2.0
     */
    public AtlasMapperIndexer(HttpClient httpClient, String index, String indexName, String atlasMapperClientUrl, String atlasMapperVersion, String baseLayerUrl) {
        super(httpClient, index, indexName);
        this.atlasMapperClientUrl = atlasMapperClientUrl;
        this.atlasMapperVersion = atlasMapperVersion;
        this.baseLayerUrl = baseLayerUrl;
    }

    public static AtlasMapperIndexer fromJSON(HttpClient httpClient, String index, String indexName, JSONObject json) {
        if (json == null || json.isEmpty()) {
            return null;
        }

        return new AtlasMapperIndexer(
            httpClient, index, indexName,
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
    public AtlasMapperLayer load(JSONObject json, AbstractLogger logger) {
        return AtlasMapperLayer.load(json, logger);
    }

    @Override
    protected AtlasMapperLayer harvestEntity(SearchClient searchClient, String layerId, AbstractLogger logger) {
        HttpClient httpClient = this.getHttpClient();
        // If we have a layerInfoService, use that to get info about the layer.
        // If not, pull the whole list of layer and find the info about that single layer.

        String mainUrlStr = HttpClient.combineUrls(
                this.atlasMapperClientUrl,
                "config/main.json");

        HttpClient.Response mainResponse;
        try {
            mainResponse = httpClient.getRequest(mainUrlStr, logger);
        } catch(Exception ex) {
            logger.addMessage(Level.ERROR, String.format("Exception occurred while downloading the AtlasMapper configuration file: %s",
                    mainUrlStr), ex);
            return null;
        }
        if (mainResponse == null) {
            return null;
        }

        JSONObject jsonMainConfig = mainResponse.jsonBody();

        // Example: /atlasmapper/public/layersInfo.jsp
        String layerInfoServiceUrl = jsonMainConfig.optString("layerInfoServiceUrl", null);
        String clientId = jsonMainConfig.optString("clientId", null);

        JSONObject jsonLayer = null;
        if (layerInfoServiceUrl != null && !layerInfoServiceUrl.isEmpty()) {
            // Get the layer info using the layer info service.
            // The service is much faster than requesting the list of all layers.
            String layerInfoUrlStr = HttpClient.combineUrls(
                        this.atlasMapperClientUrl,
                        String.format("%s?client=%s&layerIds=%s",
                                layerInfoServiceUrl, clientId, layerId));

            HttpClient.Response layerInfoResponse;
            try {
                layerInfoResponse = httpClient.getRequest(layerInfoUrlStr, logger);
            } catch(Exception ex) {
                logger.addMessage(Level.ERROR, String.format("Exception occurred while downloading the AtlasMapper configuration file: %s",
                        layerInfoUrlStr), ex);
                return null;
            }

            if (layerInfoResponse != null) {
                JSONObject jsonLayerResponse = layerInfoResponse.jsonBody();
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
            String layersUrlStr = HttpClient.combineUrls(
                    this.atlasMapperClientUrl,
                    "config/layers.json");

            HttpClient.Response layersResponse;
            try {
                layersResponse = httpClient.getRequest(layersUrlStr, logger);
            } catch(Exception ex) {
                logger.addMessage(Level.ERROR, String.format("Exception occurred while downloading the AtlasMapper configuration file: %s",
                        layersUrlStr), ex);
                return null;
            }

            if (layersResponse != null) {
                JSONObject jsonLayers = layersResponse.jsonBody();
                jsonLayer = jsonLayers.optJSONObject(layerId);
            }
        }

        AtlasMapperLayer layerEntity = new AtlasMapperLayer(
                this.getIndex(), this.atlasMapperClientUrl, layerId,
                jsonLayer, jsonMainConfig, logger);

        // Always update the thumbnail, when indexing a single layer
        AtlasMapperIndexer.updateThumbnail(
                httpClient, layerId, this.getIndex(), jsonLayer,
                this.baseLayerUrl, jsonMainConfig, layerEntity, 120000, logger);

        return layerEntity;
    }

    @Override
    public boolean supportsIndexLatest() {
        // In this case, index latest re-index everything but doesn't refresh thumbnails (preview images).
        // It's not as quick as only indexing the latest layers, but it's good enough.
        return true;
    }

    @Override
    protected void internalIndex(SearchClient searchClient, Long lastHarvested, AbstractLogger logger) {
        HttpClient httpClient = this.getHttpClient();
        // There is no way to get last modified layers from AtlasMapper.
        // Therefore, we only perform a harvest if the JSON files are more recent than lastHarvested.

        // Get the main configuration file, containing the map of data sources
        // "https://maps.eatlas.org.au/config/main.json"
        String mainUrlStr = HttpClient.combineUrls(
                this.atlasMapperClientUrl,
                "config/main.json");

        HttpClient.Response mainResponse;
        try {
            mainResponse = httpClient.getRequest(mainUrlStr, logger);
        } catch(Exception ex) {
            logger.addMessage(Level.ERROR, String.format("Exception occurred while downloading the AtlasMapper configuration file: %s",
                    mainUrlStr), ex);
            return;
        }

        if (mainResponse == null) {
            return;
        }
        Long mainLastModified = mainResponse.lastModified();

        // Get the list of layers
        // "https://maps.eatlas.org.au/config/layers.json"
        String layersUrlStr = HttpClient.combineUrls(
                this.atlasMapperClientUrl,
                "config/layers.json");

        HttpClient.Response layersResponse;
        try {
            layersResponse = httpClient.getRequest(layersUrlStr, logger);
        } catch(Exception ex) {
            logger.addMessage(Level.ERROR, String.format("Exception occurred while downloading the AtlasMapper configuration file: %s",
                    layersUrlStr), ex);
            return;
        }

        if (layersResponse == null) {
            return;
        }
        Long layersLastModified = layersResponse.lastModified();

        boolean refreshThumbnails = true;
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

            // The atlas mapper files were changed.
            // Re-harvest the layers, but do not attempt to re-generate the thumbnails.
            refreshThumbnails = false;
        }

        JSONObject jsonMainConfig = mainResponse.jsonBody();
        JSONObject jsonLayersConfig = layersResponse.jsonBody();

        this.indexLayers(searchClient, jsonMainConfig, jsonLayersConfig, refreshThumbnails, logger);
    }

    public void indexLayers(
            SearchClient searchClient,
            JSONObject jsonMainConfig,
            JSONObject jsonLayersConfig,
            boolean refreshThumbnails,
            AbstractLogger logger) {

        long harvestStart = System.currentTimeMillis();

        Set<String> usedThumbnails = Collections.synchronizedSet(new HashSet<String>());
        this.setTotal((long)jsonLayersConfig.length());
        int current = 0;

        ThreadPoolExecutor threadPool = (ThreadPoolExecutor) Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        for (String atlasMapperLayerId : jsonLayersConfig.keySet()) {
            current++;

            Thread thread = new AtlasMapperIndexerThread(
                    searchClient, logger, atlasMapperLayerId, jsonMainConfig, jsonLayersConfig,
                    this.getBaseLayerUrl(), usedThumbnails, refreshThumbnails, current);

            threadPool.execute(thread);
        }

        threadPool.shutdown();
        try {
            // Waiting for GeoServer to generate the preview images can take quite a long time...
            threadPool.awaitTermination(20, TimeUnit.HOURS);
        } catch(InterruptedException ex) {
            logger.addMessage(Level.ERROR, "The AtlasMapper layers indexation was interrupted", ex);
        }

        // Delete old thumbnails older than the TTL (1 month old)
        this.cleanUp(searchClient, harvestStart, usedThumbnails, "AtlasMapper layer", logger);
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
            HttpClient httpClient,
            String layerId, String index,
            JSONObject jsonLayer,
            String baseLayerUrl,
            JSONObject jsonMainConfig,
            AtlasMapperLayer layerEntity,
            Integer timeout,
            AbstractLogger logger) {

        try {
            File cachedThumbnailFile = AtlasMapperIndexer.createLayerThumbnail(
                    httpClient, layerId, index, jsonLayer, baseLayerUrl, jsonMainConfig, timeout, logger);

            if (cachedThumbnailFile != null) {
                layerEntity.setCachedThumbnailFilename(cachedThumbnailFile.getName());
            }
        } catch(Exception ex) {
            logger.addMessage(Level.WARNING,
                    String.format("Exception occurred while creating a thumbnail image for AtlasMapper layer: %s",
                    layerEntity.getId()), ex);
        }
        layerEntity.setThumbnailLastIndexed(System.currentTimeMillis());
    }

    private static File createLayerThumbnail(
            HttpClient httpClient,
            String layerId, String index,
            JSONObject jsonLayer,
            String baseLayerUrlStr,
            JSONObject jsonMainConfig,
            Integer timeout,
            AbstractLogger logger) throws Exception {

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
        if (baseLayerUrlStr != null && bboxInfo != null && !isBaseLayer) {
            // Get URL of the base layer
            baseLayerUrl = new URL(baseLayerUrlStr.replace("{BBOX}", bboxInfo.getBbox())
                .replace("{WIDTH}", bboxInfo.getWidth())
                .replace("{HEIGHT}", bboxInfo.getHeight()));
        }

        // If layer is a base layer (or can't find a WMS base layer), simply call cache with the layer URL.
        if (baseLayerUrl == null) {
            return ImageCache.cache(httpClient, layerUrl, timeout, index, layerId, logger);
        }

        // Combine layers and cache them.
        return ImageCache.cacheLayer(httpClient, baseLayerUrl, layerUrl, timeout, index, layerId, logger);
    }

    private static class BboxInfo {
        private String bbox;
        private String width;
        private String height;

        public static BboxInfo parse(JSONArray bbox) {
            SearchEngineConfig config = SearchEngineConfig.getInstance();
            int minThumbnailWidth = config.getThumbnailWidth();
            int minThumbnailHeight = config.getThumbnailHeight();

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
            float thumbnailRatio = (float)minThumbnailWidth / minThumbnailHeight;

            int width = minThumbnailWidth;
            int height = minThumbnailHeight;
            if (bboxRatio < thumbnailRatio) {
                height = (int)(minThumbnailWidth / bboxRatio);
            } else {
                width = (int)(minThumbnailHeight * bboxRatio);
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

    public class AtlasMapperIndexerThread extends Thread {
        private final SearchClient searchClient;
        private final AbstractLogger logger;
        private final String atlasMapperLayerId;
        private final JSONObject jsonMainConfig;
        private final JSONObject jsonLayersConfig;
        private final String baseLayerUrl;
        private final Set<String> usedThumbnails;
        private final boolean refreshThumbnails;
        private final int current;

        public AtlasMapperIndexerThread(
                SearchClient searchClient,
                AbstractLogger logger,
                String atlasMapperLayerId,
                JSONObject jsonMainConfig,
                JSONObject jsonLayersConfig,
                String baseLayerUrl,
                Set<String> usedThumbnails,
                boolean refreshThumbnails,
                int current
        ) {
            this.searchClient = searchClient;
            this.logger = logger;
            this.atlasMapperLayerId = atlasMapperLayerId;
            this.jsonMainConfig = jsonMainConfig;
            this.jsonLayersConfig = jsonLayersConfig;
            this.baseLayerUrl = baseLayerUrl;
            this.usedThumbnails = usedThumbnails;
            this.refreshThumbnails = refreshThumbnails;
            this.current = current;
        }

        @Override
        public void run() {
            HttpClient httpClient = AtlasMapperIndexer.this.getHttpClient();
            JSONObject jsonLayer = this.jsonLayersConfig.optJSONObject(this.atlasMapperLayerId);

            AtlasMapperLayer layerEntity = new AtlasMapperLayer(
                    AtlasMapperIndexer.this.getIndex(), AtlasMapperIndexer.this.getAtlasMapperClientUrl(),
                    this.atlasMapperLayerId, jsonLayer, this.jsonMainConfig, this.logger);

            // Create the thumbnail if it's missing or outdated
            AtlasMapperLayer oldLayer =
                    AtlasMapperIndexer.this.safeGet(this.searchClient, AtlasMapperLayer.class, this.atlasMapperLayerId, this.logger);

            // Figure out if the thumbnail (preview image) needs to be created or updated:
            //   If the layer is new (not in the index yet), we need to generate its thumbnail.
            //   If the layer have a thumbnail, re-generate it if:
            //     refreshThumbnails is set to true and
            //     the thumbnail image is old enough (outdated).
            boolean newLayer = oldLayer == null;
            boolean outdatedThumbnail = layerEntity.isThumbnailOutdated(
                    oldLayer, AtlasMapperIndexer.this.getSafeThumbnailTTL(),
                    AtlasMapperIndexer.this.getSafeBrokenThumbnailTTL(), this.logger);

            boolean thumbnailNeedsUpdate = newLayer || (outdatedThumbnail && this.refreshThumbnails);
            if (thumbnailNeedsUpdate) {
                AtlasMapperIndexer.updateThumbnail(
                        httpClient, this.atlasMapperLayerId, AtlasMapperIndexer.this.getIndex(),
                        jsonLayer, this.baseLayerUrl, this.jsonMainConfig,
                        layerEntity, THUMBNAIL_REQUEST_TIMEOUT, this.logger);
            } else {
                layerEntity.useCachedThumbnail(oldLayer, this.logger);
            }

            // Keep a list of used thumbnails, so we can delete unused ones at the end of the indexation.
            String thumbnailFilename = layerEntity.getCachedThumbnailFilename();
            if (thumbnailFilename != null) {
                this.usedThumbnails.add(thumbnailFilename);
            }

            try {
                IndexResponse indexResponse = AtlasMapperIndexer.this.indexEntity(this.searchClient, layerEntity, this.logger);

                this.logger.addMessage(Level.INFO, String.format("[%d/%d] Indexing AtlasMapper layer ID: %s, index response status: %s",
                        this.current, AtlasMapperIndexer.this.getTotal(),
                        this.atlasMapperLayerId,
                        indexResponse.result()));
            } catch(Exception ex) {
                this.logger.addMessage(Level.WARNING,
                        String.format("Exception occurred while indexing an AtlasMapper layer: %s", this.atlasMapperLayerId), ex);
            }

            AtlasMapperIndexer.this.incrementCompleted();

            try {
                // Wait a little before sending the next request, to be gentle on GeoServer
                Thread.sleep(AtlasMapperIndexer.REQUEST_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
