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

import org.apache.log4j.Logger;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.json.JSONObject;

import java.net.URL;

public class DrupalMedia extends Entity {
    private static final Logger LOGGER = Logger.getLogger(DrupalMedia.class.getName());

    private Integer mid;

    private DrupalMedia() {}

    // Load from Drupal JSON:API output
    public DrupalMedia(String index, JSONObject jsonApiMedia) {
        this.setIndex(index);

        if (jsonApiMedia != null) {
            URL baseUrl = DrupalMedia.getDrupalBaseUrl(jsonApiMedia);

            // UUID
            this.setId(jsonApiMedia.optString("id", null));

            // Media ID
            JSONObject jsonAttributes = jsonApiMedia.optJSONObject("attributes");
            String midStr = jsonAttributes == null ? null : jsonAttributes.optString("drupal_internal__mid", null);
            this.mid = midStr == null ? null : Integer.parseInt(midStr);

            // Last modified
            String changedDateStr = jsonAttributes == null ? null : jsonAttributes.optString("changed", null);
            if (changedDateStr != null && !changedDateStr.isEmpty()) {
                DateTimeFormatter dateParser = ISODateTimeFormat.dateTimeNoMillis();
                DateTime changedDate = dateParser.parseDateTime(changedDateStr);
                if (changedDate != null) {
                    this.setLastModified(changedDate.getMillis());
                }
            }

            // Media URL
            String mediaRelativePath = DrupalMedia.getMediaRelativeUrl(jsonApiMedia);
            if (baseUrl != null && mediaRelativePath != null) {
                try {
                    this.setLink(new URL(baseUrl, mediaRelativePath));
                } catch(Exception ex) {
                    LOGGER.error(String.format("Can not craft media URL from Drupal base URL: %s", baseUrl), ex);
                }
            }

            // Lang code
            this.setLangcode(jsonAttributes == null ? null : jsonAttributes.optString("langcode", null));
        }
    }

    public static URL getDrupalBaseUrl(JSONObject jsonApiMedia) {
        JSONObject jsonLinks = jsonApiMedia == null ? null : jsonApiMedia.optJSONObject("links");
        JSONObject jsonLinksSelf = jsonLinks == null ? null : jsonLinks.optJSONObject("self");
        String linksSelfHref = jsonLinksSelf == null ? null : jsonLinksSelf.optString("href", null);

        URL linksSelfUrl = null;
        if (linksSelfHref != null) {
            try {
                linksSelfUrl = new URL(linksSelfHref);
            } catch(Exception ex) {
                LOGGER.error(String.format("Invalid URL found in links.self.href: %s", linksSelfHref), ex);
            }
        }

        if (linksSelfUrl != null) {
            try {
                return new URL(linksSelfUrl.getProtocol(), linksSelfUrl.getHost(), linksSelfUrl.getPort(), "/");
            } catch(Exception ex) {
                LOGGER.error(String.format("Can not get root URL from links.self.href: %s", linksSelfUrl), ex);
            }
        }

        return null;
    }

    private static String getMediaRelativeUrl(JSONObject jsonApiMedia) {
        // First, look if there is a path alias
        JSONObject jsonAttributes = jsonApiMedia == null ? null : jsonApiMedia.optJSONObject("attributes");
        JSONObject jsonAttributesPath = jsonAttributes == null ? null : jsonAttributes.optJSONObject("path");
        String pathAlias = jsonAttributesPath == null ? null : jsonAttributesPath.optString("alias", null);
        if (pathAlias != null) {
            return pathAlias;
        }

        // Otherwise, return "/media/[MEDIA ID]"
        String mid = jsonAttributes == null ? null : jsonAttributes.optString("drupal_internal__mid", null);
        if (mid != null) {
            return "/media/" + mid;
        }

        return null;
    }

    public Integer getMid() {
        return this.mid;
    }

    public static DrupalMedia load(JSONObject json) {
        DrupalMedia media = new DrupalMedia();
        media.loadJSON(json);
        String midStr = json.optString("mid", null);
        if (midStr != null && !midStr.isEmpty()) {
            media.mid = Integer.parseInt(midStr);
        }

        return media;
    }

    @Override
    public JSONObject toJSON() {
        return super.toJSON()
            .put("mid", this.mid);
    }
}
