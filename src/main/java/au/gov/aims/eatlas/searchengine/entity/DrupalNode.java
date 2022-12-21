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

import au.gov.aims.eatlas.searchengine.admin.rest.Messages;
import au.gov.aims.eatlas.searchengine.index.AbstractDrupalEntityIndexer;
import org.json.JSONObject;

import java.net.URL;
import java.util.List;

public class DrupalNode extends AbstractDrupalEntity {
    private Integer nid;

    protected DrupalNode() {
        super();
    }

    // Load from Drupal JSON:API output
    public DrupalNode(String index, JSONObject jsonApiNode, Messages messages) {
        super(jsonApiNode, messages);
        this.setIndex(index);

        if (jsonApiNode != null) {
            URL baseUrl = DrupalNode.getDrupalBaseUrl(jsonApiNode, messages);

            // UUID
            this.setId(jsonApiNode.optString("id", null));

            // Node ID
            JSONObject jsonAttributes = jsonApiNode.optJSONObject("attributes");
            String nidStr = jsonAttributes == null ? null : jsonAttributes.optString("drupal_internal__nid", null);
            this.nid = nidStr == null ? null : Integer.parseInt(nidStr);

            // Title
            this.setTitle(jsonAttributes == null ? null : jsonAttributes.optString("title", null));

            // Last modified
            this.setLastModified(AbstractDrupalEntityIndexer.parseLastModified(jsonApiNode));

            // Node URL
            String nodeRelativePath = DrupalNode.getNodeRelativeUrl(jsonApiNode);
            if (baseUrl != null && nodeRelativePath != null) {
                try {
                    this.setLink(new URL(baseUrl, nodeRelativePath));
                } catch(Exception ex) {
                    messages.addMessage(Messages.Level.ERROR,
                            String.format("Can not craft node URL from Drupal base URL: %s", baseUrl), ex);
                }
            }

            // Lang code
            this.setLangcode(jsonAttributes == null ? null : jsonAttributes.optString("langcode", null));
        }
    }

    public static URL getDrupalBaseUrl(JSONObject jsonApiNode, Messages messages) {
        JSONObject jsonLinks = jsonApiNode == null ? null : jsonApiNode.optJSONObject("links");
        JSONObject jsonLinksSelf = jsonLinks == null ? null : jsonLinks.optJSONObject("self");
        String linksSelfHref = jsonLinksSelf == null ? null : jsonLinksSelf.optString("href", null);

        URL linksSelfUrl = null;
        if (linksSelfHref != null) {
            try {
                linksSelfUrl = new URL(linksSelfHref);
            } catch(Exception ex) {
                messages.addMessage(Messages.Level.ERROR,
                        String.format("Invalid URL found in links.self.href: %s", linksSelfHref), ex);
            }
        }

        if (linksSelfUrl != null) {
            try {
                return new URL(linksSelfUrl.getProtocol(), linksSelfUrl.getHost(), linksSelfUrl.getPort(), "/");
            } catch(Exception ex) {
                messages.addMessage(Messages.Level.ERROR,
                        String.format("Can not get root URL from links.self.href: %s", linksSelfUrl), ex);
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

    public Integer getNid() {
        return this.nid;
    }

    protected void setNid(Integer nid) {
        this.nid = nid;
    }

    public static DrupalNode load(JSONObject json, Messages messages) {
        DrupalNode node = new DrupalNode();
        node.loadJSON(json, messages);
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
