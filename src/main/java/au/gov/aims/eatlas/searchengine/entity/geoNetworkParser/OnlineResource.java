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

import org.json.JSONObject;

public class OnlineResource {
    private final String protocol; // WWW:LINK-1.0-http--link
    private final String linkage;  // https://eatlas.org.au
    private final String name;     // eAtlas portal
    private final String description; // NESP TWQ Project page

    public OnlineResource(String protocol, String linkage, String name, String description) {
        this.protocol = protocol;
        this.linkage = linkage;
        this.name = name;
        this.description = description;
    }

    public String getLabel() {
        return this.name == null ? this.description : this.name;
    }

    public String getProtocol() {
        return this.protocol;
    }

    public String getLinkage() {
        return this.linkage;
    }

    public String getName() {
        return this.name;
    }

    public String getDescription() {
        return this.description;
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
