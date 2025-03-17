package au.gov.aims.eatlas.searchengine.index;

import au.gov.aims.eatlas.searchengine.MockHttpClient;
import au.gov.aims.eatlas.searchengine.admin.SearchEngineConfig;
import au.gov.aims.eatlas.searchengine.logger.ConsoleLogger;
import au.gov.aims.eatlas.searchengine.logger.AbstractLogger;
import au.gov.aims.eatlas.searchengine.entity.AtlasMapperLayer;
import au.gov.aims.eatlas.searchengine.entity.DrupalMedia;
import au.gov.aims.eatlas.searchengine.entity.GeoNetworkRecord;
import au.gov.aims.eatlas.searchengine.rest.Search;
import au.gov.aims.eatlas.searchengine.search.IndexSummary;
import au.gov.aims.eatlas.searchengine.search.SearchResults;
import au.gov.aims.eatlas.searchengine.search.Summary;
import co.elastic.clients.elasticsearch._types.HealthStatus;
import co.elastic.clients.elasticsearch._types.SortOptions;
import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.geojson.GeoJsonWriter;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SearchWktTest extends IndexerTestBase {

    // Rough polygons used for the index / search.
    // Created using this online tool:
    //   https://clydedacruz.github.io/openstreetmap-wkt-playground/

    // Australia
    private static final String WKT_AUSTRALIA = "POLYGON((131.8 -7.7,112.5 -21.3,114.3 -36.9,147 -44.6,156.8 -28,143.1 -9.4,131.8 -7.7))";
    // Queensland
    private static final String WKT_QUEENSLAND = "POLYGON((137.9 -16.4,137.9 -26,141.1 -26,141.1 -29,154.7 -29.2,149.9 -20.8,142.4 -10.3,140.7 -16.6,137.9 -16.4))";
    private static final String WKT_TOWNSVILLE = "POLYGON((146.7 -19.4,146.6 -19.3,146.7 -19.2,146.8 -19.1,146.9 -19.1,146.9 -19.3,146.8 -19.4,146.7 -19.4))"; // Includes magnetic island
    private static final String WKT_MAGNETIC_ISLAND = "POLYGON((146.77 -19.12,146.86 -19.09,146.89 -19.11,146.85 -19.19,146.79 -19.16,146.77 -19.12))";
    // Western Australia
    private static final String WKT_WESTERN_AUSTRALIA = "POLYGON((129 -13.8,125.4 -13.2,120.4 -18.9,112.1 -22.3,114.3 -35,119.5 -35.9,129 -32.3,129 -13.8))";
    private static final String WKT_PILBARA = "POLYGON((115 -23.5,129 -23.5,129 -21.5,126.4 -21.5,126.4 -19.7,119 -19.8,116.6 -20.5,115 -21.6,115 -23.5))";

    // Outside Australia
    private static final String WKT_NEW_ZEALAND = "POLYGON((171.6 -33.9,172.9 -39.5,165.2 -46,168.1 -48.3,180.3 -38,174 -33.9,171.6 -33.9))";

    // Layers BBOX
    // GeoServer doesn't return fancy WKT.
    // BBOX are saved as: [West, South, East, North]
    private static final float[] LAYER_BBOX_WORLD = new float[]{ -180, -90, 180, 90 };
    private static final float[] LAYER_BBOX_AUSTRALIA = new float[]{ 112, -44, 154, -10 };
    private static final float[] LAYER_BBOX_QUEENSLAND = new float[]{ 138, -29, 154, -10 };

    // Used for search
    private static final String BBOX_WORLD = "POLYGON((-180 90,180 90,180 -90,-180 -90,-180 90))";
    private static final String BBOX_WESTERN_AUSTRALIA = "POLYGON((112 -36,129 -36,129 -12,112 -12,112 -36))";
    private static final String BBOX_MAGNETIC_ISLAND = "POLYGON((146.76 -19.07,146.76 -19.21,146.93 -19.21,146.93 -19.07,146.76 -19.07))";


    @Test
    public void testSearchWkt() throws Exception {
        SearchEngineConfig config = SearchEngineConfig.getInstance();
        AbstractLogger logger = ConsoleLogger.getInstance();
        MockHttpClient mockHttpClient = MockHttpClient.getInstance();
        try (MockSearchClient searchClient = this.createMockSearchClient()) {
            Assertions.assertEquals(HealthStatus.Green, searchClient.getHealthStatus(), "The Elastic Search engine health status is not Green before starting the test.");

            String metadataRecordIndex = "junit_records";
            String layersIndex = "junit_layers";
            String imagesIndex = "junit_images";

            // Index mix content
            this.indexMetadataRecords(metadataRecordIndex, config, searchClient, mockHttpClient, logger);
            this.indexLayers(layersIndex, config, searchClient, mockHttpClient, logger);
            this.indexImages(imagesIndex, config, searchClient, mockHttpClient, logger);


            SearchResults results = null;

            // Search - Try to find everything
            try {
                String q = ""; // Get every single layers
                Integer start = 0;
                Integer hits = 50; // Number of result per page. There is only 11 documents in the index
                String wkt = null; // No geographic filtering, for now
                List<String> idx = List.of(metadataRecordIndex, layersIndex, imagesIndex);
                List<SortOptions> sortOptionsList = new ArrayList<>();

                results = Search.paginationSearch(searchClient, q, start, hits, wkt, sortOptionsList, idx, null, logger);

                Summary searchSummary = results.getSummary();

                // Check indexes in the summary.
                Map<String, IndexSummary> indexSummaryMap = searchSummary.getIndexSummaries();
                Assertions.assertNotNull(indexSummaryMap,
                        "Index summary is null.");
                Assertions.assertEquals(3, indexSummaryMap.size(),
                        "Wrong number if index summary.");
                Assertions.assertTrue(indexSummaryMap.containsKey(metadataRecordIndex),
                        String.format("Missing index from the search summary: %s", metadataRecordIndex));
                Assertions.assertTrue(indexSummaryMap.containsKey(layersIndex),
                        String.format("Missing index from the search summary: %s", layersIndex));
                Assertions.assertTrue(indexSummaryMap.containsKey(imagesIndex),
                        String.format("Missing index from the search summary: %s", imagesIndex));

                IndexSummary metadataRecordSummary = searchSummary.getIndexSummary(metadataRecordIndex);
                Assertions.assertEquals(4, metadataRecordSummary.getHits(),
                        String.format("Wrong number of search result in the index summary for: %s", metadataRecordIndex));

                IndexSummary layersIndexSummary = searchSummary.getIndexSummary(layersIndex);
                Assertions.assertEquals(3, layersIndexSummary.getHits(),
                        String.format("Wrong number of search result in the index summary for: %s", layersIndex));

                IndexSummary imagesIndexSummary = searchSummary.getIndexSummary(imagesIndex);
                Assertions.assertEquals(4, imagesIndexSummary.getHits(),
                        String.format("Wrong number of search result in the index summary for: %s", imagesIndex));

                Assertions.assertEquals(11, searchSummary.getHits(),
                        "Wrong total number of search result in the index summary.");

            } catch(Exception ex) {
                Assertions.fail("Exception thrown while testing the Search API.", ex);
            }



            // Search with WKT

            // The world = Should return every records
            try {
                String q = ""; // Get every single layers
                Integer start = 0;
                Integer hits = 50; // Number of result per page. There is only 11 documents in the index
                String wkt = BBOX_WORLD; // No geographic filtering, for now
                List<String> idx = List.of(metadataRecordIndex, layersIndex, imagesIndex);
                List<SortOptions> sortOptionsList = new ArrayList<>();

                results = Search.paginationSearch(searchClient, q, start, hits, wkt, sortOptionsList, idx, null, logger);

                Summary searchSummary = results.getSummary();

                // Check indexes in the summary.
                Map<String, IndexSummary> indexSummaryMap = searchSummary.getIndexSummaries();
                Assertions.assertNotNull(indexSummaryMap,
                        "Index summary is null.");
                Assertions.assertEquals(3, indexSummaryMap.size(),
                        "Wrong number if index summary.");
                Assertions.assertTrue(indexSummaryMap.containsKey(metadataRecordIndex),
                        String.format("Missing index from the search summary: %s", metadataRecordIndex));
                Assertions.assertTrue(indexSummaryMap.containsKey(layersIndex),
                        String.format("Missing index from the search summary: %s", layersIndex));
                Assertions.assertTrue(indexSummaryMap.containsKey(imagesIndex),
                        String.format("Missing index from the search summary: %s", imagesIndex));

                IndexSummary metadataRecordSummary = searchSummary.getIndexSummary(metadataRecordIndex);
                Assertions.assertEquals(4, metadataRecordSummary.getHits(),
                        String.format("Wrong number of search result in the index summary for: %s", metadataRecordIndex));

                IndexSummary layersIndexSummary = searchSummary.getIndexSummary(layersIndex);
                Assertions.assertEquals(3, layersIndexSummary.getHits(),
                        String.format("Wrong number of search result in the index summary for: %s", layersIndex));

                IndexSummary imagesIndexSummary = searchSummary.getIndexSummary(imagesIndex);
                Assertions.assertEquals(4, imagesIndexSummary.getHits(),
                        String.format("Wrong number of search result in the index summary for: %s", imagesIndex));

                Assertions.assertEquals(11, searchSummary.getHits(),
                        "Wrong total number of search result in the index summary.");

            } catch(Exception ex) {
                Assertions.fail("Exception thrown while testing the Search API.", ex);
            }



            // Western Australia
            try {
                String q = ""; // Get every single layers
                Integer start = 0;
                Integer hits = 50; // Number of result per page. There is only 11 documents in the index
                String wkt = BBOX_WESTERN_AUSTRALIA; // No geographic filtering, for now
                List<String> idx = List.of(metadataRecordIndex, layersIndex, imagesIndex);
                List<SortOptions> sortOptionsList = new ArrayList<>();

                results = Search.paginationSearch(searchClient, q, start, hits, wkt, sortOptionsList, idx, null, logger);

                Summary searchSummary = results.getSummary();

                // Check indexes in the summary.
                Map<String, IndexSummary> indexSummaryMap = searchSummary.getIndexSummaries();
                Assertions.assertNotNull(indexSummaryMap,
                        "Index summary is null.");
                Assertions.assertEquals(3, indexSummaryMap.size(),
                        "Wrong number if index summary.");
                Assertions.assertTrue(indexSummaryMap.containsKey(metadataRecordIndex),
                        String.format("Missing index from the search summary: %s", metadataRecordIndex));
                Assertions.assertTrue(indexSummaryMap.containsKey(layersIndex),
                        String.format("Missing index from the search summary: %s", layersIndex));
                Assertions.assertTrue(indexSummaryMap.containsKey(imagesIndex),
                        String.format("Missing index from the search summary: %s", imagesIndex));

                IndexSummary metadataRecordSummary = searchSummary.getIndexSummary(metadataRecordIndex);
                Assertions.assertEquals(2, metadataRecordSummary.getHits(),
                        String.format("Wrong number of search result in the index summary for: %s", metadataRecordIndex));

                IndexSummary layersIndexSummary = searchSummary.getIndexSummary(layersIndex);
                Assertions.assertEquals(2, layersIndexSummary.getHits(),
                        String.format("Wrong number of search result in the index summary for: %s", layersIndex));

                IndexSummary imagesIndexSummary = searchSummary.getIndexSummary(imagesIndex);
                Assertions.assertEquals(2, imagesIndexSummary.getHits(),
                        String.format("Wrong number of search result in the index summary for: %s", imagesIndex));

                Assertions.assertEquals(6, searchSummary.getHits(),
                        "Wrong total number of search result in the index summary.");

            } catch(Exception ex) {
                Assertions.fail("Exception thrown while testing the Search API.", ex);
            }



            // Magnetic Island
            try {
                String q = ""; // Get every single layers
                Integer start = 0;
                Integer hits = 50; // Number of result per page. There is only 11 documents in the index
                String wkt = BBOX_MAGNETIC_ISLAND; // No geographic filtering, for now
                List<String> idx = List.of(metadataRecordIndex, layersIndex, imagesIndex);
                List<SortOptions> sortOptionsList = new ArrayList<>();

                results = Search.paginationSearch(searchClient, q, start, hits, wkt, sortOptionsList, idx, null, logger);

                Summary searchSummary = results.getSummary();

                // Check indexes in the summary.
                Map<String, IndexSummary> indexSummaryMap = searchSummary.getIndexSummaries();
                Assertions.assertNotNull(indexSummaryMap,
                        "Index summary is null.");
                Assertions.assertEquals(3, indexSummaryMap.size(),
                        "Wrong number if index summary.");
                Assertions.assertTrue(indexSummaryMap.containsKey(metadataRecordIndex),
                        String.format("Missing index from the search summary: %s", metadataRecordIndex));
                Assertions.assertTrue(indexSummaryMap.containsKey(layersIndex),
                        String.format("Missing index from the search summary: %s", layersIndex));
                Assertions.assertTrue(indexSummaryMap.containsKey(imagesIndex),
                        String.format("Missing index from the search summary: %s", imagesIndex));

                IndexSummary metadataRecordSummary = searchSummary.getIndexSummary(metadataRecordIndex);
                Assertions.assertEquals(4, metadataRecordSummary.getHits(),
                        String.format("Wrong number of search result in the index summary for: %s", metadataRecordIndex));

                IndexSummary layersIndexSummary = searchSummary.getIndexSummary(layersIndex);
                Assertions.assertEquals(3, layersIndexSummary.getHits(),
                        String.format("Wrong number of search result in the index summary for: %s", layersIndex));

                IndexSummary imagesIndexSummary = searchSummary.getIndexSummary(imagesIndex);
                Assertions.assertEquals(1, imagesIndexSummary.getHits(),
                        String.format("Wrong number of search result in the index summary for: %s", imagesIndex));

                Assertions.assertEquals(8, searchSummary.getHits(),
                        "Wrong total number of search result in the index summary.");

            } catch(Exception ex) {
                Assertions.fail("Exception thrown while testing the Search API.", ex);
            }



            try {
                String q = "coral"; // Get every single layers
                Integer start = 0;
                Integer hits = 50; // Number of result per page. There is only 11 documents in the index
                String wkt = BBOX_MAGNETIC_ISLAND; // No geographic filtering, for now
                List<String> idx = List.of(metadataRecordIndex, layersIndex, imagesIndex);
                List<SortOptions> sortOptionsList = new ArrayList<>();

                results = Search.paginationSearch(searchClient, q, start, hits, wkt, sortOptionsList, idx, null, logger);

                Summary searchSummary = results.getSummary();

                // Check indexes in the summary.
                Map<String, IndexSummary> indexSummaryMap = searchSummary.getIndexSummaries();
                Assertions.assertNotNull(indexSummaryMap,
                        "Index summary is null.");
                Assertions.assertEquals(2, indexSummaryMap.size(),
                        "Wrong number if index summary.");
                Assertions.assertTrue(indexSummaryMap.containsKey(metadataRecordIndex),
                        String.format("Missing index from the search summary: %s", metadataRecordIndex));
                Assertions.assertFalse(indexSummaryMap.containsKey(layersIndex),
                        String.format("Unexpected index found in the search summary: %s", layersIndex));
                Assertions.assertTrue(indexSummaryMap.containsKey(imagesIndex),
                        String.format("Missing index from the search summary: %s", imagesIndex));

                IndexSummary metadataRecordSummary = searchSummary.getIndexSummary(metadataRecordIndex);
                Assertions.assertEquals(2, metadataRecordSummary.getHits(),
                        String.format("Wrong number of search result in the index summary for: %s", metadataRecordIndex));

                IndexSummary imagesIndexSummary = searchSummary.getIndexSummary(imagesIndex);
                Assertions.assertEquals(1, imagesIndexSummary.getHits(),
                        String.format("Wrong number of search result in the index summary for: %s", imagesIndex));

                Assertions.assertEquals(3, searchSummary.getHits(),
                        "Wrong total number of search result in the index summary.");

            } catch(Exception ex) {
                Assertions.fail("Exception thrown while testing the Search API.", ex);
            }



            Assertions.assertEquals(HealthStatus.Green, searchClient.getHealthStatus(), "The Elastic Search engine health status is not Green after the test.");
        }
    }





    private void indexMetadataRecords(String index, SearchEngineConfig config, MockSearchClient searchClient, MockHttpClient mockHttpClient, AbstractLogger logger) throws ParseException, IOException {
        searchClient.createIndex(index);
        GeoNetworkIndexer indexer = new GeoNetworkIndexer(mockHttpClient, index, index,
            "http://eatlas-geonetwork/geonetwork",
            "http://domain.com/geonetwork",
            "3.0");

        // Add the indexer to the SearchEngineConfig, so the EntityDeserializer (Jackson)
        //   can serialise / deserialise the Entity.
        config.addIndexer(indexer);

        // Australia
        GeoNetworkRecord australiaRecord = new GeoNetworkRecord(indexer, "00000000-0000-0000-0000-000000000000", "iso19115-3.2018", "3.0");
        australiaRecord.setTitle("Australia record");
        australiaRecord.setDocument("Record that covers whole of Australia coral.");
        australiaRecord.setWktAndAttributes(WKT_AUSTRALIA);
        indexer.indexEntity(searchClient, australiaRecord, logger);

        // Queensland
        GeoNetworkRecord qldRecord = new GeoNetworkRecord(indexer, "00000000-0000-0000-0000-000000000001", "iso19115-3.2018", "3.0");
        qldRecord.setTitle("Queensland record");
        qldRecord.setDocument("Record that covers whole of Queensland.");
        qldRecord.setWktAndAttributes(WKT_QUEENSLAND);
        indexer.indexEntity(searchClient, qldRecord, logger);

        // Townsville
        GeoNetworkRecord townsvilleRecord = new GeoNetworkRecord(indexer, "00000000-0000-0000-0000-000000000002", "iso19115-3.2018", "3.0");
        townsvilleRecord.setTitle("Townsville record");
        townsvilleRecord.setDocument("Record of Townsville.");
        townsvilleRecord.setWktAndAttributes(WKT_TOWNSVILLE);
        indexer.indexEntity(searchClient, townsvilleRecord, logger);

        // No WKT - Expected to default to whole world
        GeoNetworkRecord unknownRecord = new GeoNetworkRecord(indexer, "00000000-0000-0000-0000-00000000000A", "iso19115-3.2018", "3.0");
        unknownRecord.setTitle("Unknown location record");
        unknownRecord.setDocument("Record that doesn't provide location coral.");
        unknownRecord.setWktAndAttributes(BBOX_WORLD);
        indexer.indexEntity(searchClient, unknownRecord, logger);

        // Wait for ElasticSearch to finish its indexation
        searchClient.refresh(index);
    }

    private void indexLayers(String index, SearchEngineConfig config, MockSearchClient searchClient, MockHttpClient mockHttpClient, AbstractLogger logger) throws IOException {
        searchClient.createIndex(index);
        String clientUrl = "http://domain.com/atlasmapper";
        String mainConfigPathStr = "searchWkt/atlasmapperFiles/main.json";

        AtlasMapperIndexer indexer = new AtlasMapperIndexer(mockHttpClient, index, index, clientUrl, "1.0", "http://domain.com/geoserver");
        // Add the indexer to the SearchEngineConfig, so the EntityDeserializer (Jackson)
        //   can serialise / deserialise the Entity.
        config.addIndexer(indexer);

        JSONObject jsonMainConfig = null;
        try (InputStream mainInputStream = SearchWktTest.class.getClassLoader().getResourceAsStream(mainConfigPathStr)) {
            Assertions.assertNotNull(mainInputStream, String.format("Can not find the AtlasMapper main config: %s", mainConfigPathStr));
            String jsonText = IOUtils.toString(mainInputStream, StandardCharsets.UTF_8);
            jsonMainConfig = new JSONObject(jsonText);
        }

        // World (base layer)
        String worldBaseLayerId = "ea_base_layer";
        JSONObject jsonWorldBaseLayer = this.createJsonLayer(
                worldBaseLayerId, "Base layer", "Base layer that covers the whole world", LAYER_BBOX_WORLD);
        AtlasMapperLayer worldBaseLayer = new AtlasMapperLayer(index, clientUrl, worldBaseLayerId, jsonWorldBaseLayer, jsonMainConfig, logger);
        indexer.indexEntity(searchClient, worldBaseLayer, logger);

        // Australia
        String australiaLayerId = "ea_australia";
        JSONObject jsonAustraliaLayer = this.createJsonLayer(
                australiaLayerId, "Australia", "Layer of Australia", LAYER_BBOX_AUSTRALIA);
        AtlasMapperLayer australiaLayer = new AtlasMapperLayer(index, clientUrl, australiaLayerId, jsonAustraliaLayer, jsonMainConfig, logger);
        indexer.indexEntity(searchClient, australiaLayer, logger);

        // Queensland
        String queenslandLayerId = "ea_queensland";
        JSONObject jsonQueenslandLayer = this.createJsonLayer(
                queenslandLayerId, "Queensland", "Layer of Queensland", LAYER_BBOX_QUEENSLAND);
        AtlasMapperLayer queenslandLayer = new AtlasMapperLayer(index, clientUrl, queenslandLayerId, jsonQueenslandLayer, jsonMainConfig, logger);
        indexer.indexEntity(searchClient, queenslandLayer, logger);

        // Wait for ElasticSearch to finish its indexation
        searchClient.refresh(index);
    }

    private JSONObject createJsonLayer(String layerId, String title, String description, float[] bbox) throws IOException {
        JSONArray jsonBbox = new JSONArray();
        for (float coord : bbox) {
            jsonBbox.put(coord);
        }

        String layerPathStr = "searchWkt/atlasmapperFiles/layer.json";
        JSONObject jsonLayerResponse = null;
        try (InputStream layerInputStream = SearchWktTest.class.getClassLoader().getResourceAsStream(layerPathStr)) {
            Assertions.assertNotNull(layerInputStream, String.format("Can not find the Layer response file: %s", layerPathStr));
            String jsonText = IOUtils.toString(layerInputStream, StandardCharsets.UTF_8);
            jsonLayerResponse = new JSONObject(jsonText);

            jsonLayerResponse.put("layerName", layerId);
            jsonLayerResponse.put("title", title);
            jsonLayerResponse.put("description", description);
            jsonLayerResponse.put("layerBoundingBox", jsonBbox);
        }

        return jsonLayerResponse;
    }

    private void indexImages(String index, SearchEngineConfig config, MockSearchClient searchClient, MockHttpClient mockHttpClient, AbstractLogger logger) throws IOException, ParseException {
        searchClient.createIndex(index);
        DrupalMediaIndexer indexer = new DrupalMediaIndexer(mockHttpClient, index, index, "http://domain.com", "http://domain.com", "11.0", "image", "field_preview", "field_title", "field_description", "field_geojson");

        // Add the indexer to the SearchEngineConfig, so the EntityDeserializer (Jackson)
        //   can serialise / deserialise the Entity.
        config.addIndexer(indexer);

        // Magnetic island
        JSONObject jsonMaggieImage = this.createJsonImage(
            "F0000000-0000-0000-0000-000000000000", "magnetic_island.jpg",
            "Magnetic island", "Image of Magnetic Island coral", WKT_MAGNETIC_ISLAND);
        DrupalMedia maggieImage = indexer.createDrupalEntity(jsonMaggieImage, null, logger); // new DrupalMedia(index, jsonMaggieImage, logger);
        indexer.parseJsonDrupalEntity(searchClient, jsonMaggieImage, null, maggieImage, logger);
        indexer.indexEntity(searchClient, maggieImage, logger);
        System.out.println(maggieImage.toString());

        // New-Zealand
        JSONObject jsonNewZealandImage = this.createJsonImage(
            "F0000000-0000-0000-0000-000000000001", "new-zealand.jpg",
            "New-Zealand", "Image of New-Zealand coral", WKT_NEW_ZEALAND);
        DrupalMedia newZealandImage = indexer.createDrupalEntity(jsonNewZealandImage, null, logger);
        indexer.parseJsonDrupalEntity(searchClient, jsonNewZealandImage, null, newZealandImage, logger);
        indexer.indexEntity(searchClient, newZealandImage, logger);

        // Western Australia
        JSONObject jsonWAImage = this.createJsonImage(
            "F0000000-0000-0000-0000-000000000002", "western_australia.jpg",
            "Western Australia", "Image of Western Australia", WKT_WESTERN_AUSTRALIA);
        DrupalMedia waImage = indexer.createDrupalEntity(jsonWAImage, null, logger);
        indexer.parseJsonDrupalEntity(searchClient, jsonWAImage, null, waImage, logger);
        indexer.indexEntity(searchClient, waImage, logger);

        // Pilbara
        JSONObject jsonPilbaraImage = this.createJsonImage(
            "F0000000-0000-0000-0000-000000000003", "Pilbara.jpg",
            "Pilbara", "Image of Pilbara coral", WKT_PILBARA);
        DrupalMedia pilbaraImage = indexer.createDrupalEntity(jsonPilbaraImage, null, logger);
        indexer.parseJsonDrupalEntity(searchClient, jsonPilbaraImage, null, pilbaraImage, logger);
        indexer.indexEntity(searchClient, pilbaraImage, logger);

        // Wait for ElasticSearch to finish its indexation
        searchClient.refresh(index);
    }

    private JSONObject createJsonImage(String imageId, String filename, String title, String description, String wkt) throws IOException, ParseException {
        JSONObject geoJson = this.wktToGeoJson(wkt);

        String imageResponsePathStr = "searchWkt/drupalMediaFiles/image.json";
        JSONObject jsonImageResponse = null;
        try (InputStream imageInputStream = SearchWktTest.class.getClassLoader().getResourceAsStream(imageResponsePathStr)) {
            Assertions.assertNotNull(imageInputStream, String.format("Can not find the Drupal image response file: %s", imageResponsePathStr));
            String jsonText = IOUtils.toString(imageInputStream, StandardCharsets.UTF_8);
            jsonImageResponse = new JSONObject(jsonText);

            jsonImageResponse.put("id", imageId);
            JSONObject jsonAttributes = jsonImageResponse.optJSONObject("attributes");
            jsonAttributes.put("name", filename);
            JSONObject jsonDescription = jsonAttributes.optJSONObject("field_description");
            jsonDescription.put("value", description);
            jsonDescription.put("processed", description);

            jsonAttributes.put("field_geojson", geoJson.toString());
            jsonAttributes.put("field_title", title);
        }

        return jsonImageResponse;
    }

    private JSONObject wktToGeoJson(String wkt) throws ParseException {
        Geometry geometry = WktUtils.wktToGeometry(wkt);
        GeoJsonWriter writer = new GeoJsonWriter();
        JSONObject geoJson = new JSONObject(writer.write(geometry));
        geoJson.remove("crs");
        return new JSONObject()
                .put("type", "Feature")
                .put("geometry", geoJson);
    }
}
