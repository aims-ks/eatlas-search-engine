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
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;
import org.locationtech.jts.io.WKTWriter;

import java.util.List;

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
        WKTReader reader = new WKTReader();

        // norm(): Normalise the geometry. Join intersecting polygons and remove duplicates.
        // buffer(0): Fix self intersecting polygons by removing parts.
        //   It's not perfect, but at least the resulting polygon should be valid.
        //   See: https://stackoverflow.com/questions/31473553/is-there-a-way-to-convert-a-self-intersecting-polygon-to-a-multipolygon-in-jts
        Geometry geometry = reader.read(wkt).norm().buffer(0);

        if (Orientation.isCCW(geometry.getCoordinates())) {
            return wkt;
        }

        return WktUtils.WKT_WRITER.write(geometry.reverse());
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

        return WktUtils.WKT_WRITER.write(multiPolygon);
    }
}
