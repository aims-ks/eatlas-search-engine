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
import org.glassfish.jersey.server.mvc.Viewable;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.HashMap;
import java.util.Map;

@Path("/")
public class Dashboard {

    @GET
    @Produces(MediaType.TEXT_HTML)
    public Viewable dashboard() {
        SearchEngineConfig config = SearchEngineConfig.getInstance();

// TODO DELETE
Messages.getInstance().addMessages(Messages.Level.INFO, "Test info level");
Messages.getInstance().addMessages(Messages.Level.WARNING, "Warning message");
Messages.getInstance().addMessages(Messages.Level.ERROR, "Something went wrong!");

        Map<String, Object> model = new HashMap<>();
        model.put("hello", "Hello");
        model.put("world", "World");
        model.put("config", config);
        model.put("messages", Messages.getInstance());
        return new Viewable("/dashboard", model);
    }
}