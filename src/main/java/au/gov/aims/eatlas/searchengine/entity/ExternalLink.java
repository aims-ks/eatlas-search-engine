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

import org.json.JSONObject;

import java.io.IOException;

public class ExternalLink extends Entity {
    private String title;
    private String url;
    private String htmlContent;
    private String textContent;

    public ExternalLink(JSONObject json) {
        this.title = json.optString("title", null);
        this.url = json.optString("url", null);
        this.htmlContent = json.optString("htmlContent", null);
        this.textContent = json.optString("textContent", null);
    }

    public ExternalLink(String title, String url) {
        this.title = title;
        this.url = url;
    }

    public void harvestHtmlContent() throws IOException {
        this.htmlContent = EntityUtils.harvestURL(this.url);
    }

    public void extractTextContent() {
        this.textContent = EntityUtils.extractTextContent(this.htmlContent);
    }


    public String getTitle() {
        return this.title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getUrl() {
        return this.url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getHtmlContent() {
        return this.htmlContent;
    }

    public void setHtmlContent(String htmlContent) {
        this.htmlContent = htmlContent;
    }

    public String getTextContent() {
        return this.textContent;
    }

    public void setTextContent(String textContent) {
        this.textContent = textContent;
    }

    @Override
    public String getId() {
        return this.url;
    }

    @Override
    public JSONObject toJSON() {
        JSONObject json = new JSONObject();
        json.put("title", this.title);
        json.put("url", this.url);
        json.put("htmlContent", this.htmlContent);
        json.put("textContent", this.textContent);

        return json;
    }
}
