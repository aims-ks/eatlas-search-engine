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
package au.gov.aims.eatlas.searchengine.client;

import au.gov.aims.eatlas.searchengine.HttpClient;
import au.gov.aims.eatlas.searchengine.admin.SearchEngineConfig;
import au.gov.aims.eatlas.searchengine.admin.SearchEngineState;
import au.gov.aims.eatlas.searchengine.index.AbstractIndexer;
import au.gov.aims.eatlas.searchengine.index.AtlasMapperIndexer;
import au.gov.aims.eatlas.searchengine.index.DrupalBlockIndexer;
import au.gov.aims.eatlas.searchengine.index.DrupalExternalLinkNodeIndexer;
import au.gov.aims.eatlas.searchengine.index.DrupalMediaIndexer;
import au.gov.aims.eatlas.searchengine.index.DrupalNodeIndexer;
import au.gov.aims.eatlas.searchengine.index.GeoNetworkIndexer;
import au.gov.aims.eatlas.searchengine.index.IndexerState;
import co.elastic.clients.elasticsearch._types.HealthStatus;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class SearchUtils {

    public static RestClient buildRestClient() throws MalformedURLException {
        SearchEngineConfig config = SearchEngineConfig.getInstance();
        List<String> elasticSearchUrls = config.getElasticSearchUrls();
        if (elasticSearchUrls == null || elasticSearchUrls.isEmpty()) {
            throw new IllegalArgumentException("The Elastic Search configuration do not specify any Elastic Search URL.");
        }

        List<HttpHost> hosts = new ArrayList<>();
        for (String elasticSearchUrl : elasticSearchUrls) {
            HttpHost host = SearchUtils.toHttpHost(elasticSearchUrl);
            if (host != null) {
                hosts.add(host);
            }
        }

        return RestClient.builder(
                hosts.toArray(new HttpHost[0])
            ).build();
    }

    private static HttpHost toHttpHost(String urlStr) throws MalformedURLException {
        if (urlStr == null || urlStr.isEmpty()) {
            return null;
        }

        URL url = new URL(urlStr);
        return new HttpHost(
            url.getHost(),
            url.getPort(),
            url.getProtocol());
    }

    public static void deleteOrphanIndexes(SearchClient searchClient) throws IOException {
        SearchEngineConfig config = SearchEngineConfig.getInstance();

        List<String> activeIndexes = new ArrayList<>();
        for (AbstractIndexer<?> indexer : config.getIndexers()) {
            activeIndexes.add(indexer.getIndex());
        }

        searchClient.deleteOrphanIndexes(activeIndexes);
    }

    // NOTE: To request the Elastic Search health status manually, use the REST API:
    //   $ curl http://localhost:9200/_cluster/health
    public static ElasticSearchStatus getElasticSearchStatus(SearchClient searchClient, HttpServletRequest httpRequest) {
        ElasticSearchStatus status = null;

        try {
            // This should give the list of indexes, if the Elastic Search engine is reachable.
            List<String> indexes = searchClient.listIndexes();

            status = new ElasticSearchStatus(true);
            status.setIndexes(indexes);

            HealthStatus healthStatus = searchClient.getHealthStatus();
            status.setHealthStatus(healthStatus);
            SearchUtils.addHealthStatusWarnings(httpRequest, status, healthStatus);
        } catch(Exception ex) {
            status = new ElasticSearchStatus(false);
            status.setException(ex);
        }

        return status;
    }

    private static void addHealthStatusWarnings(HttpServletRequest httpRequest, ElasticSearchStatus status, HealthStatus healthStatus) {
        if (healthStatus == null) {
            // This should not happen
            status.addWarning(
                "Elastic Search health status is null."
            );
        } else {
            String helpPageUrl = httpRequest.getContextPath() + "/admin/help";

            switch(healthStatus) {
                case Green:
                    // Everything is going well, no warning
                    break;
                case Yellow:
                    // Yellow status, something is about to go bad.
                    // Probably disk space related.
                    status.addWarning(
                        "Yellow health status. " +
                        "Identify and fix the issue before it gets worse. " +
                        "Check the <a href=\"" + helpPageUrl + "#es-yellow-status\">help page</a> for more information."
                    );
                    break;
                case Red:
                    // Red status, Elastic Search is not working properly.
                    // Disk almost full?
                    status.addWarning(
                        "Red health status. " +
                        "Identify and fix the issue to allow Elastic Search engine to work properly. " +
                        "Check the <a href=\"" + helpPageUrl + "#es-red-status\">help page</a> for more information."
                    );
                    break;
                default:
                    // Unknown status.
                    // Did Elastic Search API change?
                    status.addWarning(
                        "Unknown HealthStatus: " + healthStatus
                    );
                    break;
            }
        }
    }

    public static void refreshIndexesCount(SearchClient searchClient) throws Exception {
        SearchEngineConfig config = SearchEngineConfig.getInstance();
        SearchEngineState searchEngineState = SearchEngineState.getInstance();

        for (AbstractIndexer<?> indexer : config.getIndexers()) {
            indexer.refreshCount(searchClient);
        }

        searchEngineState.save();
    }

    public static String generateUniqueIndexName(String index) {
        if (index == null || index.isEmpty()) {
            index = "index";
        }

        if (!SearchUtils.indexExists(index)) {
            return index;
        }

        int suffix = 1;
        while (SearchUtils.indexExists(index + "_" + suffix)) {
            suffix++;
        }

        return index + "_" + suffix;
    }

    // NOTE: deleteOrphanIndexes() should be called prior to calling this method,
    //     to make sure the list of index found in the config
    //     is in sync with the list of index in ElasticSearch.
    public static boolean indexExists(String index) {
        if (index == null || index.isEmpty()) {
            return false;
        }

        SearchEngineConfig config = SearchEngineConfig.getInstance();

        AbstractIndexer<?> foundIndexer = config.getIndexer(index);
        return foundIndexer != null;
    }

    public static AbstractIndexer<?> addIndex(HttpClient httpClient, String newIndexType) throws Exception {
        if (newIndexType == null || newIndexType.isEmpty()) {
            return null;
        }

        SearchEngineConfig config = SearchEngineConfig.getInstance();

        String newIndex = null;
        IndexerState state = null;
        AbstractIndexer<?> newIndexer = null;
        switch (newIndexType) {
            case "DrupalNodeIndexer":
                newIndex = SearchUtils.generateUniqueIndexName("drupal-node");
                newIndexer = new DrupalNodeIndexer(httpClient, newIndex, null, null, null, null, null, null);
                break;

            case "DrupalMediaIndexer":
                newIndex = SearchUtils.generateUniqueIndexName("drupal-media");
                newIndexer = new DrupalMediaIndexer(httpClient, newIndex, null, null, null, null, null, null, null);
                break;

            case "DrupalExternalLinkNodeIndexer":
                newIndex = SearchUtils.generateUniqueIndexName("drupal-extlink");
                newIndexer = new DrupalExternalLinkNodeIndexer(httpClient, newIndex, null, null, null, null, null, null, null);
                break;

            case "DrupalBlockIndexer":
                newIndex = SearchUtils.generateUniqueIndexName("drupal-block");
                newIndexer = new DrupalBlockIndexer(httpClient, newIndex, null, null, null, null, null, null);
                break;

            case "GeoNetworkIndexer":
                newIndex = SearchUtils.generateUniqueIndexName("geonetwork");
                newIndexer = new GeoNetworkIndexer(httpClient, newIndex, null, null);
                break;

            case "AtlasMapperIndexer":
                newIndex = SearchUtils.generateUniqueIndexName("atlasmapper");
                newIndexer = new AtlasMapperIndexer(httpClient, newIndex, null, null, null);
                break;

            default:
                throw new IllegalArgumentException(String.format("Unsupported index type: %s", newIndexType));
        }

        if (newIndexer != null) {
            config.addIndexer(newIndexer);
            config.save();
        }

        return newIndexer;
    }
}
