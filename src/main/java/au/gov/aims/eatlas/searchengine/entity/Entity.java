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
package au.gov.aims.eatlas.searchengine.entity;

import org.apache.log4j.Logger;
import org.json.JSONObject;

import java.net.URL;

public abstract class Entity {
    private static final Logger LOGGER = Logger.getLogger(DrupalNode.class.getName());

    private String id;
    private URL link;

    private String title;
    private String document;

    // The index in which the result was found
    private String index;

    // URL to the search result thumbnail image
    private URL thumbnail;

    // "en"
    private String langcode;

    public String getId() {
        return this.id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public URL getLink() {
        return this.link;
    }

    public void setLink(URL link) {
        this.link = link;
    }

    public String getTitle() {
        return this.title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDocument() {
        return this.document;
    }

    public void setDocument(String document) {
        this.document = document;
    }

    public String getIndex() {
        return this.index;
    }

    public void setIndex(String index) {
        this.index = index;
    }

    public URL getThumbnail() {
        return this.thumbnail;
    }

    public void setThumbnail(URL thumbnail) {
        this.thumbnail = thumbnail;
    }

    public String getLangcode() {
        return this.langcode;
    }

    public void setLangcode(String langcode) {
        this.langcode = langcode;
    }

    public JSONObject toJSON() {
        URL linkUrl = this.getLink();
        URL thumbnailUrl = this.getThumbnail();
        return new JSONObject()
            .put("id", this.getId())
            .put("class", this.getClass().getSimpleName())
            .put("link", linkUrl == null ? null : linkUrl.toString())
            .put("title", this.getTitle())
            .put("document", this.getDocument())
            .put("thumbnail", thumbnailUrl == null ? null : thumbnailUrl.toString())
            .put("langcode", this.getLangcode());
    }

    @Override
    public String toString() {
        JSONObject json = this.toJSON();
        return json == null ? null : json.toString(4);
    }
}
