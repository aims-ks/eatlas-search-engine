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

import au.gov.aims.eatlas.searchengine.entity.GeoServerLayer;

import java.util.List;

public class GeoServerIndex extends AbstractIndex<GeoServerLayer> {
    private String geoServerUrl;
    private String geoServerVersion;

    public GeoServerIndex(String index) {
        super(index);
    }

    /**
     * index: eatlas_layer
     * geoServerUrl: https://maps.eatlas.org.au/maps
     * geoServerVersion: 2.13.2
     */
    public GeoServerIndex(String index, String geoServerUrl, String geoServerVersion) {
        super(index);
        this.geoServerUrl = geoServerUrl;
        this.geoServerVersion = geoServerVersion;
    }

    @Override
    public List<GeoServerLayer> harvest(int limit, int offset) {
        // TODO Implement
        return null;
    }

    public String getGeoServerUrl() {
        return this.geoServerUrl;
    }

    public void setGeoServerUrl(String geoServerUrl) {
        this.geoServerUrl = geoServerUrl;
    }

    public String getGeoServerVersion() {
        return this.geoServerVersion;
    }

    public void setGeoServerVersion(String geoServerVersion) {
        this.geoServerVersion = geoServerVersion;
    }
}
