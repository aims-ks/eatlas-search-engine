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

import au.gov.aims.eatlas.searchengine.admin.rest.Messages;
import au.gov.aims.eatlas.searchengine.client.SearchClient;
import au.gov.aims.eatlas.searchengine.entity.EntityUtils;
import au.gov.aims.eatlas.searchengine.entity.GeoNetworkRecord;
import au.gov.aims.eatlas.searchengine.rest.ImageCache;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
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
import java.util.concurrent.TimeUnit;

public class GeoNetworkIndexer extends AbstractIndexer<GeoNetworkRecord> {
    private static final Logger LOGGER = Logger.getLogger(GeoNetworkIndexer.class.getName());
    private static final int THREAD_POOL_SIZE = 10;

    private String geoNetworkUrl;
    private String geoNetworkVersion;

    public static GeoNetworkIndexer fromJSON(String index, JSONObject json) {
        if (json == null || json.isEmpty()) {
            return null;
        }

        return new GeoNetworkIndexer(
            index,
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

    public GeoNetworkRecord load(JSONObject json, Messages messages) {
        return GeoNetworkRecord.load(json, messages);
    }

    /**
     * index: eatlas_metadata
     * geoNetworkUrl: https://eatlas.org.au/geonetwork
     * geoNetworkVersion: 3.6.0 ?
     */
    public GeoNetworkIndexer(String index, String geoNetworkUrl, String geoNetworkVersion) {
        super(index);
        this.geoNetworkUrl = geoNetworkUrl;
        this.geoNetworkVersion = geoNetworkVersion;
    }

    @Override
    public boolean supportsIndexLatest() {
        return true;
    }

    @Override
    protected void internalIndex(SearchClient client, Long lastHarvested, Messages messages) {
        String lastHarvestedISODateStr = null;
        if (lastHarvested != null) {
            // Date format: YYYY-MM-DD, according to the doc for Q search (which is the same as xml.search)
            //     https://geonetwork-opensource.org/manuals/3.10.x/en/api/q-search.html#date-searches
            // NOTE: GeoNetwork last modified date (aka dateFrom) is quantised to the day.
            //     Use a 1 day margin for safety. A record might get harvested 2x,
            //     but it's better than not been harvested.
            DateTime lastHarvestedDate = new DateTime(lastHarvested).minusDays(1);
            lastHarvestedISODateStr = lastHarvestedDate.toString(DateTimeFormat.forPattern("yyyy-MM-dd"));
        }
        boolean fullHarvest = lastHarvestedISODateStr == null;

        Set<String> usedThumbnails = null;
        if (fullHarvest) {
            usedThumbnails = Collections.synchronizedSet(new HashSet<String>());
        }

        // GeoNetwork export API URL
        // If we have a "lastHarvested" parameter, request metadata records modified since that date (with a small buffer)
        // Otherwise, request everything.
        // NOTE: According to the doc, the parameter "dateFrom" is used to filter on creation date, but in fact,
        //     it filters on modification date (which is what we want).
        //     https://geonetwork-opensource.org/manuals/2.10.4/eng/developer/xml_services/metadata_xml_search_retrieve.html
        String urlBase = String.format("%s/srv/eng/xml.search", this.geoNetworkUrl);
        URIBuilder uriBuilder;
        try {
            uriBuilder = new URIBuilder(urlBase);
        } catch(URISyntaxException ex) {
            messages.addMessage(Messages.Level.ERROR,
                    String.format("Invalid GeoNetwork URL. Exception occurred while building the URL: %s", urlBase), ex);
            return;
        }
        if (!fullHarvest) {
            uriBuilder.setParameter("dateFrom", lastHarvestedISODateStr);
        }

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

            String responseStr = null;
            try {
                responseStr = EntityUtils.harvestGetURL(url, messages);
            } catch(Exception ex) {
                if (!crashed) {
                    messages.addMessage(Messages.Level.ERROR, String.format("Exception occurred while harvesting GeoNetwork record list: %s",
                            url), ex);
                }
                crashed = true;
            }

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

                                GeoNetworkIndexerThread thread = new GeoNetworkIndexerThread(
                                        client, messages, factory, metadataRecordUUID, metadataSchema,
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
            client.refresh(this.getIndex());
        } catch(Exception ex) {
            messages.addMessage(Messages.Level.ERROR, String.format("Exception occurred while refreshing the search index: %s", this.getIndex()), ex);
        }

        // We have added all the records.
        // Lets fix the records parent title.
        for (String recordUUID : orphanMetadataRecordList) {
            GeoNetworkRecord geoNetworkRecord = this.safeGet(client, GeoNetworkRecord.class, recordUUID, messages);
            if (geoNetworkRecord != null) {
                String parentRecordUUID = geoNetworkRecord.getParentUUID();
                GeoNetworkRecord parentRecord = this.safeGet(client, GeoNetworkRecord.class, parentRecordUUID, messages);

                if (parentRecord != null) {
                    geoNetworkRecord.setParentTitle(parentRecord.getTitle());

                    try {
                        IndexResponse indexResponse = this.indexEntity(client, geoNetworkRecord, messages, false);

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
            this.cleanUp(client, harvestStart, usedThumbnails, "GeoNetwork metadata record", messages);
        }
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


    public class GeoNetworkIndexerThread extends Thread {
        private final SearchClient client;
        private final Messages messages;
        private final DocumentBuilderFactory documentBuilderFactory;
        private final String metadataRecordUUID;
        private final String metadataSchema;
        private final List<String> orphanMetadataRecordList;
        private final Set<String> usedThumbnails;
        private final long current;

        public GeoNetworkIndexerThread(
                SearchClient client,
                Messages messages,
                DocumentBuilderFactory documentBuilderFactory,
                String metadataRecordUUID,
                String metadataSchema,
                List<String> orphanMetadataRecordList,
                Set<String> usedThumbnails,
                long current
        ) {
            this.client = client;
            this.messages = messages;
            this.documentBuilderFactory = documentBuilderFactory;
            this.metadataRecordUUID = metadataRecordUUID;
            this.metadataSchema = metadataSchema;
            this.orphanMetadataRecordList = orphanMetadataRecordList;
            this.usedThumbnails = usedThumbnails;
            this.current = current;
        }

        @Override
        public void run() {
            GeoNetworkRecord geoNetworkRecord = this.loadGeoNetworkRecord(this.client, this.documentBuilderFactory, this.metadataRecordUUID, this.metadataSchema);
            if (geoNetworkRecord != null) {
                // If the record have a parent UUID,
                // keep it's UUID in a list so we can come back to it later to set its parent title.
                String parentUUID = geoNetworkRecord.getParentUUID();
                if (parentUUID != null && !parentUUID.isEmpty()) {
                    this.orphanMetadataRecordList.add(this.metadataRecordUUID);
                }

                try {
                    IndexResponse indexResponse = GeoNetworkIndexer.this.indexEntity(this.client, geoNetworkRecord, this.messages);

                    LOGGER.debug(String.format("[%d/%d] Indexing GeoNetwork metadata record: %s, index response status: %s",
                            this.current, GeoNetworkIndexer.this.getTotal(),
                            this.metadataRecordUUID,
                            indexResponse.result()));
                } catch(Exception ex) {
                    this.messages.addMessage(Messages.Level.WARNING, String.format("Exception occurred while indexing a GeoNetwork record: %s", this.metadataRecordUUID), ex);
                }

                if (this.usedThumbnails != null) {
                    String thumbnailFilename = geoNetworkRecord.getCachedThumbnailFilename();
                    if (thumbnailFilename != null) {
                        this.usedThumbnails.add(thumbnailFilename);
                    }
                }
            }

            GeoNetworkIndexer.this.incrementCompleted();
        }

        private GeoNetworkRecord loadGeoNetworkRecord(SearchClient client, DocumentBuilderFactory documentBuilderFactory, String metadataRecordUUID, String metadataSchema) {
            String url;
            String geoNetworkUrl = GeoNetworkIndexer.this.getGeoNetworkUrl();
            String urlBase = String.format("%s/srv/eng/xml.metadata.get", geoNetworkUrl);
            try {
                URIBuilder b = new URIBuilder(urlBase);
                b.setParameter("uuid", metadataRecordUUID);
                URL urlObj = b.build().toURL();

                url = urlObj.toString();
            } catch (Exception ex) {
                this.messages.addMessage(Messages.Level.ERROR, String.format("Exception occurred while building the URL to harvest the metadata record UUID: %s%nUrl: %s",
                        metadataRecordUUID, urlBase), ex);

                return null;
            }

            String responseStr;
            try {
                //LOGGER.info(String.format("Harvesting metadata record UUID: %s from: %s", metadataRecordUUID, url));
                responseStr = EntityUtils.harvestGetURL(url, this.messages);
            } catch (Exception ex) {
                this.messages.addMessage(Messages.Level.ERROR, String.format("Exception occurred while harvesting the metadata record UUID: %s%nUrl: %s",
                        metadataRecordUUID, url), ex);

                return null;
            }

            if (responseStr != null && !responseStr.isEmpty()) {
                try (ByteArrayInputStream input = new ByteArrayInputStream(responseStr.getBytes(StandardCharsets.UTF_8))) {

                    DocumentBuilder builder;
                    try {
                        builder = documentBuilderFactory.newDocumentBuilder();
                    } catch(Exception ex) {
                        // Should not happen
                        this.messages.addMessage(Messages.Level.ERROR, String.format("Exception occurred while creating XML document builder for index: %s",
                                GeoNetworkIndexer.this.getIndex()), ex);
                        return null;
                    }

                    Document document = builder.parse(input);
                    GeoNetworkRecord geoNetworkRecord = new GeoNetworkRecord(GeoNetworkIndexer.this.getIndex());
                    geoNetworkRecord.parseRecord(metadataRecordUUID, metadataSchema, geoNetworkUrl, document, this.messages);

                    if (geoNetworkRecord.getId() != null) {
                        URL thumbnailUrl = geoNetworkRecord.getThumbnailUrl();
                        geoNetworkRecord.setThumbnailUrl(thumbnailUrl);

                        // Create the thumbnail if it's missing or outdated
                        GeoNetworkRecord oldRecord = GeoNetworkIndexer.this.safeGet(client, GeoNetworkRecord.class, geoNetworkRecord.getId(), this.messages);
                        if (geoNetworkRecord.isThumbnailOutdated(oldRecord, GeoNetworkIndexer.this.getSafeThumbnailTTL(), GeoNetworkIndexer.this.getSafeBrokenThumbnailTTL(), this.messages)) {
                            try {
                                File cachedThumbnailFile = ImageCache.cache(thumbnailUrl, GeoNetworkIndexer.this.getIndex(), geoNetworkRecord.getId(), this.messages);
                                if (cachedThumbnailFile != null) {
                                    geoNetworkRecord.setCachedThumbnailFilename(cachedThumbnailFile.getName());
                                }
                            } catch(Exception ex) {
                                this.messages.addMessage(Messages.Level.WARNING, String.format("Exception occurred while creating a thumbnail for metadata record UUID: %s",
                                        metadataRecordUUID), ex);
                            }
                            geoNetworkRecord.setThumbnailLastIndexed(System.currentTimeMillis());
                        } else {
                            geoNetworkRecord.useCachedThumbnail(oldRecord);
                        }

                        return geoNetworkRecord;
                    } else {
                        this.messages.addMessage(Messages.Level.ERROR, String.format("Invalid metadata record UUID: %s", metadataRecordUUID));
                    }
                } catch(Exception ex) {
                    this.messages.addMessage(Messages.Level.ERROR, String.format("Exception occurred while harvesting metadata record UUID: %s - %s", metadataRecordUUID, url), ex);
                }
            }

            return null;
        }

    }
}
