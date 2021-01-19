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

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Bean used to hold the results of a search, and its summary
 */
public class SearchResults {
    private Summary summary;
    private List<SearchResult> searchResults;

    public Summary getSummary() {
        return this.summary;
    }

    public SearchResults setSummary(Summary summary) {
        this.summary = summary;
        return this;
    }

    public List<SearchResult> getSearchResults() {
        return this.searchResults;
    }

    public SearchResults setSearchResults(List<SearchResult> searchResults) {
        this.searchResults = searchResults;
        return this;
    }

    public SearchResults addSearchResult(SearchResult searchResult) {
        if (this.searchResults == null) {
            this.searchResults = new ArrayList<SearchResult>();
        }

        this.searchResults.add(searchResult);
        return this;
    }

    public JSONObject toJSON() {
        JSONArray jsonResults = new JSONArray();
        if (this.searchResults != null) {
            for (SearchResult result : this.searchResults) {
                jsonResults.put(result.toJSON());
            }
        }

        return new JSONObject()
            .put("summary", this.summary.toJSON())
            .put("results", jsonResults);
    }

    @Override
    public String toString() {
        return this.toJSON().toString();
    }
}
