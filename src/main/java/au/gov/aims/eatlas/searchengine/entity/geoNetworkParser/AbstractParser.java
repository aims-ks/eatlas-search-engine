/*
 *  Copyright (C) 2022 Australian Institute of Marine Science
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
package au.gov.aims.eatlas.searchengine.entity.geoNetworkParser;

import au.gov.aims.eatlas.searchengine.logger.AbstractLogger;
import au.gov.aims.eatlas.searchengine.entity.GeoNetworkRecord;
import au.gov.aims.eatlas.searchengine.index.WktUtils;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;
import org.w3c.dom.Element;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public abstract class AbstractParser {
    // New line used to separate lines in the indexed document.
    // It doesn't really matter which new line scheme is used, as long as it's supported by ElasticSearch.
    public static final String NL = "\n";
    // https://www.usna.edu/Users/oceano/pguth/md_help/html/approx_equivalents.htm
    public static final double GIS_EPSILON = 0.001; // About 100 metres

    public static final Map<String, Locale> LANGCODE_MAP = new HashMap<>();
    static {
        String[] languages = Locale.getISOLanguages();
        for (String language : languages) {
            Locale locale = new Locale(language);
            LANGCODE_MAP.put(locale.getISO3Language(), locale);
        }
    }

    public static final Map<String, String> ROLE_LABEL_MAP = new HashMap<>();
    static {
        ROLE_LABEL_MAP.put("resourceProvider", "Resource provider");
        ROLE_LABEL_MAP.put("custodian", "Custodian");
        ROLE_LABEL_MAP.put("owner", "Owner");
        ROLE_LABEL_MAP.put("user", "User");
        ROLE_LABEL_MAP.put("distributor", "Distributor");
        ROLE_LABEL_MAP.put("originator", "Originator");
        ROLE_LABEL_MAP.put("pointOfContact", "Point of contact");
        ROLE_LABEL_MAP.put("principalInvestigator", "Principal investigator");
        ROLE_LABEL_MAP.put("processor", "Processor");
        ROLE_LABEL_MAP.put("publisher", "Publisher");
        ROLE_LABEL_MAP.put("author", "Author");
        ROLE_LABEL_MAP.put("coInvestigator", "Co-investigator");
        ROLE_LABEL_MAP.put("licensor", "Licensor");
        ROLE_LABEL_MAP.put("researchAssistant", "Research assistant");
        ROLE_LABEL_MAP.put("ipOwner", "Intellectual property owner");
        ROLE_LABEL_MAP.put("moralRightsOwner", "Moral rights owner");
        ROLE_LABEL_MAP.put("metadataContact", "Metadata contact");
    }

    public abstract void parseRecord(GeoNetworkRecord record, String geoNetworkUrlStr, Element rootElement, AbstractLogger logger);

    public static void addResponsibleParty(ResponsibleParty responsibleParty, Map<String, List<ResponsibleParty>> responsiblePartyMap) {
        if (responsibleParty != null && responsiblePartyMap != null) {
            String role = responsibleParty.getRole();
            if (role == null) {
                role = "UNKNOWN";
            }
            List<ResponsibleParty> list = responsiblePartyMap.get(role);
            if (list == null) {
                list = new ArrayList<ResponsibleParty>();
                responsiblePartyMap.put(role, list);
            }
            list.add(responsibleParty);
        }
    }

    public URL getMetadataLink(GeoNetworkRecord record, String geoNetworkUrlStr) throws MalformedURLException {
        int geoNetworkMajorVersion = -1;

        String geoNetworkVersion = record.getGeoNetworkVersion();
        if (geoNetworkVersion != null) {
            if (geoNetworkVersion.startsWith("2.")) {
                geoNetworkMajorVersion = 2;
            } else if (geoNetworkVersion.startsWith("3.")) {
                geoNetworkMajorVersion = 3;
            }
        }

        String geonetworkMetadataUrlStr = null;
        switch (geoNetworkMajorVersion) {
            case 2:
                geonetworkMetadataUrlStr = String.format("%s/srv/eng/metadata.show?uuid=%s", geoNetworkUrlStr, record.getId());
                break;

            case 3:
            default:
                geonetworkMetadataUrlStr = String.format("%s/srv/eng/catalog.search#/metadata/%s", geoNetworkUrlStr, record.getId());
                break;
        }

        return new URL(geonetworkMetadataUrlStr);
    }

    public Polygon parseBoundingBox(String northStr, String eastStr, String southStr, String westStr) {
        return this.parseBoundingBox(
            Double.parseDouble(northStr),
            Double.parseDouble(eastStr),
            Double.parseDouble(southStr),
            Double.parseDouble(westStr)
        );
    }

    public Polygon parseBoundingBox(double north, double east, double south, double west) {
        // Normalise north and south
        if (north > 90) { north = 90; }
        if (south < -90) { south = -90; }

        boolean northSouthEquals = (Math.abs(north - south) < GIS_EPSILON);
        boolean eastWestEquals = (Math.abs(east - west) < GIS_EPSILON);

        // Elastic Search do not allow bbox with 0 area.
        // We could use WKT GEOMETRYCOLLECTION to mix of points, lines and polygons.
        // But the easiest solution is to slightly increase the size of the bbox.
        if (northSouthEquals) {
            north += GIS_EPSILON/2;
            south -= GIS_EPSILON/2;

            // Normalise north and south
            if (north > 90) { north = 90; }
            if (south < -90) { south = -90; }
        }
        if (eastWestEquals) {
            west -= GIS_EPSILON/2;
            east += GIS_EPSILON/2;
        }

        return WktUtils.createPolygon(new Coordinate[] {
            new Coordinate(west, north),
            new Coordinate(west, south),
            new Coordinate(east, south),
            new Coordinate(east, north),
            new Coordinate(west, north)
        });
    }

    public LinearRing parseCoordinatesLinearRing(String coordinatesStr) {
        // 145.0010529126619,-10.68188894877146,0 142.5315075081651,-10.68756998592151,0 142.7881039791155,-11.08215241694377,0 142.8563635161634,-11.84494938862347,0 ...
        if (coordinatesStr == null || coordinatesStr.isEmpty()) {
            return null;
        }

        List<Coordinate> coordinateList = new ArrayList<Coordinate>();
        for (String coordinatePoint : coordinatesStr.split(" ")) {
            String[] coordinateValues = coordinatePoint.split(",");
            if (coordinateValues.length >= 2) {
                double lon = Double.parseDouble(coordinateValues[0]);
                double lat = Double.parseDouble(coordinateValues[1]);

                Coordinate coordinate = new Coordinate(lon, lat);
                coordinateList.add(coordinate);
            }
        }

        if (coordinateList.isEmpty()) {
            return null;
        }

        return WktUtils.GEOMETRY_FACTORY.createLinearRing(coordinateList.toArray(new Coordinate[0]));
    }

    // NOTE: The JTS library have no issue handling points, but when they go in and out of the index,
    //     points are merged together to form a large bounding box.
    //     It's better to handle points as small squares.
    public Polygon parsePos(String coordinateStr, int dimension, boolean lonlat) {
        if (coordinateStr == null || coordinateStr.isEmpty() || dimension < 2) {
            return null;
        }

        Polygon point = null;
        String[] coordinateValues = coordinateStr.split(" ");
        int coordinateValueLength = coordinateValues.length;
        if (coordinateValueLength > 0 && (coordinateValueLength % dimension) == 0) {
            double lon, lat;
            if (lonlat) {
                lon = Double.parseDouble(coordinateValues[0]);
                lat = Double.parseDouble(coordinateValues[1]);
            } else {
                lat = Double.parseDouble(coordinateValues[0]);
                lon = Double.parseDouble(coordinateValues[1]);
            }

            point = this.parseBoundingBox(lat, lon, lat, lon);
        }

        return point;
    }

    public LinearRing parsePosListLinearRing(String coordinatesStr, int dimension, boolean lonlat) {
        // 151.083984375 -24.521484375 153.80859375 -24.521484375 153.45703125 -20.830078125 147.12890625 -17.490234375 145.810546875 -13.798828125 144.4921875 -12.832031250000002 ...
        if (coordinatesStr == null || coordinatesStr.isEmpty() || dimension < 2) {
            return null;
        }

        List<Coordinate> coordinateList = new ArrayList<Coordinate>();
        String[] coordinateValues = coordinatesStr.split(" ");
        int coordinateValueLength = coordinateValues.length;
        if (coordinateValueLength > 0 && (coordinateValueLength % dimension) == 0) {
            for (int i=0; i<coordinateValueLength; i+=dimension) {
                double lon, lat;
                if (lonlat) {
                    lon = Double.parseDouble(coordinateValues[i]);
                    lat = Double.parseDouble(coordinateValues[i+1]);
                } else {
                    lat = Double.parseDouble(coordinateValues[i]);
                    lon = Double.parseDouble(coordinateValues[i+1]);
                }

                if (Double.isNaN(lat) || Double.isNaN(lon)) {
                    throw new IllegalArgumentException(
                        String.format("Invalid coordinate value: [%s, %s].", coordinateValues[i], coordinateValues[i+1]));
                }

                Coordinate coordinate = new Coordinate(lon, lat);
                coordinateList.add(coordinate);
            }
        }

        if (coordinateList.isEmpty()) {
            return null;
        }

        // Ensure the ring is closed
        if (!coordinateList.get(0).equals(coordinateList.get(coordinateList.size() - 1))) {
            coordinateList.add(new Coordinate(coordinateList.get(0)));  // Close the ring
        }

        return WktUtils.GEOMETRY_FACTORY.createLinearRing(coordinateList.toArray(new Coordinate[0]));
    }

}
