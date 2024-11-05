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
import au.gov.aims.eatlas.searchengine.client.ESClient;
import au.gov.aims.eatlas.searchengine.client.SearchClient;
import au.gov.aims.eatlas.searchengine.client.SearchUtils;
import au.gov.aims.eatlas.searchengine.index.AbstractIndexer;
import au.gov.aims.eatlas.searchengine.index.IndexUtils;
import au.gov.aims.eatlas.searchengine.logger.AbstractLogger;
import au.gov.aims.eatlas.searchengine.logger.Level;
import au.gov.aims.eatlas.searchengine.logger.Message;
import au.gov.aims.eatlas.searchengine.logger.SessionLogger;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import org.glassfish.jersey.server.mvc.Viewable;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Path("/reindex")
public class ReindexPage {

    @GET
    @Produces(MediaType.TEXT_HTML)
    public Viewable reindexPage(
        @Context HttpServletRequest httpRequest
    ) {
        HttpSession session = httpRequest.getSession(true);
        AbstractLogger logger = SessionLogger.getInstance(session);
        SearchEngineConfig config = SearchEngineConfig.getInstance();

        Map<String, Object> model = new HashMap<>();
        model.put("title", "Re-indexation");
        model.put("reindexActive", "active");
        model.put("logger", logger);
        model.put("config", config);

        Object index = httpRequest.getAttribute("index");
        if (index != null) {
            model.put("index", index);
        }
        Object logs = httpRequest.getAttribute("logs");
        if (logs != null) {
            model.put("logs", logs);
        }

        // Log filters
        Object showLogInfo = httpRequest.getAttribute("showLogInfo");
        if (showLogInfo != null) {
            model.put("showLogInfo", showLogInfo);
        }
        Object showLogWarning = httpRequest.getAttribute("showLogWarning");
        if (showLogWarning != null) {
            model.put("showLogWarning", showLogWarning);
        }
        Object showLogError = httpRequest.getAttribute("showLogError");
        if (showLogError != null) {
            model.put("showLogError", showLogError);
        }

        // Load the template: src/main/webapp/WEB-INF/jsp/reindex.jsp
        return new Viewable("/reindex", model);
    }


    @GET
    @Path("/progress")
    @Produces({ MediaType.APPLICATION_JSON })
    public String reindexProgress(
        @Context HttpServletRequest httpRequest
    ) {
        SearchEngineConfig config = SearchEngineConfig.getInstance();

        JSONObject jsonIndexersProgress = new JSONObject();

        // If it's not running, show 100%
        int runningCount = 0;
        for (AbstractIndexer<?> indexer : config.getIndexers()) {
            if (indexer != null) {
                Double progress = 1.0;
                boolean running = false;
                if (indexer.isRunning()) {
                    runningCount++;

                    running = true;
                    progress = indexer.getProgress();
                    if (progress != null) {
                        // Progress, floored to 2 decimal places.
                        // We want 99.9999% to be 99%, to avoid getting 100% before the process is truly done.
                        progress = Math.floor(progress * 100) / 100;
                    }
                }

                String index = indexer.getIndex();
                JSONObject jsonIndexerProgress = new JSONObject()
                        .put("index", index)
                        .put("progress", progress)
                        .put("running", running);

                jsonIndexersProgress.put(index, jsonIndexerProgress);
            }
        }

        JSONObject jsonResponse = new JSONObject()
                .put("indexes", jsonIndexersProgress)
                .put("runningCount", runningCount);

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
        AbstractLogger sessionLogger = SessionLogger.getInstance(session);

        try {
            SearchClient searchClient = ESClient.getInstance();

            if (form.containsKey("reindex-all-button")) {
                this.reindexAll(searchClient, true, sessionLogger);
            } else if (form.containsKey("index-latest-all-button")) {
                this.reindexAll(searchClient, false, sessionLogger);
            } else if (form.containsKey("refresh-count-button")) {
                this.refreshCount(searchClient, sessionLogger);

            } else if (form.containsKey("recreate-index-button")) {
                this.recreateIndex(searchClient, FormUtils.getFormStringValue(form, "recreate-index-button"), sessionLogger);
            } else if (form.containsKey("reindex-button")) {
                this.reindex(searchClient, FormUtils.getFormStringValue(form, "reindex-button"), true, sessionLogger);
            } else if (form.containsKey("index-latest-button")) {
                this.reindex(searchClient, FormUtils.getFormStringValue(form, "index-latest-button"), false, sessionLogger);
            } else if (form.containsKey("view-log-button") && !form.containsKey("close-window-button")) {
                // Get filter state
                boolean showInfo = !form.containsKey("show-log-info") || "true".equals(FormUtils.getFormStringValue(form, "show-log-info"));
                boolean showWarning = !form.containsKey("show-log-warning") || "true".equals(FormUtils.getFormStringValue(form, "show-log-warning"));
                boolean showError = !form.containsKey("show-log-error") || "true".equals(FormUtils.getFormStringValue(form, "show-log-error"));
                // Change filter state if a button has been clicked
                if (form.containsKey("show-log-info-button")) {
                    showInfo = "filter-on".equals(FormUtils.getFormStringValue(form, "show-log-info-button"));
                }
                if (form.containsKey("show-log-warning-button")) {
                    showWarning = "filter-on".equals(FormUtils.getFormStringValue(form, "show-log-warning-button"));
                }
                if (form.containsKey("show-log-error-button")) {
                    showError = "filter-on".equals(FormUtils.getFormStringValue(form, "show-log-error-button"));
                }
                this.viewLogs(httpRequest, FormUtils.getFormStringValue(form, "view-log-button"), showInfo, showWarning, showError, sessionLogger);
            }
        } catch (Exception ex) {
            sessionLogger.addMessage(Level.ERROR,
                "An exception occurred while accessing the Elastic Search server", ex);
        }

        return this.reindexPage(httpRequest);
    }

    private void reindexAll(SearchClient searchClient, boolean fullHarvest, AbstractLogger sessionLogger) {
        try {
            IndexUtils.internalReindex(searchClient, fullHarvest);
        } catch (Exception ex) {
            sessionLogger.addMessage(Level.ERROR,
                "An exception occurred during the indexation.", ex);
        }
    }

    private void refreshCount(SearchClient searchClient, AbstractLogger logger) {
        try {
            SearchUtils.refreshIndexesCount(searchClient);
        } catch (Exception ex) {
            logger.addMessage(Level.ERROR,
                "An exception occurred while refreshing the indexes count.", ex);
        }
    }

    private void recreateIndex(SearchClient searchClient, String index, AbstractLogger sessionLogger) {
        try {
            searchClient.deleteIndex(index);
        } catch (Exception ex) {
            sessionLogger.addMessage(Level.ERROR,
                String.format("An exception occurred while deleting the index: %s", index), ex);
            return;
        }

        this.reindex(searchClient, index, true, sessionLogger);
    }

    private void reindex(SearchClient searchClient, String index, boolean fullHarvest, AbstractLogger sessionLogger) {
        if (index == null || index.isEmpty()) {
            sessionLogger.addMessage(Level.ERROR,
                "No index provided for indexation.");

        } else {
            SearchEngineConfig config = SearchEngineConfig.getInstance();
            AbstractIndexer<?> indexer = config.getIndexer(index);

            try {
                AbstractLogger fileLogger = indexer.getFileLogger();
                fileLogger.clear();

                indexer.index(searchClient, fullHarvest, fileLogger);
            } catch (Exception ex) {
                sessionLogger.addMessage(Level.ERROR,
                    String.format("An exception occurred during the indexation of index: %s", index), ex);
            }
        }
    }

    private void viewLogs(HttpServletRequest httpRequest, String index, boolean showInfo, boolean showWarning, boolean showError, AbstractLogger sessionLogger) {
        SearchEngineConfig config = SearchEngineConfig.getInstance();
        AbstractIndexer<?> indexer = config.getIndexer(index);

        List<Message> logs = indexer.getFileLogger().getFilteredMessages(showInfo, showWarning, showError);

        httpRequest.setAttribute("index", index);
        httpRequest.setAttribute("logs", logs);
        httpRequest.setAttribute("showLogInfo", showInfo);
        httpRequest.setAttribute("showLogWarning", showWarning);
        httpRequest.setAttribute("showLogError", showError);
    }
}
