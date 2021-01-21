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

import org.elasticsearch.common.text.Text;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Bean used to hold a search result
 */
public class SearchResult {
    // URL to follow to access the indexed document
    private String id;
    private JSONObject entity;

    private List<String> highlights;

    // The index in which the result was found
    private String index;

    // Lucene document score
    //   https://lucene.apache.org/core/8_6_2/core/org/apache/lucene/search/ScoreDoc.html
    private float score;

    public String getId() {
        return this.id;
    }

    public SearchResult setId(String id) {
        this.id = id;
        return this;
    }

    public JSONObject getEntity() {
        return this.entity;
    }

    public SearchResult setEntity(JSONObject entity) {
        this.entity = entity;
        return this;
    }

    public List<String> getHighlights() {
        return this.highlights;
    }

    public SearchResult addHighlight(String highlight) {
        if (this.highlights == null) {
            this.highlights = new ArrayList<String>();
        }
        this.highlights.add(highlight);
        return this;
    }

    // Helper
    public SearchResult addHighlights(Map<String, HighlightField> highlightsMap) {
        if (highlightsMap != null) {
            for (HighlightField highlightField : highlightsMap.values()) {
                Text[] fragments = highlightField.fragments();
                if (fragments != null) {
                    for (Text fragment : fragments) {
                        this.addHighlight(fragment.string());
                    }
                }
            }
        }
        return this;
    }

    public String getIndex() {
        return this.index;
    }

    public SearchResult setIndex(String index) {
        this.index = index;
        return this;
    }

    public float getScore() {
        return this.score;
    }

    public SearchResult setScore(float score) {
        this.score = score;
        return this;
    }

    public JSONObject toJSON() {
        return new JSONObject()
            .put("id", this.id)
            .put("index", this.index)
            .put("score", this.score)
            .put("entity", this.entity)
            .put("highlights", this.highlights);
    }

    @Override
    public String toString() {
        return this.toJSON().toString();
    }
}
