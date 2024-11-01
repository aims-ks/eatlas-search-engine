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

public class ISO19115_3_2018_parser extends AbstractParser {
    public void parseRecord(GeoNetworkRecord record, String geoNetworkUrlStr, Element rootElement, AbstractLogger logger) {
        // UUID
        // NOTE: Get it from the XML document if not provided already
        if (record.getId() == null) {
            Element metadataIdentifier = IndexUtils.getXMLChild(rootElement, "mdb:metadataIdentifier");
            Element mdIdentifier = IndexUtils.getXMLChild(metadataIdentifier, "mcc:MD_Identifier");
            record.setId(IndexUtils.parseCharacterString(IndexUtils.getXMLChild(mdIdentifier, "mcc:code")));
        }

        // Parent UUID
        Element parentMetadata = IndexUtils.getXMLChild(rootElement, "mdb:parentMetadata");
        record.setParentUUID(IndexUtils.parseAttribute(parentMetadata, "uuidref"));

        // The responsible parties are not always where they are expected.
        // Let's parse responsible parties where ever they are found and put them in the right category.
        // Key: role
        Map<String, List<ResponsibleParty>> responsiblePartyMap = new HashMap<>();

        // Metadata contact info
        List<Element> metadataContacts = IndexUtils.getXMLChildren(rootElement, "mdb:contact");
        for (Element metadataContact : metadataContacts) {
            ResponsibleParty metadataContactResponsibleParty = this.parseResponsibleParty(IndexUtils.getXMLChild(metadataContact, "cit:CI_Responsibility"));
            addResponsibleParty(metadataContactResponsibleParty, responsiblePartyMap);
        }

        // Title
        Element identificationInfo = IndexUtils.getXMLChild(rootElement, "mdb:identificationInfo");

        Element mdDataIdentification = IndexUtils.getXMLChild(identificationInfo, "mri:MD_DataIdentification");
        Element dataCitation = IndexUtils.getXMLChild(mdDataIdentification, "mri:citation");
        Element dataCiCitation = IndexUtils.getXMLChild(dataCitation, "cit:CI_Citation");
        record.setTitle(IndexUtils.parseCharacterString(IndexUtils.getXMLChild(dataCiCitation, "cit:title")));

        String dataAbstract = IndexUtils.parseCharacterString(IndexUtils.getXMLChild(mdDataIdentification, "mri:abstract"));

        // Point of contact in MD_DataIdentification
        List<Element> dataPointOfContactElements = IndexUtils.getXMLChildren(mdDataIdentification, "mri:pointOfContact");
        for (Element dataPointOfContactElement : dataPointOfContactElements) {
            ResponsibleParty dataPointOfContact = this.parseResponsibleParty(IndexUtils.getXMLChild(dataPointOfContactElement, "cit:CI_Responsibility"));
            addResponsibleParty(dataPointOfContact, responsiblePartyMap);
        }

        // Preview image
        //   Key: fileDescription (thumbnail, large_thumbnail, etc)
        //   Value: fileName (used to craft the URL)
        Map<String, String> previewImageMap = new HashMap<>();
        List<Element> graphicOverviewElements = IndexUtils.getXMLChildren(mdDataIdentification, "mri:graphicOverview");


        for (Element graphicOverviewElement : graphicOverviewElements) {
            Element mdBrowseGraphic = IndexUtils.getXMLChild(graphicOverviewElement, "mcc:MD_BrowseGraphic");
            String fileDescription = IndexUtils.parseCharacterString(IndexUtils.getXMLChild(mdBrowseGraphic, "mcc:fileDescription"));
            if (fileDescription == null) {
                fileDescription = "UNKNOWN";
            }
            String fileName = IndexUtils.parseCharacterString(IndexUtils.getXMLChild(mdBrowseGraphic, "mcc:fileName"));
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
        Element defaultLocale = IndexUtils.getXMLChild(mdDataIdentification, "mri:defaultLocale");
        Element ptLocale = IndexUtils.getXMLChild(defaultLocale, "lan:PT_Locale");
        Element language = IndexUtils.getXMLChild(ptLocale, "lan:language");
        Element languageCode = IndexUtils.getXMLChild(language, "lan:LanguageCode");
        String geonetworkLangcode = IndexUtils.parseAttribute(languageCode, "codeListValue");
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
        List<Element> extentList = IndexUtils.getXMLChildren(mdDataIdentification, "mri:extent");
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
        List<OnlineResource> onlineResources = new ArrayList<>();
        Element distributionInfo = IndexUtils.getXMLChild(rootElement, "mdb:distributionInfo");
        Element mdDistribution = IndexUtils.getXMLChild(distributionInfo, "mrd:MD_Distribution");
        Element transferOptions = IndexUtils.getXMLChild(mdDistribution, "mrd:transferOptions");
        Element mdDigitalTransferOptions = IndexUtils.getXMLChild(transferOptions, "mrd:MD_DigitalTransferOptions");
        List<Element> onlineElements = IndexUtils.getXMLChildren(mdDigitalTransferOptions, "mrd:onLine");

        for (Element onlineElement : onlineElements) {
            Element ciOnlineResource = IndexUtils.getXMLChild(onlineElement, "cit:CI_OnlineResource");
            OnlineResource onlineResource = this.parseOnlineResource(ciOnlineResource);
            if (onlineResource != null) {
                onlineResources.add(onlineResource);
            }
        }

        // Point of Truth URL
        String pointOfTruthUrlStr = null;
        Element potMetadataLinkage = IndexUtils.getXMLChild(rootElement, "mdb:metadataLinkage");
        Element potCiOnlineResource = IndexUtils.getXMLChild(potMetadataLinkage, "cit:CI_OnlineResource");
        OnlineResource potOnlineResource = this.parseOnlineResource(potCiOnlineResource);
        if (potOnlineResource != null) {
            String label = potOnlineResource.getLabel();
            String safeLabel = label == null ? "" : label.toLowerCase(Locale.ENGLISH);

            if ("WWW:LINK-1.0-http--metadata-URL".equals(potOnlineResource.getProtocol()) && safeLabel.contains("point of truth")) {
                if (potOnlineResource.getLinkage() != null) {
                    if (pointOfTruthUrlStr != null) {
                        logger.addMessage(Level.WARNING, String.format("Metadata record UUID %s have multiple point of truth",
                            record.getId()));
                    }
                    pointOfTruthUrlStr = potOnlineResource.getLinkage();
                    if (!pointOfTruthUrlStr.contains(record.getId())) {
                        logger.addMessage(Level.WARNING, String.format("Metadata record UUID %s point of truth is not pointing to itself: %s",
                            record.getId(), pointOfTruthUrlStr));
                    }
                }
            } else {
                onlineResources.add(potOnlineResource);
            }
        }


        // Cited parties
        List<Element> citedResponsiblePartyElements = IndexUtils.getXMLChildren(dataCiCitation, "cit:citedResponsibleParty");
        for (Element citedResponsiblePartyElement : citedResponsiblePartyElements) {
            ResponsibleParty citedResponsibleParty = this.parseResponsibleParty(IndexUtils.getXMLChild(citedResponsiblePartyElement, "cit:CI_Responsibility"));
            addResponsibleParty(citedResponsibleParty, responsiblePartyMap);
        }

        // Point of contact in document root
        List<Element> rootPointOfContactElements = IndexUtils.getXMLChildren(rootElement, "mdb:contact");
        for (Element rootPointOfContactElement : rootPointOfContactElements) {
            ResponsibleParty rootPointOfContact = this.parseResponsibleParty(IndexUtils.getXMLChild(rootPointOfContactElement, "cit:CI_Responsibility"));
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
                logger.addMessage(Level.ERROR, String.format("Invalid metadata record URL found in Point Of Truth of record %s: %s", record.getId(), pointOfTruthUrlStr), ex);
            }
        }

        if (metadataRecordUrl == null) {
            try {
                metadataRecordUrl = this.getMetadataLink(record, geoNetworkUrlStr);
            } catch(Exception ex) {
                logger.addMessage(Level.ERROR, String.format("Invalid metadata record URL for record %s: %s", record.getId(), geoNetworkUrlStr), ex);
            }
        }

        record.setLink(metadataRecordUrl);
    }

    // Parse a "cit:CI_Responsibility" node
    private ResponsibleParty parseResponsibleParty(Node responsiblePartyNode) {
        if (!(responsiblePartyNode instanceof Element)) {
            return null;
        }

        Element roleElement = IndexUtils.getXMLChild(responsiblePartyNode, "cit:role");
        Element roleCode = IndexUtils.getXMLChild(roleElement, "cit:CI_RoleCode");
        String role = IndexUtils.parseAttribute(roleCode, "codeListValue");

        Element partyElement = IndexUtils.getXMLChild(responsiblePartyNode, "cit:party");
        Element ciOrganisation = IndexUtils.getXMLChild(partyElement, "cit:CI_Organisation");

        String organisation = IndexUtils.parseCharacterString(IndexUtils.getXMLChild(ciOrganisation, "cit:name"));

        Element individual = IndexUtils.getXMLChild(ciOrganisation, "cit:individual");
        Element ciIndividual = IndexUtils.getXMLChild(individual, "cit:CI_Individual");

        String name = IndexUtils.parseCharacterString(IndexUtils.getXMLChild(ciIndividual, "cit:name"));
        String position = IndexUtils.parseCharacterString(IndexUtils.getXMLChild(ciIndividual, "cit:positionName"));

        Element contactInfo = IndexUtils.getXMLChild(ciIndividual, "cit:contactInfo");
        Element ciContact = IndexUtils.getXMLChild(contactInfo, "cit:CI_Contact");

        // Get the phone number
        String phone = null;
        List<Element> phoneElements = IndexUtils.getXMLChildren(ciContact, "cit:phone");
        for (Element phoneElement : phoneElements) {
            Element ciTelephone = IndexUtils.getXMLChild(phoneElement, "cit:CI_Telephone");

            Element numberType = IndexUtils.getXMLChild(ciTelephone, "cit:numberType");
            Element ciTelephoneTypeCode = IndexUtils.getXMLChild(numberType, "cit:CI_TelephoneTypeCode");
            String type = IndexUtils.parseAttribute(ciTelephoneTypeCode, "codeListValue");

            String number = IndexUtils.parseCharacterString(IndexUtils.getXMLChild(ciTelephone, "cit:number"));

            // Parse the phone number, ignore facsimile, etc
            if ("voice".equalsIgnoreCase(type)) {
                phone = number;
            }
        }

        // Get the address (spread across multiple fields)
        Element addressElement = IndexUtils.getXMLChild(ciContact, "cit:address");
        Element ciAddress = IndexUtils.getXMLChild(addressElement, "cit:CI_Address");
        String deliveryPoint = IndexUtils.parseCharacterString(IndexUtils.getXMLChild(ciAddress, "cit:deliveryPoint"));
        String city = IndexUtils.parseCharacterString(IndexUtils.getXMLChild(ciAddress, "cit:city"));
        String postalCode = IndexUtils.parseCharacterString(IndexUtils.getXMLChild(ciAddress, "cit:postalCode"));
        // Technically, it's not always a "state". It can be a territory, province, etc. but the word "state" is shorter and easier to understand.
        String state = IndexUtils.parseCharacterString(IndexUtils.getXMLChild(ciAddress, "cit:administrativeArea"));
        String country = IndexUtils.parseCharacterString(IndexUtils.getXMLChild(ciAddress, "cit:country"));

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
        String email = IndexUtils.parseCharacterString(IndexUtils.getXMLChild(ciAddress, "cit:electronicMailAddress"));

        // Website
        Element onlineResource = IndexUtils.getXMLChild(ciContact, "cit:onlineResource");
        OnlineResource website = this.parseOnlineResource(IndexUtils.getXMLChild(onlineResource, "cit:CI_OnlineResource"));

        return new ResponsibleParty(name, organisation, position, role, phone, address, email, website);
    }

    // Parse a "cit:CI_OnlineResource" node
    private OnlineResource parseOnlineResource(Node onlineResourceNode) {
        if (!(onlineResourceNode instanceof Element)) {
            return null;
        }

        String protocol = IndexUtils.parseCharacterString(IndexUtils.getXMLChild(onlineResourceNode, "cit:protocol"));
        String name = IndexUtils.parseCharacterString(IndexUtils.getXMLChild(onlineResourceNode, "cit:name"));
        String description = IndexUtils.parseCharacterString(IndexUtils.getXMLChild(onlineResourceNode, "cit:description"));
        String linkage = IndexUtils.parseCharacterString(IndexUtils.getXMLChild(onlineResourceNode, "cit:linkage"));

        return new OnlineResource(protocol, linkage, name, description);
    }

    private String parseExtentList(List<Element> extentList) {
        List<Polygon> polygons = new ArrayList<Polygon>();

        for (Element extentElement : extentList) {
            List<Element> exExtentList = IndexUtils.getXMLChildren(extentElement, "gex:EX_Extent");
            for (Element exExtentElement : exExtentList) {
                List<Element> geographicElementList = IndexUtils.getXMLChildren(exExtentElement, "gex:geographicElement");
                for (Element geographicElement : geographicElementList) {

                    List<Element> exGeographicBoundingBoxList = IndexUtils.getXMLChildren(geographicElement, "gex:EX_GeographicBoundingBox");
                    for (Element exGeographicBoundingBoxElement : exGeographicBoundingBoxList) {
                        Element northElement = IndexUtils.getXMLChild(exGeographicBoundingBoxElement, "gex:northBoundLatitude");
                        Element eastElement = IndexUtils.getXMLChild(exGeographicBoundingBoxElement, "gex:eastBoundLongitude");
                        Element southElement = IndexUtils.getXMLChild(exGeographicBoundingBoxElement, "gex:southBoundLatitude");
                        Element westElement = IndexUtils.getXMLChild(exGeographicBoundingBoxElement, "gex:westBoundLongitude");

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

                    List<Element> exBoundingPolygonList = IndexUtils.getXMLChildren(geographicElement, "gex:EX_BoundingPolygon");
                    for (Element exBoundingPolygonElement : exBoundingPolygonList) {
                        List<Element> gexPolygonList = IndexUtils.getXMLChildren(exBoundingPolygonElement, "gex:polygon");
                        for (Element gexPolygonElement : gexPolygonList) {

                            List<Element> gmlPointList = IndexUtils.getXMLChildren(gexPolygonElement, "gml:Point");
                            for (Element gmlPointElement : gmlPointList) {
                                Polygon point = parseGmlPoint(gmlPointElement);
                                if (point != null) {
                                    polygons.add(point);
                                }
                            }

                            List<Element> gmlPolygonList = IndexUtils.getXMLChildren(gexPolygonElement, "gml:Polygon");
                            for (Element gmlPolygonElement : gmlPolygonList) {
                                Polygon polygon = parseGmlPolygon(gmlPolygonElement);
                                if (polygon != null) {
                                    polygons.add(polygon);
                                }
                            }

                            List<Element> multiSurfaceList = IndexUtils.getXMLChildren(gexPolygonElement, "gml:MultiSurface");
                            for (Element multiSurfaceElement : multiSurfaceList) {
                                List<Element> surfaceMemberList = IndexUtils.getXMLChildren(multiSurfaceElement, "gml:surfaceMember");
                                for (Element surfaceMemberElement : surfaceMemberList) {
                                    List<Element> gmlPolygonSubList = IndexUtils.getXMLChildren(surfaceMemberElement, "gml:Polygon");
                                    for (Element gmlPolygonElement : gmlPolygonSubList) {
                                        Polygon polygon = parseGmlPolygon(gmlPolygonElement);
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

        return WktUtils.polygonsToWKT(polygons);
    }

    private Polygon parseGmlPoint(Element gmlPointElement) {
        Element posElement = IndexUtils.getXMLChild(gmlPointElement, "gml:pos");
        String srsDimensionStr = IndexUtils.parseAttribute(posElement, "srsDimension");

        int dimension = 2;
        if (srsDimensionStr != null) {
            dimension = Integer.parseInt(srsDimensionStr);
        }

        return this.parsePos(IndexUtils.parseText(posElement), dimension, false);
    }

    private Polygon parseGmlPolygon(Element gmlPolygonElement) {
        Element exteriorElement = IndexUtils.getXMLChild(gmlPolygonElement, "gml:exterior");
        Element exteriorLinearRingElement = IndexUtils.getXMLChild(exteriorElement, "gml:LinearRing");

        LinearRing exteriorLinearRing = null;
        Element exteriorPosListElement = IndexUtils.getXMLChild(exteriorLinearRingElement, "gml:posList");
        if (exteriorPosListElement != null) {
            String srsDimensionStr = IndexUtils.parseAttribute(exteriorPosListElement, "srsDimension");

            int dimension = 2;
            if (srsDimensionStr != null) {
                dimension = Integer.parseInt(srsDimensionStr);
            }
            exteriorLinearRing = this.parsePosListLinearRing(IndexUtils.parseText(exteriorPosListElement), dimension, false);
        }

        Element coordinatesElement = IndexUtils.getXMLChild(exteriorLinearRingElement, "gml:coordinates");
        if (coordinatesElement != null) {
            exteriorLinearRing = this.parseCoordinatesLinearRing(IndexUtils.parseText(coordinatesElement));
        }

        // Holes in polygon
        List<LinearRing> holes = new ArrayList<LinearRing>();
        List<Element> interiorList = IndexUtils.getXMLChildren(gmlPolygonElement, "gml:interior");
        for (Element interiorElement : interiorList) {
            Element interiorLinearRingElement = IndexUtils.getXMLChild(interiorElement, "gml:LinearRing");
            Element interiorPosListElement = IndexUtils.getXMLChild(interiorLinearRingElement, "gml:posList");

            LinearRing interiorLinearRing = null;

            if (interiorPosListElement != null) {
                String interiorSrsDimensionStr = IndexUtils.parseAttribute(interiorPosListElement, "srsDimension");

                int interiorDimension = 2;
                if (interiorSrsDimensionStr != null) {
                    interiorDimension = Integer.parseInt(interiorSrsDimensionStr);
                }
                interiorLinearRing = this.parsePosListLinearRing(IndexUtils.parseText(interiorPosListElement), interiorDimension, false);
            }

            Element interiorCoordinatesElement = IndexUtils.getXMLChild(interiorLinearRingElement, "gml:coordinates");
            if (interiorCoordinatesElement != null) {
                interiorLinearRing = this.parseCoordinatesLinearRing(IndexUtils.parseText(interiorCoordinatesElement));
            }

            holes.add(interiorLinearRing);
        }

        Polygon polygon = null;
        if (exteriorLinearRing != null) {
            if (holes.isEmpty()) {
                polygon = WktUtils.createPolygon(exteriorLinearRing);
            } else {
                polygon = WktUtils.createPolygon(exteriorLinearRing, holes);
            }
        }

        return polygon;
    }

}
