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
public class Results {
    private Summary summary;
    private List<Result> results;

    public Summary getSummary() {
        return this.summary;
    }

    public Results setSummary(Summary summary) {
        this.summary = summary;
        return this;
    }

    public List<Result> getResults() {
        return this.results;
    }

    public Results setResults(List<Result> results) {
        this.results = results;
        return this;
    }

    public Results addResult(Result result) {
        if (this.results == null) {
            this.results = new ArrayList<Result>();
        }

        this.results.add(result);
        return this;
    }

    public JSONObject toJSON() {
        JSONArray jsonResults = new JSONArray();
        if (this.results != null) {
            for (Result result : this.results) {
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
