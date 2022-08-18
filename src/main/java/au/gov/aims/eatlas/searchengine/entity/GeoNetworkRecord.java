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
import au.gov.aims.eatlas.searchengine.entity.geoNetworkParser.AbstractParser;
import au.gov.aims.eatlas.searchengine.entity.geoNetworkParser.ISO19115_3_2018_parser;
import au.gov.aims.eatlas.searchengine.entity.geoNetworkParser.ISO19139_parser;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class GeoNetworkRecord extends Entity {
    private String metadataSchema;
    private String geoNetworkVersion;
    private String parentUUID;
    // TODO Get from index after the harvest, if we decide that it's needed
    private String parentTitle;

    private GeoNetworkRecord() {}

    // geoNetworkUrlStr: https://eatlas.org.au/geonetwork
    // metadataRecordUUID: UUID of the record. If omitted, the parser will grab the UUID from the document.
    public GeoNetworkRecord(String index, String metadataRecordUUID, String metadataSchema, String geonetworkVersion) {
        this.setIndex(index);
        this.metadataSchema = metadataSchema;
        this.geoNetworkVersion = geonetworkVersion;

        if (metadataRecordUUID != null) {
            metadataRecordUUID = metadataRecordUUID.trim();
            if (metadataRecordUUID.isEmpty()) {
                metadataRecordUUID = null;
            }
        }

        if (metadataRecordUUID != null) {
            this.setId(metadataRecordUUID);
        }
    }

    public void parseRecord(String geoNetworkUrlStr, Document xmlMetadataRecord, Messages messages) {
        String metadataRecordUUID = this.getId();

        if (this.metadataSchema == null || this.metadataSchema.isEmpty()) {
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

        AbstractParser parser = null;
        switch(this.metadataSchema) {
            case "iso19139":
            case "iso19139.anzlic":
            case "iso19139.mcp":
            case "iso19139.mcp-1.4":
                parser = new ISO19139_parser();
                break;

            case "iso19115-3.2018":
                parser = new ISO19115_3_2018_parser();
                break;

            default:
                messages.addMessage(Messages.Level.WARNING, String.format("Metadata UUID %s has unsupported schema %s", metadataRecordUUID, metadataSchema));
                break;
        }

        if (parser != null) {
            parser.parseRecord(this, geoNetworkUrlStr, root, messages);
        }
    }

    public String getMetadataSchema() {
        return this.metadataSchema;
    }

    public String getGeoNetworkVersion() {
        return this.geoNetworkVersion;
    }

    public String getParentUUID() {
        return this.parentUUID;
    }

    public void setParentUUID(String parentUUID) {
        this.parentUUID = parentUUID;
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
    //     It just needs to look good enough for the generated search highlights.
    @Override
    public String getDocument() {
        String document = super.getDocument();
        if (this.parentTitle != null && !this.parentTitle.isEmpty()) {
            return document + AbstractParser.NL + AbstractParser.NL + "Parent: " + this.parentTitle;
        }
        return document;
    }

    public static GeoNetworkRecord load(JSONObject json, Messages messages) {
        GeoNetworkRecord record = new GeoNetworkRecord();
        record.loadJSON(json, messages);
        record.metadataSchema = json.optString("metadataSchema", null);
        record.geoNetworkVersion = json.optString("geoNetworkVersion", null);
        record.parentUUID = json.optString("parentUUID", null);
        record.parentTitle = json.optString("parent", null);

        return record;
    }

    @Override
    public JSONObject toJSON() {
        return super.toJSON()
            .put("metadataSchema", this.metadataSchema)
            .put("geoNetworkVersion", this.geoNetworkVersion)
            .put("parentUUID", this.parentUUID)
            .put("parent", this.parentTitle);
    }
}
