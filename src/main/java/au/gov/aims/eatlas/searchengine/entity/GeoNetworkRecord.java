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

import org.apache.log4j.Logger;
import org.w3c.dom.Element;

import java.net.URL;

public class GeoNetworkRecord extends Entity {
    private static final Logger LOGGER = Logger.getLogger(GeoNetworkRecord.class.getName());

    public GeoNetworkRecord(String metadataRecordUUID, String metadataRecordUrlStr, Element xmlMetadataRecord) {
        this.setId(metadataRecordUUID);

        // Set the metadata link to the original GeoNetwork URL.
        // If we find a valid "point-of-truth" URL in the document,
        // we will use that one instead.
        if (metadataRecordUrlStr != null) {
            try {
                this.setLink(new URL(metadataRecordUrlStr));
            } catch(Exception ex) {
                LOGGER.error(String.format("Invalid metadata record URL: %s", metadataRecordUrlStr), ex);
            }
        }

        if (xmlMetadataRecord != null) {

            // TODO Parse the record!
            // Example:
            //   https://eatlas.org.au/geonetwork/srv/eng/xml_iso19139.mcp-1.4?uuid=ffa396f7-11fd-4be0-858d-2e4bc72a7ed7&styleSheet=xml_iso19139.mcp-1.4.xsl

        }
    }
}
