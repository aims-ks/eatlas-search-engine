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

import au.gov.aims.eatlas.searchengine.rest.ImageCache;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.net.URL;

public class DrupalNode extends Entity {
    private static final Logger LOGGER = Logger.getLogger(DrupalNode.class.getName());

    private Integer nid;

    private DrupalNode() {}

    // Load from Drupal JSON:API output
    public DrupalNode(String index, JSONObject jsonApiNode, JSONArray included, String previewImageField) {
        this.setIndex(index);

        if (jsonApiNode != null) {
            URL baseUrl = DrupalNode.getDrupalBaseUrl(jsonApiNode);

            // UUID
            this.setId(jsonApiNode.optString("id", null));

            // Node ID
            JSONObject jsonAttributes = jsonApiNode.optJSONObject("attributes");
            String nidStr = jsonAttributes == null ? null : jsonAttributes.optString("drupal_internal__nid", null);
            this.nid = nidStr == null ? null : Integer.parseInt(nidStr);

            // Title
            this.setTitle(jsonAttributes == null ? null : jsonAttributes.optString("title", null));

            // Node URL
            String nodeRelativePath = DrupalNode.getNodeRelativeUrl(jsonApiNode);
            if (baseUrl != null && nodeRelativePath != null) {
                try {
                    this.setLink(new URL(baseUrl, nodeRelativePath));
                } catch(Exception ex) {
                    LOGGER.error(String.format("Can not craft node URL from Drupal base URL: %s", baseUrl), ex);
                }
            }

            // Lang code
            this.setLangcode(jsonAttributes == null ? null : jsonAttributes.optString("langcode", null));

            // Body
            JSONObject jsonBody = jsonAttributes == null ? null : jsonAttributes.optJSONObject("body");
            this.setDocument(jsonBody == null ? null :
                EntityUtils.extractHTMLTextContent(jsonBody.optString("processed", null)));

            // Thumbnail (aka preview image)
            if (baseUrl != null && previewImageField != null) {
                String previewImageUUID = DrupalNode.getPreviewImageUUID(jsonApiNode, previewImageField);
                if (previewImageUUID != null) {
                    String previewImageRelativePath = DrupalNode.findPreviewImageRelativePath(previewImageUUID, included);
                    if (previewImageRelativePath != null) {
                        try {
                            URL thumbnailUrl = new URL(baseUrl, previewImageRelativePath);
                            this.setThumbnailUrl(thumbnailUrl);
                            File cachedThumbnailFile = ImageCache.cache(thumbnailUrl, this.getIndex(), this.getId());
                            if (cachedThumbnailFile != null) {
                                this.setCachedThumbnailFilename(cachedThumbnailFile.getName());
                            }
                        } catch(Exception ex) {
                            LOGGER.error(String.format("Can not craft node URL from Drupal base URL: %s", baseUrl), ex);
                        }
                    }
                }
            }
        }
    }

    private static URL getDrupalBaseUrl(JSONObject jsonApiNode) {
        JSONObject jsonLinks = jsonApiNode == null ? null : jsonApiNode.optJSONObject("links");
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

    private static String getNodeRelativeUrl(JSONObject jsonApiNode) {
        // First, look if there is a path alias
        JSONObject jsonAttributes = jsonApiNode == null ? null : jsonApiNode.optJSONObject("attributes");
        JSONObject jsonAttributesPath = jsonAttributes == null ? null : jsonAttributes.optJSONObject("path");
        String pathAlias = jsonAttributesPath == null ? null : jsonAttributesPath.optString("alias", null);
        if (pathAlias != null) {
            return pathAlias;
        }

        // Otherwise, return "/node/[NODE ID]"
        String nid = jsonAttributes == null ? null : jsonAttributes.optString("drupal_internal__nid", null);
        if (nid != null) {
            return "/node/" + nid;
        }

        return null;
    }

    private static String getPreviewImageUUID(JSONObject jsonApiNode, String previewImageField) {
        JSONObject jsonRelationships = jsonApiNode == null ? null : jsonApiNode.optJSONObject("relationships");
        JSONObject jsonRelFieldImage = jsonRelationships == null ? null : jsonRelationships.optJSONObject(previewImageField);
        JSONObject jsonRelFieldImageData = jsonRelFieldImage == null ? null : jsonRelFieldImage.optJSONObject("data");
        return jsonRelFieldImageData == null ? null : jsonRelFieldImageData.optString("id", null);
    }

    private static String findPreviewImageRelativePath(String imageUUID, JSONArray included) {
        if (imageUUID == null || included == null) {
            return null;
        }

        for (int i=0; i < included.length(); i++) {
            JSONObject jsonInclude = included.optJSONObject(i);
            String includeId = jsonInclude == null ? null : jsonInclude.optString("id", null);
            if (imageUUID.equals(includeId)) {
                JSONObject jsonIncludeAttributes = jsonInclude == null ? null : jsonInclude.optJSONObject("attributes");
                JSONObject jsonIncludeAttributesUri = jsonIncludeAttributes == null ? null : jsonIncludeAttributes.optJSONObject("uri");
                return jsonIncludeAttributesUri == null ? null : jsonIncludeAttributesUri.optString("url", null);
            }
        }

        return null;
    }

    public Integer getNid() {
        return this.nid;
    }

    public static DrupalNode load(JSONObject json) {
        DrupalNode node = new DrupalNode();
        node.loadJSON(json);
        String nidStr = json.optString("nid", null);
        if (nidStr != null && !nidStr.isEmpty()) {
            node.nid = Integer.parseInt(nidStr);
        }

        return node;
    }

    @Override
    public JSONObject toJSON() {
        return super.toJSON()
            .put("nid", this.nid);
    }
}
