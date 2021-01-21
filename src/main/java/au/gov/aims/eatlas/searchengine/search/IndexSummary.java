/*
 *  Copyright (C) 2021 Australian Institute of Marine Science
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
package au.gov.aims.eatlas.searchengine.search;

import org.json.JSONObject;

public class IndexSummary {
    private String index;

    private Long hits;

    public String getIndex() {
        return this.index;
    }

    public IndexSummary setIndex(String index) {
        this.index = index;
        return this;
    }

    public Long getHits() {
        return this.hits;
    }

    public IndexSummary setHits(Long hits) {
        this.hits = hits;
        return this;
    }

    public JSONObject toJSON() {
        return new JSONObject()
            .put("index", this.index)
            .put("hits", this.hits);
    }

    @Override
    public String toString() {
        return this.toJSON().toString();
    }
}
