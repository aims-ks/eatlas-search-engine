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
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Path("/settings")
public class SettingsPage {

    @GET
    @Produces(MediaType.TEXT_HTML)
    public Viewable settingsPage() {
        SearchEngineConfig config = SearchEngineConfig.getInstance();

        Map<String, Object> model = new HashMap<>();
        model.put("messages", Messages.getInstance());
        model.put("config", config);

        // Load the template: src/main/webapp/WEB-INF/jsp/settings.jsp
        return new Viewable("/settings", model);
    }

    /**
     * Edit the settings.
     * NOTE: This method should expect a PUT request, but HTML Standards for form submission only support GET and POST:
     *   https://html.spec.whatwg.org/multipage/form-control-infrastructure.html#attributes-for-form-submission
     * @param form
     * @return
     */
    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public Viewable saveSettings(
        MultivaluedMap<String, String> form
    ) {
        // TODO DELETE
        System.out.println("Form size: " + form.size());
        for(String key : form.keySet()) {
            System.out.println("Form key: " + key);
        }

        String imageCacheDirectory = form.getFirst("imageCacheDirectory");
        System.out.println("imageCacheDirectory: " + imageCacheDirectory);

        String globalThumbnailTTL = form.getFirst("globalThumbnailTTL");
        System.out.println("globalThumbnailTTL: " + globalThumbnailTTL);
        // TODO END DELETE


        // NOTE: Heavily restrict characters for index name [a-z0-9\-_]

        SearchEngineConfig config = SearchEngineConfig.getInstance();

        config.setImageCacheDirectory(form.getFirst("imageCacheDirectory"));
        config.setGlobalThumbnailTTL(Long.parseLong(form.getFirst("globalThumbnailTTL")));
        config.setGlobalBrokenThumbnailTTL(Long.parseLong(form.getFirst("globalBrokenThumbnailTTL")));

        try {
            config.save();
        } catch (IOException ex) {
            Messages.getInstance().addMessages(Messages.Level.ERROR,
                "An exception occurred while saving the search engine settings.", ex);
        }

        return settingsPage();
    }
}
