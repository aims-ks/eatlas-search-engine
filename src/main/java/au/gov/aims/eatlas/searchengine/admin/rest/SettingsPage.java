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
import au.gov.aims.eatlas.searchengine.client.SearchUtils;
import au.gov.aims.eatlas.searchengine.index.AbstractIndexer;
import au.gov.aims.eatlas.searchengine.index.AtlasMapperIndexer;
import au.gov.aims.eatlas.searchengine.index.DrupalExternalLinkNodeIndexer;
import au.gov.aims.eatlas.searchengine.index.DrupalMediaIndexer;
import au.gov.aims.eatlas.searchengine.index.DrupalNodeIndexer;
import au.gov.aims.eatlas.searchengine.index.GeoNetworkIndexer;
import org.glassfish.jersey.server.mvc.Viewable;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Path("/settings")
public class SettingsPage {

    @GET
    @Produces(MediaType.TEXT_HTML)
    public Viewable settingsPage(
        @Context HttpServletRequest httpRequest
    ) {
        HttpSession session = httpRequest.getSession(true);
        Messages messages = Messages.getInstance(session);

        SearchEngineConfig config = SearchEngineConfig.getInstance();

        Map<String, Object> model = new HashMap<>();
        model.put("messages", messages);
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
        @Context HttpServletRequest httpRequest,
        MultivaluedMap<String, String> form
    ) {
        HttpSession session = httpRequest.getSession(true);
        Messages messages = Messages.getInstance(session);

        if (form.containsKey("save-button")) {
            this.save(form, messages);
        } else if (form.containsKey("commit-button")) {
            this.commit(form, messages);
        } else if (form.containsKey("add-index-button")) {
            this.addIndex(form, messages);
        } else if (form.containsKey("delete-button")) {
            this.deleteIndex(FormUtils.getFormStringValue(form, "delete-button"), messages);
        } else {
            // Default value when the user press enter in a text field, or when the form is submitted by JavaScript.
            // Most modern browser used the next submit button in the DOM tree (which is why we have a hidden save button),
            // but some browser submits the form without "clicking" a submit button.
            this.save(form, messages);
        }

        return settingsPage(httpRequest);
    }

    private void addIndex(MultivaluedMap<String, String> form, Messages messages) {
        String newIndexType = FormUtils.getFormStringValue(form, "newIndexType");
        // Call delete orphan indexes
        try {
            SearchUtils.deleteOrphanIndexes();
        } catch (IOException ex) {
            messages.addMessage(Messages.Level.ERROR,
                "An exception occurred while deleting orphan search indexes.", ex);
        }

        try {
            SearchUtils.addIndex(newIndexType);
        } catch (Exception ex) {
            messages.addMessage(Messages.Level.ERROR,
                String.format("An exception occurred while adding the new index: %s", newIndexType), ex);
        }
    }

    private void deleteIndex(String deleteIndex, Messages messages) {
        if (deleteIndex != null) {
            SearchEngineConfig config = SearchEngineConfig.getInstance();

            AbstractIndexer deletedIndexer = null;
            try {
                deletedIndexer = config.removeIndexer(deleteIndex);
            } catch (Exception ex) {
                messages.addMessage(Messages.Level.ERROR,
                    String.format("An exception occurred while deleting the search index: %s", deleteIndex), ex);
            }

            if (deletedIndexer != null) {
                try {
                    config.save();
                } catch (Exception ex) {
                    messages.addMessage(Messages.Level.ERROR,
                        "An exception occurred while saving the search engine settings.", ex);
                }

                // Call delete orphan indexes
                try {
                    SearchUtils.deleteOrphanIndexes();
                } catch (IOException ex) {
                    messages.addMessage(Messages.Level.ERROR,
                        "An exception occurred deleting orphan search indexes.", ex);
                }

            } else {
                messages.addMessage(Messages.Level.ERROR,
                        String.format("Can not delete the index %s. The index does not exist.", deleteIndex));
            }
        }
    }

    private void save(MultivaluedMap<String, String> form, Messages messages) {
        SearchEngineConfig config = SearchEngineConfig.getInstance();

        config.setImageCacheDirectory(FormUtils.getFormStringValue(form, "imageCacheDirectory"));
        config.setGlobalThumbnailTTL(FormUtils.getFormLongValue(form, "globalThumbnailTTL"));
        config.setGlobalBrokenThumbnailTTL(FormUtils.getFormLongValue(form, "globalBrokenThumbnailTTL"));
        config.setElasticSearchUrls(FormUtils.getFormStringValues(form, "elasticSearchUrl"));
        config.setReindexToken(FormUtils.getFormStringValue(form, "reindexToken"));
        config.setSearchEngineBaseUrl(FormUtils.getFormStringValue(form, "searchEngineBaseUrl"));

        boolean valid = true;
        for (AbstractIndexer indexer : config.getIndexers()) {
            String index = indexer.getIndex();

            String newIndex = FormUtils.getFormStringValue(form, index + "_index");
            if (newIndex != null && !newIndex.equals(index)) {
                if (!newIndex.matches("[a-zA-Z0-9_-]+")) {
                    messages.addMessage(Messages.Level.WARNING,
                        String.format("Invalid index supplied: %s. Invalid characters in the index. Rolled back to previous index: %s", newIndex, index));
                } else if (SearchUtils.indexExists(newIndex)) {
                    messages.addMessage(Messages.Level.WARNING,
                        String.format("Invalid index supplied: %s. Index already exists. Rolled back to previous index: %s", newIndex, index));
                } else {
                    indexer.setIndex(newIndex);
                }
            }

            indexer.setEnabled(FormUtils.getFormBooleanValue(form, index + "_enabled"));
            indexer.setThumbnailTTL(FormUtils.getFormLongValue(form, index + "_thumbnailTTL"));
            indexer.setBrokenThumbnailTTL(FormUtils.getFormLongValue(form, index + "_brokenThumbnailTTL"));

            if (indexer instanceof DrupalNodeIndexer) {
                // DrupalNodeIndexer
                DrupalNodeIndexer drupalNodeIndexer = (DrupalNodeIndexer)indexer;

                drupalNodeIndexer.setDrupalUrl(FormUtils.getFormStringValue(form, index + "_drupalUrl"));
                drupalNodeIndexer.setDrupalVersion(FormUtils.getFormStringValue(form, index + "_drupalVersion"));
                drupalNodeIndexer.setDrupalBundleId(FormUtils.getFormStringValue(form, index + "_drupalNodeType"));
                drupalNodeIndexer.setDrupalPreviewImageField(FormUtils.getFormStringValue(form, index + "_drupalPreviewImageField"));
                drupalNodeIndexer.setDrupalIndexedFields(FormUtils.getFormStringValue(form, index + "_drupalIndexedFields"));
                drupalNodeIndexer.setDrupalGeoJSONField(FormUtils.getFormStringValue(form, index + "_drupalGeoJSONField"));

            } else if (indexer instanceof DrupalMediaIndexer) {
                // DrupalMediaIndexer
                DrupalMediaIndexer drupalMediaIndexer = (DrupalMediaIndexer)indexer;

                drupalMediaIndexer.setDrupalUrl(FormUtils.getFormStringValue(form, index + "_drupalUrl"));
                drupalMediaIndexer.setDrupalVersion(FormUtils.getFormStringValue(form, index + "_drupalVersion"));
                drupalMediaIndexer.setDrupalBundleId(FormUtils.getFormStringValue(form, index + "_drupalMediaType"));
                drupalMediaIndexer.setDrupalPreviewImageField(FormUtils.getFormStringValue(form, index + "_drupalPreviewImageField"));
                drupalMediaIndexer.setDrupalTitleField(FormUtils.getFormStringValue(form, index + "_drupalTitleField"));
                drupalMediaIndexer.setDrupalIndexedFields(FormUtils.getFormStringValue(form, index + "_drupalIndexedFields"));
                drupalMediaIndexer.setDrupalGeoJSONField(FormUtils.getFormStringValue(form, index + "_drupalGeoJSONField"));

            } else if (indexer instanceof DrupalExternalLinkNodeIndexer) {
                // DrupalExternalLinkNodeIndexer
                DrupalExternalLinkNodeIndexer drupalExternalLinkNodeIndexer = (DrupalExternalLinkNodeIndexer)indexer;

                drupalExternalLinkNodeIndexer.setDrupalUrl(FormUtils.getFormStringValue(form, index + "_drupalUrl"));
                drupalExternalLinkNodeIndexer.setDrupalVersion(FormUtils.getFormStringValue(form, index + "_drupalVersion"));
                drupalExternalLinkNodeIndexer.setDrupalBundleId(FormUtils.getFormStringValue(form, index + "_drupalNodeType"));
                drupalExternalLinkNodeIndexer.setDrupalPreviewImageField(FormUtils.getFormStringValue(form, index + "_drupalPreviewImageField"));
                drupalExternalLinkNodeIndexer.setDrupalExternalUrlField(FormUtils.getFormStringValue(form, index + "_drupalExternalUrlField"));
                drupalExternalLinkNodeIndexer.setDrupalContentOverwriteField(FormUtils.getFormStringValue(form, index + "_drupalContentOverwriteField"));
                drupalExternalLinkNodeIndexer.setDrupalGeoJSONField(FormUtils.getFormStringValue(form, index + "_drupalGeoJSONField"));

            } else if (indexer instanceof GeoNetworkIndexer) {
                // GeoNetworkIndexer
                GeoNetworkIndexer geoNetworkIndexer = (GeoNetworkIndexer)indexer;

                geoNetworkIndexer.setGeoNetworkUrl(FormUtils.getFormStringValue(form, index + "_geoNetworkUrl"));
                geoNetworkIndexer.setGeoNetworkVersion(FormUtils.getFormStringValue(form, index + "_geoNetworkVersion"));

            } else if (indexer instanceof AtlasMapperIndexer) {
                // AtlasMapperIndexer
                AtlasMapperIndexer atlasMapperIndexer = (AtlasMapperIndexer)indexer;

                atlasMapperIndexer.setAtlasMapperClientUrl(FormUtils.getFormStringValue(form, index + "_atlasMapperClientUrl"));
                atlasMapperIndexer.setAtlasMapperVersion(FormUtils.getFormStringValue(form, index + "_atlasMapperVersion"));
                atlasMapperIndexer.setBaseLayerUrl(FormUtils.getFormStringValue(form, index + "_baseLayerUrl"));

            } else {
                messages.addMessage(Messages.Level.WARNING,
                    String.format("Unsupported settings sent to the server. Unknown indexer type: %s", indexer.getClass().getSimpleName()));
            }

            valid = valid && indexer.validate();
        }

        if (!valid) {
            // This message is rather cryptic, but it should only happen when the user
            // modify the form using the browser's developer tool,
            // or if the browser doesn't support HTML 5.
            messages.addMessage(Messages.Level.ERROR,
                "Form validation failed.");
        } else {
            try {
                config.save();
            } catch (Exception ex) {
                messages.addMessage(Messages.Level.ERROR,
                    "An exception occurred while saving the search engine settings.", ex);
            }
        }
    }

    private void commit(MultivaluedMap<String, String> form, Messages messages) {
        // TODO Implement

        messages.addMessage(Messages.Level.ERROR,
            "Not implemented.");
    }
}
