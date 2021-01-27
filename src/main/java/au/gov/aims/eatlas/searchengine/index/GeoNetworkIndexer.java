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
import au.gov.aims.eatlas.searchengine.client.ESRestHighLevelClient;
import au.gov.aims.eatlas.searchengine.entity.EntityUtils;
import au.gov.aims.eatlas.searchengine.entity.GeoNetworkRecord;
import org.apache.http.HttpHost;
import org.apache.log4j.Logger;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GeoNetworkIndexer extends AbstractIndexer<GeoNetworkRecord> {
    private static final Logger LOGGER = Logger.getLogger(GeoNetworkIndexer.class.getName());

    private String geoNetworkUrl;
    private String geoNetworkVersion;

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
    public void harvest() throws Exception {
        // https://geonetwork-opensource.org/manuals/2.10.4/eng/developer/xml_services/metadata_xml_search_retrieve.html
        String url = String.format("%s/srv/eng/xml.search", this.geoNetworkUrl);

        String responseStr = EntityUtils.harvestGetURL(url);
        if (responseStr != null && !responseStr.isEmpty()) {

            // JDOM tutorial:
            //     https://www.tutorialspoint.com/java_xml/java_dom_parse_document.htm
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();

            try (ByteArrayInputStream input = new ByteArrayInputStream(
                responseStr.getBytes(StandardCharsets.UTF_8))) {

                Document document = builder.parse(input);
                // Fix the document, if needed
                document.getDocumentElement().normalize();

                Element root = document.getDocumentElement();

                List<Element> metadataRecordList = IndexUtils.getXMLChildren(root, "metadata");
                try (ESClient client = new ESRestHighLevelClient(new RestHighLevelClient(
                        RestClient.builder(
                                new HttpHost("localhost", 9200, "http"),
                                new HttpHost("localhost", 9300, "http"))))) {

                    // List of metadata records which needs its parent title to be set
                    List<String> orphanMetadataRecordList = new ArrayList<>();
                    for (Element metadataRecordElement : metadataRecordList) {
                        Element metadataRecordInfoElement = IndexUtils.getXMLChild(metadataRecordElement, "geonet:info");
                        Element metadataRecordUUIDElement = IndexUtils.getXMLChild(metadataRecordInfoElement, "uuid");
                        if (metadataRecordUUIDElement != null) {
                            String metadataRecordUUID = IndexUtils.parseText(metadataRecordUUIDElement);

                            GeoNetworkRecord oldRecord = this.get(client, metadataRecordUUID);
                            if (oldRecord != null) {
                                oldRecord.delete();
                            }
                            GeoNetworkRecord geoNetworkRecord = this.loadGeoNetworkRecord(builder, metadataRecordUUID);
                            if (geoNetworkRecord != null) {
                                // If the record have a parent UUID,
                                // keep it's UUID in a list so we can come back to it later to set its parent title.
                                String parentUUID = geoNetworkRecord.getParentUUID();
                                if (parentUUID != null && !parentUUID.isEmpty()) {
                                    orphanMetadataRecordList.add(metadataRecordUUID);
                                }

                                IndexResponse indexResponse = this.index(client, geoNetworkRecord);

                                LOGGER.debug(String.format("Indexing GeoNetwork metadata record: %s, status: %d",
                                        geoNetworkRecord.getId(),
                                        indexResponse.status().getStatus()));
                            }
                        }
                    }

                    // We have added all the records.
                    // Lets fix the records parent title.
                    for (String recordUUID : orphanMetadataRecordList) {
                        GeoNetworkRecord geoNetworkRecord = this.get(client, recordUUID);
                        if (geoNetworkRecord != null) {
                            String parentRecordUUID = geoNetworkRecord.getParentUUID();
                            GeoNetworkRecord parentRecord = this.get(client, parentRecordUUID);
                            if (parentRecord != null) {
                                geoNetworkRecord.setParentTitle(parentRecord.getTitle());

                                IndexResponse indexResponse = this.index(client, geoNetworkRecord);

                                LOGGER.debug(String.format("Re-indexing GeoNetwork metadata record: %s with parent title: %s, status: %d",
                                        geoNetworkRecord.getId(),
                                        geoNetworkRecord.getParentTitle(),
                                        indexResponse.status().getStatus()));
                            }
                        }
                    }
                }
            }
        }
    }

    private GeoNetworkRecord loadGeoNetworkRecord(DocumentBuilder builder, String metadataRecordUUID) {
        String url = String.format("%s/srv/eng/xml.metadata.get", this.geoNetworkUrl);

        Map<String, String> dataMap = new HashMap<String, String>();
        dataMap.put("uuid", metadataRecordUUID);

        String responseStr;
        try {
            //LOGGER.info(String.format("Harvesting metadata record UUID: %s from: %s", metadataRecordUUID, url));
            responseStr = EntityUtils.harvestPostURL(url, dataMap);
        } catch (Exception ex) {
            LOGGER.error(String.format("Exception occurred while harvesting the metadata record UUID: %s%nUrl: %s%nPOST data: %s",
                    metadataRecordUUID, url, EntityUtils.mapToString(dataMap)), ex);

            return null;
        }

        if (responseStr != null && !responseStr.isEmpty()) {
            try (ByteArrayInputStream input = new ByteArrayInputStream(
                responseStr.getBytes(StandardCharsets.UTF_8))) {

                Document document = builder.parse(input);
                GeoNetworkRecord geoNetworkRecord = new GeoNetworkRecord(this.getIndex(), metadataRecordUUID, this.geoNetworkUrl, document);
                if (geoNetworkRecord.getId() != null) {
                    return geoNetworkRecord;
                } else {
                    LOGGER.error(String.format("Invalid metadata record UUID: %s", metadataRecordUUID));
                }
            } catch(Exception ex) {
                LOGGER.error(String.format("Exception occurred while harvesting metadata record UUID: %s", metadataRecordUUID), ex);
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
