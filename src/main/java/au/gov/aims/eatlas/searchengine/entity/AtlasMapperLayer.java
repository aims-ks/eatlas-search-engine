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

import au.gov.aims.eatlas.searchengine.admin.rest.Messages;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URL;

public class AtlasMapperLayer extends Entity {
    private String dataSourceName;

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
    public AtlasMapperLayer(String index, String atlasMapperClientUrl, String atlasMapperLayerId, JSONObject jsonLayer, JSONObject jsonMainConfig, Messages messages) {
        this.setId(atlasMapperLayerId);
        this.setIndex(index);

        if (jsonLayer != null && jsonMainConfig != null) {
            JSONObject dataSource = AtlasMapperLayer.getDataSourceConfig(jsonLayer, jsonMainConfig);
            this.dataSourceName = dataSource == null ? null : dataSource.optString("dataSourceName", null);

            String document = WikiFormatter.getText(jsonLayer.optString("description", null));
            if (this.dataSourceName != null && !this.dataSourceName.isEmpty()) {
                if (document == null) {
                    document = "";
                }
                document += String.format("%nData source: %s", this.dataSourceName);
            }

            this.setTitle(jsonLayer.optString("title", null));
            this.setDocument(document);

            JSONArray layerBoundingBox = jsonLayer.optJSONArray("layerBoundingBox");
            boolean validBbox = false;
            if (layerBoundingBox != null && layerBoundingBox.length() == 4) {
                // AtlasMapper BBOX follows OpenLayers order:
                //   West, South, East, North
                double west = layerBoundingBox.optDouble(0, -180);
                double south = layerBoundingBox.optDouble(1, -90);
                double east = layerBoundingBox.optDouble(2, 180);
                double north = layerBoundingBox.optDouble(3, 90);

                // Validate
                validBbox = true;
                if (west < -180 || west > 180) { validBbox = false; }
                if (east < -180 || east > 180) { validBbox = false; }
                if (south < -90 || south > 90) { validBbox = false; }
                if (north < -90 || north > 90) { validBbox = false; }
                if (west > east) { validBbox = false; }
                if (south > north) { validBbox = false; }

                if (validBbox) {
                    // WKT orders:
                    //   West, East, North, South
                    String wkt = String.format("BBOX (%.8f, %.8f, %.8f, %.8f)", west, east, north, south);
                    this.setWkt(wkt);
                }
            }
            if (!validBbox) {
                // Set bbox for the whole world
                this.setWkt("BBOX (-180, 180, 90, -90)");
            }

            this.setLink(this.getLayerMapUrl(atlasMapperClientUrl, atlasMapperLayerId, jsonLayer, jsonMainConfig, messages));
            this.setLangcode("en");
        }
    }

    private URL getLayerMapUrl(String atlasMapperClientUrl, String atlasMapperLayerId, JSONObject jsonLayer, JSONObject jsonMainConfig, Messages messages) {
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
        String baseLayer = AtlasMapperLayer.getBaseLayer(jsonMainConfig);
        if (baseLayer != null) {
            linkStr += "," + baseLayer;
        }
        if (bbox != null) {
            linkStr += "&bbox=" + bbox;
        }

        try {
            return new URL(linkStr);
        } catch(Exception ex) {
            messages.addMessage(Messages.Level.ERROR,
                    String.format("Invalid layer URL: %s", linkStr), ex);
        }

        return null;
    }

    // Find the base layer from the AtlasMapper main config
    public static String getBaseLayer(JSONObject jsonMainConfig) {
        if (jsonMainConfig == null) {
            return null;
        }

        JSONArray jsonDefaultLayers = jsonMainConfig.optJSONArray("defaultLayers");
        if (jsonDefaultLayers == null) {
            return null;
        }

        for (int i=0; i<jsonDefaultLayers.length(); i++) {
            JSONObject jsonDefaultLayer = jsonDefaultLayers.getJSONObject(i);
            if (jsonDefaultLayer != null) {
                boolean isBaseLayer = jsonDefaultLayer.optBoolean("isBaseLayer", false);
                if (isBaseLayer) {
                    String atlasMapperLayerId = jsonDefaultLayer.optString("layerId", null);
                    if (atlasMapperLayerId != null) {
                        return atlasMapperLayerId;
                    }
                }
            }
        }

        return null;
    }

    public static String getWMSBaseLayer(JSONObject jsonMainConfig, JSONObject jsonLayersConfig) {
        if (jsonMainConfig == null) {
            return null;
        }

        JSONArray jsonDefaultLayers = jsonMainConfig.optJSONArray("defaultLayers");
        if (jsonDefaultLayers == null) {
            return null;
        }

        JSONObject dataSources = jsonMainConfig.optJSONObject("dataSources");
        if (dataSources == null) {
            return null;
        }

        for (int i=0; i<jsonDefaultLayers.length(); i++) {
            JSONObject jsonDefaultLayer = jsonDefaultLayers.getJSONObject(i);
            if (isWMSBaseLayer(jsonDefaultLayer, dataSources)) {
                String atlasMapperLayerId = jsonDefaultLayer.optString("layerId", null);
                if (atlasMapperLayerId != null) {
                    return atlasMapperLayerId;
                }
            }
        }

        // There is no WMS base layers in default layers. Look for a WMS base layer in the list of layer.
        // NOTE: This could be expensive (lots of CPU). Hopefully that won't happen often.
        for (String atlasMapperLayerId : jsonLayersConfig.keySet()) {
            JSONObject jsonLayer = jsonLayersConfig.optJSONObject(atlasMapperLayerId);
            if (isWMSBaseLayer(jsonLayer, dataSources)) {
                return atlasMapperLayerId;
            }
        }

        return null;
    }

    private static boolean isWMSBaseLayer(JSONObject jsonLayer, JSONObject dataSources) {
        if (jsonLayer == null || dataSources == null) {
            return false;
        }

        boolean isBaseLayer = jsonLayer.optBoolean("isBaseLayer", false);
        if (!isBaseLayer) {
            return false;
        }

        String dataSourceId = jsonLayer.optString("dataSourceId", null);
        if (dataSourceId == null) {
            return false;
        }

        JSONObject dataSource = dataSources.optJSONObject(dataSourceId);
        if (dataSource == null) {
            return false;
        }

        return AtlasMapperLayer.isWMS(dataSource);
    }

    public static boolean isWMS(JSONObject dataSource) {
        if (dataSource == null) {
            return false;
        }

        String layerType = dataSource.optString("layerType", null);
        if (layerType == null) {
            return false;
        }

        switch (layerType) {
            case "WMS":
            case "WMTS":
            case "NCWMS":
            case "THREDDS":
                return true;

            case "ARCGIS_MAPSERVER":
            case "ARCGIS_CACHE":
            case "KML":
            case "GOOGLE":
            case "BING":
            case "XYZ":
            default:
                return false;
        }
    }

    public String getDataSourceName() {
        return this.dataSourceName;
    }

    public static JSONObject getDataSourceConfig(JSONObject jsonLayer, JSONObject jsonMainConfig) {
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

    public static AtlasMapperLayer load(JSONObject json, Messages messages) {
        AtlasMapperLayer layer = new AtlasMapperLayer();
        layer.loadJSON(json, messages);
        layer.dataSourceName = json.optString("datasource", null);

        return layer;
    }

    @Override
    public JSONObject toJSON() {
        return super.toJSON()
            .put("datasource", this.dataSourceName);
    }
}
