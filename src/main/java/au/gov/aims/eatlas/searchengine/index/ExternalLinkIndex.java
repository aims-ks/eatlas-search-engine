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
import org.apache.log4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ExternalLinkIndex extends AbstractIndex<ExternalLink> {
    private static final Logger LOGGER = Logger.getLogger(ExternalLinkIndex.class.getName());

    private String title;
    private String url;
    private String thumbnail;

    public ExternalLinkIndex(String index) {
        super(index);
    }

    /**
     * index: eatlas_extlink
     * url: http://www.csiro.au/connie2/
     */
    public ExternalLinkIndex(String index, String url, String thumbnail, String title) {
        super(index);
        this.title = title;
        this.setUrl(url);
        this.setThumbnail(thumbnail);
    }

    /**
     * NOTE: Harvest for external links is a bit special
     *   since there is always only one entity to harvest.
     */
    @Override
    public List<ExternalLink> harvest(int limit, int offset) throws IOException {
        List<ExternalLink> entityList = new ArrayList<>();

        ExternalLink entity = new ExternalLink(this.url, this.thumbnail, this.title, EntityUtils.harvestURLText(this.url));
        entityList.add(entity);

        return entityList;
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

    public String getThumbnail() {
        return this.thumbnail;
    }

    public void setThumbnail(String thumbnail) {
        this.thumbnail = thumbnail;
    }
}
