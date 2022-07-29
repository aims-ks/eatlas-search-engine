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
package au.gov.aims.eatlas.searchengine.entity;

import au.gov.aims.eatlas.searchengine.admin.rest.Messages;
import au.gov.aims.eatlas.searchengine.index.IndexUtils;
import org.json.JSONObject;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.io.WKTWriter;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class GeoNetworkRecord extends Entity {
    // New line used to separate lines in the indexed document.
    // It doesn't really matter which new line scheme is used, as long as it's supported by ElasticSearch.
    private static final String NL = "\n";

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();
    private static final WKTWriter WKT_WRITER = new WKTWriter(2);
    private static final double GIS_EPSILON = 0.001; // About 100 metres

    private static final Map<String, Locale> LANGCODE_MAP = new HashMap<>();
    static {
        String[] languages = Locale.getISOLanguages();
        for (String language : languages) {
            Locale locale = new Locale(language);
            LANGCODE_MAP.put(locale.getISO3Language(), locale);
        }
    }

    private static final Map<String, String> ROLE_LABEL_MAP = new HashMap<>();
    static {
        ROLE_LABEL_MAP.put("resourceProvider", "Resource provider");
        ROLE_LABEL_MAP.put("custodian", "Custodian");
        ROLE_LABEL_MAP.put("owner", "Owner");
        ROLE_LABEL_MAP.put("user", "User");
        ROLE_LABEL_MAP.put("distributor", "Distributor");
        ROLE_LABEL_MAP.put("originator", "Originator");
        ROLE_LABEL_MAP.put("pointOfContact", "Point of contact");
        ROLE_LABEL_MAP.put("principalInvestigator", "Principal investigator");
        ROLE_LABEL_MAP.put("processor", "Processor");
        ROLE_LABEL_MAP.put("publisher", "Publisher");
        ROLE_LABEL_MAP.put("author", "Author");
        ROLE_LABEL_MAP.put("coInvestigator", "Co-investigator");
        ROLE_LABEL_MAP.put("licensor", "Licensor");
        ROLE_LABEL_MAP.put("researchAssistant", "Research assistant");
        ROLE_LABEL_MAP.put("ipOwner", "Intellectual property owner");
        ROLE_LABEL_MAP.put("moralRightsOwner", "Moral rights owner");
        ROLE_LABEL_MAP.put("metadataContact", "Metadata contact");
    }

    private String parentUUID;
    // TODO Get from index after the harvest, if we decide that it's needed
    private String parentTitle;

    private GeoNetworkRecord() {}

    // geoNetworkUrlStr: https://eatlas.org.au/geonetwork
    // metadataRecordUUID: UUID of the record. If omitted, the parser will grab the UUID from the document.
    public GeoNetworkRecord(String index) {
        this.setIndex(index);
    }

    public void parseRecord(String metadataRecordUUID, String metadataSchema, String geoNetworkUrlStr, Document xmlMetadataRecord, Messages messages) {
        if (metadataRecordUUID != null) {
            metadataRecordUUID = metadataRecordUUID.trim();
            if (metadataRecordUUID.isEmpty()) {
                metadataRecordUUID = null;
            }
        }

        if (metadataSchema == null || metadataSchema.isEmpty()) {
            messages.addMessage(Messages.Level.WARNING, String.format("Metadata UUID %s has no defined metadata schema.", metadataRecordUUID));
            return;
        }

        if (xmlMetadataRecord == null) {
            messages.addMessage(Messages.Level.WARNING, String.format("Metadata UUID %s has no metadata record.", metadataRecordUUID));
            return;
        }

        // Fix the document, if needed
        xmlMetadataRecord.getDocumentElement().normalize();
        Element root = xmlMetadataRecord.getDocumentElement();
        if (root == null) {
            messages.addMessage(Messages.Level.WARNING, String.format("Metadata UUID %s has no root in its metadata document.", metadataRecordUUID));
            return;
        }

        switch(metadataSchema) {
            case "iso19139":
            case "iso19139.anzlic":
            case "iso19139.mcp":
            case "iso19139.mcp-1.4":
                this.parse_ISO19139_Record(metadataRecordUUID, geoNetworkUrlStr, root, messages);
                break;

            case "iso19115-3.2018":
                this.parse_ISO19115_3_2018_Record(metadataRecordUUID, geoNetworkUrlStr, root, messages);
                break;

            default:
                messages.addMessage(Messages.Level.WARNING, String.format("Metadata UUID %s has unsupported schema %s", metadataRecordUUID, metadataSchema));
                break;
        }
    }


    /**
     * ISO 19115-3.2018 (GeoNetwork 3.x)
     */

    private void parse_ISO19115_3_2018_Record(String metadataRecordUUID, String geoNetworkUrlStr, Element rootElement, Messages messages) {
        if (metadataRecordUUID != null) {
            this.setId(metadataRecordUUID);
        }

        // UUID
        // NOTE: Get it from the XML document if not provided already
        if (this.getId() == null) {
            Element metadataIdentifier = IndexUtils.getXMLChild(rootElement, "mdb:metadataIdentifier");
            Element mdIdentifier = IndexUtils.getXMLChild(metadataIdentifier, "mcc:MD_Identifier");
            this.setId(IndexUtils.parseCharacterString(IndexUtils.getXMLChild(mdIdentifier, "mcc:code")));
        }

        // Parent UUID
        Element parentMetadata = IndexUtils.getXMLChild(rootElement, "mdb:parentMetadata");
        this.parentUUID = IndexUtils.parseAttribute(parentMetadata, "uuidref");

        // The responsible parties are not always where they are expected.
        // Let's parse responsible parties where ever they are found and put them in the right category.
        // Key: role
        Map<String, List<ResponsibleParty>> responsiblePartyMap = new HashMap<>();

        // Metadata contact info
        List<Element> metadataContacts = IndexUtils.getXMLChildren(rootElement, "mdb:contact");
        for (Element metadataContact : metadataContacts) {
            ResponsibleParty metadataContactResponsibleParty = ResponsibleParty.parseIso19115_3_2018Node(IndexUtils.getXMLChild(metadataContact, "cit:CI_Responsibility"));
            addResponsibleParty(metadataContactResponsibleParty, responsiblePartyMap);
        }

        // Title
        Element identificationInfo = IndexUtils.getXMLChild(rootElement, "mdb:identificationInfo");

        Element mdDataIdentification = IndexUtils.getXMLChild(identificationInfo, "mri:MD_DataIdentification");
        Element dataCitation = IndexUtils.getXMLChild(mdDataIdentification, "mri:citation");
        Element dataCiCitation = IndexUtils.getXMLChild(dataCitation, "cit:CI_Citation");
        this.setTitle(IndexUtils.parseCharacterString(IndexUtils.getXMLChild(dataCiCitation, "cit:title")));

        String dataAbstract = IndexUtils.parseCharacterString(IndexUtils.getXMLChild(mdDataIdentification, "mri:abstract"));

        // Point of contact in MD_DataIdentification
        List<Element> dataPointOfContactElements = IndexUtils.getXMLChildren(mdDataIdentification, "mri:pointOfContact");
        for (Element dataPointOfContactElement : dataPointOfContactElements) {
            ResponsibleParty dataPointOfContact = ResponsibleParty.parseIso19115_3_2018Node(IndexUtils.getXMLChild(dataPointOfContactElement, "cit:CI_Responsibility"));
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
                            geoNetworkUrlStr, this.getId(), fileName);
                }

                try {
                    URL thumbnailUrl = new URL(previewUrlStr);
                    this.setThumbnailUrl(thumbnailUrl);
                } catch(Exception ex) {
                    this.setThumbnailUrl(null);
                    messages.addMessage(Messages.Level.ERROR, String.format("Invalid metadata thumbnail URL found in record %s: %s", this.getId(), previewUrlStr), ex);
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
        this.setLangcode(langcode);


        // GIS Information (bbox, points, polygons, etc.)
        List<Element> extentList = IndexUtils.getXMLChildren(mdDataIdentification, "mri:extent");
        String wkt = GeoNetworkRecord.parseIso19115_3_2018ExtentList(extentList);
        if (wkt != null && !wkt.isEmpty()) {
            this.setWkt(wkt);
        } else {
            messages.addMessage(Messages.Level.WARNING, String.format("Metadata record %s has no extent.", this.getId()));
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
            OnlineResource onlineResource = OnlineResource.parseIso19115_3_2018Node(ciOnlineResource);
            if (onlineResource != null) {
                onlineResources.add(onlineResource);
            }
        }

        // Point of Truth URL
        String pointOfTruthUrlStr = null;
        Element potMetadataLinkage = IndexUtils.getXMLChild(rootElement, "mdb:metadataLinkage");
        Element potCiOnlineResource = IndexUtils.getXMLChild(potMetadataLinkage, "cit:CI_OnlineResource");
        OnlineResource potOnlineResource = OnlineResource.parseIso19115_3_2018Node(potCiOnlineResource);
        if (potOnlineResource != null) {
            String label = potOnlineResource.getLabel();
            String safeLabel = label == null ? "" : label.toLowerCase(Locale.ENGLISH);

            if ("WWW:LINK-1.0-http--metadata-URL".equals(potOnlineResource.protocol) && safeLabel.contains("point of truth")) {
                if (potOnlineResource.linkage != null) {
                    if (pointOfTruthUrlStr != null) {
                        messages.addMessage(Messages.Level.WARNING, String.format("Metadata record UUID %s have multiple point of truth",
                            this.getId()));
                    }
                    pointOfTruthUrlStr = potOnlineResource.linkage;
                    if (!pointOfTruthUrlStr.contains(this.getId())) {
                        messages.addMessage(Messages.Level.WARNING, String.format("Metadata record UUID %s point of truth is not pointing to itself: %s",
                            this.getId(), pointOfTruthUrlStr));
                    }
                }
            } else {
                onlineResources.add(potOnlineResource);
            }
        }


        // Cited parties
        List<Element> citedResponsiblePartyElements = IndexUtils.getXMLChildren(dataCiCitation, "cit:citedResponsibleParty");
        for (Element citedResponsiblePartyElement : citedResponsiblePartyElements) {
            ResponsibleParty citedResponsibleParty = ResponsibleParty.parseIso19115_3_2018Node(IndexUtils.getXMLChild(citedResponsiblePartyElement, "cit:CI_Responsibility"));
            addResponsibleParty(citedResponsibleParty, responsiblePartyMap);
        }

        // Point of contact in document root
        List<Element> rootPointOfContactElements = IndexUtils.getXMLChildren(rootElement, "mdb:contact");
        for (Element rootPointOfContactElement : rootPointOfContactElements) {
            ResponsibleParty rootPointOfContact = ResponsibleParty.parseIso19115_3_2018Node(IndexUtils.getXMLChild(rootPointOfContactElement, "cit:CI_Responsibility"));
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

        this.setDocument(documentPartList.isEmpty() ? null :
                String.join(NL + NL, documentPartList));


        // Set the metadata link to the original GeoNetwork URL.
        // If we find a valid "point-of-truth" URL in the document,
        // we will use that one instead.
        URL metadataRecordUrl = null;
        if (pointOfTruthUrlStr != null) {
            try {
                metadataRecordUrl = new URL(pointOfTruthUrlStr);
            } catch(Exception ex) {
                messages.addMessage(Messages.Level.ERROR, String.format("Invalid metadata record URL found in Point Of Truth of record %s: %s", this.getId(), pointOfTruthUrlStr), ex);
            }
        }

        if (metadataRecordUrl == null) {
            String geonetworkMetadataUrlStr = String.format("%s/srv/eng/metadata.show?uuid=%s", geoNetworkUrlStr, this.getId());
            try {
                metadataRecordUrl = new URL(geonetworkMetadataUrlStr);
            } catch(Exception ex) {
                messages.addMessage(Messages.Level.ERROR, String.format("Invalid metadata record URL for record %s: %s", this.getId(), geonetworkMetadataUrlStr), ex);
            }
        }

        this.setLink(metadataRecordUrl);
    }


    /**
     * ISO 19139 (GeoNetwork 2.x)
     */

    private void parse_ISO19139_Record(String metadataRecordUUID, String geoNetworkUrlStr, Element rootElement, Messages messages) {
        if (metadataRecordUUID != null) {
            this.setId(metadataRecordUUID);
        }

        // UUID
        // NOTE: Get it from the XML document if not provided already
        if (this.getId() == null) {
            this.setId(IndexUtils.parseCharacterString(IndexUtils.getXMLChild(rootElement, "gmd:fileIdentifier")));
        }

        // Parent UUID
        this.parentUUID = IndexUtils.parseCharacterString(IndexUtils.getXMLChild(rootElement, "gmd:parentIdentifier"));

        // The responsible parties are not always where they are expected.
        // Let's parse responsible parties where ever they are found and put them in the right category.
        // Key: role
        Map<String, List<ResponsibleParty>> responsiblePartyMap = new HashMap<>();

        // Metadata contact info
        List<Element> metadataContacts = IndexUtils.getXMLChildren(rootElement, "gmd:contact");
        for (Element metadataContact : metadataContacts) {
            ResponsibleParty metadataContactResponsibleParty = ResponsibleParty.parseIso19139Node(IndexUtils.getXMLChild(metadataContact, "gmd:CI_ResponsibleParty"));
            addResponsibleParty(metadataContactResponsibleParty, responsiblePartyMap);
        }

        // Title
        Element identificationInfo = IndexUtils.getXMLChild(rootElement, "gmd:identificationInfo");
        Element mdDataIdentification = IndexUtils.getXMLChild(identificationInfo, "gmd:MD_DataIdentification", "mcp:MD_DataIdentification");
        Element dataCitation = IndexUtils.getXMLChild(mdDataIdentification, "gmd:citation");
        Element dataCiCitation = IndexUtils.getXMLChild(dataCitation, "gmd:CI_Citation");
        this.setTitle(IndexUtils.parseCharacterString(IndexUtils.getXMLChild(dataCiCitation, "gmd:title")));

        String dataAbstract = IndexUtils.parseCharacterString(IndexUtils.getXMLChild(mdDataIdentification, "gmd:abstract"));

        // Point of contact in MD_DataIdentification
        List<Element> dataPointOfContactElements = IndexUtils.getXMLChildren(mdDataIdentification, "gmd:pointOfContact");
        for (Element dataPointOfContactElement : dataPointOfContactElements) {
            ResponsibleParty dataPointOfContact = ResponsibleParty.parseIso19139Node(IndexUtils.getXMLChild(dataPointOfContactElement, "gmd:CI_ResponsibleParty"));
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
                            geoNetworkUrlStr, this.getId(), fileName);
                }

                try {
                    URL thumbnailUrl = new URL(previewUrlStr);
                    this.setThumbnailUrl(thumbnailUrl);
                } catch(Exception ex) {
                    this.setThumbnailUrl(null);
                    messages.addMessage(Messages.Level.ERROR, String.format("Invalid metadata thumbnail URL found in record %s: %s", this.getId(), previewUrlStr), ex);
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
        this.setLangcode(langcode);


        // GIS Information (bbox, points, polygons, etc.)
        List<Element> extentList = IndexUtils.getXMLChildren(mdDataIdentification, "gmd:extent");
        String wkt = GeoNetworkRecord.parseIso19139ExtentList(extentList);
        if (wkt != null && !wkt.isEmpty()) {
            this.setWkt(wkt);
        } else {
            messages.addMessage(Messages.Level.WARNING, String.format("Metadata record %s has no extent.", this.getId()));
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
            OnlineResource onlineResource = OnlineResource.parseIso19139Node(ciOnlineResource);
            if (onlineResource != null) {
                String label = onlineResource.getLabel();
                String safeLabel = label == null ? "" : label.toLowerCase(Locale.ENGLISH);
                if ("WWW:LINK-1.0-http--metadata-URL".equals(onlineResource.protocol) && safeLabel.contains("point of truth")) {
                    if (onlineResource.linkage != null) {
                        if (pointOfTruthUrlStr != null) {
                            messages.addMessage(Messages.Level.WARNING, String.format("Metadata record UUID %s have multiple point of truth",
                                this.getId()));
                        }
                        pointOfTruthUrlStr = onlineResource.linkage;
                        if (!pointOfTruthUrlStr.contains(this.getId())) {
                            messages.addMessage(Messages.Level.WARNING, String.format("Metadata record UUID %s point of truth is not pointing to itself: %s",
                                this.getId(), pointOfTruthUrlStr));
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
            ResponsibleParty citedResponsibleParty = ResponsibleParty.parseIso19139Node(IndexUtils.getXMLChild(citedResponsiblePartyElement, "gmd:CI_ResponsibleParty"));
            addResponsibleParty(citedResponsibleParty, responsiblePartyMap);
        }

        // Point of contact in document root
        List<Element> rootPointOfContactElements = IndexUtils.getXMLChildren(rootElement, "gmd:contact");
        for (Element rootPointOfContactElement : rootPointOfContactElements) {
            ResponsibleParty rootPointOfContact = ResponsibleParty.parseIso19139Node(IndexUtils.getXMLChild(rootPointOfContactElement, "gmd:CI_ResponsibleParty"));
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

        this.setDocument(documentPartList.isEmpty() ? null :
                String.join(NL + NL, documentPartList));


        // Set the metadata link to the original GeoNetwork URL.
        // If we find a valid "point-of-truth" URL in the document,
        // we will use that one instead.
        URL metadataRecordUrl = null;
        if (pointOfTruthUrlStr != null) {
            try {
                metadataRecordUrl = new URL(pointOfTruthUrlStr);
            } catch(Exception ex) {
                messages.addMessage(Messages.Level.ERROR, String.format("Invalid metadata record URL found in Point Of Truth of record %s: %s", this.getId(), pointOfTruthUrlStr), ex);
            }
        }

        if (metadataRecordUrl == null) {
            String geonetworkMetadataUrlStr = String.format("%s/srv/eng/metadata.show?uuid=%s", geoNetworkUrlStr, this.getId());
            try {
                metadataRecordUrl = new URL(geonetworkMetadataUrlStr);
            } catch(Exception ex) {
                messages.addMessage(Messages.Level.ERROR, String.format("Invalid metadata record URL for record %s: %s", this.getId(), geonetworkMetadataUrlStr), ex);
            }
        }

        this.setLink(metadataRecordUrl);
    }

    // GeoNetwork 3
    private static String parseIso19115_3_2018ExtentList(List<Element> extentList) {
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

                        Polygon polygon = GeoNetworkRecord.parseBoundingBox(
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
                            List<Element> multiSurfaceList = IndexUtils.getXMLChildren(gexPolygonElement, "gml:MultiSurface");
                            for (Element multiSurfaceElement : multiSurfaceList) {
                                List<Element> surfaceMemberList = IndexUtils.getXMLChildren(multiSurfaceElement, "gml:surfaceMember");
                                for (Element surfaceMemberElement : surfaceMemberList) {
                                    List<Element> gmlPolygonList = IndexUtils.getXMLChildren(surfaceMemberElement, "gml:Polygon");
                                    for (Element gmlPolygonElement : gmlPolygonList) {
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
                                            exteriorLinearRing = GeoNetworkRecord.parsePosListLinearRing(IndexUtils.parseText(exteriorPosListElement), dimension, false);
                                        }

                                        Element coordinatesElement = IndexUtils.getXMLChild(exteriorLinearRingElement, "gml:coordinates");
                                        if (coordinatesElement != null) {
                                            exteriorLinearRing = GeoNetworkRecord.parseCoordinatesLinearRing(IndexUtils.parseText(coordinatesElement));
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
                                                interiorLinearRing = GeoNetworkRecord.parsePosListLinearRing(IndexUtils.parseText(interiorPosListElement), interiorDimension, false);
                                            }

                                            Element interiorCoordinatesElement = IndexUtils.getXMLChild(interiorLinearRingElement, "gml:coordinates");
                                            if (interiorCoordinatesElement != null) {
                                                interiorLinearRing = GeoNetworkRecord.parseCoordinatesLinearRing(IndexUtils.parseText(interiorCoordinatesElement));
                                            }

                                            holes.add(interiorLinearRing);
                                        }

                                        if (exteriorLinearRing != null) {
                                            Polygon polygon = null;
                                            if (holes.isEmpty()) {
                                                polygon = GeoNetworkRecord.GEOMETRY_FACTORY.createPolygon(exteriorLinearRing);
                                            } else {
                                                polygon = GeoNetworkRecord.GEOMETRY_FACTORY.createPolygon(exteriorLinearRing, holes.toArray(new LinearRing[0]));
                                            }

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

        return GeoNetworkRecord.polygonsToWKT(polygons);
    }

    // GeoNetwork 2 - Needs to be tested against a GeoNetwork 2 server
    private static String parseIso19139ExtentList(List<Element> extentList) {
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

                        Polygon polygon = GeoNetworkRecord.parseBoundingBox(
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
                                            LinearRing linearRing = GeoNetworkRecord.parseCoordinatesLinearRing(IndexUtils.parseText(coordinatesElement));
                                            if (linearRing != null) {
                                                Polygon polygon = GeoNetworkRecord.GEOMETRY_FACTORY.createPolygon(linearRing);
                                                if (polygon != null) {
                                                    polygons.add(polygon);
                                                }
                                            }
                                        }

                                        List<Element> posListList = IndexUtils.getXMLChildren(linearRingElement, "gml:posList");
                                        for (Element posListElement : posListList) {
                                            LinearRing linearRing = GeoNetworkRecord.parsePosListLinearRing(IndexUtils.parseText(posListElement), 2, true);
                                            if (linearRing != null) {
                                                Polygon polygon = GeoNetworkRecord.GEOMETRY_FACTORY.createPolygon(linearRing);
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

        return GeoNetworkRecord.polygonsToWKT(polygons);
    }

    private static Polygon parseBoundingBox(String northStr, String eastStr, String southStr, String westStr) {
        double north = Double.parseDouble(northStr);
        double east = Double.parseDouble(eastStr);
        double south = Double.parseDouble(southStr);
        double west = Double.parseDouble(westStr);

        // Normalise north and south
        if (north > 90) { north = 90; }
        if (south < -90) { south = -90; }

        boolean northSouthEquals = (Math.abs(north - south) < GIS_EPSILON);
        boolean eastWestEquals = (Math.abs(east - west) < GIS_EPSILON);

        // Elastic Search do not allow bbox with 0 area.
        // The WKT library does not seem to allow mixing of point, lines and polygons.
        // The easiest solution is to slightly increase the size of the bbox.
        if (northSouthEquals) {
            north += GIS_EPSILON/2;
            south -= GIS_EPSILON/2;

            // Normalise north and south
            if (north > 90) { north = 90; }
            if (south < -90) { south = -90; }
        }
        if (eastWestEquals) {
            west -= GIS_EPSILON/2;
            east += GIS_EPSILON/2;
        }

        return GeoNetworkRecord.GEOMETRY_FACTORY.createPolygon(new Coordinate[] {
            new Coordinate(west, north),
            new Coordinate(east, north),
            new Coordinate(east, south),
            new Coordinate(west, south),
            new Coordinate(west, north)
        });
    }

    private static LinearRing parseCoordinatesLinearRing(String coordinatesStr) {
        // 145.0010529126619,-10.68188894877146,0 142.5315075081651,-10.68756998592151,0 142.7881039791155,-11.08215241694377,0 142.8563635161634,-11.84494938862347,0 ...
        if (coordinatesStr == null || coordinatesStr.isEmpty()) {
            return null;
        }

        List<Coordinate> coordinateList = new ArrayList<Coordinate>();
        for (String coordinatePoint : coordinatesStr.split(" ")) {
            String[] coordinateValues = coordinatePoint.split(",");
            if (coordinateValues.length >= 2) {
                double lon = Double.parseDouble(coordinateValues[0]);
                double lat = Double.parseDouble(coordinateValues[1]);

                Coordinate coordinate = new Coordinate(lon, lat);
                coordinateList.add(coordinate);
            }
        }

        if (coordinateList.isEmpty()) {
            return null;
        }

        return GeoNetworkRecord.GEOMETRY_FACTORY.createLinearRing(coordinateList.toArray(new Coordinate[0]));
    }

    private static LinearRing parsePosListLinearRing(String coordinatesStr, int dimension, boolean lonlat) {
        // 151.083984375 -24.521484375 153.80859375 -24.521484375 153.45703125 -20.830078125 147.12890625 -17.490234375 145.810546875 -13.798828125 144.4921875 -12.832031250000002 ...
        if (coordinatesStr == null || coordinatesStr.isEmpty() || dimension < 2) {
            return null;
        }

        List<Coordinate> coordinateList = new ArrayList<Coordinate>();
        String[] coordinateValues = coordinatesStr.split(" ");
        int coordinateValueLength = coordinateValues.length;
        if (coordinateValueLength > 0 && (coordinateValueLength % dimension) == 0) {
            for (int i=0; i<coordinateValueLength; i+=dimension) {
                double lon, lat;
                if (lonlat) {
                    lon = Double.parseDouble(coordinateValues[i]);
                    lat = Double.parseDouble(coordinateValues[i+1]);
                } else {
                    lat = Double.parseDouble(coordinateValues[i]);
                    lon = Double.parseDouble(coordinateValues[i+1]);
                }

                Coordinate coordinate = new Coordinate(lon, lat);
                coordinateList.add(coordinate);
            }
        }

        if (coordinateList.isEmpty()) {
            return null;
        }

        return GeoNetworkRecord.GEOMETRY_FACTORY.createLinearRing(coordinateList.toArray(new Coordinate[0]));
    }

    private static String polygonsToWKT(List<Polygon> polygons) {
        if (polygons == null || polygons.isEmpty()) {
            return null;
        }

        Geometry multiPolygon = null;
        if (polygons.size() == 1) {
            multiPolygon = polygons.get(0);
        } else {
            multiPolygon = GeoNetworkRecord.GEOMETRY_FACTORY.createMultiPolygon(polygons.toArray(new Polygon[0]));
        }

        // norm(): Normalise the geometry. Join intersecting polygons and remove duplicates.
        // buffer(0): Fix self intersecting polygons by removing parts.
        //   It's not perfect, but at least the resulting polygon should be valid.
        //   See: https://stackoverflow.com/questions/31473553/is-there-a-way-to-convert-a-self-intersecting-polygon-to-a-multipolygon-in-jts
        return GeoNetworkRecord.WKT_WRITER.write(multiPolygon.norm().buffer(0));
    }


    private static void addResponsibleParty(ResponsibleParty responsibleParty, Map<String, List<ResponsibleParty>> responsiblePartyMap) {
        if (responsibleParty != null && responsiblePartyMap != null) {
            String role = responsibleParty.role;
            if (role == null) {
                role = "UNKNOWN";
            }
            List<ResponsibleParty> list = responsiblePartyMap.get(role);
            if (list == null) {
                list = new ArrayList<ResponsibleParty>();
                responsiblePartyMap.put(role, list);
            }
            list.add(responsibleParty);
        }
    }

    public String getParentUUID() {
        return this.parentUUID;
    }

    public String getParentTitle() {
        return this.parentTitle;
    }

    public void setParentTitle(String parentTitle) {
        this.parentTitle = parentTitle;
    }

    // Add the parent title at the end of the document
    //     to index it with the record.
    // NOTE: This method generate a document for indexation. It doesn't need to look pretty.
    //     It just need to look good enough for the generated search highlights.
    @Override
    public String getDocument() {
        String document = super.getDocument();
        if (this.parentTitle != null && !this.parentTitle.isEmpty()) {
            return document + NL + NL + "Parent: " + this.parentTitle;
        }
        return document;
    }

    public static GeoNetworkRecord load(JSONObject json, Messages messages) {
        GeoNetworkRecord record = new GeoNetworkRecord();
        record.loadJSON(json, messages);
        record.parentUUID = json.optString("parentUUID", null);
        record.parentTitle = json.optString("parent", null);

        return record;
    }

    @Override
    public JSONObject toJSON() {
        return super.toJSON()
            .put("parentUUID", this.parentUUID)
            .put("parent", this.parentTitle);
    }


    private static class ResponsibleParty {
        private String name;
        private String position;
        private String organisation;
        private String phone;
        private String address;
        private String email;
        private OnlineResource website;
        private String role; // pointOfContact, principalInvestigator, coInvestigator, metadataContact, etc

        private ResponsibleParty() {}

        // Parse a "cit:CI_Responsibility" node
        public static ResponsibleParty parseIso19115_3_2018Node(Node responsiblePartyNode) {
            if (!(responsiblePartyNode instanceof Element)) {
                return null;
            }

            ResponsibleParty responsibleParty = new ResponsibleParty();

            Element role = IndexUtils.getXMLChild(responsiblePartyNode, "cit:role");
            Element roleCode = IndexUtils.getXMLChild(role, "cit:CI_RoleCode");
            responsibleParty.role = IndexUtils.parseAttribute(roleCode, "codeListValue");

            Element party = IndexUtils.getXMLChild(responsiblePartyNode, "cit:party");
            Element ciOrganisation = IndexUtils.getXMLChild(party, "cit:CI_Organisation");

            responsibleParty.organisation = IndexUtils.parseCharacterString(IndexUtils.getXMLChild(ciOrganisation, "cit:name"));

            Element individual = IndexUtils.getXMLChild(ciOrganisation, "cit:individual");
            Element ciIndividual = IndexUtils.getXMLChild(individual, "cit:CI_Individual");

            responsibleParty.name = IndexUtils.parseCharacterString(IndexUtils.getXMLChild(ciIndividual, "cit:name"));
            responsibleParty.position = IndexUtils.parseCharacterString(IndexUtils.getXMLChild(ciIndividual, "cit:positionName"));

            Element contactInfo = IndexUtils.getXMLChild(ciIndividual, "cit:contactInfo");
            Element ciContact = IndexUtils.getXMLChild(contactInfo, "cit:CI_Contact");

            // Get the phone number
            List<Element> phoneElements = IndexUtils.getXMLChildren(ciContact, "cit:phone");
            for (Element phoneElement : phoneElements) {
                Element ciTelephone = IndexUtils.getXMLChild(phoneElement, "cit:CI_Telephone");

                Element numberType = IndexUtils.getXMLChild(ciTelephone, "cit:numberType");
                Element ciTelephoneTypeCode = IndexUtils.getXMLChild(numberType, "cit:CI_TelephoneTypeCode");
                String type = IndexUtils.parseAttribute(ciTelephoneTypeCode, "codeListValue");

                String number = IndexUtils.parseCharacterString(IndexUtils.getXMLChild(ciTelephone, "cit:number"));

                // Parse the phone number, ignore facsimile, etc
                if ("voice".equalsIgnoreCase(type)) {
                    responsibleParty.phone = number;
                }
            }

            // Get the address (spread across multiple fields)
            Element address = IndexUtils.getXMLChild(ciContact, "cit:address");
            Element ciAddress = IndexUtils.getXMLChild(address, "cit:CI_Address");
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
            responsibleParty.address = addressPartList.isEmpty() ? null : String.join(", ", addressPartList);

            // E-mail address
            responsibleParty.email = IndexUtils.parseCharacterString(IndexUtils.getXMLChild(ciAddress, "cit:electronicMailAddress"));

            // Website
            Element onlineResource = IndexUtils.getXMLChild(ciContact, "cit:onlineResource");
            responsibleParty.website = OnlineResource.parseIso19115_3_2018Node(IndexUtils.getXMLChild(onlineResource, "cit:CI_OnlineResource"));

            return responsibleParty;
        }

        // Parse a "gmd:CI_ResponsibleParty" node
        public static ResponsibleParty parseIso19139Node(Node responsiblePartyNode) {
            if (!(responsiblePartyNode instanceof Element)) {
                return null;
            }

            ResponsibleParty responsibleParty = new ResponsibleParty();

            responsibleParty.name = IndexUtils.parseCharacterString(IndexUtils.getXMLChild(responsiblePartyNode, "gmd:individualName"));
            responsibleParty.position = IndexUtils.parseCharacterString(IndexUtils.getXMLChild(responsiblePartyNode, "gmd:positionName"));
            responsibleParty.organisation = IndexUtils.parseCharacterString(IndexUtils.getXMLChild(responsiblePartyNode, "gmd:organisationName"));

            Element contactInfo = IndexUtils.getXMLChild(responsiblePartyNode, "gmd:contactInfo");
            Element ciContact = IndexUtils.getXMLChild(contactInfo, "gmd:CI_Contact");

            // Get the phone number
            Element phone = IndexUtils.getXMLChild(ciContact, "gmd:phone");
            Element telephone = IndexUtils.getXMLChild(phone, "gmd:CI_Telephone");
            Element voice = IndexUtils.getXMLChild(telephone, "gmd:voice");
            responsibleParty.phone = IndexUtils.parseCharacterString(voice);

            // Get the address (spread across multiple fields)
            Element address = IndexUtils.getXMLChild(ciContact, "gmd:address");
            Element ciAddress = IndexUtils.getXMLChild(address, "gmd:CI_Address");
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
            responsibleParty.address = addressPartList.isEmpty() ? null : String.join(", ", addressPartList);

            // E-mail address
            responsibleParty.email = IndexUtils.parseCharacterString(IndexUtils.getXMLChild(ciAddress, "gmd:electronicMailAddress"));

            // Website
            Element onlineResource = IndexUtils.getXMLChild(ciContact, "gmd:onlineResource");
            responsibleParty.website = OnlineResource.parseIso19139Node(IndexUtils.getXMLChild(onlineResource, "gmd:CI_OnlineResource"));

            Element role = IndexUtils.getXMLChild(responsiblePartyNode, "gmd:role");
            Element roleCode = IndexUtils.getXMLChild(role, "gmd:CI_RoleCode");
            responsibleParty.role = IndexUtils.parseAttribute(roleCode, "codeListValue");

            return responsibleParty;
        }

        public JSONObject toJSON() {
            return new JSONObject()
                .put("name", this.name)
                .put("position", this.position)
                .put("organisation", this.organisation)
                .put("phone", this.phone)
                .put("address", this.address)
                .put("email", this.email)
                .put("website", this.website == null ? null : this.website.toJSON())
                .put("role", this.role);
        }

        @Override
        public String toString() {
            List<String> partList = new ArrayList<String>();
            if (this.name != null) {
                partList.add(this.position == null ? this.name : String.format("%s (%s)", this.name, this.position));
            }
            if (this.organisation != null) {
                partList.add(this.organisation);
            }
            if (this.phone != null) {
                partList.add(String.format("Phone: %s", this.phone));
            }
            if (this.address != null) {
                partList.add(this.address);
            }
            if (this.email != null) {
                partList.add(this.email);
            }
            // NOTE: Do not index the URLs
            //if (this.website != null) {
            //    partList.add(this.website.toString());
            //}

            // Role do not need to be indexed.
            return partList.isEmpty() ? null : String.join(NL, partList);
        }
    }

    private static class OnlineResource {
        private String protocol; // WWW:LINK-1.0-http--link
        private String linkage;  // https://eatlas.org.au
        private String name;     // eAtlas portal
        private String description; // NESP TWQ Project page

        private OnlineResource() {}

        // Parse a "cit:CI_OnlineResource" node
        public static OnlineResource parseIso19115_3_2018Node(Node onlineResourceNode) {
            if (!(onlineResourceNode instanceof Element)) {
                return null;
            }

            OnlineResource onlineResource = new OnlineResource();

            onlineResource.protocol = IndexUtils.parseCharacterString(IndexUtils.getXMLChild(onlineResourceNode, "cit:protocol"));
            onlineResource.name = IndexUtils.parseCharacterString(IndexUtils.getXMLChild(onlineResourceNode, "cit:name"));
            onlineResource.description = IndexUtils.parseCharacterString(IndexUtils.getXMLChild(onlineResourceNode, "cit:description"));
            onlineResource.linkage = IndexUtils.parseCharacterString(IndexUtils.getXMLChild(onlineResourceNode, "cit:linkage"));

            return onlineResource;
        }

        // Parse a "gmd:CI_OnlineResource" node
        public static OnlineResource parseIso19139Node(Node onlineResourceNode) {
            if (!(onlineResourceNode instanceof Element)) {
                return null;
            }

            OnlineResource onlineResource = new OnlineResource();

            onlineResource.protocol = IndexUtils.parseCharacterString(IndexUtils.getXMLChild(onlineResourceNode, "gmd:protocol"));
            onlineResource.name = IndexUtils.parseCharacterString(IndexUtils.getXMLChild(onlineResourceNode, "gmd:name"));
            onlineResource.description = IndexUtils.parseCharacterString(IndexUtils.getXMLChild(onlineResourceNode, "gmd:description"));

            Element linkage = IndexUtils.getXMLChild(onlineResourceNode, "gmd:linkage");
            Element linkageUrl = IndexUtils.getXMLChild(linkage, "gmd:URL");
            onlineResource.linkage = IndexUtils.parseText(linkageUrl);

            return onlineResource;
        }

        public String getLabel() {
            return this.name == null ? this.description : this.name;
        }

        public JSONObject toJSON() {
            return new JSONObject()
                .put("protocol", this.protocol)
                .put("linkage", this.linkage)
                .put("name", this.name)
                .put("description", this.description);
        }

        @Override
        public String toString() {
            String safeProtocol = this.protocol == null ? "" : this.protocol;
            switch(safeProtocol) {
                case "OGC:WMS-1.1.1-http-get-map":
                    return this.name == null ? "" : String.format("Layer: %s", this.name);

                default:
                    String label = this.getLabel();
                    if (label == null) {
                        label = "";
                    }
                    // NOTE: Do not index the URLs
                    return label;
            }
        }
    }
}
