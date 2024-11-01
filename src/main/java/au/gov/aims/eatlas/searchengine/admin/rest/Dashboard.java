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
import au.gov.aims.eatlas.searchengine.admin.SearchEngineState;
import au.gov.aims.eatlas.searchengine.client.ESClient;
import au.gov.aims.eatlas.searchengine.client.ElasticSearchStatus;
import au.gov.aims.eatlas.searchengine.client.SearchClient;
import au.gov.aims.eatlas.searchengine.client.SearchUtils;
import au.gov.aims.eatlas.searchengine.index.AbstractIndexer;
import au.gov.aims.eatlas.searchengine.logger.AbstractLogger;
import au.gov.aims.eatlas.searchengine.logger.Level;
import au.gov.aims.eatlas.searchengine.logger.SessionLogger;
import au.gov.aims.eatlas.searchengine.rest.ImageCache;
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

import java.io.File;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Path("/")
public class Dashboard {

    @GET
    @Produces(MediaType.TEXT_HTML)
    public Viewable dashboard(
        @Context HttpServletRequest httpRequest
        //@Context SecurityContext securityContext
    ) {
        HttpSession session = httpRequest.getSession(true);
        AbstractLogger logger = SessionLogger.getInstance(session);

        SearchEngineConfig config = SearchEngineConfig.getInstance();
        SearchEngineState state = SearchEngineState.getInstance();

        Map<String, Object> model = new HashMap<>();
        model.put("logger", logger);
        model.put("config", config);

        try {
            SearchClient searchClient = ESClient.getInstance();
            ElasticSearchStatus status = SearchUtils.getElasticSearchStatus(searchClient, httpRequest);
            model.put("status", status);

            // Refresh index count, when the search engine is reachable
            if (status.isReachable()) {
                try {
                    SearchUtils.refreshIndexesCount(searchClient);
                } catch (Exception ex) {
                    logger.addMessage(Level.ERROR,
                        "An exception occurred while refreshing the indexes count.", ex);
                }
            }
        } catch (Exception ex) {
            logger.addMessage(Level.ERROR,
                "An exception occurred while accessing the Elastic Search server", ex);
        }

        File configFile = config.getConfigFile();
        model.put("configFile", configFile);
        model.put("configFileLastModifiedDate", configFile == null ? null : new Date(configFile.lastModified()));

        File stateFile = state.getStateFile();
        model.put("stateFile", stateFile);
        model.put("stateFileLastModifiedDate", stateFile == null ? null : new Date(stateFile.lastModified()));

        String imageCacheDirStr = config.getImageCacheDirectory();
        File imageCacheDirectory = imageCacheDirStr == null ? null : new File(imageCacheDirStr);
        model.put("imageCacheDirectory", imageCacheDirectory);

        Map<String, File> cacheDirectories = new HashMap<>();
        for (AbstractIndexer<?> indexer : config.getIndexers()) {
            String index = indexer.getIndex();
            File cacheDirectory = ImageCache.getCacheDirectory(index, logger);
            cacheDirectories.put(index, cacheDirectory);
        }
        model.put("imageCacheDirectories", cacheDirectories);

        // Load the template: src/main/webapp/WEB-INF/jsp/dashboard.jsp
        return new Viewable("/dashboard", model);
    }

    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public Viewable submit(
        @Context HttpServletRequest httpRequest,
        MultivaluedMap<String, String> form
    ) {
        HttpSession session = httpRequest.getSession(true);
        AbstractLogger logger = SessionLogger.getInstance(session);

        if (form.containsKey("reload-config-button")) {
            this.reloadConfigFile(logger);
        }

        if (form.containsKey("reload-state-button")) {
            this.reloadStateFile(logger);
        }

        return this.dashboard(httpRequest);
    }

    private void reloadConfigFile(AbstractLogger logger) {
        SearchEngineConfig config = SearchEngineConfig.getInstance();
        try {
            config.reload(logger);
            logger.addMessage(Level.INFO,
                    String.format("Application configuration file reloaded: %s", config.getConfigFile()));
        } catch (Exception ex) {
            logger.addMessage(Level.ERROR,
                String.format("An exception occurred while reloading the configuration file: %s", config.getConfigFile()), ex);
        }
    }

    private void reloadStateFile(AbstractLogger logger) {
        SearchEngineState state = SearchEngineState.getInstance();
        try {
            state.reload();
            logger.addMessage(Level.INFO,
                    String.format("Application state file reloaded: %s", state.getStateFile()));
        } catch (Exception ex) {
            logger.addMessage(Level.ERROR,
                String.format("An exception occurred while reloading the application state file: %s", state.getStateFile()), ex);
        }
    }
}
