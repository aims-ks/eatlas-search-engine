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
    // TODO Write parser for GeoNetwork 3.x
    public GeoNetworkRecord(String index, String geoNetworkVersion, String metadataRecordUUID, String geoNetworkUrlStr, Document xmlMetadataRecord, Messages messages) {
        this.setIndex(index);

        if (geoNetworkVersion.startsWith("2.")) {
            this.parseGeoNetwork2Record(index, metadataRecordUUID, geoNetworkUrlStr, xmlMetadataRecord, messages);
        } else {
            this.parseGeoNetwork3Record(index, metadataRecordUUID, geoNetworkUrlStr, xmlMetadataRecord, messages);
        }
    }

    /**
     * GeoNetwork 3.x
     */

    private void parseGeoNetwork3Record(String index, String metadataRecordUUID, String geoNetworkUrlStr, Document xmlMetadataRecord, Messages messages) {
        // TODO Implement!
    }


    /**
     * GeoNetwork 2.x
     */

    private void parseGeoNetwork2Record(String index, String metadataRecordUUID, String geoNetworkUrlStr, Document xmlMetadataRecord, Messages messages) {
        String pointOfTruthUrlStr = null;
        if (xmlMetadataRecord != null) {

            // Fix the document, if needed
            xmlMetadataRecord.getDocumentElement().normalize();
            Element root = xmlMetadataRecord.getDocumentElement();
            if (root != null) {
                if (metadataRecordUUID != null) {
                    String trimmedUuid = metadataRecordUUID.trim();
                    if (!trimmedUuid.isEmpty()) {
                        this.setId(metadataRecordUUID);
                    }
                }

                // UUID
                // NOTE: Get it from the XML document if not provided already
                if (this.getId() == null) {
                    this.setId(IndexUtils.parseCharacterString(IndexUtils.getXMLChild(root, "gmd:fileIdentifier")));
                }

                // Parent UUID
                this.parentUUID = IndexUtils.parseCharacterString(IndexUtils.getXMLChild(root, "gmd:parentIdentifier"));

                // The responsible parties are not always where they are expected.
                // Lets parse responsible parties where ever they are found and put them in the right category.
                // Key: role
                Map<String, List<ResponsibleParty>> responsiblePartyMap = new HashMap<>();

                // Metadata contact info
                List<Element> metadataContacts = IndexUtils.getXMLChildren(root, "gmd:contact");
                for (Element metadataContact : metadataContacts) {
                    ResponsibleParty metadataContactResponsibleParty = ResponsibleParty.parseNode(IndexUtils.getXMLChild(metadataContact, "gmd:CI_ResponsibleParty"));
                    addResponsibleParty(metadataContactResponsibleParty, responsiblePartyMap);
                }

                // Title
                Element identificationInfo = IndexUtils.getXMLChild(root, "gmd:identificationInfo");
                Element mdDataIdentification = IndexUtils.getXMLChild(identificationInfo, "gmd:MD_DataIdentification", "mcp:MD_DataIdentification");
                Element dataCitation = IndexUtils.getXMLChild(mdDataIdentification, "gmd:citation");
                Element dataCiCitation = IndexUtils.getXMLChild(dataCitation, "gmd:CI_Citation");
                this.setTitle(IndexUtils.parseCharacterString(IndexUtils.getXMLChild(dataCiCitation, "gmd:title")));

                String dataAbstract = IndexUtils.parseCharacterString(IndexUtils.getXMLChild(mdDataIdentification, "gmd:abstract"));

                // Point of contact in MD_DataIdentification
                List<Element> dataPointOfContactElements = IndexUtils.getXMLChildren(mdDataIdentification, "gmd:pointOfContact");
                for (Element dataPointOfContactElement : dataPointOfContactElements) {
                    ResponsibleParty dataPointOfContact = ResponsibleParty.parseNode(IndexUtils.getXMLChild(dataPointOfContactElement, "gmd:CI_ResponsibleParty"));
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
                            // Some records puts the full URL in the preview field.
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

                // TODO GIS Information (bbox, points, polygons, etc.
                // List<Element> extentList = IndexUtils.getXMLChildren(mdDataIdentification, "gmd:extent");

                // Online Resources
                // including pointOfTruthUrlStr
                List<OnlineResource> onlineResources = new ArrayList<>();
                Element distributionInfo = IndexUtils.getXMLChild(root, "gmd:distributionInfo");
                Element transferOptions = IndexUtils.getXMLChild(distributionInfo, "gmd:transferOptions");
                Element mdDigitalTransferOptions = IndexUtils.getXMLChild(transferOptions, "gmd:MD_DigitalTransferOptions");
                List<Element> onlineElements = IndexUtils.getXMLChildren(mdDigitalTransferOptions, "gmd:onLine");
                for (Element onlineElement : onlineElements) {
                    Element ciOnlineResource = IndexUtils.getXMLChild(onlineElement, "gmd:CI_OnlineResource");
                    OnlineResource onlineResource = OnlineResource.parseNode(ciOnlineResource);
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
                    ResponsibleParty citedResponsibleParty = ResponsibleParty.parseNode(IndexUtils.getXMLChild(citedResponsiblePartyElement, "gmd:CI_ResponsibleParty"));
                    addResponsibleParty(citedResponsibleParty, responsiblePartyMap);
                }

                // Point of contact in document root
                List<Element> rootPointOfContactElements = IndexUtils.getXMLChildren(root, "gmd:contact");
                for (Element rootPointOfContactElement : rootPointOfContactElements) {
                    ResponsibleParty rootPointOfContact = ResponsibleParty.parseNode(IndexUtils.getXMLChild(rootPointOfContactElement, "gmd:CI_ResponsibleParty"));
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
        }
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

        // Parse a "gmd:CI_ResponsibleParty" node
        public static ResponsibleParty parseNode(Node responsiblePartyNode) {
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
            responsibleParty.website = OnlineResource.parseNode(IndexUtils.getXMLChild(onlineResource, "gmd:CI_OnlineResource"));

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

        // Parse a "gmd:CI_OnlineResource" node
        public static OnlineResource parseNode(Node onlineResourceNode) {
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
