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

import au.gov.aims.eatlas.searchengine.entity.DrupalNode;

import java.util.List;

public class DrupalNodeIndex extends AbstractIndex<DrupalNode> {
    private String drupalUrl;
    private String drupalVersion;
    private String drupalNodeType;

    public DrupalNodeIndex(String index) {
        super(index);
    }

    /**
     * index: eatlas-article
     * drupalUrl: http://localhost:9090
     * drupalVersion: 9.0
     * drupalNodeType: article
     */
    public DrupalNodeIndex(String index, String drupalUrl, String drupalVersion, String drupalNodeType) {
        super(index);
        this.drupalUrl = drupalUrl;
        this.drupalVersion = drupalVersion;
        this.drupalNodeType = drupalNodeType;
    }

    @Override
    public List<DrupalNode> harvest() {
        // TODO Implement
        // http://localhost:9090/jsonapi/node/article?sort=-changed&page[limit]=10&page[offset]=10
        return null;
    }

    public String getDrupalUrl() {
        return this.drupalUrl;
    }

    public void setDrupalUrl(String drupalUrl) {
        this.drupalUrl = drupalUrl;
    }

    public String getDrupalVersion() {
        return this.drupalVersion;
    }

    public void setDrupalVersion(String drupalVersion) {
        this.drupalVersion = drupalVersion;
    }

    public String getDrupalNodeType() {
        return this.drupalNodeType;
    }

    public void setDrupalNodeType(String drupalNodeType) {
        this.drupalNodeType = drupalNodeType;
    }
}
