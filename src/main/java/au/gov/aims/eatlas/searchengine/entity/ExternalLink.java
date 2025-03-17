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

import au.gov.aims.eatlas.searchengine.index.DrupalExternalLinkNodeIndexer;
import au.gov.aims.eatlas.searchengine.logger.AbstractLogger;
import org.json.JSONObject;

/**
 * This class is the same as DrupalNode, but it shows as "ExternalLink" in the JSON search results.
 */
public class ExternalLink extends DrupalNode {
    private ExternalLink() {
        super();
    }

    public ExternalLink(DrupalExternalLinkNodeIndexer indexer, JSONObject jsonApiNode, AbstractLogger logger) {
        super(indexer, jsonApiNode, logger);
    }

    public static ExternalLink load(JSONObject json, AbstractLogger logger) {
        ExternalLink externalLink = new ExternalLink();
        externalLink.loadJSON(json, logger);
        String nidStr = json.optString("nid", null);
        if (nidStr != null && !nidStr.isEmpty()) {
            externalLink.setNid(Integer.parseInt(nidStr));
        }

        return externalLink;
    }
}
