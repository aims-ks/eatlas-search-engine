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
package au.gov.aims.eatlas.searchengine.entity;

import au.gov.aims.eatlas.searchengine.index.GeoNetworkIndexer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

public class GeoShapeTest {

    /**
     * Some WKT may cause issue when parsed due to edge cases.
     * This test make sure those edge cases are managed properly.
     * @throws Exception Thrown when the JTS library can't parse the WKT string,
     *   or Elastic Search can't index the record.
     */
    @Test
    public void testGeoShapes() throws Exception {
        // Use linked hashmap, just to make it easier to debug (the entries in the map are in insert order)
        Map<String, Double> expectedAreaMap = new LinkedHashMap<>();

        // Original polygon found in a GeoNetwork record.
        // 2                      3
        //   *------------------*
        //   |                  |
        //   *--------*---------*
        // 1          5           4
        expectedAreaMap.put(
            "POLYGON ((-175.4736328125 -31.245117187500004, -171.2548828125 36.9580078125, 171.8701171875 39.0673828125, 175.3857421875 -30.5419921875, -32.0361328125 -34.0576171875, -175.4736328125 -31.245117187500004))",
            24453.25927734375
        );

        // Variations on the original polygon
        expectedAreaMap.put(
            "POLYGON ((-175 -31, -171 36, 171 39, 175 -30, -32 -34, -175 -31))",
            24124.5
        );
        expectedAreaMap.put(
            "POLYGON ((-175 -31, -171 36, 171 39, 175 -31, -32 -34, -175 -31))",
            24226.0
        );
        // Thin variations
        expectedAreaMap.put(
            "POLYGON ((-175 -2, -175 0, 175 0, 175 -1, -32 -5, -175 -2))",
            1121.5
        );
        // Polygon
        // 2                      3
        //   *------------------*
        //   |                /
        //   *--------------*
        // 1                 4
        expectedAreaMap.put(
            "POLYGON ((-150 -1, -150 0, 175 0, -32 -4, -150 -1))",
            709.0
        );
        expectedAreaMap.put(
            "POLYGON ((-146 -1, -146 0, 175 0, -32 -4, -146 -1))",
            699.0
        );
        expectedAreaMap.put(
            "POLYGON ((-33 -1, -33 0, 175 0, -32 -1, -33 -1))",
            104.5
        );
        expectedAreaMap.put(
            "POLYGON ((-147 -1, -147 0, 174 0, -32 -4, -147 -1))",
            699.5
        );
        expectedAreaMap.put(
            "POLYGON ((-147 -1, -147 0, 175 0, -32 -4, -147 -1))",
            701.5
        );
        expectedAreaMap.put(
            "POLYGON ((-147 -1, -147 0, 175 0, -32 -1, -147 -1))",
            218.5
        );

        // Large triangle
        expectedAreaMap.put(
            "POLYGON ((-91 9, 0 80, 90 10, -90 9, -91 9))",
            6380.5
        );

        String index = "unit_test";
        GeoNetworkIndexer indexer = new GeoNetworkIndexer(null, index, index, "http://eatlas-geonetwork/geonetwork", "https://eatlas.org.au/geonetwork", "3.0");
        GeoNetworkRecord record = new GeoNetworkRecord(indexer, "00000000-0000-0000-0000-000000000000", "iso19115-3.2018", "3.0");
        record.setTitle("Dummy record");
        record.setDocument("Dummy record content.");

        int testNumber = 0;
        for (Map.Entry<String, Double> expectedAreaEntry : expectedAreaMap.entrySet()) {
            testNumber++;
            String wkt = expectedAreaEntry.getKey();
            Double expectedArea = expectedAreaEntry.getValue();
            record.setWktAndAttributes(wkt);

            Assertions.assertEquals(expectedArea, record.getWktArea(), 0.001,
                    String.format("Wrong WKT area [test No.%d].", testNumber));
        }
    }

    /**
     * Test parsing a very large WKT: 11.8 MB
     * @throws Exception Throws exception if something goes wrong in the parsing of the very large WKT.
     */
    @Test
    public void testNaturalEarthDataWkt() throws Exception {
        String index = "unit_test";
        GeoNetworkIndexer indexer = new GeoNetworkIndexer(null, index, index, "http://eatlas-geonetwork/geonetwork", "https://eatlas.org.au/geonetwork", "3.0");
        GeoNetworkRecord record = new GeoNetworkRecord(indexer, "00000000-0000-0000-0000-000000000000", "iso19115-3.2018", "3.0");
        record.setTitle("Dummy record - Natural Earth Data");
        record.setDocument("Dummy record content.");

        String bigWkt = new String(Files.readAllBytes(Paths.get(GeoNetworkRecord.class.getClassLoader().getResource("ne_10m.wkt").toURI())));
        record.setWktAndAttributes(bigWkt);

        Assertions.assertEquals(21170.37977367463, record.getWktArea(), 0.001,
                "Wrong WKT area [Natural Earth Data].");
    }

    /**
     * Test parsing a large WKT: 730 kB
     * @throws Exception Throws exception if something goes wrong in the parsing of the very large WKT.
     */
    @Test
    public void testCAPAD() throws Exception {
        String index = "unit_test";
        GeoNetworkIndexer indexer = new GeoNetworkIndexer(null, index, index, "http://eatlas-geonetwork/geonetwork", "https://eatlas.org.au/geonetwork", "3.0");
        GeoNetworkRecord record = new GeoNetworkRecord(indexer, "00000000-0000-0000-0000-000000000000", "iso19115-3.2018", "3.0");
        record.setTitle("Dummy CAPAD record");
        record.setDocument("Dummy CAPAD record content.");

        String bigWkt = new String(Files.readAllBytes(Paths.get(GeoNetworkRecord.class.getClassLoader().getResource("capad_2020.wkt").toURI())));
        record.setWktAndAttributes(bigWkt);

        Assertions.assertEquals(519.8734815557457, record.getWktArea(), 0.001,
                "Wrong WKT area [CAPAD].");
    }
}
