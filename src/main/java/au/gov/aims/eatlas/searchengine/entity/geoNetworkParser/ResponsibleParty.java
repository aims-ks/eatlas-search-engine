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

import java.util.ArrayList;
import java.util.List;

public class ResponsibleParty {
    private final String name;
    private final String position;
    private final String organisation;
    private final String phone;
    private final String address;
    private final String email;
    private final OnlineResource website;
    private final String role; // pointOfContact, principalInvestigator, coInvestigator, metadataContact, etc

    public ResponsibleParty(String name, String organisation, String position, String role,
            String phone, String address, String email,
            OnlineResource website) {

        this.name = name;
        this.position = position;
        this.organisation = organisation;
        this.phone = phone;
        this.address = address;
        this.email = email;
        this.website = website;
        this.role = role;
    }

    public String getName() {
        return this.name;
    }

    public String getPosition() {
        return this.position;
    }

    public String getOrganisation() {
        return this.organisation;
    }

    public String getPhone() {
        return this.phone;
    }

    public String getAddress() {
        return this.address;
    }

    public String getEmail() {
        return this.email;
    }

    public OnlineResource getWebsite() {
        return this.website;
    }

    public String getRole() {
        return this.role;
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
        return partList.isEmpty() ? null : String.join(AbstractParser.NL, partList);
    }
}
