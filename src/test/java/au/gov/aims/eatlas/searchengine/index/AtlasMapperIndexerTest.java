package au.gov.aims.eatlas.searchengine.index;

import au.gov.aims.eatlas.searchengine.MockHttpClient;
import au.gov.aims.eatlas.searchengine.admin.rest.Messages;
import au.gov.aims.eatlas.searchengine.rest.Search;
import au.gov.aims.eatlas.searchengine.search.IndexSummary;
import au.gov.aims.eatlas.searchengine.search.SearchResults;
import au.gov.aims.eatlas.searchengine.search.Summary;
import co.elastic.clients.elasticsearch._types.HealthStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AtlasMapperIndexerTest extends IndexerTestBase {

    @Test
    public void testIndexLayers() throws Exception {
        try (MockSearchClient searchClient = this.createMockSearchClient()) {
            MockHttpClient mockHttpClient = this.getMockHttpClient();

            Map<String, String> urlMap = new HashMap<>();
            // AtlasMapper config files
            urlMap.put("https://domain.com/config/main.json", "atlasmapperFiles/config/main.json");
            urlMap.put("https://domain.com/config/layers.json", "atlasmapperFiles/config/layers.json");

            // Preview images
            // Only handle preview image for 5 layers, to make sure the indexer is able to index layer when preview images can't be generated.
            // Base layers (jpg)
            urlMap.put("https://domain.com/maps/wms?SERVICE=WMS&REQUEST=GetMap&LAYERS=ea-be:World_Bright-Earth-e-Atlas-basemap&FORMAT=image/jpeg&TRANSPARENT=false&VERSION=1.1.1&SRS=EPSG:4326&BBOX=142.210525512695,-9.240038871765,142.223922729492,-9.223721504211&WIDTH=164&HEIGHT=200", "atlasmapperFiles/preview/preview_baselayer.jpg");
            urlMap.put("https://domain.com/maps/wms?SERVICE=WMS&REQUEST=GetMap&LAYERS=ea-be:World_Bright-Earth-e-Atlas-basemap&FORMAT=image/jpeg&TRANSPARENT=false&VERSION=1.1.1&SRS=EPSG:4326&BBOX=142.576446533203,-9.434902191162,142.822082519531,-9.357866287231&WIDTH=300&HEIGHT=94", "atlasmapperFiles/preview/preview_baselayer.jpg");
            urlMap.put("https://domain.com/maps/wms?SERVICE=WMS&REQUEST=GetMap&LAYERS=ea-be:World_Bright-Earth-e-Atlas-basemap&FORMAT=image/jpeg&TRANSPARENT=false&VERSION=1.1.1&SRS=EPSG:4326&BBOX=144.406311035156,-20.206789016724,147.492309570313,-14.980501174927&WIDTH=118&HEIGHT=200", "atlasmapperFiles/preview/preview_baselayer.jpg");
            urlMap.put("https://domain.com/maps/wms?SERVICE=WMS&REQUEST=GetMap&LAYERS=ea-be:World_Bright-Earth-e-Atlas-basemap&FORMAT=image/jpeg&TRANSPARENT=false&VERSION=1.1.1&SRS=EPSG:4326&BBOX=142.174987792969,-10.580891609192,142.236785888672,-10.514998435974&WIDTH=187&HEIGHT=200", "atlasmapperFiles/preview/preview_baselayer.jpg");
            // Overlay layers (png)
            urlMap.put("https://domain.com/maps/wms?REQUEST=GetMap&FORMAT=image%2Fpng&SRS=EPSG%3A4326&CRS=EPSG%3A4326&BBOX=144.406311035156%2C-20.206789016724%2C147.492309570313%2C-14.980501174927&VERSION=1.1.1&STYLES=&SERVICE=WMS&WIDTH=118&HEIGHT=200&TRANSPARENT=true&LAYERS=WT_MTSRF_JCU_Vertebrate-atlas_2010%3ARealized-MSTAR", "atlasmapperFiles/preview/preview_Realized-MSTAR.png");
            urlMap.put("https://domain.com/maps/wms?REQUEST=GetMap&FORMAT=image%2Fpng&SRS=EPSG%3A4326&CRS=EPSG%3A4326&BBOX=144.406311035156%2C-20.206789016724%2C147.492309570313%2C-14.980501174927&VERSION=1.1.1&STYLES=&SERVICE=WMS&WIDTH=118&HEIGHT=200&TRANSPARENT=true&LAYERS=WT_MTSRF_JCU_Vertebrate-atlas_2010%3ARealized-LITNANN", "atlasmapperFiles/preview/preview_Realized-LITNANN.png");
            urlMap.put("https://domain.com/maps/wms?REQUEST=GetMap&FORMAT=image%2Fpng&SRS=EPSG%3A4326&CRS=EPSG%3A4326&BBOX=142.210525512695%2C-9.240038871765%2C142.223922729492%2C-9.223721504211&VERSION=1.1.1&STYLES=&SERVICE=WMS&WIDTH=164&HEIGHT=200&TRANSPARENT=true&LAYERS=TS_TSRA_SLUP-2010%3ABoigu-Contours_Major", "atlasmapperFiles/preview/preview_Boigu-Contours_Major.png");
            urlMap.put("https://domain.com/maps/wms?REQUEST=GetMap&FORMAT=image%2Fpng&SRS=EPSG%3A4326&CRS=EPSG%3A4326&BBOX=142.174987792969%2C-10.580891609192%2C142.236785888672%2C-10.514998435974&VERSION=1.1.1&STYLES=&SERVICE=WMS&WIDTH=187&HEIGHT=200&TRANSPARENT=true&LAYERS=TS_TSRA_SLUP-2010%3AHammond-PASS", "atlasmapperFiles/preview/preview_Hammond-PASS.png");
            urlMap.put("https://domain.com/maps/wms?REQUEST=GetMap&FORMAT=image%2Fpng&SRS=EPSG%3A4326&CRS=EPSG%3A4326&BBOX=142.576446533203%2C-9.434902191162%2C142.822082519531%2C-9.357866287231&VERSION=1.1.1&STYLES=&SERVICE=WMS&WIDTH=300&HEIGHT=94&TRANSPARENT=true&LAYERS=TS_TSRA_SLUP-2010%3ASaibai-Boundary", "atlasmapperFiles/preview/preview_Saibai-Boundary.png");

            mockHttpClient.setUrlMap(urlMap);

            Assertions.assertEquals(HealthStatus.Green, searchClient.getHealthStatus(), "The Elastic Search engine health status is not Green before starting the test.");

            String index = "atlasmapper";
            Messages messages = Messages.getInstance(null);

            searchClient.createIndex(index);

            // Find the indexer, defined in the config file
            AtlasMapperIndexer atlasMapperIndexer =
                    (AtlasMapperIndexer)this.getConfig().getIndexer(index);

            atlasMapperIndexer.internalIndex(searchClient, null, messages);

            // Wait for ElasticSearch to finish its indexation
            searchClient.refresh(index);

            // Check indexed documents
            SearchResults results = null;

            // Get every layers
            try {
                String q = ""; // Get every single layers
                Integer start = 0;
                Integer hits = 50; // Number of result per page. There is only 10 documents in the index
                String wkt = null; // No geographic filtering
                List<String> idx = List.of(index);

                results = Search.paginationSearch(searchClient, q, start, hits, wkt, idx, null, messages);

                Summary searchSummary = results.getSummary();

                // Check indexes in the summary.
                Map<String, IndexSummary> indexSummaryMap = searchSummary.getIndexSummaries();
                Assertions.assertNotNull(indexSummaryMap,
                        "Index summary is null.");
                Assertions.assertEquals(1, indexSummaryMap.size(),
                        "Wrong number if index summary.");
                Assertions.assertTrue(indexSummaryMap.containsKey(index),
                        String.format("Missing index from the search summary: %s", index));

                IndexSummary layersIndexSummary = searchSummary.getIndexSummary(index);

                Assertions.assertEquals(10, layersIndexSummary.getHits(),
                        "Wrong number of search result in the index summary.");

                Assertions.assertEquals(10, searchSummary.getHits(),
                        "Wrong total number of search result in the index summary.");

            } catch(Exception ex) {
                Assertions.fail("Exception thrown while testing the Search API.", ex);
            }

            // Search for layers containing the word "coral"
            try {
                String q = "coral"; // Search for the word "coral", present in multiple layers
                Integer start = 0;
                Integer hits = 50; // Number of result per page. There is only 10 documents in the index
                String wkt = null; // No geographic filtering
                List<String> idx = List.of(index);

                results = Search.paginationSearch(searchClient, q, start, hits, wkt, idx, null, messages);

                Summary searchSummary = results.getSummary();

                // Check indexes in the summary.
                Map<String, IndexSummary> indexSummaryMap = searchSummary.getIndexSummaries();
                Assertions.assertNotNull(indexSummaryMap,
                        "Index summary is null.");
                Assertions.assertEquals(1, indexSummaryMap.size(),
                        "Wrong number if index summary.");
                Assertions.assertTrue(indexSummaryMap.containsKey(index),
                        String.format("Missing index from the search summary: %s", index));

                IndexSummary layersIndexSummary = searchSummary.getIndexSummary(index);

                Assertions.assertEquals(5, layersIndexSummary.getHits(),
                        "Wrong number of search result in the index summary.");

            } catch(Exception ex) {
                Assertions.fail("Exception thrown while testing the Search API.", ex);
            }

            Assertions.assertEquals(HealthStatus.Green, searchClient.getHealthStatus(), "The Elastic Search engine health status is not Green after the test.");
        }
    }
}
