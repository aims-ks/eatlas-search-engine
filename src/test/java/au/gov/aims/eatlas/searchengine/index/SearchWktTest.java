package au.gov.aims.eatlas.searchengine.index;

import au.gov.aims.eatlas.searchengine.MockHttpClient;
import au.gov.aims.eatlas.searchengine.admin.SearchEngineConfig;
import au.gov.aims.eatlas.searchengine.admin.rest.Messages;
import au.gov.aims.eatlas.searchengine.entity.AtlasMapperLayer;
import au.gov.aims.eatlas.searchengine.entity.GeoNetworkRecord;
import co.elastic.clients.elasticsearch._types.HealthStatus;
import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.io.ParseException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class SearchWktTest extends IndexerTestBase {

    // Rough polygons used for the index / search.
    // Created using this online tool:
    //   https://clydedacruz.github.io/openstreetmap-wkt-playground/

    // Australia
    private static final String WKT_AUSTRALIA = "POLYGON((131.8 -7.7,112.5 -21.3,114.3 -36.9,147 -44.6,156.8 -28,143.1 -9.4,131.8 -7.7))";
    private static final String WKT_QUEENSLAND = "POLYGON((137.9 -16.4,137.9 -26,141.1 -26,141.1 -29,154.7 -29.2,149.9 -20.8,142.4 -10.3,140.7 -16.6,137.9 -16.4))";
    private static final String WKT_TOWNSVILLE = "POLYGON((146.7 -19.4,146.6 -19.3,146.7 -19.2,146.8 -19.1,146.9 -19.1,146.9 -19.3,146.8 -19.4,146.7 -19.4))"; // Includes magnetic island
    private static final String WKT_MAGNETIC_ISLAND = "POLYGON((146.77 -19.12,146.86 -19.09,146.89 -19.11,146.85 -19.19,146.79 -19.16,146.77 -19.12))";
    private static final String WKT_WESTERN_AUSTRALIA = "POLYGON((129 -13.8,125.4 -13.2,120.4 -18.9,112.1 -22.3,114.3 -35,119.5 -35.9,129 -32.3,129 -13.8))";

    // Outside Australia
    private static final String WKT_NEW_ZEALAND = "POLYGON((171.6 -33.9,172.9 -39.5,165.2 -46,168.1 -48.3,180.3 -38,174 -33.9,171.6 -33.8))";
    // Western Australia
    private static final String WKT_PILBARA = "POLYGON((115 -23.5,129 -23.5,129 -21.5,126.4 -21.5,126.4 -19.7,119 -19.8,116.6 -20.5,115 -21.6,115 -23.5))";

    // Layers BBOX
    // GeoServer doesn't return fancy WKT.
    // BBOX are saved as: [West, South, East, North]
    private static final float[] LAYER_BBOX_WORLD = new float[]{ -180, -90, 180, 90 };

    // Used for search (and some indexed documents)
    private static final String BBOX_WORLD = "POLYGON((-180 90,180 90,180 -90,-180 -90,-180 90))";
    private static final String BBOX_WESTERN_AUSTRALIA = "POLYGON((112 -36,129 -36,129 -12,112 -12,112 -36))";
    private static final String BBOX_MAGNETIC_ISLAND = "POLYGON((146.76 -19.07,146.76 -19.21,146.93 -19.21,146.93 -19.07,146.76 -19.07))";

    @Test
    public void testSearchWkt() throws Exception {
        SearchEngineConfig config = SearchEngineConfig.getInstance();
        Messages messages = Messages.getInstance(null);
        MockHttpClient mockHttpClient = MockHttpClient.getInstance();
        try (MockSearchClient searchClient = this.createMockSearchClient()) {
            Assertions.assertEquals(HealthStatus.Green, searchClient.getHealthStatus(), "The Elastic Search engine health status is not Green before starting the test.");

            String metadataRecordIndex = "junit_records";
            String layersIndex = "junit_layers";
            String imagesIndex = "junit_images";

            // Index mix content
            this.indexMetadataRecords(metadataRecordIndex, config, searchClient, mockHttpClient, messages);
            this.indexLayers(layersIndex, config, searchClient, mockHttpClient, messages);
            this.indexImages(imagesIndex, config, searchClient, mockHttpClient, messages);

            // TODO: Search with WKT

            Assertions.assertEquals(HealthStatus.Green, searchClient.getHealthStatus(), "The Elastic Search engine health status is not Green after the test.");
        }
    }

    private void indexMetadataRecords(String index, SearchEngineConfig config, MockSearchClient searchClient, MockHttpClient mockHttpClient, Messages messages) throws ParseException, IOException {
        searchClient.createIndex(index);
        GeoNetworkIndexer indexer = new GeoNetworkIndexer(mockHttpClient, index, "http://domain.com/geonetwork", "3.0");

        // Add the indexer to the SearchEngineConfig, so the EntityDeserializer (Jackson)
        //   can serialise / deserialise the Entity.
        config.addIndexer(indexer);


        // Australia
        GeoNetworkRecord australiaRecord = new GeoNetworkRecord(index, "00000000-0000-0000-0000-000000000000", "iso19115-3.2018", "3.0");
        australiaRecord.setTitle("Australia record");
        australiaRecord.setDocument("Record that covers whole of Australia.");
        australiaRecord.setWktAndAttributes(WKT_AUSTRALIA);
        indexer.indexEntity(searchClient, australiaRecord, messages);

        // Queensland
        GeoNetworkRecord qldRecord = new GeoNetworkRecord(index, "00000000-0000-0000-0000-000000000001", "iso19115-3.2018", "3.0");
        qldRecord.setTitle("Queensland record");
        qldRecord.setDocument("Record that covers whole of Queensland.");
        qldRecord.setWktAndAttributes(WKT_QUEENSLAND);
        indexer.indexEntity(searchClient, qldRecord, messages);

        // Wait for ElasticSearch to finish its indexation
        searchClient.refresh(index);
    }

    private void indexLayers(String index, SearchEngineConfig config, MockSearchClient searchClient, MockHttpClient mockHttpClient, Messages messages) throws IOException {
        searchClient.createIndex(index);
        String clientUrl = "http://domain.com/atlasmapper";
        String mainConfigPathStr = "searchWkt/atlasmapperFiles/main.json";

        AtlasMapperIndexer indexer = new AtlasMapperIndexer(mockHttpClient, index, clientUrl, "1.0", "http://domain.com/geoserver");
        // Add the indexer to the SearchEngineConfig, so the EntityDeserializer (Jackson)
        //   can serialise / deserialise the Entity.
        config.addIndexer(indexer);

        JSONObject jsonMainConfig = null;
        try (InputStream mainInputStream = IndexerTest.class.getClassLoader().getResourceAsStream(mainConfigPathStr)) {
            Assertions.assertNotNull(mainInputStream, String.format("Can not find the AtlasMapper main config: %s", mainConfigPathStr));
            String jsonText = IOUtils.toString(mainInputStream, StandardCharsets.UTF_8);
            jsonMainConfig = new JSONObject(jsonText);
        }

        // TODO: Add a few layers
        String worldBaseLayerId = "ea_base_layer";
        JSONObject jsonWorldBaseLayer = this.createJsonLayer(worldBaseLayerId, "Base layer", "Base layer that covers the whole world", LAYER_BBOX_WORLD);
        AtlasMapperLayer worldBaseLayer = new AtlasMapperLayer(index, clientUrl, worldBaseLayerId, jsonWorldBaseLayer, jsonMainConfig, messages);
        indexer.indexEntity(searchClient, worldBaseLayer, messages);

        // Wait for ElasticSearch to finish its indexation
        searchClient.refresh(index);
    }

    private JSONObject createJsonLayer(String layerId, String title, String description, float[] bbox) {
        JSONArray jsonBbox = new JSONArray();
        for (float coord : bbox) {
            jsonBbox.put(coord);
        }

        return new JSONObject()
                .put("dataSourceId", "ea")
                .put("layerName", layerId)
                .put("title", title)
                .put("description", description)
                .put("layerBoundingBox", jsonBbox)

                .put("wmsQueryable", true)
                .put("cached", true);

/*
{
        "dataSourceId": "ea",
        "wmsQueryable": true,
        "cached": true,
        "layerBoundingBox": [
            142.5969132059194, West
            -9.428481638741948, South
            142.80160546544423, East
            -9.364285559724976 North
        ],
        "description": "This project seeks to ensure that planning for the future development of the Torres Strait Islands is sustainable and capable of taking into account ecological and social information, assets, risk and existing infrastructure.\n\nThis Plan provides the following information for each island:\n\u2022identification of key environmental assets;\n\u2022identification of key land management issues;\n\u2022identification of key infrastructure needs;\n\u2022land use mapping identifying land suitable for development and conservation; and\n\u2022land use for the future sustainable management.\n\nMethodology\nIn 2007 the TSRA invited 15 of the Torres Strait Island community to participate in the Sustainable  Land Use Study, funded by the NHT (now Caring for the Country). Based on submissions received, the communities of Boigu, Dauan, Erub, Iama, Masig and Saibai were accepted to be involved in the project as stage 1 pilot project. In 2009 the TSRA, via funding from the major infrastructure project, requested the Land Use Plans be extended to the remaining 9 communities of Hammond, Kubin, St. Pauls, Badu, Warraber, Poruma, Mabuyag, Ugar and Mer. Stage 1 occurred between 2007 and 2008. Stage 2 occurred between 2009 and 2010.\n\nPreliminary Consultation\nThe project team met with all Community Council (prior to amalgamation) and Prescribed Bodies Corporate (PBC) to discuss the project objectives and methodology.\n\nPhase 1 - Fauna and Habitat Assessment.\nField Study\nThe project team undertook field studies on the islands to identify key environmental assets and associated land management issues, identify areas of conservation importance and undertake fauna identification.\n\nPhase 2 - Information Gathering & Research.\nThe project team collated all available data for the islands to order to produce a compressive collection of information on the islands. Data included plans and surveys from major infrastructure projects, data collected as part of other TSRA projects (e.g. regional ecosystem mapping, tide levels) and well as existing State government data. Also during this phase, the project team undertook a literature review of natural resource management issues in the context of the Torres Strait. This research, along with local knowledge obtained by Community in Phase 5, provided the foundation for the best practice principles outlined in the Plan.\n\nPhase 3 - Constraints and Information Mapping.\nThe project team produced a series of constraints and information mapping. This included:\n\u2022analysis of the data collected in Phases 2&3;\n\u2022analysis of existing spatial datasets, including\naerial photographs, maps and satellite imagery;\n\u2022analysis of Commonwealth and State legislation, policies, strategies, reports and community plans;\n\u2022development and sourcing of relevant GIS data layers;\n\u2022preparation of base mapping showing satellite imagery, slope analysis, coastal impacts and inundation, fauna and habitat values, bushfire risk, limited cultural heritage information, extent of service infrastructure.\n\n*Online resources*\n* [[https://eatlas.org.au/data/uuid/9fdeb5b3-b407-49e8-ba71-cb10eb31615b|Point of truth URL of this metadata record]]\n* [[http://www.tsra.gov.au/the-tsra/programs-and-output/env-mgt-program/publications-and-resources|Sustainable Land Use Plans, as well as supporting maps and resources, are available for download below.]]",
        "styles": [{
            "default": true,
            "cached": true,
            "name": "line",
            "description": "Default line style, 1 pixel wide blue",
            "title": "1 px blue line"
        }],
        "layerName": "TS_TSRA_SLUP-2010:Saibai-Boundary",
        "title": "Saibai Boundary  (TSRA)",
        "treePath": "Boundaries/TS: Sustainable Land Use Plan 2010 (TSRA, RPS)/Saibai"
    }
*/
    }

    private void indexImages(String index, SearchEngineConfig config, MockSearchClient searchClient, MockHttpClient mockHttpClient, Messages messages) throws IOException {
        searchClient.createIndex(index);
        DrupalMediaIndexer indexer = new DrupalMediaIndexer(mockHttpClient, index, "http://domain.com", "11.0", "image", "field_preview", "title", "body", "field_wkt");

        // Add the indexer to the SearchEngineConfig, so the EntityDeserializer (Jackson)
        //   can serialise / deserialise the Entity.
        config.addIndexer(indexer);

        // TODO: Add a few images
        // WKT_MAGNETIC_ISLAND
        // WKT_NEW_ZEALAND

        // Wait for ElasticSearch to finish its indexation
        searchClient.refresh(index);
    }
}
