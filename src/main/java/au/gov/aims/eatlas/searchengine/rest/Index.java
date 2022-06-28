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

import au.gov.aims.eatlas.searchengine.admin.SearchEngineConfig;
import au.gov.aims.eatlas.searchengine.admin.rest.Messages;
import au.gov.aims.eatlas.searchengine.index.AbstractIndexer;
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
import java.io.IOException;
import java.util.List;

@Path("/index/v1")
public class Index {
    private static final Logger LOGGER = Logger.getLogger(Index.class.getName());

    // http://localhost:8080/eatlas-search-engine/public/index/v1/reindex?full=false&token=[REINDEX TOKEN]
    // REINDEX TOKEN: The token is set in the search engine configuration file. See setting page:
    //   http://localhost:8080/eatlas-search-engine/admin/settings
    // NOTE: To index Drupal nodes, the following core modules needs to be enabled:
    // - WEB SERVICES
    //   - [X] JSON:API
    //   - [X] Serialization
    // NOTE:
    //   The search engine will need to do a complete re-harvest once in a while to remove deleted nodes
    @GET
    @Path("reindex")
    @Produces(MediaType.APPLICATION_JSON)
    public Response reindex(
            @Context HttpServletRequest httpRequest,
            @QueryParam("full") Boolean full, // List of indexes to query
            @QueryParam("token") String token
    ) {
        SearchEngineConfig config = SearchEngineConfig.getInstance();
        String expectedToken = config.getReindexToken();

        CacheControl noCache = new CacheControl();
        noCache.setNoCache(true);

        if (expectedToken == null || expectedToken.isEmpty()) {
            JSONObject jsonStatus = new JSONObject()
                .put("status", "error")
                .put("message", "Reindex token not set in Search Engine configuration.");

            String responseTxt = jsonStatus.toString();

            return Response.serverError().entity(responseTxt).cacheControl(noCache).build();
        }

        if (token == null || token.isEmpty()) {
            JSONObject jsonStatus = new JSONObject()
                .put("status", "error")
                .put("message", "Parameter token required.");

            String responseTxt = jsonStatus.toString();

            return Response.status(Response.Status.BAD_REQUEST).entity(responseTxt).cacheControl(noCache).build();
        }

        if (!expectedToken.equals(token)) {
            JSONObject jsonStatus = new JSONObject()
                .put("status", "error")
                .put("message", "Invalid token provided.");

            String responseTxt = jsonStatus.toString();

            return Response.status(Response.Status.FORBIDDEN).entity(responseTxt).cacheControl(noCache).build();
        }

        // API call - display messages using the LOGGER.
        Messages messages = Messages.getInstance(null);

        try {
            JSONObject jsonStatus = Index.internalReindex(full == null ? true : full, messages);

            String responseTxt = jsonStatus.toString();

            // Return the JSON object with an OK status.
            return Response.ok(responseTxt).cacheControl(noCache).build();

        } catch(Exception ex) {
            JSONObject jsonStatus = new JSONObject()
                .put("status", "error")
                .put("message", ServletUtils.getExceptionMessage(ex))
                .put("stacktrace", ServletUtils.exceptionToJSON(ex));

            String responseTxt = jsonStatus.toString();

            // Return the JSON object with an ERROR status.
            return Response.serverError().entity(responseTxt).cacheControl(noCache).build();
        }
    }

    public static JSONObject internalReindex(boolean full, Messages messages) throws IOException {
        SearchEngineConfig config = SearchEngineConfig.getInstance();

        // Reindex
        if (config != null) {
            List<AbstractIndexer> indexers = config.getIndexers();
            if (indexers != null && !indexers.isEmpty()) {
                for (AbstractIndexer indexer : indexers) {
                    if (indexer.isEnabled()) {
                        Index.internalReindex(indexer, full, messages);
                    }
                }
            }
        }

        return new JSONObject()
            .put("status", "success");
    }

    public static void internalReindex(AbstractIndexer indexer, boolean full, Messages messages) throws IOException {
        LOGGER.debug(String.format("Reindexing %s class %s",
                indexer.getIndex(), indexer.getClass().getSimpleName()));

        indexer.index(full, messages);
    }

}
