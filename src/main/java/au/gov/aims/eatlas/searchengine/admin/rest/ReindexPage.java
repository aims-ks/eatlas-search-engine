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
import au.gov.aims.eatlas.searchengine.rest.Index;
import org.glassfish.jersey.server.mvc.Viewable;
import org.json.JSONObject;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

@Path("/reindex")
public class ReindexPage {

    @GET
    @Produces(MediaType.TEXT_HTML)
    public Viewable reindexPage() {
        SearchEngineConfig config = SearchEngineConfig.getInstance();

        Map<String, Object> model = new HashMap<>();
        model.put("messages", Messages.getInstance());
        model.put("config", config);

        // Load the template: src/main/webapp/WEB-INF/jsp/reindex.jsp
        return new Viewable("/reindex", model);
    }


    @GET
    @Path("/progress")
    @Produces({ MediaType.APPLICATION_JSON })
    public String reindexProgress(
        @QueryParam("index") String index
    ) {
        SearchEngineConfig config = SearchEngineConfig.getInstance();

        // If it's not running, show 100%
        Double progress = 1.0;
        boolean running = false;
        AbstractIndexer indexer = config.getIndexer(index);
        if (indexer != null && indexer.isRunning()) {
            running = true;
            progress = indexer.getProgress();
            if (progress != null) {
                // Progress, floored to 2 decimal places.
                // We want 99.9999% to be 99%, to avoid getting 100% before the process is truly done.
                progress = Math.floor(progress * 100) / 100;
            }
        }

        JSONObject jsonResponse = new JSONObject()
                .put("index", index)
                .put("progress", progress)
                .put("running", running);

        return jsonResponse.toString();
    }

    static Map<String, Float> progressMap;
    private float getFakeProgress(String index) {
        if (progressMap == null) {
            progressMap = new HashMap<>();
        }

        Random random = new Random();
        Float progress = progressMap.get(index);
        if (progress == null || progress >= 1) {
            progress = 0f;
        } else {
            progress += (random.nextFloat() / 5);
        }
        if (progress > 1) {
            progress = 1f;
        }
        progressMap.put(index, progress);

        return progress;
    }

    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public Viewable reindex(
        MultivaluedMap<String, String> form
    ) {
        if (form.containsKey("reindex-all-button")) {
            this.reindexAll(true);
        } else if (form.containsKey("index-latest-all-button")) {
            this.reindexAll(false);
        } else if (form.containsKey("refresh-count-button")) {
            this.refreshCount();

        } else if (form.containsKey("reindex-button")) {
            this.reindex(FormUtils.getFormStringValue(form, "reindex-button"), true);
        } else if (form.containsKey("index-latest-button")) {
            this.reindex(FormUtils.getFormStringValue(form, "index-latest-button"), false);
        }

        return this.reindexPage();
    }

    private void reindexAll(boolean fullHarvest) {
        try {
            Index.internalReindex(fullHarvest);
        } catch (Exception ex) {
            Messages.getInstance().addMessages(Messages.Level.ERROR,
                "An exception occurred during the indexation.", ex);
        }
    }

    private void refreshCount() {
        try {
            SearchUtils.refreshIndexesCount();
        } catch (Exception ex) {
            Messages.getInstance().addMessages(Messages.Level.ERROR,
                "An exception occurred while refreshing the indexes count.", ex);
        }
    }


    private void reindex(String index, boolean fullHarvest) {
        if (index == null || index.isEmpty()) {
            Messages.getInstance().addMessages(Messages.Level.ERROR,
                "No index provided for indexation.");

        } else {
            SearchEngineConfig config = SearchEngineConfig.getInstance();
            AbstractIndexer indexer = config.getIndexer(index);

            try {
                Index.internalReindex(indexer, fullHarvest);
            } catch (Exception ex) {
                Messages.getInstance().addMessages(Messages.Level.ERROR,
                    String.format("An exception occurred during the indexation of index: %s", index), ex);
            }
        }
    }
}
