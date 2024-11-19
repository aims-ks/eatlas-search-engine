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
import au.gov.aims.eatlas.searchengine.admin.SearchEnginePrivateConfig;
import au.gov.aims.eatlas.searchengine.client.ESClient;
import au.gov.aims.eatlas.searchengine.client.SearchClient;
import au.gov.aims.eatlas.searchengine.index.AbstractIndexer;
import au.gov.aims.eatlas.searchengine.index.IndexUtils;
import au.gov.aims.eatlas.searchengine.logger.AbstractLogger;
import au.gov.aims.eatlas.searchengine.logger.MemoryLogger;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import org.json.JSONObject;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Path("/index/v1")
public class Index {
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    // http://localhost:8080/eatlas-search-engine/public/index-all/v1/reindex?full=false&token=[REINDEX TOKEN]
    // REINDEX TOKEN: The token is set in the search engine configuration file. See setting page:
    //   http://localhost:8080/eatlas-search-engine/admin/settings
    // NOTE: To index Drupal nodes, the following core modules needs to be enabled:
    // - WEB SERVICES
    //   - [X] JSON:API
    //   - [X] Serialization
    // NOTE:
    //   The search engine will need to do a complete re-harvest once in a while to remove deleted nodes
    //   This API endpoint expect a GET URL to make it easier to call from the cron.
    @GET
    @Path("reindex-all")
    @Produces(MediaType.APPLICATION_JSON)
    public Response reindexAll(
            @Context HttpServletRequest httpRequest,
            @QueryParam("full") Boolean full,
            @QueryParam("token") String token
    ) {
        Response checkTokenResponse = this.getCheckTokenResponse(token);
        if (checkTokenResponse != null) {
            return checkTokenResponse;
        }

        try {
            SearchClient searchClient = ESClient.getInstance();
            JSONObject jsonStatus = IndexUtils.internalReindex(searchClient, full == null ? true : full);

            String responseTxt = jsonStatus.toString();

            // Return the JSON object with an OK status.
            return Response.ok(responseTxt).cacheControl(ServletUtils.getNoCacheControl()).build();

        } catch(Exception ex) {
            return this.getExceptionResponse(ex);
        }
    }

    /**
     * Re-index for a singe document.
     * For "Re-index" button on search results.
     * @param httpRequest The request
     * @param formValues The values sent with the POST request. Expects:
     * - index The ID of the index, as specified in the search engine configuration file.
     * - id The document ID.
     * - token The token set in the search engine configuration file.
     * @return JSON response
     */
    @POST
    @Path("reindex")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    public Response reindex(
            @Context HttpServletRequest httpRequest,
            MultivaluedMap<String, String> formValues
    ) {
        String index = formValues.getFirst("index");
        String id = formValues.getFirst("id");
        String token = formValues.getFirst("token");

        Response checkTokenResponse = this.getCheckTokenResponse(token);
        if (checkTokenResponse != null) {
            return checkTokenResponse;
        }

        if (index == null || index.isBlank()) {
            JSONObject jsonStatus = new JSONObject()
                .put("status", "error")
                .put("message", "Invalid request. Missing parameter \"index\"");

            String responseTxt = jsonStatus.toString();

            return Response.status(Response.Status.BAD_REQUEST).entity(responseTxt).cacheControl(ServletUtils.getNoCacheControl()).build();
        }
        if (id == null || id.isBlank()) {
            JSONObject jsonStatus = new JSONObject()
                .put("status", "error")
                .put("message", "Invalid request. Missing parameter \"id\"");

            String responseTxt = jsonStatus.toString();

            return Response.status(Response.Status.BAD_REQUEST).entity(responseTxt).cacheControl(ServletUtils.getNoCacheControl()).build();
        }

        try {
            SearchClient searchClient = ESClient.getInstance();
            SearchEngineConfig config = SearchEngineConfig.getInstance();
            AbstractIndexer<?> indexer = config.getIndexer(index);
            if (indexer == null) {
                JSONObject jsonStatus = new JSONObject()
                    .put("status", "error")
                    .put("message", String.format("Invalid index: %s", index));

                String responseTxt = jsonStatus.toString();

                return Response.status(Response.Status.BAD_REQUEST).entity(responseTxt).cacheControl(ServletUtils.getNoCacheControl()).build();
            }

            AbstractLogger logger = new MemoryLogger();
            indexer.reindex(searchClient, id, logger);
            // Wait for the index to update the document.
            searchClient.refresh(index);

            JSONObject jsonStatus = logger.toJSON()
                .put("status", "success");

            String responseTxt = jsonStatus.toString();

            // Return the JSON object with an OK status.
            return Response.ok(responseTxt).cacheControl(ServletUtils.getNoCacheControl()).build();
        } catch (Exception ex) {
            return this.getExceptionResponse(ex);
        }
    }

    /**
     * Re-index for a singe document, after a delay.
     * This is used for event listeners in systems that can send a
     * re-indexation request during the update of an entity,
     * but are not able to send the request after the
     * update is completed.
     * Example: Drupal
     * @param httpRequest The request
     * @param formValues The values sent with the POST request. Expects:
     * - index The ID of the index, as specified in the search engine configuration file.
     * - id The document ID.
     * - token The token set in the search engine configuration file.
     * - delay Number of seconds to wait before re-indexing the document.
     * @return JSON response
     */
    @POST
    @Path("reindex-after-delay")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    public Response reindexAfterDelay(
            @Context HttpServletRequest httpRequest,
            MultivaluedMap<String, String> formValues
    ) {
        String index = formValues.getFirst("index");
        String id = formValues.getFirst("id");
        String token = formValues.getFirst("token");
        String delayStr = formValues.getFirst("delay");

        Response checkTokenResponse = this.getCheckTokenResponse(token);
        if (checkTokenResponse != null) {
            return checkTokenResponse;
        }

        if (index == null || index.isBlank()) {
            JSONObject jsonStatus = new JSONObject()
                .put("status", "error")
                .put("message", "Invalid request. Missing parameter \"index\"");

            String responseTxt = jsonStatus.toString();

            return Response.status(Response.Status.BAD_REQUEST).entity(responseTxt).cacheControl(ServletUtils.getNoCacheControl()).build();
        }
        if (id == null || id.isBlank()) {
            JSONObject jsonStatus = new JSONObject()
                .put("status", "error")
                .put("message", "Invalid request. Missing parameter \"id\"");

            String responseTxt = jsonStatus.toString();

            return Response.status(Response.Status.BAD_REQUEST).entity(responseTxt).cacheControl(ServletUtils.getNoCacheControl()).build();
        }
        if (delayStr == null || delayStr.isBlank()) {
            JSONObject jsonStatus = new JSONObject()
                .put("status", "error")
                .put("message", "Invalid request. Missing parameter \"delay\"");

            String responseTxt = jsonStatus.toString();

            return Response.status(Response.Status.BAD_REQUEST).entity(responseTxt).cacheControl(ServletUtils.getNoCacheControl()).build();
        }
        int delay = 0;
        try {
            delay = Integer.parseInt(delayStr);
        } catch (Exception ex) {
            JSONObject jsonStatus = new JSONObject()
                .put("status", "error")
                .put("message", "Invalid request. Parameter \"delay\" is not an integer");

            String responseTxt = jsonStatus.toString();

            return Response.status(Response.Status.BAD_REQUEST).entity(responseTxt).cacheControl(ServletUtils.getNoCacheControl()).build();
        }

        try {
            SearchClient searchClient = ESClient.getInstance();
            SearchEngineConfig config = SearchEngineConfig.getInstance();
            AbstractIndexer<?> indexer = config.getIndexer(index);
            if (indexer == null) {
                JSONObject jsonStatus = new JSONObject()
                    .put("status", "error")
                    .put("message", String.format("Invalid index: %s", index));

                String responseTxt = jsonStatus.toString();

                return Response.status(Response.Status.BAD_REQUEST).entity(responseTxt).cacheControl(ServletUtils.getNoCacheControl()).build();
            }

            this.scheduler.schedule(() -> {
                try {
                    AbstractLogger logger = new MemoryLogger();
                    indexer.reindex(searchClient, id, logger);
                    // Wait for the index to update the document.
                    searchClient.refresh(index);
                } catch (Exception ex) {
                    // The re-indexation is run async, there is no way to give feedback to the user.
                }
            }, delay, TimeUnit.SECONDS);

            JSONObject jsonStatus = new JSONObject()
                .put("status", "success");

            String responseTxt = jsonStatus.toString();

            // Return the JSON object with an OK status.
            return Response.ok(responseTxt).cacheControl(ServletUtils.getNoCacheControl()).build();
        } catch (Exception ex) {
            return this.getExceptionResponse(ex);
        }
    }


    private Response getCheckTokenResponse(String token) {
        SearchEnginePrivateConfig privateConfig = SearchEnginePrivateConfig.getInstance();
        String expectedToken = privateConfig.getReindexToken();

        if (expectedToken == null || expectedToken.isEmpty()) {
            JSONObject jsonStatus = new JSONObject()
                .put("status", "error")
                .put("message", "Reindex token not set in Search Engine configuration.");

            String responseTxt = jsonStatus.toString();

            return Response.serverError().entity(responseTxt).cacheControl(ServletUtils.getNoCacheControl()).build();
        }

        if (token == null || token.isEmpty()) {
            JSONObject jsonStatus = new JSONObject()
                .put("status", "error")
                .put("message", "Parameter token required.");

            String responseTxt = jsonStatus.toString();

            return Response.status(Response.Status.BAD_REQUEST).entity(responseTxt).cacheControl(ServletUtils.getNoCacheControl()).build();
        }

        if (!expectedToken.equals(token)) {
            JSONObject jsonStatus = new JSONObject()
                .put("status", "error")
                .put("message", "Invalid token provided.");

            String responseTxt = jsonStatus.toString();

            return Response.status(Response.Status.FORBIDDEN).entity(responseTxt).cacheControl(ServletUtils.getNoCacheControl()).build();
        }

        return null;
    }

    private Response getExceptionResponse(Exception ex) {
        JSONObject jsonStatus = new JSONObject()
            .put("status", "error")
            .put("message", ServletUtils.getExceptionMessage(ex))
            .put("stacktrace", ServletUtils.exceptionToJSON(ex));

        String responseTxt = jsonStatus.toString();

        // Return the JSON object with an ERROR status.
        return Response.serverError().entity(responseTxt).cacheControl(ServletUtils.getNoCacheControl()).build();
    }
}
