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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import java.util.HashMap;
import java.util.Map;

@Path("/reindex")
public class ReindexPage {

    @GET
    @Produces(MediaType.TEXT_HTML)
    public Viewable reindexPage(
        @Context HttpServletRequest httpRequest
    ) {
        HttpSession session = httpRequest.getSession(true);
        Messages messages = Messages.getInstance(session);

        SearchEngineConfig config = SearchEngineConfig.getInstance();

        Map<String, Object> model = new HashMap<>();
        model.put("messages", messages);
        model.put("config", config);

        // Load the template: src/main/webapp/WEB-INF/jsp/reindex.jsp
        return new Viewable("/reindex", model);
    }


    @GET
    @Path("/progress")
    @Produces({ MediaType.APPLICATION_JSON })
    public String reindexProgress(
        @Context HttpServletRequest httpRequest,
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

    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public Viewable reindex(
        @Context HttpServletRequest httpRequest,
        MultivaluedMap<String, String> form
    ) {
        HttpSession session = httpRequest.getSession(true);
        Messages messages = Messages.getInstance(session);

        if (form.containsKey("reindex-all-button")) {
            this.reindexAll(true, messages);
        } else if (form.containsKey("index-latest-all-button")) {
            this.reindexAll(false, messages);
        } else if (form.containsKey("refresh-count-button")) {
            this.refreshCount(messages);

        } else if (form.containsKey("reindex-button")) {
            this.reindex(FormUtils.getFormStringValue(form, "reindex-button"), true, messages);
        } else if (form.containsKey("index-latest-button")) {
            this.reindex(FormUtils.getFormStringValue(form, "index-latest-button"), false, messages);
        }

        return this.reindexPage(httpRequest);
    }

    private void reindexAll(boolean fullHarvest, Messages messages) {
        try {
            Index.internalReindex(fullHarvest, messages);
        } catch (Exception ex) {
            messages.addMessage(Messages.Level.ERROR,
                "An exception occurred during the indexation.", ex);
        }
    }

    private void refreshCount(Messages messages) {
        try {
            SearchUtils.refreshIndexesCount();
        } catch (Exception ex) {
            messages.addMessage(Messages.Level.ERROR,
                "An exception occurred while refreshing the indexes count.", ex);
        }
    }


    private void reindex(String index, boolean fullHarvest, Messages messages) {
        if (index == null || index.isEmpty()) {
            messages.addMessage(Messages.Level.ERROR,
                "No index provided for indexation.");

        } else {
            SearchEngineConfig config = SearchEngineConfig.getInstance();
            AbstractIndexer indexer = config.getIndexer(index);

            try {
                Index.internalReindex(indexer, fullHarvest, messages);
            } catch (Exception ex) {
                messages.addMessage(Messages.Level.ERROR,
                    String.format("An exception occurred during the indexation of index: %s", index), ex);
            }
        }
    }
}
