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
package au.gov.aims.eatlas.searchengine.admin.rest;

import au.gov.aims.eatlas.searchengine.admin.SearchEngineConfig;
import au.gov.aims.eatlas.searchengine.index.AbstractIndexer;
import au.gov.aims.eatlas.searchengine.index.AtlasMapperIndexer;
import au.gov.aims.eatlas.searchengine.index.DrupalExternalLinkNodeIndexer;
import au.gov.aims.eatlas.searchengine.index.DrupalMediaIndexer;
import au.gov.aims.eatlas.searchengine.index.DrupalNodeIndexer;
import au.gov.aims.eatlas.searchengine.index.GeoNetworkIndexer;
import org.glassfish.jersey.server.mvc.Viewable;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Path("/settings")
public class SettingsPage {

    @GET
    @Produces(MediaType.TEXT_HTML)
    public Viewable settingsPage() {
        SearchEngineConfig config = SearchEngineConfig.getInstance();

        Map<String, Object> model = new HashMap<>();
        model.put("messages", Messages.getInstance());
        model.put("config", config);

        // Load the template: src/main/webapp/WEB-INF/jsp/settings.jsp
        return new Viewable("/settings", model);
    }

    /**
     * Edit the settings.
     * NOTE: This method should expect a PUT request, but HTML Standards for form submission only support GET and POST:
     *   https://html.spec.whatwg.org/multipage/form-control-infrastructure.html#attributes-for-form-submission
     * @param form
     * @return
     */
    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public Viewable saveSettings(
        MultivaluedMap<String, String> form
    ) {
        // TODO DELETE
        System.out.println("Form size: " + form.size());
        for(String key : form.keySet()) {
            System.out.println("Form key: " + key);
        }

        String imageCacheDirectory = this.getFormStringValue(form, "imageCacheDirectory");
        System.out.println("imageCacheDirectory: " + imageCacheDirectory);

        String globalThumbnailTTL = this.getFormStringValue(form, "globalThumbnailTTL");
        System.out.println("globalThumbnailTTL: " + globalThumbnailTTL);
        // TODO END DELETE


        // NOTE: Heavily restrict characters for index name [a-z0-9\-_]

        SearchEngineConfig config = SearchEngineConfig.getInstance();

        config.setImageCacheDirectory(this.getFormStringValue(form, "imageCacheDirectory"));
        config.setGlobalThumbnailTTL(this.getFormLongValue(form, "globalThumbnailTTL"));
        config.setGlobalBrokenThumbnailTTL(this.getFormLongValue(form, "globalBrokenThumbnailTTL"));

        for (AbstractIndexer indexer : config.getIndexers()) {
            String index = indexer.getIndex();

            String newIndex = this.getFormStringValue(form, index + "_index");
            if (newIndex != null && !newIndex.equals(index)) {
                if (newIndex.matches("[a-zA-Z0-9_-]+")) {
                    indexer.setIndex(newIndex);
                } else {
                    Messages.getInstance().addMessages(Messages.Level.WARNING,
                        String.format("Invalid index supplied: %s. Rolled back to previous index: %s", newIndex, index));
                }
            }

            indexer.setEnabled(this.getFormBooleanValue(form, index + "_enabled"));
            indexer.setThumbnailTTL(this.getFormLongValue(form, index + "_thumbnailTTL"));
            indexer.setBrokenThumbnailTTL(this.getFormLongValue(form, index + "_brokenThumbnailTTL"));

            if (indexer instanceof DrupalNodeIndexer) {
                // DrupalNodeIndexer
                DrupalNodeIndexer drupalNodeIndexer = (DrupalNodeIndexer)indexer;

                drupalNodeIndexer.setDrupalUrl(this.getFormStringValue(form, index + "_drupalUrl"));
                drupalNodeIndexer.setDrupalVersion(this.getFormStringValue(form, index + "_drupalVersion"));
                drupalNodeIndexer.setDrupalNodeType(this.getFormStringValue(form, index + "_drupalNodeType"));
                drupalNodeIndexer.setDrupalPreviewImageField(this.getFormStringValue(form, index + "_drupalPreviewImageField"));

            } else if (indexer instanceof DrupalMediaIndexer) {
                // DrupalMediaIndexer
                DrupalMediaIndexer drupalMediaIndexer = (DrupalMediaIndexer)indexer;

                drupalMediaIndexer.setDrupalUrl(this.getFormStringValue(form, index + "_drupalUrl"));
                drupalMediaIndexer.setDrupalVersion(this.getFormStringValue(form, index + "_drupalVersion"));
                drupalMediaIndexer.setDrupalMediaType(this.getFormStringValue(form, index + "_drupalMediaType"));
                drupalMediaIndexer.setDrupalPreviewImageField(this.getFormStringValue(form, index + "_drupalPreviewImageField"));
                drupalMediaIndexer.setDrupalTitleField(this.getFormStringValue(form, index + "_drupalTitleField"));
                drupalMediaIndexer.setDrupalDescriptionField(this.getFormStringValue(form, index + "_drupalDescriptionField"));

            } else if (indexer instanceof DrupalExternalLinkNodeIndexer) {
                // DrupalExternalLinkNodeIndexer
                DrupalExternalLinkNodeIndexer drupalExternalLinkNodeIndexer = (DrupalExternalLinkNodeIndexer)indexer;

                drupalExternalLinkNodeIndexer.setDrupalUrl(this.getFormStringValue(form, index + "_drupalUrl"));
                drupalExternalLinkNodeIndexer.setDrupalVersion(this.getFormStringValue(form, index + "_drupalVersion"));
                drupalExternalLinkNodeIndexer.setDrupalNodeType(this.getFormStringValue(form, index + "_drupalNodeType"));
                drupalExternalLinkNodeIndexer.setDrupalPreviewImageField(this.getFormStringValue(form, index + "_drupalPreviewImageField"));
                drupalExternalLinkNodeIndexer.setDrupalExternalUrlField(this.getFormStringValue(form, index + "_drupalExternalUrlField"));
                drupalExternalLinkNodeIndexer.setDrupalContentOverwriteField(this.getFormStringValue(form, index + "_drupalContentOverwriteField"));

            } else if (indexer instanceof GeoNetworkIndexer) {
                // GeoNetworkIndexer
                GeoNetworkIndexer geoNetworkIndexer = (GeoNetworkIndexer)indexer;

                geoNetworkIndexer.setGeoNetworkUrl(this.getFormStringValue(form, index + "_geoNetworkUrl"));
                geoNetworkIndexer.setGeoNetworkVersion(this.getFormStringValue(form, index + "_geoNetworkVersion"));

            } else if (indexer instanceof AtlasMapperIndexer) {
                // AtlasMapperIndexer
                AtlasMapperIndexer atlasMapperIndexer = (AtlasMapperIndexer)indexer;

                atlasMapperIndexer.setAtlasMapperClientUrl(this.getFormStringValue(form, index + "_atlasMapperClientUrl"));
                atlasMapperIndexer.setAtlasMapperVersion(this.getFormStringValue(form, index + "_atlasMapperVersion"));

            } else {
                Messages.getInstance().addMessages(Messages.Level.WARNING,
                    String.format("Unsupported settings sent to the server. Unknown indexer type: %s", indexer.getClass().getSimpleName()));
            }
        }

        try {
            config.save();
        } catch (IOException ex) {
            Messages.getInstance().addMessages(Messages.Level.ERROR,
                "An exception occurred while saving the search engine settings.", ex);
        }

        return settingsPage();
    }

    private String getFormStringValue(MultivaluedMap<String, String> form, String key) {
        String value = form.getFirst(key);
        if (value == null) {
            return null;
        }
        value = value.trim();
        return value.isEmpty() ? null : value;
    }

    private Long getFormLongValue(MultivaluedMap<String, String> form, String key) {
        String value = this.getFormStringValue(form, key);
        return value == null ? null : Long.parseLong(value);
    }

    private boolean getFormBooleanValue(MultivaluedMap<String, String> form, String key) {
        String value = this.getFormStringValue(form, key);
        return value != null;
    }
}
