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

    public AtlasMapperLayer load(JSONObject json, Messages messages) {
        return AtlasMapperLayer.load(json, messages);
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
    protected void internalIndex(SearchClient client, Long lastHarvested, Messages messages) {
        // There is no way to get last modified layers from AtlasMapper.
        // Therefore, we only perform an harvest if the JSON files are more recent than lastHarvested.

        // Get the main configuration file, containing the map of data sources
        // "https://maps.eatlas.org.au/config/main.json"
        String mainUrlStr = String.format("%s/config/main.json", this.atlasMapperClientUrl);

        Connection.Response mainResponse = null;
        try {
            mainResponse = EntityUtils.jsoupExecuteWithRetry(mainUrlStr, messages);
        } catch(Exception ex) {
            messages.addMessage(Messages.Level.ERROR, String.format("Exception occurred while downloading the AtlasMapper main configuration file: %s",
                    mainUrlStr), ex);
            return;
        }
        Long mainLastModified = IndexUtils.parseHttpLastModifiedHeader(mainResponse, messages);

        // Get the list of layers
        // "https://maps.eatlas.org.au/config/layers.json"
        String layersUrlStr = String.format("%s/config/layers.json", this.atlasMapperClientUrl);

        Connection.Response layersResponse = null;
        try {
            layersResponse = EntityUtils.jsoupExecuteWithRetry(layersUrlStr, messages);
        } catch(Exception ex) {
            messages.addMessage(Messages.Level.ERROR, String.format("Exception occurred while downloading the AtlasMapper layers file: %s",
                    layersUrlStr), ex);
            return;
        }
        Long layersLastModified = IndexUtils.parseHttpLastModifiedHeader(layersResponse, messages);

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

        Set<String> usedThumbnails = Collections.synchronizedSet(new HashSet<String>());
        this.setTotal((long)jsonLayersConfig.length());
        int current = 0;

        ThreadPoolExecutor threadPool = (ThreadPoolExecutor) Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        for (String atlasMapperLayerId : jsonLayersConfig.keySet()) {
            current++;

            Thread thread = new AtlasMapperIndexerThread(
                    client, messages, atlasMapperLayerId, jsonMainConfig, jsonLayersConfig, baseLayerId, usedThumbnails, current);

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


    // Static methods, use by AtlasMapperIndexerThread

    public static URL getLayerUrl(JSONObject dataSource, JSONObject jsonLayer, JSONArray bbox) throws Exception {
        if (dataSource == null || jsonLayer == null) {
            return null;
        }

        if (AtlasMapperLayer.isWMS(dataSource)) {
            return AtlasMapperIndexer.getWMSLayerUrl(dataSource, jsonLayer, bbox);
        }

        return null;
    }

    public static URL getWMSLayerUrl(JSONObject dataSource, JSONObject jsonLayer, JSONArray bbox) throws Exception {
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

    public class AtlasMapperIndexerThread extends Thread {
        private final SearchClient client;
        private final Messages messages;
        private final String atlasMapperLayerId;
        private final JSONObject jsonMainConfig;
        private final JSONObject jsonLayersConfig;
        private final String baseLayerId;
        private final Set<String> usedThumbnails;
        private final int current;

        public AtlasMapperIndexerThread(
                SearchClient client,
                Messages messages,
                String atlasMapperLayerId,
                JSONObject jsonMainConfig,
                JSONObject jsonLayersConfig,
                String baseLayerId,
                Set<String> usedThumbnails,
                int current
        ) {
            this.client = client;
            this.messages = messages;
            this.atlasMapperLayerId = atlasMapperLayerId;
            this.jsonMainConfig = jsonMainConfig;
            this.jsonLayersConfig = jsonLayersConfig;
            this.baseLayerId = baseLayerId;
            this.usedThumbnails = usedThumbnails;
            this.current = current;
        }

        @Override
        public void run() {
            JSONObject jsonLayer = this.jsonLayersConfig.optJSONObject(this.atlasMapperLayerId);

            AtlasMapperLayer layerEntity = new AtlasMapperLayer(
                    AtlasMapperIndexer.this.getIndex(), AtlasMapperIndexer.this.atlasMapperClientUrl, this.atlasMapperLayerId, jsonLayer, this.jsonMainConfig, this.messages);

            // Create the thumbnail if it's missing or outdated
            AtlasMapperLayer oldLayer = AtlasMapperIndexer.this.safeGet(this.client, AtlasMapperLayer.class, this.atlasMapperLayerId, this.messages);
            if (layerEntity.isThumbnailOutdated(oldLayer, AtlasMapperIndexer.this.getSafeThumbnailTTL(), AtlasMapperIndexer.this.getSafeBrokenThumbnailTTL(), this.messages)) {
                try {
                    File cachedThumbnailFile = this.createLayerThumbnail(jsonLayer);
                    if (cachedThumbnailFile != null) {
                        layerEntity.setCachedThumbnailFilename(cachedThumbnailFile.getName());
                    }
                } catch(Exception ex) {
                    messages.addMessage(Messages.Level.WARNING,
                            String.format("Exception occurred while creating a thumbnail image for AtlasMapper layer: %s",
                            this.atlasMapperLayerId), ex);
                }
                layerEntity.setThumbnailLastIndexed(System.currentTimeMillis());
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

        private File createLayerThumbnail(JSONObject jsonLayer) throws Exception {
            if (this.atlasMapperLayerId == null || this.jsonLayersConfig == null || this.jsonMainConfig == null) {
                return null;
            }

            // Get list of data source
            JSONObject dataSources = this.jsonMainConfig.optJSONObject("dataSources");

            // Get layer configuration
            if (jsonLayer == null || dataSources == null) {
                return null;
            }

            // Get layer data source
            JSONObject dataSource = AtlasMapperLayer.getDataSourceConfig(jsonLayer, this.jsonMainConfig);

            JSONArray bbox = jsonLayer.optJSONArray("layerBoundingBox");

            // Get URL of the layer
            URL layerUrl = AtlasMapperIndexer.getLayerUrl(dataSource, jsonLayer, bbox);

            boolean isBaseLayer = jsonLayer.optBoolean("isBaseLayer", false);

            URL baseLayerUrl = null;
            if (!isBaseLayer) {
                // Get URL of the base layer
                JSONObject jsonBaseLayer = this.baseLayerId == null ? null : this.jsonLayersConfig.optJSONObject(this.baseLayerId);
                String baseLayerDataSourceId = jsonBaseLayer == null ? null : jsonBaseLayer.optString("dataSourceId", null);
                JSONObject baseLayerDataSource = dataSources.optJSONObject(baseLayerDataSourceId);

                baseLayerUrl = AtlasMapperIndexer.getLayerUrl(baseLayerDataSource, jsonBaseLayer, bbox);
            }

            // If layer is a base layer (or can't find a WMS base layer), simply call cache with the layer URL.
            if (baseLayerUrl == null) {
                return ImageCache.cache(layerUrl, AtlasMapperIndexer.this.getIndex(), this.atlasMapperLayerId, this.messages);
            }

            // Combine layers and cache them.
            return ImageCache.cacheLayer(baseLayerUrl, layerUrl, AtlasMapperIndexer.this.getIndex(), this.atlasMapperLayerId, this.messages);
        }
    }
}
