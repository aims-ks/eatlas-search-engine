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

import au.gov.aims.eatlas.searchengine.entity.GeoNetworkRecord;

import java.util.List;

public class GeoNetworkIndex extends AbstractIndex<GeoNetworkRecord> {
    private String geoNetworkUrl;
    private String geoNetworkVersion;

    public GeoNetworkIndex(String index) {
        super(index);
    }

    /**
     * index: eatlas_metadata
     * geoNetworkUrl: https://eatlas.org.au/geonetwork
     * geoNetworkVersion: 3.6.0 ?
     */
    public GeoNetworkIndex(String index, String geoNetworkUrl, String geoNetworkVersion) {
        super(index);
        this.geoNetworkUrl = geoNetworkUrl;
        this.geoNetworkVersion = geoNetworkVersion;
    }

    @Override
    public List<GeoNetworkRecord> harvest() {
        // TODO Implement
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
