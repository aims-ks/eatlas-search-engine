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
import au.gov.aims.eatlas.searchengine.rest.Search;
import au.gov.aims.eatlas.searchengine.search.SearchResults;
import org.glassfish.jersey.server.mvc.Viewable;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
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
        @QueryParam("page") Integer page
    ) {
        HttpSession session = httpRequest.getSession(true);
        Messages messages = Messages.getInstance(session);

        SearchEngineConfig config = SearchEngineConfig.getInstance();

        if (hitsPerPage == null || hitsPerPage <= 0) {
            hitsPerPage = 10;
        }
        if (page == null || page <= 0) {
            page = 1;
        }

        SearchResults results = null;
        if (query != null && !query.isEmpty()) {
            try {
                int start = (page-1) * hitsPerPage;
                results = Search.paginationSearch(query, start, hitsPerPage, indexes, indexes, messages);
            } catch(Exception ex) {
                messages.addMessage(Messages.Level.ERROR,
                    "An exception occurred during the search.", ex);
            }
        }

        int nbPage = 0;
        if (results != null) {
            nbPage = (int)Math.ceil(((double)results.getSummary().getHits()) / hitsPerPage);
        }

        Map<String, Object> model = new HashMap<>();
        model.put("messages", messages);
        model.put("config", config);
        model.put("query", query);
        model.put("page", page);
        model.put("hitsPerPage", hitsPerPage);
        model.put("nbPage", nbPage);

        model.put("indexes", indexes);
        model.put("results", results);

        // Load the template: src/main/webapp/WEB-INF/jsp/searchPage.jsp
        return new Viewable("/searchPage", model);
    }

}
