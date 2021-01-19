/*
 *  Copyright (C) 2021 Australian Institute of Marine Science
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
package au.gov.aims.eatlas.searchengine.rest;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.json.JSONObject;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.CacheControl;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;

@Path("/v1")
public class Index {
    private static final Logger LOGGER = Logger.getLogger(Index.class.getName());

    // TODO Require login!
    @GET
    @Path("status")
    @Produces(MediaType.APPLICATION_JSON)
    public Response status(
            @Context HttpServletRequest servletRequest,
            @QueryParam("idx") List<String> idx // List of indexes to query
    ) {

        for (int i=0; i<idx.size(); i++) {
            LOGGER.log(Level.WARN, "idx["+i+"]: " + idx.get(i));
        }

        // TODO Implement
        long remaining = 5;
        long total = 1024;

        JSONObject jsonStatus = new JSONObject()
            .put("remaining", remaining)
            .put("total", total);

        String responseTxt = jsonStatus.toString();
        LOGGER.log(Level.DEBUG, responseTxt);

        // Disable cache DURING DEVELOPMENT!
        CacheControl noCache = new CacheControl();
        noCache.setNoCache(true);

        // Return the JSON array with a OK status.
        return Response.ok(responseTxt).cacheControl(noCache).build();
    }


    // NOTE: To index Drupal nodes, the following core modules needs to be enabled:
    // - WEB SERVICES
    //   - [X] JSON:API
    //   - [X] Serialization
    // URL: http://localhost:9090/jsonapi/node/article
    //   http://localhost:9090/jsonapi/node/article?sort=-changed
    //   http://localhost:9090/jsonapi/node/article?sort=-changed&page[limit]=10&page[offset]=10
    // NOTE:
    //   The search engine will need to do a complete re-harvest once in a while to remove deleted nodes
}
