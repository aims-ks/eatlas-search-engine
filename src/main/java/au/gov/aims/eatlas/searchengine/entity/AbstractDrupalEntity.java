/*
 *  Copyright (C) 2022 Australian Institute of Marine Science
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

import au.gov.aims.eatlas.searchengine.index.AbstractDrupalEntityIndexer;
import au.gov.aims.eatlas.searchengine.logger.AbstractLogger;
import au.gov.aims.eatlas.searchengine.logger.Level;
import org.json.JSONObject;

import java.net.URL;

public abstract class AbstractDrupalEntity extends Entity {
    protected AbstractDrupalEntity() {}

    public AbstractDrupalEntity(JSONObject jsonApiEntity, AbstractLogger logger) {}

    public static URL getDrupalBaseUrl(JSONObject jsonApiEntity, AbstractLogger logger) {
        JSONObject jsonLinks = jsonApiEntity == null ? null : jsonApiEntity.optJSONObject("links");
        JSONObject jsonLinksSelf = jsonLinks == null ? null : jsonLinks.optJSONObject("self");
        String linksSelfHref = jsonLinksSelf == null ? null : jsonLinksSelf.optString("href", null);

        URL linksSelfUrl = null;
        if (linksSelfHref != null) {
            try {
                linksSelfUrl = new URL(linksSelfHref);
            } catch(Exception ex) {
                logger.addMessage(Level.ERROR,
                        String.format("Invalid URL found in links.self.href: %s", linksSelfHref), ex);
            }
        }

        if (linksSelfUrl != null) {
            try {
                return new URL(linksSelfUrl.getProtocol(), linksSelfUrl.getHost(), linksSelfUrl.getPort(), "/");
            } catch(Exception ex) {
                logger.addMessage(Level.ERROR,
                        String.format("Can not get root URL from links.self.href: %s", linksSelfUrl), ex);
            }
        }

        return null;
    }

    public static URL getDrupalPublicBaseUrl(AbstractDrupalEntityIndexer<?> indexer, JSONObject jsonApiEntity, AbstractLogger logger) {
        String publicUrl = indexer.getDrupalPublicUrl();
        if (publicUrl != null && !publicUrl.isBlank()) {
            try {
                return new URL(publicUrl);
            } catch(Exception ex) {
                logger.addMessage(Level.ERROR,
                        String.format("Invalid Drupal public URL found in: %s", indexer.getIndex()), ex);
            }
        }
        return AbstractDrupalEntity.getDrupalBaseUrl(jsonApiEntity, logger);
    }
}
