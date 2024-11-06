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

import java.util.HashMap;
import java.util.Map;

public class Summary {
    // Total number of search results returned by the search query
    private Long hits;
    private Long pages; // Number of pages of results

    // Key = index
    private Map<String, IndexSummary> indexSummaries;

    public Long getHits() {
        return this.hits;
    }

    public Summary setHits(Long hits) {
        this.hits = hits;
        return this;
    }

    public Long getPages() {
        return this.pages;
    }

    public Summary setPages(Long pages) {
        this.pages = pages;
        return this;
    }

    public Map<String, IndexSummary> getIndexSummaries() {
        return this.indexSummaries;
    }

    public Summary setIndexSummaries(Map<String, IndexSummary> indexes) {
        this.indexSummaries = indexes;
        return this;
    }

    public Summary putIndexSummary(IndexSummary indexSummary) {
        if (this.indexSummaries == null) {
            this.indexSummaries = new HashMap<String, IndexSummary>();
        }
        this.indexSummaries.put(indexSummary.getIndex(), indexSummary);

        return this;
    }
    public IndexSummary getIndexSummary(String index) {
        return this.indexSummaries == null ? null : this.indexSummaries.get(index);
    }

    public JSONObject toJSON() {
        JSONObject jsonIndexSummaries = new JSONObject();
        if (this.indexSummaries != null) {
            for (Map.Entry<String, IndexSummary> indexEntry : this.indexSummaries.entrySet()) {
                jsonIndexSummaries.put(indexEntry.getKey(), indexEntry.getValue().toJSON());
            }
        }

        return new JSONObject()
            .put("hits", this.hits)
            .put("pages", this.pages)
            .put("indexes", jsonIndexSummaries);
    }

    @Override
    public String toString() {
        return this.toJSON().toString();
    }
}
