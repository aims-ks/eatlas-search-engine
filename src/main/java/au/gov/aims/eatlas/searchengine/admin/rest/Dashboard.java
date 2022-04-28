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
import au.gov.aims.eatlas.searchengine.client.SearchUtils;
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
        Messages messages = Messages.getInstance(session);

        SearchEngineConfig config = SearchEngineConfig.getInstance();
        SearchEngineState state = SearchEngineState.getInstance();

        Map<String, Object> model = new HashMap<>();
        model.put("messages", messages);
        model.put("config", config);

        model.put("status", SearchUtils.getElasticSearchStatus());

        File configFile = config.getConfigFile();
        model.put("configFile", configFile);
        model.put("configFileLastModifiedDate", configFile == null ? null : new Date(configFile.lastModified()));

        File stateFile = state.getStateFile();
        model.put("stateFile", stateFile);
        model.put("stateFileLastModifiedDate", stateFile == null ? null : new Date(stateFile.lastModified()));

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
        Messages messages = Messages.getInstance(session);

        if (form.containsKey("reload-config-button")) {
            this.reloadConfigFile(messages);
        }

        if (form.containsKey("reload-state-button")) {
            this.reloadStateFile(messages);
        }

        return this.dashboard(httpRequest);
    }

    private void reloadConfigFile(Messages messages) {
        SearchEngineConfig config = SearchEngineConfig.getInstance();
        try {
            config.reload();
            messages.addMessage(Messages.Level.INFO,
                    String.format("Application configuration file reloaded: %s", config.getConfigFile()));
        } catch (Exception ex) {
            messages.addMessage(Messages.Level.ERROR,
                String.format("An exception occurred while reloading the configuration file: %s", config.getConfigFile()), ex);
        }
    }

    private void reloadStateFile(Messages messages) {
        SearchEngineState state = SearchEngineState.getInstance();
        try {
            state.reload();
            messages.addMessage(Messages.Level.INFO,
                    String.format("Application state file reloaded: %s", state.getStateFile()));
        } catch (Exception ex) {
            messages.addMessage(Messages.Level.ERROR,
                String.format("An exception occurred while reloading the application state file: %s", state.getStateFile()), ex);
        }
    }
}
