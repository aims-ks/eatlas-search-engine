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
package au.gov.aims.eatlas.searchengine.entity.geoNetworkParser;

import au.gov.aims.eatlas.searchengine.logger.AbstractLogger;
import au.gov.aims.eatlas.searchengine.entity.GeoNetworkRecord;
import au.gov.aims.eatlas.searchengine.entity.WikiFormatter;
import au.gov.aims.eatlas.searchengine.index.AbstractIndexer;
import au.gov.aims.eatlas.searchengine.index.IndexUtils;
import au.gov.aims.eatlas.searchengine.index.WktUtils;
import au.gov.aims.eatlas.searchengine.logger.Level;
import au.gov.aims.eatlas.searchengine.logger.Message;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.io.ParseException;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ISO19139_parser extends AbstractParser {
    public void parseRecord(GeoNetworkRecord record, String geoNetworkUrlStr, Element rootElement, AbstractLogger logger) {
        // UUID
        // NOTE: Get it from the XML document if not provided already
        if (record.getId() == null) {
            record.setId(IndexUtils.parseCharacterString(IndexUtils.getXMLChild(rootElement, "gmd:fileIdentifier")));
        }

        // Parent UUID
        record.setParentUUID(IndexUtils.parseCharacterString(IndexUtils.getXMLChild(rootElement, "gmd:parentIdentifier")));

        // The responsible parties are not always where they are expected.
        // Let's parse responsible parties where ever they are found and put them in the right category.
        // Key: role
        Map<String, List<ResponsibleParty>> responsiblePartyMap = new HashMap<>();

        // Metadata contact info
        List<Element> metadataContacts = IndexUtils.getXMLChildren(rootElement, "gmd:contact");
        for (Element metadataContact : metadataContacts) {
            ResponsibleParty metadataContactResponsibleParty = this.parseResponsibleParty(IndexUtils.getXMLChild(metadataContact, "gmd:CI_ResponsibleParty"));
            addResponsibleParty(metadataContactResponsibleParty, responsiblePartyMap);
        }

        // Title
        Element identificationInfo = IndexUtils.getXMLChild(rootElement, "gmd:identificationInfo");
        Element mdDataIdentification = IndexUtils.getXMLChild(identificationInfo, "gmd:MD_DataIdentification", "mcp:MD_DataIdentification");
        Element dataCitation = IndexUtils.getXMLChild(mdDataIdentification, "gmd:citation");
        Element dataCiCitation = IndexUtils.getXMLChild(dataCitation, "gmd:CI_Citation");
        record.setTitle(IndexUtils.parseCharacterString(IndexUtils.getXMLChild(dataCiCitation, "gmd:title")));

        String dataAbstract = IndexUtils.parseCharacterString(IndexUtils.getXMLChild(mdDataIdentification, "gmd:abstract"));

        // Point of contact in MD_DataIdentification
        List<Element> dataPointOfContactElements = IndexUtils.getXMLChildren(mdDataIdentification, "gmd:pointOfContact");
        for (Element dataPointOfContactElement : dataPointOfContactElements) {
            ResponsibleParty dataPointOfContact = this.parseResponsibleParty(IndexUtils.getXMLChild(dataPointOfContactElement, "gmd:CI_ResponsibleParty"));
            addResponsibleParty(dataPointOfContact, responsiblePartyMap);
        }

        // Preview image
        //   Key: fileDescription (thumbnail, large_thumbnail, etc)
        //   Value: fileName (used to craft the URL)
        Map<String, String> previewImageMap = new HashMap<>();
        List<Element> graphicOverviewElements = IndexUtils.getXMLChildren(mdDataIdentification, "gmd:graphicOverview");
        for (Element graphicOverviewElement : graphicOverviewElements) {
            Element mdBrowseGraphic = IndexUtils.getXMLChild(graphicOverviewElement, "gmd:MD_BrowseGraphic");
            String fileDescription = IndexUtils.parseCharacterString(IndexUtils.getXMLChild(mdBrowseGraphic, "gmd:fileDescription"));
            if (fileDescription == null) {
                fileDescription = "UNKNOWN";
            }
            String fileName = IndexUtils.parseCharacterString(IndexUtils.getXMLChild(mdBrowseGraphic, "gmd:fileName"));
            if (fileName != null) {
                previewImageMap.put(fileDescription, fileName);
            }
        }
        // Look for a "large_thumbnail".
        // If there is none, look for a "thumbnail".
        // If there is none, just pick a random thumbnail, something is better than nothing.
        if (!previewImageMap.isEmpty()) {
            String fileName = previewImageMap.get("large_thumbnail");
            if (fileName == null) {
                fileName = previewImageMap.get("thumbnail");
            }
            if (fileName == null) {
                // There is no "large_thumbnail" nor "thumbnail" (very unlikely).
                // Just get one, anyone will do...
                for (String randomFileName : previewImageMap.values()) {
                    fileName = randomFileName;
                    break;
                }
            }

            if (fileName != null) {
                String previewUrlStr;
                if (fileName.startsWith("http://") || fileName.startsWith("https://")) {
                    // Some records put the full URL in the preview field.
                    // Example:
                    //     https://eatlas.org.au/geonetwork/srv/eng/xml.metadata.get?uuid=a86f062e-f47c-49f1-ace5-3e03a2272088
                    previewUrlStr = fileName;
                } else {
                    previewUrlStr = String.format("%s/srv/eng/resources.get?uuid=%s&fname=%s&access=public",
                            geoNetworkUrlStr, record.getId(), fileName);
                }

                try {
                    URL thumbnailUrl = new URL(previewUrlStr);
                    record.setThumbnailUrl(thumbnailUrl);
                } catch(Exception ex) {
                    record.setThumbnailUrl(null);
                    logger.addMessage(Level.ERROR, String.format("Invalid metadata thumbnail URL found in record %s: %s",
                            record.getId(), previewUrlStr), ex);
                }
            }
        }

        // Langcode (example: "en")
        String langcode = null;
        String geonetworkLangcode = IndexUtils.parseCharacterString(IndexUtils.getXMLChild(mdDataIdentification, "gmd:language"));
        if (geonetworkLangcode != null) {
            if (geonetworkLangcode.length() == 2) {
                langcode = geonetworkLangcode;
            } else if (geonetworkLangcode.length() == 3) {
                Locale locale = LANGCODE_MAP.get(geonetworkLangcode);
                if (locale != null) {
                    // getLanguage returns the 2 letters langcode
                    langcode = locale.getLanguage();
                }
            }
        }
        record.setLangcode(langcode);


        // GIS Information (bbox, points, polygons, etc.)
        List<Element> extentList = IndexUtils.getXMLChildren(mdDataIdentification, "gmd:extent");
        String wkt = null;
        try {
            wkt = this.parseExtentList(extentList);
        } catch (Exception ex) {
            logger.addMessage(Level.ERROR, String.format("Metadata record %s - %s", record.getId(), ex.getMessage()), ex);
        }

        if (wkt == null || wkt.isEmpty()) {
            logger.addMessage(Level.WARNING, String.format("Metadata record %s has no extent.", record.getId()));
            wkt = AbstractIndexer.DEFAULT_WKT;
        }

        try {
            record.setWktAndAttributes(wkt);
        } catch(ParseException ex) {
            Message message = logger.addMessage(Level.WARNING, "Invalid WKT", ex);
            message.addDetail(wkt);
        }


        // Online Resources
        // including pointOfTruthUrlStr
        String pointOfTruthUrlStr = null;
        List<OnlineResource> onlineResources = new ArrayList<>();
        Element distributionInfo = IndexUtils.getXMLChild(rootElement, "gmd:distributionInfo");
        Element transferOptions = IndexUtils.getXMLChild(distributionInfo, "gmd:transferOptions");
        Element mdDigitalTransferOptions = IndexUtils.getXMLChild(transferOptions, "gmd:MD_DigitalTransferOptions");
        List<Element> onlineElements = IndexUtils.getXMLChildren(mdDigitalTransferOptions, "gmd:onLine");
        for (Element onlineElement : onlineElements) {
            Element ciOnlineResource = IndexUtils.getXMLChild(onlineElement, "gmd:CI_OnlineResource");
            OnlineResource onlineResource = this.parseOnlineResource(ciOnlineResource);
            if (onlineResource != null) {
                String label = onlineResource.getLabel();
                String safeLabel = label == null ? "" : label.toLowerCase(Locale.ENGLISH);
                if ("WWW:LINK-1.0-http--metadata-URL".equals(onlineResource.getProtocol()) && safeLabel.contains("point of truth")) {
                    if (onlineResource.getLinkage() != null) {
                        if (pointOfTruthUrlStr != null) {
                            logger.addMessage(Level.WARNING, String.format("Metadata record UUID %s have multiple point of truth",
                                record.getId()));
                        }
                        pointOfTruthUrlStr = onlineResource.getLinkage();
                        if (!pointOfTruthUrlStr.contains(record.getId())) {
                            logger.addMessage(Level.WARNING, String.format("Metadata record UUID %s point of truth is not pointing to itself: %s",
                                record.getId(), pointOfTruthUrlStr));
                        }
                    }
                } else {
                    onlineResources.add(onlineResource);
                }
            }
        }

        // Cited parties
        List<Element> citedResponsiblePartyElements = IndexUtils.getXMLChildren(dataCiCitation, "gmd:citedResponsibleParty");
        for (Element citedResponsiblePartyElement : citedResponsiblePartyElements) {
            ResponsibleParty citedResponsibleParty = this.parseResponsibleParty(IndexUtils.getXMLChild(citedResponsiblePartyElement, "gmd:CI_ResponsibleParty"));
            addResponsibleParty(citedResponsibleParty, responsiblePartyMap);
        }

        // Point of contact in document root
        List<Element> rootPointOfContactElements = IndexUtils.getXMLChildren(rootElement, "gmd:contact");
        for (Element rootPointOfContactElement : rootPointOfContactElements) {
            ResponsibleParty rootPointOfContact = this.parseResponsibleParty(IndexUtils.getXMLChild(rootPointOfContactElement, "gmd:CI_ResponsibleParty"));
            addResponsibleParty(rootPointOfContact, responsiblePartyMap);
        }




        // Build the document string
        List<String> documentPartList = new ArrayList<String>();

        // Add abstract
        String parsedAbstract = WikiFormatter.getText(dataAbstract);
        if (parsedAbstract != null) {
            documentPartList.add(parsedAbstract);
        }

        // Add contact (excluding metadataContact)
        for (Map.Entry<String, List<ResponsibleParty>> responsiblePartyEntry : responsiblePartyMap.entrySet()) {
            String role = responsiblePartyEntry.getKey();
            if (!"metadataContact".equals(role)) {
                String label = ROLE_LABEL_MAP.get(role);
                if (label == null) {
                    label = String.format("Other (%s)", role);
                }
                documentPartList.add(label);
                List<ResponsibleParty> responsiblePartyList = responsiblePartyEntry.getValue();
                for (ResponsibleParty responsibleParty : responsiblePartyList) {
                    documentPartList.add(responsibleParty.toString());
                }
            }
        }

        // Online resources
        for (OnlineResource onlineResource : onlineResources) {
            documentPartList.add(onlineResource.toString());
        }

        record.setDocument(documentPartList.isEmpty() ? null :
                String.join(NL + NL, documentPartList));


        // Set the metadata link to the original GeoNetwork URL.
        // If we find a valid "point-of-truth" URL in the document,
        // we will use that one instead.
        URL metadataRecordUrl = null;
        if (pointOfTruthUrlStr != null) {
            try {
                metadataRecordUrl = new URL(pointOfTruthUrlStr);
            } catch(Exception ex) {
                logger.addMessage(Level.ERROR, String.format("Invalid metadata record URL found in Point Of Truth of record %s: %s",
                        record.getId(), pointOfTruthUrlStr), ex);
            }
        }

        if (metadataRecordUrl == null) {
            try {
                metadataRecordUrl = this.getMetadataLink(record, geoNetworkUrlStr);
            } catch(Exception ex) {
                logger.addMessage(Level.ERROR, String.format("Invalid metadata record URL for record %s: %s",
                        record.getId(), geoNetworkUrlStr), ex);
            }
        }

        record.setLink(metadataRecordUrl);
    }

    // Parse a "gmd:CI_ResponsibleParty" node
    private ResponsibleParty parseResponsibleParty(Node responsiblePartyNode) {
        if (!(responsiblePartyNode instanceof Element)) {
            return null;
        }

        String name = IndexUtils.parseCharacterString(IndexUtils.getXMLChild(responsiblePartyNode, "gmd:individualName"));
        String position = IndexUtils.parseCharacterString(IndexUtils.getXMLChild(responsiblePartyNode, "gmd:positionName"));
        String organisation = IndexUtils.parseCharacterString(IndexUtils.getXMLChild(responsiblePartyNode, "gmd:organisationName"));

        Element contactInfo = IndexUtils.getXMLChild(responsiblePartyNode, "gmd:contactInfo");
        Element ciContact = IndexUtils.getXMLChild(contactInfo, "gmd:CI_Contact");

        // Get the phone number
        Element phoneElement = IndexUtils.getXMLChild(ciContact, "gmd:phone");
        Element telephone = IndexUtils.getXMLChild(phoneElement, "gmd:CI_Telephone");
        Element voice = IndexUtils.getXMLChild(telephone, "gmd:voice");
        String phone = IndexUtils.parseCharacterString(voice);

        // Get the address (spread across multiple fields)
        Element addressElement = IndexUtils.getXMLChild(ciContact, "gmd:address");
        Element ciAddress = IndexUtils.getXMLChild(addressElement, "gmd:CI_Address");
        String deliveryPoint = IndexUtils.parseCharacterString(IndexUtils.getXMLChild(ciAddress, "gmd:deliveryPoint"));
        String city = IndexUtils.parseCharacterString(IndexUtils.getXMLChild(ciAddress, "gmd:city"));
        String postalCode = IndexUtils.parseCharacterString(IndexUtils.getXMLChild(ciAddress, "gmd:postalCode"));
        // Technically, it's not always a "state". It can be a territory, province, etc. but the word "state" is shorter and easier to understand.
        String state = IndexUtils.parseCharacterString(IndexUtils.getXMLChild(ciAddress, "gmd:administrativeArea"));
        String country = IndexUtils.parseCharacterString(IndexUtils.getXMLChild(ciAddress, "gmd:country"));

        // Build the address string
        //   using a clever "join" trick on a list, so we don't have
        //   to deal with "," between null or not null fields.
        List<String> addressPartList = new ArrayList<String>();
        if (deliveryPoint != null) {
            addressPartList.add(deliveryPoint);
        }
        if (city != null) {
            addressPartList.add(city);
        }
        if (postalCode != null) {
            addressPartList.add(postalCode);
        }
        if (state != null) {
            addressPartList.add(state);
        }
        if (country != null) {
            addressPartList.add(country);
        }
        String address = addressPartList.isEmpty() ? null : String.join(", ", addressPartList);

        // E-mail address
        String email = IndexUtils.parseCharacterString(IndexUtils.getXMLChild(ciAddress, "gmd:electronicMailAddress"));

        // Website
        Element onlineResource = IndexUtils.getXMLChild(ciContact, "gmd:onlineResource");
        OnlineResource website = this.parseOnlineResource(IndexUtils.getXMLChild(onlineResource, "gmd:CI_OnlineResource"));

        Element roleElement = IndexUtils.getXMLChild(responsiblePartyNode, "gmd:role");
        Element roleCode = IndexUtils.getXMLChild(roleElement, "gmd:CI_RoleCode");
        String role = IndexUtils.parseAttribute(roleCode, "codeListValue");

        return new ResponsibleParty(name, organisation, position, role, phone, address, email, website);
    }

    // Parse a "gmd:CI_OnlineResource" node
    private OnlineResource parseOnlineResource(Node onlineResourceNode) {
        if (!(onlineResourceNode instanceof Element)) {
            return null;
        }

        String protocol = IndexUtils.parseCharacterString(IndexUtils.getXMLChild(onlineResourceNode, "gmd:protocol"));
        String name = IndexUtils.parseCharacterString(IndexUtils.getXMLChild(onlineResourceNode, "gmd:name"));
        String description = IndexUtils.parseCharacterString(IndexUtils.getXMLChild(onlineResourceNode, "gmd:description"));

        Element linkageElement = IndexUtils.getXMLChild(onlineResourceNode, "gmd:linkage");
        Element linkageUrl = IndexUtils.getXMLChild(linkageElement, "gmd:URL");
        String linkage = IndexUtils.parseText(linkageUrl);

        return new OnlineResource(protocol, linkage, name, description);
    }

    private String parseExtentList(List<Element> extentList) {
        List<Polygon> polygons = new ArrayList<Polygon>();

        for (Element extentElement : extentList) {
            List<Element> exExtentList = IndexUtils.getXMLChildren(extentElement, "gmd:EX_Extent");
            for (Element exExtentElement : exExtentList) {
                List<Element> geographicElementList = IndexUtils.getXMLChildren(exExtentElement, "gmd:geographicElement");
                for (Element geographicElement : geographicElementList) {

                    List<Element> exGeographicBoundingBoxList = IndexUtils.getXMLChildren(geographicElement, "gmd:EX_GeographicBoundingBox");
                    for (Element exGeographicBoundingBoxElement : exGeographicBoundingBoxList) {
                        Element northElement = IndexUtils.getXMLChild(exGeographicBoundingBoxElement, "gmd:northBoundLatitude");
                        Element eastElement = IndexUtils.getXMLChild(exGeographicBoundingBoxElement, "gmd:eastBoundLongitude");
                        Element southElement = IndexUtils.getXMLChild(exGeographicBoundingBoxElement, "gmd:southBoundLatitude");
                        Element westElement = IndexUtils.getXMLChild(exGeographicBoundingBoxElement, "gmd:westBoundLongitude");

                        Polygon polygon = this.parseBoundingBox(
                            IndexUtils.parseText(northElement),
                            IndexUtils.parseText(eastElement),
                            IndexUtils.parseText(southElement),
                            IndexUtils.parseText(westElement)
                        );

                        if (polygon != null) {
                            polygons.add(polygon);
                        }
                    }

                    List<Element> exBoundingPolygonList = IndexUtils.getXMLChildren(geographicElement, "gmd:EX_BoundingPolygon");
                    for (Element exBoundingPolygonElement : exBoundingPolygonList) {
                        List<Element> polygonList = IndexUtils.getXMLChildren(exBoundingPolygonElement, "gmd:polygon");
                        for (Element polygonElement : polygonList) {
                            List<Element> gmlPolygonList = IndexUtils.getXMLChildren(polygonElement, "gml:Polygon");
                            for (Element gmlPolygonElement : gmlPolygonList) {
                                List<Element> exteriorList = IndexUtils.getXMLChildren(gmlPolygonElement, "gml:exterior");
                                for (Element exteriorElement : exteriorList) {
                                    List<Element> linearRingList = IndexUtils.getXMLChildren(exteriorElement, "gml:LinearRing");
                                    for (Element linearRingElement : linearRingList) {

                                        List<Element> coordinatesList = IndexUtils.getXMLChildren(linearRingElement, "gml:coordinates");
                                        for (Element coordinatesElement : coordinatesList) {
                                            LinearRing linearRing = this.parseCoordinatesLinearRing(IndexUtils.parseText(coordinatesElement));
                                            if (linearRing != null) {
                                                Polygon polygon = WktUtils.createPolygon(linearRing);
                                                if (polygon != null) {
                                                    polygons.add(polygon);
                                                }
                                            }
                                        }

                                        List<Element> posListList = IndexUtils.getXMLChildren(linearRingElement, "gml:posList");
                                        for (Element posListElement : posListList) {
                                            LinearRing linearRing = this.parsePosListLinearRing(IndexUtils.parseText(posListElement), 2, true);
                                            if (linearRing != null) {
                                                Polygon polygon = WktUtils.createPolygon(linearRing);
                                                if (polygon != null) {
                                                    polygons.add(polygon);
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return WktUtils.polygonsToWKT(polygons);
    }
}
