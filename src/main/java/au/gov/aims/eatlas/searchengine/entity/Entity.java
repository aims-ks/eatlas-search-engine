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

import au.gov.aims.eatlas.searchengine.admin.SearchEngineConfig;
import au.gov.aims.eatlas.searchengine.admin.rest.Messages;
import au.gov.aims.eatlas.searchengine.index.WktUtils;
import au.gov.aims.eatlas.searchengine.rest.ImageCache;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.apache.commons.text.StringEscapeUtils;
import org.json.JSONObject;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.ParseException;

import java.io.File;
import java.net.URL;

/*
 * TODO Use Jackson to serialise / deserialise the Entities instead of defining a toJSON and fromJSON method.
 *    (that's what Jackson is for)
 */

@JsonDeserialize(using = EntityDeserializer.class)
public abstract class Entity {
    private static final long DAY_MS = 24 * 60 * 60 * 1000;

    private String index;

    // Last the entry was saved into the search index
    private Long lastIndexed;
    // Last time the thumbnail was downloaded.
    // That's also used for records which doesn't have a thumbnail,
    // to avoid attempting to download them everytime.
    private Long thumbnailLastIndexed;

    // Last modified, as provided by the entity provider
    // i.e. metadata last modified, provided by GeoNetwork
    private Long lastModified;

    private String id;
    private URL link;

    private String title;
    private String document;
    private String wkt;
    private Double wktArea;
    private Bbox wktBbox;

    // Search result thumbnail image
    // If there is a cachedThumbnailFilename, call /public/img/v1/{index}/{filename} to get the image.
    private String cachedThumbnailFilename;
    // Otherwise, if there is a thumbnailUrl, use it as is.
    private URL thumbnailUrl;

    // The 2 letters langcode representing the language of this entity.
    // Example: "en"
    private String langcode;

    public void setIndex(String index) {
        this.index = index;
    }

    public String getIndex() {
        return this.index;
    }

    public void setLastIndexed(Long lastIndexed) {
        this.lastIndexed = lastIndexed;
    }

    public Long getLastIndexed() {
        return this.lastIndexed;
    }

    public void setThumbnailLastIndexed(Long thumbnailLastIndexed) {
        this.thumbnailLastIndexed = thumbnailLastIndexed;
    }

    public Long getThumbnailLastIndexed() {
        return this.thumbnailLastIndexed;
    }

    public void setLastModified(Long lastModified) {
        this.lastModified = lastModified;
    }

    public Long getLastModified() {
        return this.lastModified;
    }

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

    public String getWkt() {
        return this.wkt;
    }

    public void setWkt(String wkt) {
        this.wkt = wkt;
    }

    public Double getWktArea() {
        return this.wktArea;
    }

    public void setWktArea(Double area) {
        this.wktArea = area;
    }

    public Bbox getWktBbox() {
        return this.wktBbox;
    }

    public void setWktBbox(Bbox wktBbox) {
        this.wktBbox = wktBbox;
    }

    public void setWktAndAttributes(String wkt, Double area, Bbox bbox) {
        this.wkt = wkt;
        this.wktArea = area;
        this.wktBbox = bbox;
    }

    public void setWktAndAttributes(String wkt) throws ParseException {
        Double area = null;
        Bbox bbox = null;

        if (wkt != null) {
            // Set area
            Geometry geometry = WktUtils.wktToGeometry(wkt);
            if (geometry != null) {
                area = geometry.getArea();
                bbox = new Bbox(geometry);
            }
        }

        this.setWktAndAttributes(wkt, area, bbox);
    }

    public String getCachedThumbnailFilename() {
        return this.cachedThumbnailFilename;
    }

    public void setCachedThumbnailFilename(String cachedThumbnailFilename) {
        this.cachedThumbnailFilename = cachedThumbnailFilename;
    }

    public String getCachedThumbnailUrl() {
        String searchEngineBaseUrl = SearchEngineConfig.getInstance().getSearchEngineBaseUrl();
        return this.cachedThumbnailFilename == null ? null :
                String.format("%s/public/img/v1/%s/%s",
                        searchEngineBaseUrl, this.index, this.cachedThumbnailFilename);
    }

    public URL getThumbnailUrl() {
        return this.thumbnailUrl;
    }

    public void setThumbnailUrl(URL thumbnailUrl) {
        this.thumbnailUrl = thumbnailUrl;
    }

    public String getLangcode() {
        return this.langcode;
    }

    public void setLangcode(String langcode) {
        this.langcode = langcode;
    }

    // Make sure thumbnailUrl is set before calling this.
    public boolean isThumbnailOutdated(Entity oldEntity, Long thumbnailTTL, Long brokenThumbnailTTL, Messages messages) {
        if (oldEntity == null) {
            // No old entity, assume it's a new entry, the thumbnail was never downloaded
            return true;
        }

        // Check if the thumbnail URL have changed
        // NOTE: equals on URL attempt to resolve the URL, which is overkill in this case.
        //     It's much faster and easier to check the URL string, even if it could give
        //     false negative (i.e. 2 different URLs pointing to the same page).
        //     Also, it's easier to check with empty string rather than trying to handle nulls.
        URL oldUrl = oldEntity.getThumbnailUrl();
        String oldUrlStr = oldUrl == null ? "" : oldUrl.toString();
        URL newUrl = this.getThumbnailUrl();
        String newUrlStr = newUrl == null ? "" : newUrl.toString();

        if (!oldUrlStr.equals(newUrlStr)) {
            return true;
        }

        Long thumbnailLastIndexed = oldEntity.getThumbnailLastIndexed();
        if (thumbnailLastIndexed == null) {
            return true;
        }

        long now = System.currentTimeMillis();
        long thumbnailIndexAgeMs = now - thumbnailLastIndexed;
        long thumbnailIndexAgeDays = thumbnailIndexAgeMs / DAY_MS;

        // Let's check if the file on disk exists
        String cachedThumbnailFilename = oldEntity.getCachedThumbnailFilename();
        if (cachedThumbnailFilename == null) {
            // Broken thumbnail

            // The last download attempt was more than 1 day ago (broken thumbnail TTL),
            // and there was no thumbnail to download (or download failed).
            return thumbnailIndexAgeDays >= brokenThumbnailTTL;

        } else {
            // Cached thumbnail
            // The thumbnail was downloaded more than 30 day ago (thumbnail TTL),
            if (thumbnailIndexAgeDays >= thumbnailTTL) {
                return true;
            }
        }

        File thumbnailFile = ImageCache.getCachedFile(index, cachedThumbnailFilename, messages);
        if (thumbnailFile == null) {
            // Unlikely, but if the thumbnail folder is manually deleted,
            // this is where it will be requested to re-download the thumbnail.
            return true;
        }

        if (!thumbnailFile.exists()) {
            // The thumbnail was manually deleted.
            return true;
        }

        long thumbnailLastModified = thumbnailFile.lastModified();
        long thumbnailAgeMs = now - thumbnailLastModified;
        long thumbnailAgeDays = thumbnailAgeMs / DAY_MS;

        return thumbnailAgeDays >= thumbnailTTL;
    }

    public void useCachedThumbnail(Entity oldEntity) {
        this.setThumbnailUrl(oldEntity == null ? null : oldEntity.getThumbnailUrl());
        this.setCachedThumbnailFilename(oldEntity == null ? null : oldEntity.getCachedThumbnailFilename());
        this.setThumbnailLastIndexed(oldEntity == null ? System.currentTimeMillis() : oldEntity.getThumbnailLastIndexed());
    }

    public void deleteThumbnail(Messages messages) {
        String index = this.getIndex();
        String cachedThumbnailFilename = this.getCachedThumbnailFilename();
        if (index != null && cachedThumbnailFilename != null) {
            File cachedFile = ImageCache.getCachedFile(index, cachedThumbnailFilename, messages);
            if (cachedFile != null && cachedFile.exists() && !cachedFile.delete()) {
                messages.addMessage(Messages.Level.ERROR,
                        String.format("Cached image can not be deleted: %s", cachedFile.toString()));
            }
        }
    }

    public JSONObject toJSON() {
        URL linkUrl = this.getLink();
        URL thumbnailUrl = this.getThumbnailUrl();

        return new JSONObject()
            .put("id", this.getId())
            .put("index", this.getIndex())
            .put("lastIndexed", this.getLastIndexed())
            .put("lastModified", this.getLastModified())
            .put("thumbnailLastIndexed", this.getThumbnailLastIndexed())
            .put("type", this.getClass().getSimpleName())
            .put("link", linkUrl == null ? null : linkUrl.toString())
            .put("title", this.getTitle())
            // Encode HTML from the document, to allow the search to all highlights as HTML tags
            .put("document", StringEscapeUtils.escapeHtml4(this.getDocument()))
            .put("wkt", this.getWkt())
            .put("wktArea", this.getWktArea())
            .put("wktBbox", this.wktBbox == null ? null : this.wktBbox.toJSON())
            .put("cachedThumbnailFilename", this.getCachedThumbnailFilename())
            .put("cachedThumbnailUrl", this.getCachedThumbnailUrl())
            .put("thumbnailUrl", thumbnailUrl == null ? null : thumbnailUrl.toString())
            .put("langcode", this.getLangcode());
    }

    protected void loadJSON(JSONObject json, Messages messages) {
        if (json != null) {
            this.setId(json.optString("id", null));
            this.setIndex(json.optString("index", null));
            this.setTitle(json.optString("title", null));
            // Decode HTML since we encoded it in the toJSON method
            this.setDocument(StringEscapeUtils.unescapeHtml4(json.optString("document", null)));
            this.setWkt(json.optString("wkt", null));
            if (json.has("wktArea")) {
                this.setWktArea(json.optDouble("wktArea"));
            }
            JSONObject jsonWktBbox = json.optJSONObject("wktBbox");
            if (jsonWktBbox != null) {
                this.wktBbox = new Bbox();
                this.wktBbox.loadJSON(jsonWktBbox, messages);
            }

            this.setLangcode(json.optString("langcode", null));
            this.setCachedThumbnailFilename(json.optString("cachedThumbnailFilename", null));

            String lastIndexedStr = json.optString("lastIndexed", null);
            if (lastIndexedStr != null) {
                this.setLastIndexed(Long.parseLong(lastIndexedStr));
            }

            String lastModifiedStr = json.optString("lastModified", null);
            if (lastModifiedStr != null) {
                this.setLastModified(Long.parseLong(lastModifiedStr));
            }

            String thumbnailLastIndexedStr = json.optString("thumbnailLastIndexed", null);
            if (thumbnailLastIndexedStr != null) {
                this.setThumbnailLastIndexed(Long.parseLong(thumbnailLastIndexedStr));
            }

            String linkStr = json.optString("link", null);
            if (linkStr != null && !linkStr.isEmpty()) {
                try {
                    this.setLink(new URL(linkStr));
                } catch(Exception ex) {
                    messages.addMessage(Messages.Level.ERROR,
                            String.format("Invalid index entity URL found: %s", linkStr), ex);
                }
            }

            String thumbnailUrlStr = json.optString("thumbnailUrl", null);
            if (thumbnailUrlStr != null && !thumbnailUrlStr.isEmpty()) {
                try {
                    this.setThumbnailUrl(new URL(thumbnailUrlStr));
                } catch(Exception ex) {
                    messages.addMessage(Messages.Level.ERROR,
                            String.format("Invalid index entity thumbnail URL found: %s", thumbnailUrlStr), ex);
                }
            }
        }
    }

    @Override
    public String toString() {
        JSONObject json = this.toJSON();
        return json == null ? null : json.toString(4);
    }
}
