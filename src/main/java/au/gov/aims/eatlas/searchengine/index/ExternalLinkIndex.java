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

import au.gov.aims.eatlas.searchengine.entity.EntityUtils;
import au.gov.aims.eatlas.searchengine.entity.ExternalLink;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ExternalLinkIndex extends AbstractIndex<ExternalLink> {
    private String title;
    private String url;

    public ExternalLinkIndex(String index) {
        super(index);
    }

    /**
     * index: eatlas_extlink
     * url: http://www.csiro.au/connie2/
     */
    public ExternalLinkIndex(String index, String title, String url) {
        super(index);
        this.title = title;
        this.url = url;
    }

    @Override
    public ExternalLink load(JSONObject json) {
        return new ExternalLink(json);
    }

    /**
     * NOTE: Harvest for external links is a bit special
     *   since there is always only one entity to harvest.
     */
    @Override
    public List<ExternalLink> harvest(int limit, int offset) throws IOException {
        List<ExternalLink> entityList = new ArrayList<>();

        ExternalLink entity = this.internalHarvest(this.harvestHtmlContent());
        entityList.add(entity);

        return entityList;
    }

    public ExternalLink internalHarvest(String htmlContent) throws IOException {
        ExternalLink entity = new ExternalLink(this.title, this.url);
        entity.setHtmlContent(htmlContent);
        entity.setTextContent(this.extractTextContent(htmlContent));
        return entity;
    }

    public String harvestHtmlContent() throws IOException {
        return EntityUtils.harvestURL(this.url);
    }

    public String extractTextContent(String htmlContent) {
        return EntityUtils.extractTextContent(htmlContent);
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
}