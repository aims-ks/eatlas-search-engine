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

import au.gov.aims.eatlas.searchengine.client.ESClient;
import au.gov.aims.eatlas.searchengine.entity.EntityUtils;
import au.gov.aims.eatlas.searchengine.entity.GeoNetworkRecord;
import au.gov.aims.eatlas.searchengine.rest.ImageCache;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import org.joda.time.DateTime;
import org.joda.time.format.ISODateTimeFormat;
import org.apache.http.client.utils.URIBuilder;
import org.apache.log4j.Logger;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GeoNetworkIndexer extends AbstractIndexer {
    private static final Logger LOGGER = Logger.getLogger(GeoNetworkIndexer.class.getName());

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

    public GeoNetworkRecord load(JSONObject json) {
        return GeoNetworkRecord.load(json);
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
    protected void internalHarvest(ESClient client, Long lastHarvested) {
        String lastHarvestedISODateStr = null;
        if (lastHarvested != null) {
            // NOTE: GeoNetwork last modified date (aka dateFrom) are rounded to second,
            //     and can be a bit off. Use a 10s margin for safety.
            DateTime lastHarvestedDate = new DateTime(lastHarvested + 10000);
            if (lastHarvestedDate != null) {
                lastHarvestedISODateStr = lastHarvestedDate.toString(ISODateTimeFormat.dateTimeNoMillis());
            }
        }
        boolean fullHarvest = lastHarvestedISODateStr == null;

        Set<String> usedThumbnails = null;
        if (fullHarvest) {
            usedThumbnails = new HashSet<String>();
        }

        // TODO PAGING!

        // GeoNetwork export API URL
        // If we have a "lastHarvested" parameter, request metadata records modified since that date (with a small buffer)
        // Otherwise, request everything.
        // NOTE: According to the doc, the parameter "dateFrom" is used to filter on creation date, but in fact,
        //     it filters on modification date (which is what we want).
        //     https://geonetwork-opensource.org/manuals/2.10.4/eng/developer/xml_services/metadata_xml_search_retrieve.html
        String url = fullHarvest ?
            String.format("%s/srv/eng/xml.search", this.geoNetworkUrl) :
            String.format("%s/srv/eng/xml.search?dateFrom=%s",
                this.geoNetworkUrl, lastHarvestedISODateStr);

        String responseStr = null;
        try {
            responseStr = EntityUtils.harvestGetURL(url);
        } catch(Exception ex) {
            LOGGER.error(String.format("Exception occurred while harvesting GeoNetwork record list: %s",
                    url), ex);
            return;
        }

        if (responseStr != null && !responseStr.isEmpty()) {

            // JDOM tutorial:
            //     https://www.tutorialspoint.com/java_xml/java_dom_parse_document.htm
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = null;
            try {
                builder = factory.newDocumentBuilder();
            } catch(Exception ex) {
                // Should not happen
                LOGGER.error(String.format("Exception occurred while creating XML document builder for index: %s",
                        this.getIndex()), ex);
                return;
            }

            try (ByteArrayInputStream input = new ByteArrayInputStream(
                responseStr.getBytes(StandardCharsets.UTF_8))) {

                Document document = builder.parse(input);
                // Fix the document, if needed
                document.getDocumentElement().normalize();

                Element root = document.getDocumentElement();

                List<Element> metadataRecordList = IndexUtils.getXMLChildren(root, "metadata");

                long harvestStart = System.currentTimeMillis();

                // List of metadata records which needs its parent title to be set
                List<String> orphanMetadataRecordList = new ArrayList<>();
                int total = metadataRecordList.size();
                int current = 0;
                for (Element metadataRecordElement : metadataRecordList) {
                    current++;
                    Element metadataRecordInfoElement = IndexUtils.getXMLChild(metadataRecordElement, "geonet:info");
                    Element metadataRecordUUIDElement = IndexUtils.getXMLChild(metadataRecordInfoElement, "uuid");
                    if (metadataRecordUUIDElement != null) {
                        String metadataRecordUUID = IndexUtils.parseText(metadataRecordUUIDElement);

                        GeoNetworkRecord geoNetworkRecord = this.loadGeoNetworkRecord(client, builder, metadataRecordUUID);
                        if (geoNetworkRecord != null) {
                            // If the record have a parent UUID,
                            // keep it's UUID in a list so we can come back to it later to set its parent title.
                            String parentUUID = geoNetworkRecord.getParentUUID();
                            if (parentUUID != null && !parentUUID.isEmpty()) {
                                orphanMetadataRecordList.add(metadataRecordUUID);
                            }

                            try {
                                IndexResponse indexResponse = this.index(client, geoNetworkRecord);

                                LOGGER.debug(String.format("[%d/%d] Indexing GeoNetwork metadata record: %s, index response status: %s",
                                        current, total,
                                        geoNetworkRecord.getId(),
                                        indexResponse.result()));
                            } catch(Exception ex) {
                                LOGGER.warn(String.format("Exception occurred while indexing a GeoNetwork record: %s", metadataRecordUUID), ex);
                            }

                            if (usedThumbnails != null) {
                                String thumbnailFilename = geoNetworkRecord.getCachedThumbnailFilename();
                                if (thumbnailFilename != null) {
                                    usedThumbnails.add(thumbnailFilename);
                                }
                            }
                        }
                    }
                }

                // Refresh the index to be sure to find parent records, if they are new
                try {
                    client.refresh(this.getIndex());
                } catch(Exception ex) {
                    LOGGER.error(String.format("Exception occurred while refreshing the search index: %s", this.getIndex()), ex);
                }

                // We have added all the records.
                // Lets fix the records parent title.
                for (String recordUUID : orphanMetadataRecordList) {
                    GeoNetworkRecord geoNetworkRecord = (GeoNetworkRecord)this.safeGet(client, recordUUID);;
                    if (geoNetworkRecord != null) {
                        String parentRecordUUID = geoNetworkRecord.getParentUUID();
                        GeoNetworkRecord parentRecord = (GeoNetworkRecord)this.safeGet(client, parentRecordUUID);

                        if (parentRecord != null) {
                            geoNetworkRecord.setParentTitle(parentRecord.getTitle());

                            try {
                                IndexResponse indexResponse = this.index(client, geoNetworkRecord);

                                LOGGER.debug(String.format("Re-indexing GeoNetwork metadata record: %s with parent title: %s, status: %s",
                                        geoNetworkRecord.getId(),
                                        geoNetworkRecord.getParentTitle(),
                                        indexResponse.result()));
                            } catch(Exception ex) {
                                LOGGER.warn(String.format("Exception occurred while re-indexing a GeoNetwork record: %s", recordUUID), ex);
                            }
                        }
                    }
                }

                // Only cleanup when we are doing a full harvest
                if (fullHarvest) {
                    this.cleanUp(client, harvestStart, usedThumbnails, "GeoNetwork metadata record");
                }
            } catch (Exception ex) {
                LOGGER.error(String.format("Exception occurred while parsing the GeoNetwork record list: %s",
                        url), ex);
            }
        }
    }

    private GeoNetworkRecord loadGeoNetworkRecord(ESClient client, DocumentBuilder builder, String metadataRecordUUID) {
        String url;
        String urlBase = String.format("%s/srv/eng/xml.metadata.get", this.geoNetworkUrl);
        try {
            URIBuilder b = new URIBuilder(urlBase);
            b.addParameter("uuid", metadataRecordUUID);
            URL urlObj = b.build().toURL();

            url = urlObj.toString();
        } catch (Exception ex) {
            LOGGER.error(String.format("Exception occurred while building the URL to harvest the metadata record UUID: %s%nUrl: %s",
                    metadataRecordUUID, urlBase), ex);

            return null;
        }

        String responseStr;
        try {
            //LOGGER.info(String.format("Harvesting metadata record UUID: %s from: %s", metadataRecordUUID, url));
            responseStr = EntityUtils.harvestGetURL(url);
        } catch (Exception ex) {
            LOGGER.error(String.format("Exception occurred while harvesting the metadata record UUID: %s%nUrl: %s",
                    metadataRecordUUID, url), ex);

            return null;
        }

        if (responseStr != null && !responseStr.isEmpty()) {
            try (ByteArrayInputStream input = new ByteArrayInputStream(responseStr.getBytes(StandardCharsets.UTF_8))) {

                Document document = builder.parse(input);
                GeoNetworkRecord geoNetworkRecord = new GeoNetworkRecord(this.getIndex(), metadataRecordUUID, this.geoNetworkUrl, document);
                if (geoNetworkRecord.getId() != null) {
                    URL thumbnailUrl = geoNetworkRecord.getThumbnailUrl();
                    geoNetworkRecord.setThumbnailUrl(thumbnailUrl);

                    // Create the thumbnail if it's missing or outdated
                    GeoNetworkRecord oldRecord = (GeoNetworkRecord)this.safeGet(client, geoNetworkRecord.getId());
                    if (geoNetworkRecord.isThumbnailOutdated(oldRecord, this.getThumbnailTTL(), this.getBrokenThumbnailTTL())) {
                        try {
                            File cachedThumbnailFile = ImageCache.cache(thumbnailUrl, this.getIndex(), geoNetworkRecord.getId());
                            if (cachedThumbnailFile != null) {
                                geoNetworkRecord.setCachedThumbnailFilename(cachedThumbnailFile.getName());
                            }
                        } catch(Exception ex) {
                            LOGGER.warn(String.format("Exception occurred while creating a thumbnail for metadata record UUID: %s",
                                    metadataRecordUUID), ex);
                        }
                        geoNetworkRecord.setThumbnailLastIndexed(System.currentTimeMillis());
                    } else {
                        geoNetworkRecord.useCachedThumbnail(oldRecord);
                    }

                    return geoNetworkRecord;
                } else {
                    LOGGER.error(String.format("Invalid metadata record UUID: %s", metadataRecordUUID));
                }
            } catch(Exception ex) {
                LOGGER.error(String.format("Exception occurred while harvesting metadata record UUID: %s - %s", metadataRecordUUID, url), ex);
                System.out.println(responseStr);
            }
        }

        return null;
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
}
