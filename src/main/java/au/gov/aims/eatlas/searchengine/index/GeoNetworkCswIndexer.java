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
package au.gov.aims.eatlas.searchengine.index;

import au.gov.aims.eatlas.searchengine.HttpClient;
import au.gov.aims.eatlas.searchengine.logger.AbstractLogger;
import au.gov.aims.eatlas.searchengine.client.SearchClient;
import au.gov.aims.eatlas.searchengine.entity.GeoNetworkRecord;
import au.gov.aims.eatlas.searchengine.entity.geoNetworkParser.AbstractParser;
import au.gov.aims.eatlas.searchengine.entity.geoNetworkParser.ISO19115_3_2018_parser;
import au.gov.aims.eatlas.searchengine.logger.Level;
import au.gov.aims.eatlas.searchengine.rest.ImageCache;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.http.client.utils.URIBuilder;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class GeoNetworkCswIndexer extends AbstractGeoNetworkIndexer<GeoNetworkRecord> {
    private static final Logger LOGGER = LogManager.getLogger(GeoNetworkCswIndexer.class.getName());
    private static final int THREAD_POOL_SIZE = 10;

    private List<String> geoNetworkCategories;

    /**
     * index: eatlas_metadata
     * geoNetworkUrl: https://eatlas-geonetwork/geonetwork
     * geoNetworkPublicUrl: https://eatlas.org.au/geonetwork
     * geoNetworkVersion: 4.2.10
     * geoNetworkCategories: eatlas, nwa, !demo, !test
     */
    public GeoNetworkCswIndexer(HttpClient httpClient, String index, String indexName, String geoNetworkUrl, String geoNetworkPublicUrl, String geoNetworkVersion, List<String> geoNetworkCategories) {
        super(httpClient, index, indexName, geoNetworkUrl, geoNetworkPublicUrl, geoNetworkVersion);
        this.geoNetworkCategories = geoNetworkCategories;
    }

    public static GeoNetworkCswIndexer fromJSON(HttpClient httpClient, String index, String indexName, JSONObject json) {
        if (json == null || json.isEmpty()) {
            return null;
        }

        List<String> categories = new ArrayList<String>();
        JSONArray geoNetworkCategoriesArray = json.optJSONArray("geoNetworkCategories");
        if (geoNetworkCategoriesArray != null) {
            for (int i = 0; i < geoNetworkCategoriesArray.length(); i++) {
                String category = geoNetworkCategoriesArray.optString(i, null);
                if (category != null && !category.isBlank()) {
                    categories.add(category.trim());
                }
            }
        }

        return new GeoNetworkCswIndexer(
            httpClient, index, indexName,
            json.optString("geoNetworkUrl", null),
            json.optString("geoNetworkPublicUrl", null),
            json.optString("geoNetworkVersion", null),
            categories.isEmpty() ? null : categories);
    }

    public JSONObject toJSON() {
        return this.getJsonBase()
            .put("geoNetworkCategories", this.geoNetworkCategories);
    }

    @Override
    public GeoNetworkRecord load(JSONObject json, AbstractLogger logger) {
        return GeoNetworkRecord.load(json, logger);
    }

    @Override
    protected GeoNetworkRecord harvestEntity(SearchClient searchClient, String id, AbstractLogger logger) {
        HttpClient httpClient = this.getHttpClient();

        String urlBase = HttpClient.combineUrls(
                this.getGeoNetworkUrl(),
                "srv/eng/csw");
        URIBuilder uriBuilder;
        try {
            uriBuilder = new URIBuilder(urlBase);
        } catch(URISyntaxException ex) {
            logger.addMessage(Level.ERROR,
                    String.format("Invalid GeoNetwork URL. Exception occurred while building the URL: %s", urlBase), ex);
            return null;
        }

        String url;
        try {
            url = uriBuilder.build().toURL().toString();
        } catch(Exception ex) {
            // Should not happen
            logger.addMessage(Level.ERROR,
                    String.format("Invalid GeoNetwork URL. Exception occurred while building a URL starting with: %s", urlBase), ex);
            return null;
        }

        List<String> andFilters = new ArrayList<String>();
        andFilters.add(String.format(
            "<ogc:PropertyIsEqualTo>" +
                "<ogc:PropertyName>Identifier</ogc:PropertyName>" +
                "<ogc:Literal>%s</ogc:Literal>" +
            "</ogc:PropertyIsEqualTo>",
            StringEscapeUtils.escapeXml10(id)));
        andFilters.addAll(getCategoriesFilters());
        String recordFilterQuery = this.andFiltersToString(andFilters);

        String outputSchema = "http://standards.iso.org/iso/19115/-3/mdb/2.0"; // iso19115-3.2018
        String xmlQuery = String.format("<?xml version=\"1.0\"?>\n" +
            "<GetRecords xmlns=\"http://www.opengis.net/cat/csw/2.0.2\" " +
                    "xmlns:ogc=\"http://www.opengis.net/ogc\" " +
                    "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" " +
                    "service=\"CSW\" " +
                    "version=\"2.0.2\" " +
                    "resultType=\"results\" " +
                    "startPosition=\"1\" " +
                    "maxRecords=\"1\" " +
                    "outputSchema=\"%s\" " +
                    "xsi:schemaLocation=\"http://www.opengis.net/cat/csw/2.0.2 http://schemas.opengis.net/csw/2.0.2/CSW-discovery.xsd\">" +
                "<Query typeNames=\"mdb:MD_Metadata\">" +
                    "<ElementSetName>full</ElementSetName>" +
                    "%s" +
                "</Query>" +
            "</GetRecords>",
            StringEscapeUtils.escapeXml10(outputSchema),
            recordFilterQuery);

        // JDOM tutorial:
        //     https://www.tutorialspoint.com/java_xml/java_dom_parse_document.htm
        DocumentBuilder xmlParser;
        try {
            xmlParser = IndexUtils.getNewXMLParser();
        } catch(Exception ex) {
            // Should not happen
            logger.addMessage(Level.ERROR, String.format("Exception occurred while creating XML document builder for index: %s",
                    this.getIndex()), ex);
            return null;
        }

        try {
            HttpClient.Response response = httpClient.postXmlRequest(url, xmlQuery, logger);

            int statusCode = response == null ? -1 : response.statusCode();
            String responseStr = response == null ? null : response.body();

            if (statusCode < 200 || statusCode >= 400) {
                logger.addMessage(Level.ERROR, String.format("Unexpected status code returned from GeoNetwork: %d%nResponse: %s",
                        statusCode, responseStr));

            } else if (responseStr != null && !responseStr.isEmpty()) {
                try (ByteArrayInputStream input = new ByteArrayInputStream(
                    responseStr.getBytes(StandardCharsets.UTF_8))) {

                    AbstractParser metadataRecordParser = new ISO19115_3_2018_parser();

                    Document document = xmlParser.parse(input);

                    // Fix the document, if needed
                    document.getDocumentElement().normalize();

                    Element root = document.getDocumentElement();

                    Element searchResultsElement = IndexUtils.getXMLChild(root, "csw:SearchResults");

                    // Loop through mdb:MD_Metadata, parse them with ISO19115_3_2018_parser
                    Element metadataElement = IndexUtils.getXMLChild(searchResultsElement, "mdb:MD_Metadata");

                    GeoNetworkRecord geoNetworkRecord = new GeoNetworkRecord(this, null, "iso19115-3.2018", this.getGeoNetworkVersion());
                    metadataRecordParser.parseRecord(this, geoNetworkRecord, metadataElement, logger);
                    this.updateThumbnail(searchClient, geoNetworkRecord, logger);

                    IndexResponse indexResponse = GeoNetworkCswIndexer.this.indexEntity(searchClient, geoNetworkRecord, logger);

                    logger.addMessage(Level.INFO, String.format("Re-indexing GeoNetwork metadata record: %s, index response status: %s",
                            geoNetworkRecord.getId(),
                            indexResponse.result()));
                }
            }
        } catch(Exception ex) {
            logger.addMessage(Level.ERROR, String.format("Exception occurred while harvesting GeoNetwork record list: %s",
                    url), ex);
        }

        return null;
    }

    public void updateThumbnail(SearchClient searchClient, GeoNetworkRecord geoNetworkRecord, AbstractLogger logger) {
        URL thumbnailUrl = geoNetworkRecord.getThumbnailUrl();
        if (thumbnailUrl != null) {
            GeoNetworkRecord oldRecord = this.safeGet(searchClient, GeoNetworkRecord.class, geoNetworkRecord.getId(), logger);
            boolean thumbnailOutdated =
                geoNetworkRecord.isThumbnailOutdated(oldRecord, this.getSafeThumbnailTTL(), this.getSafeBrokenThumbnailTTL(), logger);

            if (thumbnailOutdated) {
                HttpClient httpClient = this.getHttpClient();

                try {
                    File cachedThumbnailFile = ImageCache.cache(httpClient, thumbnailUrl, this.getIndex(), geoNetworkRecord.getId(), logger);
                    if (cachedThumbnailFile != null) {
                        geoNetworkRecord.setCachedThumbnailFilename(cachedThumbnailFile.getName());
                    }
                } catch(Exception ex) {
                    logger.addMessage(Level.WARNING,
                            String.format("Exception occurred while creating a thumbnail for GeoNetwork record id: %s",
                                    geoNetworkRecord.getId()), ex);
                }
                geoNetworkRecord.setThumbnailLastIndexed(System.currentTimeMillis());
            } else {
                geoNetworkRecord.useCachedThumbnail(oldRecord, logger);
            }
        }
    }

    @Override
    public boolean supportsIndexLatest() {
        return true;
    }

    @Override
    protected void internalIndex(SearchClient searchClient, Long lastHarvested, AbstractLogger logger) {
        HttpClient httpClient = this.getHttpClient();

        boolean fullHarvest = lastHarvested == null;

        Set<String> usedThumbnails = null;
        if (fullHarvest) {
            usedThumbnails = Collections.synchronizedSet(new HashSet<String>());
        }

        // GeoNetwork's CSW API URL
        // The request is provided as XML data in a POST request.
        String urlBase = HttpClient.combineUrls(
                this.getGeoNetworkUrl(),
                "srv/eng/csw");
        URIBuilder uriBuilder;
        try {
            uriBuilder = new URIBuilder(urlBase);
        } catch(URISyntaxException ex) {
            logger.addMessage(Level.ERROR,
                    String.format("Invalid GeoNetwork URL. Exception occurred while building the URL: %s", urlBase), ex);
            return;
        }

        String url;
        try {
            url = uriBuilder.build().toURL().toString();
        } catch(Exception ex) {
            // Should not happen
            logger.addMessage(Level.ERROR,
                    String.format("Invalid GeoNetwork URL. Exception occurred while building a URL starting with: %s", urlBase), ex);
            return;
        }

        String recordSortQuery =
            "<ogc:SortBy xmlns:ogc=\"http://www.opengis.net/ogc\">" +
                "<ogc:SortProperty>" +
                    "<ogc:PropertyName>Identifier</ogc:PropertyName>" +
                    "<ogc:SortOrder>ASC</ogc:SortOrder>" +
                "</ogc:SortProperty>" +
            "</ogc:SortBy>";

        // If we have a "lastHarvested" parameter, request metadata records modified since that date.
        // Otherwise, request everything.
        List<String> andFilters = new ArrayList<String>();
        if (!fullHarvest) {
            // Harvest only modified records
            andFilters.add(String.format(
                "<ogc:PropertyIsGreaterThan>" +
                    "<ogc:PropertyName>Modified</ogc:PropertyName>" +
                    "<ogc:Literal>%d</ogc:Literal>" +
                "</ogc:PropertyIsGreaterThan>",
                lastHarvested));
        }
        andFilters.addAll(getCategoriesFilters());
        String recordFilterQuery = this.andFiltersToString(andFilters);

        String outputSchema = "http://standards.iso.org/iso/19115/-3/mdb/2.0"; // iso19115-3.2018
        int startPosition = 1;
        int recordCounter = 0;
        int recordsPerPage = 10;

        int numberOfRecordsMatched = 0;
        boolean crashed = false;
        boolean empty = false;

        // JDOM tutorial:
        //     https://www.tutorialspoint.com/java_xml/java_dom_parse_document.htm
        DocumentBuilder xmlParser;
        try {
            xmlParser = IndexUtils.getNewXMLParser();
        } catch(Exception ex) {
            // Should not happen
            logger.addMessage(Level.ERROR, String.format("Exception occurred while creating XML document builder for index: %s",
                    this.getIndex()), ex);
            return;
        }

        // List of metadata records which needs its parent title to be set
        List<String> orphanMetadataRecordList = Collections.synchronizedList(new ArrayList<>());
        ThreadPoolExecutor threadPool = (ThreadPoolExecutor) Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        AbstractParser metadataRecordParser = new ISO19115_3_2018_parser();

        long harvestStart = System.currentTimeMillis();
        do {
            String xmlQuery = String.format("<?xml version=\"1.0\"?>\n" +
                "<GetRecords xmlns=\"http://www.opengis.net/cat/csw/2.0.2\" " +
                        "xmlns:ogc=\"http://www.opengis.net/ogc\" " +
                        "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" " +
                        "service=\"CSW\" " +
                        "version=\"2.0.2\" " +
                        "resultType=\"results\" " +
                        "startPosition=\"%d\" " +
                        "maxRecords=\"%d\" " +
                        "outputSchema=\"%s\" " +
                        "xsi:schemaLocation=\"http://www.opengis.net/cat/csw/2.0.2 http://schemas.opengis.net/csw/2.0.2/CSW-discovery.xsd\">" +
                    "<Query typeNames=\"mdb:MD_Metadata\">" +
                        "<ElementSetName>full</ElementSetName>" +
                        "%s" +
                        "%s" +
                    "</Query>" +
                "</GetRecords>",
                startPosition,
                recordsPerPage,
                StringEscapeUtils.escapeXml10(outputSchema),
                recordFilterQuery,
                recordSortQuery);

            HttpClient.Response response = null;
            try {
                response = httpClient.postXmlRequest(url, xmlQuery, logger);
            } catch(Exception ex) {
                if (!crashed) {
                    logger.addMessage(Level.ERROR, String.format("Exception occurred while harvesting GeoNetwork record list: %s",
                            url), ex);
                }
                crashed = true;
            }

            int statusCode = response == null ? -1 : response.statusCode();
            String responseStr = response == null ? null : response.body();

            if (statusCode < 200 || statusCode >= 400) {
                logger.addMessage(Level.ERROR, String.format("Unexpected status code returned from GeoNetwork: %d%nResponse: %s",
                        statusCode, responseStr));
                crashed = true;
            } else if (responseStr != null && !responseStr.isEmpty()) {
                try (ByteArrayInputStream input = new ByteArrayInputStream(
                    responseStr.getBytes(StandardCharsets.UTF_8))) {

                    Document document = xmlParser.parse(input);

                    // Fix the document, if needed
                    document.getDocumentElement().normalize();

                    Element root = document.getDocumentElement();

                    Element searchResultsElement = IndexUtils.getXMLChild(root, "csw:SearchResults");
                    String numberOfRecordsMatchedStr = IndexUtils.parseAttribute(searchResultsElement, "numberOfRecordsMatched");
                    numberOfRecordsMatched = numberOfRecordsMatchedStr == null ? -1 : Integer.parseInt(numberOfRecordsMatchedStr);
                    this.setTotal((long)numberOfRecordsMatched);

                    // Loop through mdb:MD_Metadata, parse them with ISO19115_3_2018_parser
                    List<Element> metadataElements = IndexUtils.getXMLChildren(searchResultsElement, "mdb:MD_Metadata");
                    for (Element metadataElement : metadataElements) {
                        recordCounter++;
                        GeoNetworkRecord geoNetworkRecord = new GeoNetworkRecord(this, null, "iso19115-3.2018", this.getGeoNetworkVersion());
                        metadataRecordParser.parseRecord(this, geoNetworkRecord, metadataElement, logger);

                        // Index records in thread.
                        // NOTE: The record parsing can't be threaded with the CSW API
                        //   since the information for the next page is in the XML response
                        //   and the response also contains the full metadata records (not just the record UUID).
                        //   It needs to be parsed sequentially.
                        GeoNetworkCswIndexerThread thread = new GeoNetworkCswIndexerThread(
                                searchClient, logger, geoNetworkRecord,
                                orphanMetadataRecordList, usedThumbnails, recordCounter);

                        threadPool.execute(thread);
                    }

                    // Prepare for next page of result
                    String nextRecordStr = IndexUtils.parseAttribute(searchResultsElement, "nextRecord");
                    startPosition = nextRecordStr == null ? -1 : Integer.parseInt(nextRecordStr);
                } catch (Exception ex) {
                    logger.addMessage(Level.ERROR, String.format("Exception occurred while parsing the GeoNetwork record list: %s",
                            url), ex);
                    crashed = true;
                }
            }

        } while(!crashed && !empty && startPosition > 0 && startPosition <= numberOfRecordsMatched);

        threadPool.shutdown();
        try {
            threadPool.awaitTermination(1, TimeUnit.HOURS);
        } catch(InterruptedException ex) {
            logger.addMessage(Level.ERROR, "The GeoNetwork indexation was interrupted", ex);
        }

        // Refresh the index to be sure to find parent records, if they are new
        try {
            searchClient.refresh(this.getIndex());
        } catch(Exception ex) {
            logger.addMessage(Level.ERROR, String.format("Exception occurred while refreshing the search index: %s", this.getIndex()), ex);
        }

        // We have added all the records.
        // Let's fix the records parent title.
        for (String recordUUID : orphanMetadataRecordList) {
            GeoNetworkRecord geoNetworkRecord = this.safeGet(searchClient, GeoNetworkRecord.class, recordUUID, logger);
            if (geoNetworkRecord != null) {
                String parentRecordUUID = geoNetworkRecord.getParentUUID();
                GeoNetworkRecord parentRecord = this.safeGet(searchClient, GeoNetworkRecord.class, parentRecordUUID, logger);

                if (parentRecord != null) {
                    geoNetworkRecord.setParentTitle(parentRecord.getTitle());

                    try {
                        IndexResponse indexResponse = this.indexEntity(searchClient, geoNetworkRecord, false, logger);

                        logger.addMessage(Level.INFO, String.format("Reindexing GeoNetwork metadata record: %s with parent title: %s, status: %s",
                                geoNetworkRecord.getId(),
                                geoNetworkRecord.getParentTitle(),
                                indexResponse.result()));
                    } catch(Exception ex) {
                        logger.addMessage(Level.WARNING, String.format("Exception occurred while reindexing a GeoNetwork record: %s", recordUUID), ex);
                    }
                }
            }
        }

        // Only cleanup when we are doing a full harvest
        if (!crashed && fullHarvest) {
            this.cleanUp(searchClient, harvestStart, usedThumbnails, "GeoNetwork metadata record", logger);
        }
    }

    private List<String> getCategoriesFilters() {
        List<String> categoriesFilters = new ArrayList<String>();

        if (this.geoNetworkCategories != null && !this.geoNetworkCategories.isEmpty()) {
            // 3.x = _cat, 4.x = "cat"
            String geoNetworkVersion = this.getGeoNetworkVersion();
            String categoryPropertyName =
                    (geoNetworkVersion != null && geoNetworkVersion.startsWith("3.")) ?
                    "_cat" : "cat";

            for (String category : this.geoNetworkCategories) {
                if (category != null && !category.isBlank()) {
                    String ogcFilter = "PropertyIsEqualTo";
                    String parsedCategory = category.trim();
                    if (parsedCategory.startsWith("!")) {
                        parsedCategory = parsedCategory.substring(1);
                        ogcFilter = "PropertyIsNotEqualTo";
                    }

                    categoriesFilters.add(String.format(
                        "<ogc:%s>" +
                            "<ogc:PropertyName>%s</ogc:PropertyName>" +
                            "<ogc:Literal>%s</ogc:Literal>" +
                        "</ogc:%s>",
                        ogcFilter,
                        StringEscapeUtils.escapeXml10(categoryPropertyName),
                        StringEscapeUtils.escapeXml10(parsedCategory),
                        ogcFilter));
                }
            }
        }

        return categoriesFilters;
    }

    private String andFiltersToString(List<String> andFilters) {
        String constraintFilterStr = "";
        if (!andFilters.isEmpty()) {
            String andFiltersString = "";
            if (andFilters.size() > 1) {
                StringBuilder filterStringBuilder = new StringBuilder();
                for (String andFilter : andFilters) {
                    filterStringBuilder.append(andFilter);
                }

                andFiltersString = String.format(
                    "<ogc:And>%s</ogc:And>",
                    filterStringBuilder);
            } else {
                andFiltersString = andFilters.get(0);
            }

            constraintFilterStr = String.format(
                "<Constraint version=\"1.1.0\">" +
                    "<ogc:Filter>%s</ogc:Filter>" +
                "</Constraint>",
                andFiltersString);
        }

        return constraintFilterStr;
    }

    public List<String> getGeoNetworkCategories() {
        return this.geoNetworkCategories;
    }

    public void setGeoNetworkCategories(List<String> geoNetworkCategories) {
        this.geoNetworkCategories = geoNetworkCategories;
    }

    public String getGeoNetworkCategoriesAsString() {
        if (this.geoNetworkCategories == null || this.geoNetworkCategories.isEmpty()) {
            return null;
        }
        StringJoiner joiner = new StringJoiner(", ");
        for (String category : this.geoNetworkCategories) {
            joiner.add(category);
        }
        return joiner.toString();
    }

    public void setGeoNetworkCategoriesFromString(String geoNetworkCategoriesString) {
        if (geoNetworkCategoriesString != null && !geoNetworkCategoriesString.isEmpty()) {
            this.geoNetworkCategories = Arrays.stream(geoNetworkCategoriesString.split(","))
                .map(String::trim) // Removes leading/trailing whitespace
                .filter(s -> !s.isEmpty()) // Removes empty categories
                .collect(Collectors.toList());
        } else {
            this.geoNetworkCategories = null;
        }
    }

    public class GeoNetworkCswIndexerThread extends Thread {
        private final SearchClient searchClient;
        private final AbstractLogger logger;
        private final GeoNetworkRecord geoNetworkRecord;
        private final List<String> orphanMetadataRecordList;
        private final Set<String> usedThumbnails;
        private final long current;

        public GeoNetworkCswIndexerThread(
                SearchClient searchClient,
                AbstractLogger logger,
                GeoNetworkRecord geoNetworkRecord,
                List<String> orphanMetadataRecordList,
                Set<String> usedThumbnails,
                long current
        ) {
            this.searchClient = searchClient;
            this.logger = logger;
            this.geoNetworkRecord = geoNetworkRecord;
            this.orphanMetadataRecordList = orphanMetadataRecordList;
            this.usedThumbnails = usedThumbnails;
            this.current = current;
        }

        @Override
        public void run() {
            if (this.geoNetworkRecord != null) {
                // If the record have a parent UUID,
                // keep it's UUID in a list, so we can come back to it later to set its parent title.
                String parentUUID = this.geoNetworkRecord.getParentUUID();
                if (parentUUID != null && !parentUUID.isEmpty()) {
                    this.orphanMetadataRecordList.add(this.geoNetworkRecord.getId());
                }

                GeoNetworkCswIndexer.this.updateThumbnail(
                        this.searchClient, this.geoNetworkRecord, this.logger);

                try {
                    IndexResponse indexResponse = GeoNetworkCswIndexer.this.indexEntity(this.searchClient, this.geoNetworkRecord, this.logger);

                    this.logger.addMessage(Level.INFO, String.format("[%d/%d] Indexing GeoNetwork metadata record: %s, index response status: %s",
                            this.current, GeoNetworkCswIndexer.this.getTotal(),
                            this.geoNetworkRecord.getId(),
                            indexResponse.result()));
                } catch(Exception ex) {
                    this.logger.addMessage(Level.ERROR, String.format("Exception occurred while indexing a GeoNetwork record: %s", this.geoNetworkRecord.getId()), ex);
                }

                if (this.usedThumbnails != null) {
                    String thumbnailFilename = this.geoNetworkRecord.getCachedThumbnailFilename();
                    if (thumbnailFilename != null) {
                        this.usedThumbnails.add(thumbnailFilename);
                    }
                }
            }

            GeoNetworkCswIndexer.this.incrementCompleted();
        }
    }
}
