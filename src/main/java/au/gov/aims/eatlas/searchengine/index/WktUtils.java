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
package au.gov.aims.eatlas.searchengine.index;

import org.locationtech.jts.algorithm.Orientation;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.util.GeometryTransformer;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;
import org.locationtech.jts.io.WKTWriter;

import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WktUtils {
    // Useful tools to work with WKT
    public static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();
    public static final WKTWriter WKT_WRITER = new WKTWriter(2);

    public static Polygon createPolygon(Coordinate[] shell) {
        return WktUtils.createPolygon(WktUtils.GEOMETRY_FACTORY.createLinearRing(shell));
    }
    public static Polygon createPolygon(LinearRing linearRing) {
        return WktUtils.GEOMETRY_FACTORY.createPolygon(fixLinearRing(linearRing));
    }

    public static Polygon createPolygon(LinearRing linearRing, List<LinearRing> holes) {
        if (holes == null || holes.isEmpty()) {
            return WktUtils.createPolygon(linearRing);
        }

        int nbHoles = holes.size();
        LinearRing[] fixedHoles = new LinearRing[nbHoles];
        for (int i=0; i<nbHoles; i++) {
            fixedHoles[i] = fixLinearRing(holes.get(i), true);
        }
        return WktUtils.GEOMETRY_FACTORY.createPolygon(fixLinearRing(linearRing), fixedHoles);
    }

    public static LinearRing fixLinearRing(LinearRing linearRing) {
        return WktUtils.fixLinearRing(linearRing, false);
    }
    public static LinearRing fixLinearRing(LinearRing linearRing, boolean isHole) {
        if (isHole) {
            // Holes needs to be clockwise
            if (!Orientation.isCCW(linearRing.getCoordinates())) {
                return linearRing;
            }
        } else {
            // Polygons needs to be counter-clockwise
            if (Orientation.isCCW(linearRing.getCoordinates())) {
                return linearRing;
            }
        }

        return linearRing.reverse();
    }

    public static String fixWkt(String wkt) throws ParseException {
        if (wkt == null || wkt.isEmpty()) {
            return null;
        }
        wkt = wkt.trim();

        if (wkt.startsWith("BBOX")) {
            // JTS doesn't support BBOX.
            return wkt;
        }

        Geometry geometry = WktUtils.wktToGeometry(wkt);
        if (geometry == null) {
            return null;
        }

        if (Orientation.isCCW(geometry.getCoordinates())) {
            return wkt;
        }

        return WktUtils.WKT_WRITER.write(geometry.reverse());
    }

    public static Geometry wktToGeometry(String wkt) throws ParseException {
        if (wkt == null || wkt.isEmpty()) {
            return null;
        }
        wkt = wkt.trim();

        // JTS doesn't support BBOX.
        if (wkt.startsWith("BBOX")) {
            return WktUtils.bboxToPolygon(wkt);
        }

        WKTReader reader = new WKTReader();

        // norm(): Normalise the geometry. Join intersecting polygons and remove duplicates.
        // buffer(0): Fix self intersecting polygons by removing parts.
        //   It's not perfect, but at least the resulting polygon should be valid.
        //   See: https://stackoverflow.com/questions/31473553/is-there-a-way-to-convert-a-self-intersecting-polygon-to-a-multipolygon-in-jts
        return reader.read(wkt).norm().buffer(0);
    }

    public static Polygon bboxToPolygon(String wkt) {
        LinearRing bboxLinearRing = WktUtils.bboxToLinearRing(wkt);
        return bboxLinearRing == null ? null :
            WktUtils.GEOMETRY_FACTORY.createPolygon(bboxLinearRing);
    }

    public static LinearRing bboxToLinearRing(String wkt) {
        if (wkt == null || wkt.isEmpty()) {
            return null;
        }
        wkt = wkt.trim();

        if (!wkt.startsWith("BBOX")) {
            return null;
        }

        Pattern pattern = Pattern.compile("BBOX\\(([\\-\\d.]+),([\\-\\d.]+),([\\-\\d.]+),([\\-\\d.]+)\\)");
        Matcher matcher = pattern.matcher(
            // Remove all whitespaces
            wkt.replaceAll("\\s+","")
        );

        if (matcher.matches()) {
            double west = Double.parseDouble(matcher.group(1));
            double east = Double.parseDouble(matcher.group(2));
            double north = Double.parseDouble(matcher.group(3));
            double south = Double.parseDouble(matcher.group(4));

            Coordinate[] coordinates = new Coordinate[] {
                new Coordinate(north, west),
                new Coordinate(south, west),
                new Coordinate(south, east),
                new Coordinate(north, east),
                new Coordinate(north, west)
            };

            return WktUtils.GEOMETRY_FACTORY.createLinearRing(coordinates);
        }

        return null;
    }

    private static Random rand = null;
    public static Geometry jiggle(Geometry geometry) {
        if (rand == null) {
            rand = new Random(8541266);
        }

        GeometryTransformer transformer = new GeometryTransformer() {
            @Override
            protected CoordinateSequence transformCoordinates(CoordinateSequence coords, Geometry parent) {
                int nbCoord = coords.size();
                for (int i = 1; i<nbCoord-1; i++) {
                    // Docs says:
                    //   Returns (possibly a copy of) the i'th coordinate in this sequence.
                    // It's not returning a copy, so we are good. But that could be an issue if that was real code...
                    Coordinate coord = coords.getCoordinate(i);

                    // n = [-999, 999]
                    int nx = rand.nextInt(1998) - 999;
                    int ny = rand.nextInt(1998) - 999;
                    // 0.00001 degree =~ 1.11m
                    // jiggle = [-0.00000999, 0.00000999 degree] =~ [-1.1m, 1.1m]
                    double jiggleX = nx * 0.00000001;
                    double jiggleY = ny * 0.00000001;

                    double newX = coord.getX() + jiggleX;
                    if (newX > 180) { newX = 180; }
                    if (newX < -180) { newX = -180; }

                    double newY = coord.getY() + jiggleY;
                    if (newY > 90) { newY = 90; }
                    if (newY < -90) { newY = -90; }

                    coord.setX(newX);
                    coord.setY(newY);
                }

                return coords;
            }
        };
        return transformer.transform(geometry).norm().buffer(0);
    }

    public static String polygonsToWKT(List<Polygon> polygons) {
        if (polygons == null || polygons.isEmpty()) {
            return null;
        }

        Geometry multiPolygon;
        if (polygons.size() == 1) {
            multiPolygon = polygons.get(0);
        } else {
            multiPolygon = WktUtils.GEOMETRY_FACTORY.createMultiPolygon(polygons.toArray(new Polygon[0]));
        }

        return WktUtils.WKT_WRITER.write(multiPolygon.norm().buffer(0));
    }
}
