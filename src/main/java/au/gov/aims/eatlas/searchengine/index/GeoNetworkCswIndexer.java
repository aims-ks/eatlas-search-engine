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
import au.gov.aims.eatlas.searchengine.admin.rest.Messages;
import au.gov.aims.eatlas.searchengine.client.SearchClient;
import au.gov.aims.eatlas.searchengine.entity.GeoNetworkRecord;
import au.gov.aims.eatlas.searchengine.entity.geoNetworkParser.AbstractParser;
import au.gov.aims.eatlas.searchengine.entity.geoNetworkParser.ISO19115_3_2018_parser;
import au.gov.aims.eatlas.searchengine.rest.ImageCache;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import org.apache.http.client.utils.URIBuilder;
import org.apache.log4j.Logger;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

public class GeoNetworkCswIndexer extends AbstractIndexer<GeoNetworkRecord> {
    private static final Logger LOGGER = Logger.getLogger(GeoNetworkCswIndexer.class.getName());
    private static final int THREAD_POOL_SIZE = 10;

    private String geoNetworkUrl;
    private String geoNetworkVersion;

    public static GeoNetworkCswIndexer fromJSON(HttpClient httpClient, String index, JSONObject json) {
        if (json == null || json.isEmpty()) {
            return null;
        }

        return new GeoNetworkCswIndexer(
            httpClient, index,
            json.optString("geoNetworkUrl", null),
            json.optString("geoNetworkVersion", null));
    }

    public JSONObject toJSON() {
        return this.getJsonBase()
            .put("geoNetworkUrl", this.geoNetworkUrl)
            .put("geoNetworkVersion", this.geoNetworkVersion);
    }

    @Override
    public boolean validate() {
        if (!super.validate()) {
            return false;
        }
        if (this.geoNetworkUrl == null || this.geoNetworkUrl.isEmpty()) {
            return false;
        }
        return true;
    }

    @Override
    public GeoNetworkRecord load(JSONObject json, Messages messages) {
        return GeoNetworkRecord.load(json, messages);
    }

    @Override
    protected GeoNetworkRecord harvestEntity(SearchClient searchClient, String id, Messages messages) {
        // Find the metadata schema from the record from the index.
        GeoNetworkRecord oldRecord = this.safeGet(searchClient, GeoNetworkRecord.class, id, messages);
        String metadataSchema = oldRecord.getMetadataSchema();

        // Re-harvest the record.
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        return this.harvestEntity(searchClient, factory, id, metadataSchema, messages);
    }

    // TODO Implement (if needed...)
    private GeoNetworkRecord harvestEntity(SearchClient searchClient, DocumentBuilderFactory documentBuilderFactory, String metadataRecordUUID, String metadataSchema, Messages messages) {
        HttpClient httpClient = this.getHttpClient();

// TODO Branch depending on version (csw)
        String url;
        String geoNetworkUrl = this.getGeoNetworkUrl();
        String urlBase = String.format("%s/srv/eng/xml.metadata.get", geoNetworkUrl);
        try {
            URIBuilder b = new URIBuilder(urlBase);
            b.setParameter("uuid", metadataRecordUUID);
            URL urlObj = b.build().toURL();

            url = urlObj.toString();
        } catch (Exception ex) {
            messages.addMessage(Messages.Level.ERROR, String.format("Exception occurred while building the URL to harvest the metadata record UUID: %s%nUrl: %s",
                    metadataRecordUUID, urlBase), ex);

            return null;
        }

        HttpClient.Response response = null;
        try {
            //LOGGER.info(String.format("Harvesting metadata record UUID: %s from: %s", metadataRecordUUID, url));
            response = httpClient.getRequest(url, messages);
        } catch (Exception ex) {
            messages.addMessage(Messages.Level.ERROR, String.format("Exception occurred while harvesting the metadata record UUID: %s%nUrl: %s",
                    metadataRecordUUID, url), ex);

            return null;
        }

        String responseStr = response == null ? null : response.body();
        if (responseStr != null && !responseStr.isEmpty()) {
            try (ByteArrayInputStream input = new ByteArrayInputStream(responseStr.getBytes(StandardCharsets.UTF_8))) {

                DocumentBuilder builder;
                try {
                    builder = documentBuilderFactory.newDocumentBuilder();
                } catch(Exception ex) {
                    // Should not happen
                    messages.addMessage(Messages.Level.ERROR, String.format("Exception occurred while creating XML document builder for index: %s",
                            this.getIndex()), ex);
                    return null;
                }

                Document document = builder.parse(input);
                GeoNetworkRecord geoNetworkRecord = new GeoNetworkRecord(this.getIndex(), metadataRecordUUID, metadataSchema, this.geoNetworkVersion);
                geoNetworkRecord.parseRecord(geoNetworkUrl, document, messages);

                if (geoNetworkRecord.getId() != null) {
                    URL thumbnailUrl = geoNetworkRecord.getThumbnailUrl();
                    geoNetworkRecord.setThumbnailUrl(thumbnailUrl);

                    // Create the thumbnail if it's missing or outdated
                    GeoNetworkRecord oldRecord = this.safeGet(searchClient, GeoNetworkRecord.class, geoNetworkRecord.getId(), messages);
                    if (geoNetworkRecord.isThumbnailOutdated(oldRecord, this.getSafeThumbnailTTL(), this.getSafeBrokenThumbnailTTL(), messages)) {
                        try {
                            File cachedThumbnailFile = ImageCache.cache(httpClient, thumbnailUrl, this.getIndex(), geoNetworkRecord.getId(), messages);
                            if (cachedThumbnailFile != null) {
                                geoNetworkRecord.setCachedThumbnailFilename(cachedThumbnailFile.getName());
                            }
                        } catch(Exception ex) {
                            messages.addMessage(Messages.Level.WARNING, String.format("Exception occurred while creating a thumbnail for metadata record UUID: %s",
                                    metadataRecordUUID), ex);
                        }
                        geoNetworkRecord.setThumbnailLastIndexed(System.currentTimeMillis());
                    } else {
                        geoNetworkRecord.useCachedThumbnail(oldRecord);
                    }

                    return geoNetworkRecord;
                } else {
                    messages.addMessage(Messages.Level.ERROR, String.format("Invalid metadata record UUID: %s", metadataRecordUUID));
                }
            } catch(Exception ex) {
                messages.addMessage(Messages.Level.ERROR, String.format("Exception occurred while harvesting metadata record UUID: %s - %s", metadataRecordUUID, url), ex);
            }
        }

        return null;
    }


    /**
     * index: eatlas_metadata
     * geoNetworkUrl: https://eatlas.org.au/geonetwork
     * geoNetworkVersion: 3.6.0 ?
     */
    public GeoNetworkCswIndexer(HttpClient httpClient, String index, String geoNetworkUrl, String geoNetworkVersion) {
        super(httpClient, index);
        this.geoNetworkUrl = geoNetworkUrl;
        this.geoNetworkVersion = geoNetworkVersion;
    }

    @Override
    public boolean supportsIndexLatest() {
        return true;
    }

    @Override
    protected void internalIndex(SearchClient searchClient, Long lastHarvested, Messages messages) {
        HttpClient httpClient = this.getHttpClient();

        boolean fullHarvest = lastHarvested == null;

        Set<String> usedThumbnails = null;
        if (fullHarvest) {
            usedThumbnails = Collections.synchronizedSet(new HashSet<String>());
        }

        // GeoNetwork's CSW API URL
        // The request is provided as XML data in a POST request.
        String urlBase = String.format("%s/srv/eng/csw", this.geoNetworkUrl);
        URIBuilder uriBuilder;
        try {
            uriBuilder = new URIBuilder(urlBase);
        } catch(URISyntaxException ex) {
            messages.addMessage(Messages.Level.ERROR,
                    String.format("Invalid GeoNetwork URL. Exception occurred while building the URL: %s", urlBase), ex);
            return;
        }

        String url;
        try {
            url = uriBuilder.build().toURL().toString();
        } catch(Exception ex) {
            // Should not happen
            messages.addMessage(Messages.Level.ERROR,
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
        String recordFilterQuery = "";
        if (!fullHarvest) {
            // Harvest only modified records
            recordFilterQuery =
                "<Constraint version=\"1.1.0\">" +
                    "<ogc:Filter>" +
                        "<ogc:PropertyIsGreaterThan>" +
                            "<ogc:PropertyName>Modified</ogc:PropertyName>" +
                            "<ogc:Literal>" + lastHarvested + "</ogc:Literal>" +
                        "</ogc:PropertyIsGreaterThan>" +
                    "</ogc:Filter>" +
                "</Constraint>";
        }

        String outputSchema = "http://standards.iso.org/iso/19115/-3/mdb/2.0"; // iso19115-3.2018
        int startPosition = 1;
        int recordCounter = 0;
        int recordsPerPage = 10;

        int numberOfRecordsMatched = 0;
        boolean crashed = false;
        boolean empty = false;

        // JDOM tutorial:
        //     https://www.tutorialspoint.com/java_xml/java_dom_parse_document.htm
        DocumentBuilderFactory xmlDocumentFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder xmlDocumentBuilder;
        try {
            xmlDocumentBuilder = xmlDocumentFactory.newDocumentBuilder();
        } catch(Exception ex) {
            // Should not happen
            messages.addMessage(Messages.Level.ERROR, String.format("Exception occurred while creating XML document builder for index: %s",
                    this.getIndex()), ex);
            return;
        }

        // List of metadata records which needs its parent title to be set
        List<String> orphanMetadataRecordList = Collections.synchronizedList(new ArrayList<>());
        ThreadPoolExecutor threadPool = (ThreadPoolExecutor) Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        AbstractParser metadataRecordParser = new ISO19115_3_2018_parser();

        do {
            String xmlQuery = "<?xml version=\"1.0\"?>\n" +
                "<GetRecords xmlns=\"http://www.opengis.net/cat/csw/2.0.2\" " +
                        "xmlns:ogc=\"http://www.opengis.net/ogc\" " +
                        "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" " +
                        "service=\"CSW\" " +
                        "version=\"2.0.2\" " +
                        "resultType=\"results\" " +
                        "startPosition=\"" + startPosition + "\" " +
                        "maxRecords=\"" + recordsPerPage + "\" " +
                        "outputSchema=\"" + outputSchema + "\" " +
                        "xsi:schemaLocation=\"http://www.opengis.net/cat/csw/2.0.2 http://schemas.opengis.net/csw/2.0.2/CSW-discovery.xsd\">" +
                    "<Query typeNames=\"mdb:MD_Metadata\">" +
                        "<ElementSetName>full</ElementSetName>" +
                        recordFilterQuery +
                        recordSortQuery +
                    "</Query>" +
                "</GetRecords>";

            HttpClient.Response response = null;
            try {
                response = httpClient.postXmlRequest(url, xmlQuery, messages);
            } catch(Exception ex) {
                if (!crashed) {
                    messages.addMessage(Messages.Level.ERROR, String.format("Exception occurred while harvesting GeoNetwork record list: %s",
                            url), ex);
                }
                crashed = true;
            }

            int statusCode = response == null ? -1 : response.statusCode();
            String responseStr = response == null ? null : response.body();

            if (statusCode < 200 || statusCode >= 400) {
                messages.addMessage(Messages.Level.ERROR, String.format("Unexpected status code returned from GeoNetwork: %d%nResponse: %s",
                        statusCode, responseStr));
                crashed = true;
            } else if (responseStr != null && !responseStr.isEmpty()) {
                try (ByteArrayInputStream input = new ByteArrayInputStream(
                    responseStr.getBytes(StandardCharsets.UTF_8))) {

                    Document document = xmlDocumentBuilder.parse(input);

                    // Fix the document, if needed
                    document.getDocumentElement().normalize();

                    Element root = document.getDocumentElement();

                    Element searchResultsElement = IndexUtils.getXMLChild(root, "csw:SearchResults");

                    // Loop through mdb:MD_Metadata, parse them with ISO19115_3_2018_parser
                    List<Element> metadataElements = IndexUtils.getXMLChildren(searchResultsElement, "mdb:MD_Metadata");
                    for (Element metadataElement : metadataElements) {
                        recordCounter++;
                        GeoNetworkRecord geoNetworkRecord = new GeoNetworkRecord(this.getIndex(), null, "iso19115-3.2018", this.geoNetworkVersion);
                        metadataRecordParser.parseRecord(geoNetworkRecord, this.geoNetworkUrl, metadataElement, messages);

                        // Index records in thread.
                        // NOTE: The record parsing can't be threaded with the CSW API
                        //   since the information for the next page is in the XML response
                        //   and the response also contains the full metadata records (not just the record UUID).
                        //   It needs to be parsed sequentially.
                        GeoNetworkCswIndexerThread thread = new GeoNetworkCswIndexerThread(
                                searchClient, messages, geoNetworkRecord,
                                orphanMetadataRecordList, usedThumbnails, recordCounter);

                        threadPool.execute(thread);
                    }

                    // Prepare for next page of result
                    String nextRecordStr = IndexUtils.parseAttribute(searchResultsElement, "nextRecord");
                    startPosition = nextRecordStr == null ? -1 : Integer.parseInt(nextRecordStr);

                    String numberOfRecordsMatchedStr = IndexUtils.parseAttribute(searchResultsElement, "numberOfRecordsMatched");
                    numberOfRecordsMatched = numberOfRecordsMatchedStr == null ? -1 : Integer.parseInt(numberOfRecordsMatchedStr);
                    this.setTotal((long)numberOfRecordsMatched);
                } catch (Exception ex) {
                    messages.addMessage(Messages.Level.ERROR, String.format("Exception occurred while parsing the GeoNetwork record list: %s",
                            url), ex);
                    crashed = true;
                }
            }

        } while(!crashed && !empty && startPosition > 0 && startPosition <= numberOfRecordsMatched);


        /*
        // List of metadata records which needs its parent title to be set
        List<String> orphanMetadataRecordList = Collections.synchronizedList(new ArrayList<>());
        ThreadPoolExecutor threadPool = (ThreadPoolExecutor) Executors.newFixedThreadPool(THREAD_POOL_SIZE);

        long from = 1;

        boolean hasMore = false;
        boolean empty = false;
        boolean crashed = false;

        long harvestStart = System.currentTimeMillis();
        do {
            // Set the "from" parameter without setting the "to" parameter,
            // to get as many records as GeoNetwork is willing to return in one query.
            // - GeoNetwork 2: The entire repository
            // - GeoNetwork 3: 100 records
            uriBuilder.setParameter("from", String.valueOf(from));

            String url;
            try {
                url = uriBuilder.build().toURL().toString();
            } catch(Exception ex) {
                // Should not happen
                messages.addMessage(Messages.Level.ERROR,
                        String.format("Invalid GeoNetwork URL. Exception occurred while building a URL starting with: %s", urlBase), ex);
                return;
            }

            HttpClient.Response response = null;
            try {
                response = httpClient.getRequest(url, messages);
            } catch(Exception ex) {
                if (!crashed) {
                    messages.addMessage(Messages.Level.ERROR, String.format("Exception occurred while harvesting GeoNetwork record list: %s",
                            url), ex);
                }
                crashed = true;
            }

            String responseStr = response == null ? null : response.body();
            if (responseStr != null && !responseStr.isEmpty()) {

                // JDOM tutorial:
                //     https://www.tutorialspoint.com/java_xml/java_dom_parse_document.htm
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

                try (ByteArrayInputStream input = new ByteArrayInputStream(
                    responseStr.getBytes(StandardCharsets.UTF_8))) {

                    DocumentBuilder builder;
                    try {
                        builder = factory.newDocumentBuilder();
                    } catch(Exception ex) {
                        // Should not happen
                        messages.addMessage(Messages.Level.ERROR, String.format("Exception occurred while creating XML document builder for index: %s",
                                this.getIndex()), ex);
                        return;
                    }
                    Document document = builder.parse(input);

                    // Fix the document, if needed
                    document.getDocumentElement().normalize();

                    Element root = document.getDocumentElement();

                    List<Element> metadataRecordList = IndexUtils.getXMLChildren(root, "metadata");
                    if (metadataRecordList.isEmpty()) {
                        empty = true;
                    }

                    if (!empty) {
                        Long count = null;
                        Element summaryElement = IndexUtils.getXMLChild(root, "summary");
                        if (summaryElement != null) {
                            String countStr = summaryElement.getAttribute("count");
                            if (countStr != null && !countStr.isEmpty()) {
                                count = Long.parseLong(countStr);
                            }
                        }
                        if (count == null) {
                            // Should not happen
                            count = (long)metadataRecordList.size();
                        }
                        this.setTotal(count);

                        for (Element metadataRecordElement : metadataRecordList) {
                            Element metadataRecordInfoElement = IndexUtils.getXMLChild(metadataRecordElement, "geonet:info");
                            Element metadataRecordUUIDElement = IndexUtils.getXMLChild(metadataRecordInfoElement, "uuid");
                            Element metadataSchemaElement = IndexUtils.getXMLChild(metadataRecordInfoElement, "schema");
                            if (metadataRecordUUIDElement != null && metadataSchemaElement != null) {
                                String metadataRecordUUID = IndexUtils.parseText(metadataRecordUUIDElement);
                                String metadataSchema = IndexUtils.parseText(metadataSchemaElement);

                                GeoNetworkCswIndexerThread thread = new GeoNetworkCswIndexerThread(
                                        searchClient, messages, factory, metadataRecordUUID, metadataSchema,
                                        orphanMetadataRecordList, usedThumbnails, from);

                                threadPool.execute(thread);
                            }

                            from++;
                        }
                    }
                } catch (Exception ex) {
                    messages.addMessage(Messages.Level.ERROR, String.format("Exception occurred while parsing the GeoNetwork record list: %s",
                            url), ex);
                }

                long total = this.getTotal() == null ? 0 : this.getTotal();
                hasMore = from < total;
            }
        } while(hasMore && !empty && !crashed);

        threadPool.shutdown();
        try {
            threadPool.awaitTermination(1, TimeUnit.HOURS);
        } catch(InterruptedException ex) {
            messages.addMessage(Messages.Level.ERROR, "The GeoNetwork indexation was interrupted", ex);
        }

        // Refresh the index to be sure to find parent records, if they are new
        try {
            searchClient.refresh(this.getIndex());
        } catch(Exception ex) {
            messages.addMessage(Messages.Level.ERROR, String.format("Exception occurred while refreshing the search index: %s", this.getIndex()), ex);
        }

        // We have added all the records.
        // Lets fix the records parent title.
        for (String recordUUID : orphanMetadataRecordList) {
            GeoNetworkRecord geoNetworkRecord = this.safeGet(searchClient, GeoNetworkRecord.class, recordUUID, messages);
            if (geoNetworkRecord != null) {
                String parentRecordUUID = geoNetworkRecord.getParentUUID();
                GeoNetworkRecord parentRecord = this.safeGet(searchClient, GeoNetworkRecord.class, parentRecordUUID, messages);

                if (parentRecord != null) {
                    geoNetworkRecord.setParentTitle(parentRecord.getTitle());

                    try {
                        IndexResponse indexResponse = this.indexEntity(searchClient, geoNetworkRecord, messages, false);

                        LOGGER.debug(String.format("Reindexing GeoNetwork metadata record: %s with parent title: %s, status: %s",
                                geoNetworkRecord.getId(),
                                geoNetworkRecord.getParentTitle(),
                                indexResponse.result()));
                    } catch(Exception ex) {
                        messages.addMessage(Messages.Level.WARNING, String.format("Exception occurred while reindexing a GeoNetwork record: %s", recordUUID), ex);
                    }
                }
            }
        }

        // Only cleanup when we are doing a full harvest
        if (!crashed && fullHarvest) {
            this.cleanUp(searchClient, harvestStart, usedThumbnails, "GeoNetwork metadata record", messages);
        }
        */
    }

    public String getGeoNetworkUrl() {
        return this.geoNetworkUrl;
    }

    public void setGeoNetworkUrl(String geoNetworkUrl) {
        this.geoNetworkUrl = geoNetworkUrl;
    }

    public String getGeoNetworkVersion() {
        return this.geoNetworkVersion;
    }

    public void setGeoNetworkVersion(String geoNetworkVersion) {
        this.geoNetworkVersion = geoNetworkVersion;
    }


    public class GeoNetworkCswIndexerThread extends Thread {
        private final SearchClient searchClient;
        private final Messages messages;
        private final GeoNetworkRecord geoNetworkRecord;
        private final List<String> orphanMetadataRecordList;
        private final Set<String> usedThumbnails;
        private final long current;

        public GeoNetworkCswIndexerThread(
                SearchClient searchClient,
                Messages messages,
                GeoNetworkRecord geoNetworkRecord,
                List<String> orphanMetadataRecordList,
                Set<String> usedThumbnails,
                long current
        ) {
            this.searchClient = searchClient;
            this.messages = messages;
            this.geoNetworkRecord = geoNetworkRecord;
            this.orphanMetadataRecordList = orphanMetadataRecordList;
            this.usedThumbnails = usedThumbnails;
            this.current = current;
        }

        @Override
        public void run() {
            if (this.geoNetworkRecord != null) {
                // If the record have a parent UUID,
                // keep it's UUID in a list so we can come back to it later to set its parent title.
                String parentUUID = this.geoNetworkRecord.getParentUUID();
                if (parentUUID != null && !parentUUID.isEmpty()) {
                    this.orphanMetadataRecordList.add(this.geoNetworkRecord.getId());
                }

                try {
                    IndexResponse indexResponse = GeoNetworkCswIndexer.this.indexEntity(this.searchClient, this.geoNetworkRecord, this.messages);

                    LOGGER.debug(String.format("[%d/%d] Indexing GeoNetwork metadata record: %s, index response status: %s",
                            this.current, GeoNetworkCswIndexer.this.getTotal(),
                            this.geoNetworkRecord.getId(),
                            indexResponse.result()));
                } catch(Exception ex) {
                    this.messages.addMessage(Messages.Level.WARNING, String.format("Exception occurred while indexing a GeoNetwork record: %s", this.geoNetworkRecord.getId()), ex);
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
