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

import au.gov.aims.eatlas.searchengine.entity.AtlasMapperLayer;
import org.json.JSONObject;

public class AtlasMapperIndexer extends AbstractIndexer<AtlasMapperLayer> {
    private String atlasMapperUrl;
    private String atlasMapperVersion;

    public AtlasMapperLayer load(JSONObject json) {
        return AtlasMapperLayer.load(json);
    }

    /**
     * index: eatlas_layer
     * atlasMapperUrl: https://maps.eatlas.org.au/atlasmapper
     * atlasMapperVersion: 2.2.0
     */
    public AtlasMapperIndexer(String index, String atlasMapperUrl, String atlasMapperVersion) {
        super(index);
        this.atlasMapperUrl = atlasMapperUrl;
        this.atlasMapperVersion = atlasMapperVersion;
    }

    @Override
    public void harvest() {
        // TODO Implement
    }

    public String getAtlasMapperUrl() {
        return this.atlasMapperUrl;
    }

    public void setAtlasMapperUrl(String atlasMapperUrl) {
        this.atlasMapperUrl = atlasMapperUrl;
    }

    public String getAtlasMapperVersion() {
        return this.atlasMapperVersion;
    }

    public void setAtlasMapperVersion(String atlasMapperVersion) {
        this.atlasMapperVersion = atlasMapperVersion;
    }
}
