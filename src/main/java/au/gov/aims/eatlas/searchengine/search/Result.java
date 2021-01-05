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

/**
 * Bean used to hold a search result
 */
public class Result {
    // URL to follow to access the indexed document
    private String link;

    private String title;
    private String document;

    // The index in which the result was found
    private String index;

    // Lucene document score
    //   https://lucene.apache.org/core/8_6_2/core/org/apache/lucene/search/ScoreDoc.html
    private float score;

    // URL to the search result thumbnail image
    private String thumbnail;

    // "en"
    private String langcode;

    public String getLink() {
        return this.link;
    }

    public Result setLink(String link) {
        this.link = link;
        return this;
    }

    public String getTitle() {
        return this.title;
    }

    public Result setTitle(String title) {
        this.title = title;
        return this;
    }

    public String getDocument() {
        return this.document;
    }

    public Result setDocument(String document) {
        this.document = document;
        return this;
    }

    public String getIndex() {
        return this.index;
    }

    public Result setIndex(String index) {
        this.index = index;
        return this;
    }

    public float getScore() {
        return this.score;
    }

    public Result setScore(float score) {
        this.score = score;
        return this;
    }

    public String getThumbnail() {
        return this.thumbnail;
    }

    public Result setThumbnail(String thumbnail) {
        this.thumbnail = thumbnail;
        return this;
    }

    public String getLangcode() {
        return this.langcode;
    }

    public Result setLangcode(String langcode) {
        this.langcode = langcode;
        return this;
    }

    public JSONObject toJSON() {
        return new JSONObject()
            .put("link", this.link)
            .put("index", this.index)
            .put("title", this.title)
            .put("score", this.score)
            .put("document", this.document)
            .put("thumbnail", this.thumbnail)
            .put("langcode", this.langcode);
    }

    @Override
    public String toString() {
        return this.toJSON().toString();
    }
}
