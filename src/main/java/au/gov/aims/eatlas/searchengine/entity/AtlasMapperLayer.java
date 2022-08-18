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
import au.gov.aims.eatlas.searchengine.index.AbstractIndexer;
import org.json.JSONArray;
import org.json.JSONObject;
import org.locationtech.jts.io.ParseException;

import java.net.URL;
import java.text.DecimalFormat;

public class AtlasMapperLayer extends Entity {
    // Used to format numbers in error messages.
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("0");
    static {
        DECIMAL_FORMAT.setMaximumFractionDigits(7);
    }

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

            this.parseWkt(jsonLayer, messages);
            //this.parseFakeWkt(jsonLayer, messages);

            this.setLink(this.getLayerMapUrl(atlasMapperClientUrl, atlasMapperLayerId, jsonLayer, jsonMainConfig, messages));
            this.setLangcode("en");
        }
    }

    private void parseWkt(JSONObject jsonLayer, Messages messages) {
        JSONArray layerBoundingBox = jsonLayer.optJSONArray("layerBoundingBox");

        String wkt = null;
        if (layerBoundingBox != null && layerBoundingBox.length() == 4) {
            boolean validBbox = false;

            // AtlasMapper BBOX follows OpenLayers order:
            //   West, South, East, North
            double west = layerBoundingBox.optDouble(0, -180);
            double south = layerBoundingBox.optDouble(1, -90);
            double east = layerBoundingBox.optDouble(2, 180);
            double north = layerBoundingBox.optDouble(3, 90);

            // Validate bbox
            validBbox = true;

            // NOTE: Allow some wiggle room for BBOX which are slightly outside of acceptable range.
            double bboxTolerance = 0.1;

            // West
            if (west > 180) {
                if (west > 180+bboxTolerance) {
                    validBbox = false;
                } else {
                    west = 180;
                }
            }
            if (west < -180) {
                if (west < -(180+bboxTolerance)) {
                    validBbox = false;
                } else {
                    west = -180;
                }
            }
            // East
            if (east > 180) {
                if (east > 180+bboxTolerance) {
                    validBbox = false;
                } else {
                    east = 180;
                }
            }
            if (east < -180) {
                if (east < -(180+bboxTolerance)) {
                    validBbox = false;
                } else {
                    east = -180;
                }
            }
            // South
            if (south > 90) {
                if (south > 90+bboxTolerance) {
                    validBbox = false;
                } else {
                    south = 90;
                }
            }
            if (south < -90) {
                if (south < -(90+bboxTolerance)) {
                    validBbox = false;
                } else {
                    south = -90;
                }
            }
            // North
            if (north > 90) {
                if (north > 90+bboxTolerance) {
                    validBbox = false;
                } else {
                    north = 90;
                }
            }
            if (north < -90) {
                if (north < -(90+bboxTolerance)) {
                    validBbox = false;
                } else {
                    north = -90;
                }
            }

            if (west > east) { validBbox = false; }
            if (south > north) { validBbox = false; }

            if (validBbox) {
                // WKT orders:
                //   West, East, North, South
                wkt = String.format("BBOX (%.8f, %.8f, %.8f, %.8f)", west, east, north, south);
            } else {
                // Set bbox for the whole world
                Messages.Message messageObj =
                    messages.addMessage(Messages.Level.WARNING, String.format("Layer: %s. Invalid bounding box. Fallback to whole world.", this.getId()));
                messageObj.addDetail(String.format("Invalid bbox: [W: %s, E: %s, N: %s, S: %s]",
                        DECIMAL_FORMAT.format(west), DECIMAL_FORMAT.format(east),
                        DECIMAL_FORMAT.format(north), DECIMAL_FORMAT.format(south)));

                wkt = AbstractIndexer.DEFAULT_WKT;
            }
        } else {
            // Set bbox for the whole world
            messages.addMessage(Messages.Level.WARNING, String.format("Layer: %s. No bounding box. Fallback to whole world.", this.getId()));
            wkt = AbstractIndexer.DEFAULT_WKT;
        }

        // Set WKT
        try {
            this.setWktAndArea(wkt);
        } catch(ParseException ex) {
            Messages.Message message = messages.addMessage(Messages.Level.WARNING, "Invalid WKT", ex);
            message.addDetail(wkt);
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
