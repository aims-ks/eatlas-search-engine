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

public class ExternalLink extends Entity {
    private static final Logger LOGGER = Logger.getLogger(ExternalLink.class.getName());

    private ExternalLink() {}

    public ExternalLink(String index, String urlStr, String title) {
        this.setId(urlStr);
        this.setIndex(index);
        this.setTitle(title);

        if (urlStr != null) {
            try {
                this.setLink(new URL(urlStr));
            } catch(Exception ex) {
                LOGGER.error(String.format("Invalid external URL found: %s", urlStr), ex);
            }
        }
    }

    public static ExternalLink load(JSONObject json) {
        ExternalLink externalLink = new ExternalLink();
        externalLink.loadJSON(json);

        return externalLink;
    }
}
