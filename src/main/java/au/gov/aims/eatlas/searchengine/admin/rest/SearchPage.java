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
import au.gov.aims.eatlas.searchengine.admin.SearchEnginePrivateConfig;
import au.gov.aims.eatlas.searchengine.client.ESClient;
import au.gov.aims.eatlas.searchengine.client.SearchClient;
import au.gov.aims.eatlas.searchengine.index.AbstractIndexer;
import au.gov.aims.eatlas.searchengine.logger.AbstractLogger;
import au.gov.aims.eatlas.searchengine.logger.Level;
import au.gov.aims.eatlas.searchengine.logger.SessionLogger;
import au.gov.aims.eatlas.searchengine.rest.Search;
import au.gov.aims.eatlas.searchengine.search.SearchResults;
import co.elastic.clients.elasticsearch._types.SortOptions;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import org.glassfish.jersey.server.mvc.Viewable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Path("/search")
public class SearchPage {

    @GET
    @Produces(MediaType.TEXT_HTML)
    public Viewable searchPage(
        @Context HttpServletRequest httpRequest,
        @QueryParam("query") String query,
        @QueryParam("indexes") List<String> indexes,
        @QueryParam("hitsPerPage") Integer hitsPerPage,
        @QueryParam("wkt") String wkt,
        @QueryParam("page") Integer page,
        @QueryParam("reindex-idx") String reindexIdx,
        @QueryParam("reindex-id") String reindexId
    ) {
        HttpSession session = httpRequest.getSession(true);
        AbstractLogger logger = SessionLogger.getInstance(session);

        SearchEngineConfig config = SearchEngineConfig.getInstance();
        SearchEnginePrivateConfig privateConfig = SearchEnginePrivateConfig.getInstance();

        Map<String, Object> model = new HashMap<>();
        model.put("title", "Search");
        model.put("searchActive", "active");
        model.put("logger", logger);
        model.put("config", config);
        model.put("privateConfig", privateConfig);

        try {
            SearchClient searchClient = ESClient.getInstance();

            if (reindexIdx != null && reindexId != null) {
                try {
                    AbstractIndexer<?> indexer = config.getIndexer(reindexIdx);
                    indexer.reindex(searchClient, reindexId, logger);
                    // Wait for the index to update the document.
                    searchClient.refresh(reindexIdx);
                } catch(Exception ex) {
                    logger.addMessage(Level.ERROR,
                        "An exception occurred during the re-indexation of the document.", ex);
                }
            }

            if (hitsPerPage == null || hitsPerPage <= 0) {
                hitsPerPage = 10;
            }
            if (page == null || page <= 0) {
                page = 1;
            }
            
            List<SortOptions> sortOptionsList = new ArrayList<>();

            SearchResults results = null;
            try {
                int start = (page-1) * hitsPerPage;
                results = Search.paginationSearch(searchClient, query, start, hitsPerPage, wkt, sortOptionsList, indexes, indexes, logger);
            } catch(Exception ex) {
                logger.addMessage(Level.ERROR,
                    "An exception occurred during the search.", ex);
            }

            long nbPage = 0;
            if (results != null) {
                nbPage = results.getSummary().getPages();
            }

            model.put("nbPage", nbPage);
            model.put("results", results);
        } catch (Exception ex) {
            logger.addMessage(Level.ERROR,
                "An exception occurred while accessing the Elastic Search server", ex);
        }

        model.put("query", query);
        model.put("wkt", wkt);
        model.put("page", page);
        model.put("hitsPerPage", hitsPerPage);
        model.put("indexes", indexes);

        // Load the template: src/main/webapp/WEB-INF/jsp/searchPage.jsp
        return new Viewable("/searchPage", model);
    }

}
