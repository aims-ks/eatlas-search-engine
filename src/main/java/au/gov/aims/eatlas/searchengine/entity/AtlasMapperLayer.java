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
package au.gov.aims.eatlas.searchengine.entity;

import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URL;

public class AtlasMapperLayer extends Entity {
    private static final Logger LOGGER = Logger.getLogger(AtlasMapperLayer.class.getName());

    private AtlasMapperLayer() {}

    /**
     * atlasMapperLayerId: "ea_TS_TSRA_SLUP-2010:Kubin-Water_Catchment-Area"
     * jsonLayer:
     *   {
     *     "dataSourceId": "ea",
     *     "wmsQueryable": true,
     *     "cached": true,
     *     "layerBoundingBox": [
     *       142.2197290114851,
     *       -10.21841497162669,
     *       142.24285496181,
     *       -10.192655696502786
     *     ],
     *     "description": "This project seeks to ensure that ...",
     *     "styles": [{
     *       "default": true,
     *       "cached": true,
     *       "name": "line",
     *       "description": "Default line style, 1 pixel wide blue",
     *       "title": "1 px blue line"
     *     }],
     *     "layerName": "TS_TSRA_SLUP-2010:Kubin-Water_Catchment-Area",
     *     "title": "Kubin Water - Catchment Area  (TSRA)",
     *     "treePath": "Boundaries/TS: Sustainable Land Use Plan 2010 (TSRA, RPS)/Kubin"
     *   }
     * jsonDataSource:
     *         {
     *             "wmsVersion": "1.1.1",
     *             "layerType": "WMS",
     *             "layerCount": 1593,
     *             "serviceUrl": "https://maps.eatlas.org.au/maps/wms?SERVICE=WMS&",
     *             "legendParameters": {
     *                 "FORMAT": "image/png",
     *                 "WIDTH": "20",
     *                 "HEIGHT": "15",
     *                 "LEGEND_OPTIONS": "fontAntiAliasing:true"
     *             },
     *             "webCacheEnable": true,
     *             "dataSourceName": "eAtlas",
     *             "webCacheCapabilitiesUrl": "https://maps.eatlas.org.au/maps/gwc/service/wmts?REQUEST=getcapabilities",
     *             "legendUrl": "https://maps.eatlas.org.au/maps/wms?SERVICE=WMS&",
     *             "dataSourceId": "ea",
     *             "featureRequestsUrl": "https://maps.eatlas.org.au/maps/wms?SERVICE=WMS&",
     *             "webCacheSupportedParameters": [
     *                 "LAYERS",
     *                 "TRANSPARENT",
     *                 "SERVICE",
     *                 "VERSION",
     *                 "REQUEST",
     *                 "EXCEPTIONS",
     *                 "FORMAT",
     *                 "CRS",
     *                 "SRS",
     *                 "BBOX",
     *                 "WIDTH",
     *                 "HEIGHT",
     *                 "STYLES"
     *             ],
     *             "legendDpiSupport": true,
     *             "showInLegend": true,
     *             "webCacheUrl": "https://maps.eatlas.org.au/maps/gwc/service/wms",
     *             "cacheWmsVersion": "1.1.1",
     *             "status": "OKAY"
     *         }
     */
    public AtlasMapperLayer(String index, String atlasMapperClientUrl, String atlasMapperLayerId, JSONObject jsonLayer, JSONObject jsonMainConfig) {
        this.setId(atlasMapperLayerId);

        if (jsonLayer != null && jsonMainConfig != null) {
            this.setTitle(jsonLayer.optString("title", null));
            this.setDocument(jsonLayer.optString("description", null));
            this.setLink(this.getLayerMapUrl(atlasMapperClientUrl, atlasMapperLayerId, jsonLayer, jsonMainConfig));

            // TODO Create a thumbnail image in "eatlas_layer" directory, save it with
            //   this.setCachedThumbnailFilename();
            this.setThumbnailUrl(this.getLayerThumbnailUrl(atlasMapperClientUrl, atlasMapperLayerId, jsonLayer, jsonMainConfig));

            this.setLangcode("en");
        }
    }

    private URL getLayerMapUrl(String atlasMapperClientUrl, String atlasMapperLayerId, JSONObject jsonLayer, JSONObject jsonMainConfig) {
        // Get the background layer ID

        // URL to a preview map with the layer zoom to its bbox.
        // Example:
        //   https://maps.eatlas.org.au/index.html?intro=false&l0=ea_ea-graphics%3ANAU_Graphics_Sept-2014_Marine-regions,ea_ea-be%3AWorld_Bright-Earth-e-Atlas-basemap&bbox=142.2197290114851,-10.21841497162669,142.24285496181,-10.192655696502786

        // Get the bbox, for the URL
        // Bounding box: 4 coma separated real values representing respectively
        //     the west (left) boundary, south (bottom) boundary, east (right) boundary and the north (top) boundary.
        String bbox = null;
        JSONArray jsonLayerBoundingBox = jsonLayer.optJSONArray("layerBoundingBox");
        if (jsonLayerBoundingBox != null) {
            StringBuilder bboxSb = new StringBuilder();
            for (int i=0; i<jsonLayerBoundingBox.length(); i++) {
                if (i>0) {
                    bboxSb.append(",");
                }
                bboxSb.append(jsonLayerBoundingBox.optString(i, "0"));
            }
            bbox = bboxSb.toString();
        }

        String linkStr = String.format("%s/index.html?intro=false&l0=%s", atlasMapperClientUrl, atlasMapperLayerId);

        // Find the default base layer:
        String baseLayer = this.getBaseLayer(jsonMainConfig);
        if (baseLayer != null) {
            linkStr += "," + baseLayer;
        }
        if (bbox != null) {
            linkStr += "&bbox=" + bbox;
        }

        try {
            return new URL(linkStr);
        } catch(Exception ex) {
            LOGGER.error(String.format("Invalid layer URL: %s", linkStr), ex);
        }

        return null;
    }

    private URL getLayerThumbnailUrl(String atlasMapperClientUrl, String atlasMapperLayerId, JSONObject jsonLayer, JSONObject jsonMainConfig) {
        // TODO
        //   1. Craft URL for the basemap, in the BBox of the layer
        //   2. Craft URL for the layer, in the BBox of the layer
        //   3. Download images and stitch them together
        //   4. Save image in the search engine cache and return cache URL
        return null;
    }

    // Find the base layer from the AtlasMapper main config
    private String getBaseLayer(JSONObject jsonMainResponse) {
        if (jsonMainResponse == null) {
            return null;
        }

        JSONArray jsonDefaultLayers = jsonMainResponse.optJSONArray("defaultLayers");
        if (jsonDefaultLayers == null) {
            return null;
        }

        for (int i=0; i<jsonDefaultLayers.length(); i++) {
            JSONObject jsonDefaultLayer = jsonDefaultLayers.getJSONObject(i);
            if (jsonDefaultLayer != null) {
                boolean isBaseLayer = jsonDefaultLayer.optBoolean("isBaseLayer", false);
                if (isBaseLayer) {
                    String baseLayer = jsonDefaultLayer.optString("layerId", null);
                    if (baseLayer != null) {
                        return baseLayer;
                    }
                }
            }
        }

        return null;
    }

    private JSONObject getDataSourceConfig(JSONObject jsonLayer, JSONObject jsonMainConfig) {
        if (jsonLayer == null || jsonMainConfig == null) {
            return null;
        }

        String dataSourceId = jsonLayer.optString("dataSourceId", null);
        if (dataSourceId != null) {
            JSONObject jsonDataSources = jsonMainConfig.optJSONObject("dataSources");
            if (jsonDataSources != null) {
                return jsonDataSources.optJSONObject(dataSourceId);
            }
        }

        return null;
    }

    public static AtlasMapperLayer load(JSONObject json) {
        AtlasMapperLayer layer = new AtlasMapperLayer();
        layer.loadJSON(json);

        return layer;
    }
}
