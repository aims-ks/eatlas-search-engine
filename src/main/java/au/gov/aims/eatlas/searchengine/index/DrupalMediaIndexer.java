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
package au.gov.aims.eatlas.searchengine.index;

import au.gov.aims.eatlas.searchengine.admin.rest.Messages;
import au.gov.aims.eatlas.searchengine.client.SearchClient;
import au.gov.aims.eatlas.searchengine.entity.DrupalMedia;
import au.gov.aims.eatlas.searchengine.entity.EntityUtils;
import org.json.JSONObject;

public class DrupalMediaIndexer extends AbstractDrupalEntityIndexer<DrupalMedia> {
    private static final String DEFAULT_PREVIEW_IMAGE_FIELD = "thumbnail";

    private String drupalTitleField;
    private String drupalDescriptionField;

    public static DrupalMediaIndexer fromJSON(String index, JSONObject json) {
        if (json == null || json.isEmpty()) {
            return null;
        }

        return new DrupalMediaIndexer(
            index,
            json.optString("drupalUrl", null),
            json.optString("drupalVersion", null),
            json.optString("drupalMediaType", null),
            json.optString("drupalPreviewImageField", null),
            json.optString("drupalTitleField", null),
            json.optString("drupalDescriptionField", null),
            json.optString("drupalWktField", null));
    }

    public JSONObject toJSON() {
        return this.getJsonBase()
            .put("drupalMediaType", this.getDrupalBundleId())
            .put("drupalTitleField", this.drupalTitleField)
            .put("drupalDescriptionField", this.drupalDescriptionField);
    }

    @Override
    public DrupalMedia load(JSONObject json, Messages messages) {
        return DrupalMedia.load(json, messages);
    }

    /**
     * index: eatlas-image
     * drupalUrl: http://localhost:9090
     * drupalVersion: 9.0
     * drupalMediaType: image
     */
    public DrupalMediaIndexer(
            String index,
            String drupalUrl,
            String drupalVersion,
            String drupalMediaType,
            String drupalPreviewImageField,
            String drupalTitleField,
            String drupalDescriptionField,
            String drupalWktField
    ) {

        super(index, drupalUrl, drupalVersion, "media", drupalMediaType, "file",
                (drupalPreviewImageField == null || drupalPreviewImageField.isEmpty()) ? DEFAULT_PREVIEW_IMAGE_FIELD : drupalPreviewImageField,
                drupalWktField);
        this.drupalTitleField = drupalTitleField;
        this.drupalDescriptionField = drupalDescriptionField;
    }

    @Override
    public boolean supportsIndexLatest() {
        return true;
    }

    @Override
    public DrupalMedia createDrupalEntity(JSONObject jsonApiMedia, Messages messages) {
        return new DrupalMedia(this.getIndex(), jsonApiMedia, messages);
    }

    @Override
    public DrupalMedia getIndexedDrupalEntity(SearchClient client, String id, Messages messages) {
        return this.safeGet(client, DrupalMedia.class, id, messages);
    }

    private static String getDrupalTitle(JSONObject jsonApiMedia, String drupalTitleField) {
        if (drupalTitleField == null || drupalTitleField.isEmpty()) {
            return null;
        }
        JSONObject jsonAttributes = jsonApiMedia == null ? null : jsonApiMedia.optJSONObject("attributes");
        return jsonAttributes == null ? null : jsonAttributes.optString(drupalTitleField, null);
    }

    private static String getDrupalDescription(JSONObject jsonApiMedia, String drupalDescriptionField) {
        if (drupalDescriptionField == null || drupalDescriptionField.isEmpty()) {
            return null;
        }
        JSONObject jsonAttributes = jsonApiMedia == null ? null : jsonApiMedia.optJSONObject("attributes");
        JSONObject jsonDesc = jsonAttributes == null ? null : jsonAttributes.optJSONObject(drupalDescriptionField);
        return jsonDesc == null ? null :
            EntityUtils.extractHTMLTextContent(jsonDesc.optString("processed", null));
    }

    public String getDrupalTitleField() {
        return this.drupalTitleField;
    }

    public void setDrupalTitleField(String drupalTitleField) {
        this.drupalTitleField = drupalTitleField;
    }

    public String getDrupalDescriptionField() {
        return this.drupalDescriptionField;
    }

    public void setDrupalDescriptionField(String drupalDescriptionField) {
        this.drupalDescriptionField = drupalDescriptionField;
    }
}
