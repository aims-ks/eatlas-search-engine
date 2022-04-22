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

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import java.util.HashMap;
import java.util.Map;

@Path("/reindex")
public class ReindexPage {

    @GET
    @Produces(MediaType.TEXT_HTML)
    public Viewable reindexPage() {
        SearchEngineConfig config = SearchEngineConfig.getInstance();

        Map<String, Object> model = new HashMap<>();
        model.put("messages", Messages.getInstance());
        model.put("config", config);

        // Load the template: src/main/webapp/WEB-INF/jsp/reindex.jsp
        return new Viewable("/reindex", model);
    }

    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public Viewable reindex(
        MultivaluedMap<String, String> form
    ) {
        if (form.containsKey("reindex-all")) {
            this.reindexAll();
        } else if (form.containsKey("index-latest-all")) {
            this.indexLatestAll();
        } else if (form.containsKey("refresh-count")) {
            this.refreshCount();

        } else if (form.containsKey("reindex")) {
            this.reindex(FormUtils.getFormStringValue(form, "reindex"));
        } else if (form.containsKey("index-latest")) {
            this.indexLatest(FormUtils.getFormStringValue(form, "index-latest"));
        }

        return this.reindexPage();
    }

    private void reindexAll() {
        System.out.println("reindexAll");
    }

    private void indexLatestAll() {
        System.out.println("indexLatestAll");
    }

    private void refreshCount() {
        System.out.println("refreshCount");
    }


    private void reindex(String index) {
        System.out.println("reindex: " + index);
    }

    private void indexLatest(String index) {
        System.out.println("indexLatest: " + index);
    }
}
