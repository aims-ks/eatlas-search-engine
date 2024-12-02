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

import au.gov.aims.eatlas.searchengine.entity.geoNetworkParser.*;
import au.gov.aims.eatlas.searchengine.logger.AbstractLogger;
import au.gov.aims.eatlas.searchengine.logger.Level;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class GeoNetworkRecord extends Entity {
    private String metadataSchema;
    private String geoNetworkVersion;
    private String parentUUID;
    // TODO Get from index after the harvest, if we decide that it's needed
    private String parentTitle;
    
    private GeoNetworkParserFactory geoNetworkParserFactory;

    private GeoNetworkRecord() {}

    // geoNetworkUrlStr: https://eatlas.org.au/geonetwork
    // metadataRecordUUID: UUID of the record. If omitted, the parser will grab the UUID from the document.
    public GeoNetworkRecord(String index, String metadataRecordUUID, String metadataSchema, String geoNetworkVersion) {
        this.setIndex(index);
        this.metadataSchema = metadataSchema;
        this.geoNetworkVersion = geoNetworkVersion;

        if (metadataRecordUUID != null) {
            metadataRecordUUID = metadataRecordUUID.trim();
            if (metadataRecordUUID.isEmpty()) {
                metadataRecordUUID = null;
            }
        }

        if (metadataRecordUUID != null) {
            this.setId(metadataRecordUUID);
        }

        this.geoNetworkParserFactory = new DefaultGeoNetworkParserFactory();
    }

    public void parseRecord(String geoNetworkUrlStr, Document xmlMetadataRecord, AbstractLogger logger) {
        String metadataRecordUUID = this.getId();

        if (this.metadataSchema == null || this.metadataSchema.isEmpty()) {
            logger.addMessage(Level.WARNING, String.format("Metadata UUID %s has no defined metadata schema.", metadataRecordUUID));
            return;
        }

        if (xmlMetadataRecord == null) {
            logger.addMessage(Level.WARNING, String.format("Metadata UUID %s has no metadata record.", metadataRecordUUID));
            return;
        }
        
        // Get the XML root element
        Element root = xmlMetadataRecord.getDocumentElement();
        if (root == null) {
            logger.addMessage(Level.WARNING, String.format("Metadata UUID %s has no root in its metadata document.", metadataRecordUUID));
            return;
        }
        // Fix the document, if needed
        root.normalize();
        
        AbstractParser parser = this.geoNetworkParserFactory.getParser(this.metadataSchema);

        if (parser == null) {
            logger.addMessage(Level.WARNING, String.format("Metadata UUID %s has unsupported schema %s", metadataRecordUUID, metadataSchema));
            return;
        }

        // Delegate the parsing task
        parser.parseRecord(this, geoNetworkUrlStr, root, logger);
    }

    public void setMetadataSchema(String metadataSchema) {
        this.metadataSchema = metadataSchema;
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

    public void setGeoNetworkParserFactory(GeoNetworkParserFactory geoNetworkParserFactory) {
        this.geoNetworkParserFactory = geoNetworkParserFactory;
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

    public static GeoNetworkRecord load(JSONObject json, AbstractLogger logger) {
        GeoNetworkRecord record = new GeoNetworkRecord();
        record.loadJSON(json, logger);
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
