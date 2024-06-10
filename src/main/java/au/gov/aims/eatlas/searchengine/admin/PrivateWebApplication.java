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
package au.gov.aims.eatlas.searchengine.admin;

import au.gov.aims.eatlas.searchengine.HttpClient;
import au.gov.aims.eatlas.searchengine.admin.rest.Messages;
import au.gov.aims.eatlas.searchengine.client.ESClient;
import au.gov.aims.eatlas.searchengine.client.SearchClient;
import au.gov.aims.eatlas.searchengine.client.SearchUtils;
import au.gov.aims.eatlas.searchengine.rest.PublicWebApplication;
import jakarta.servlet.ServletContext;
import jakarta.ws.rs.core.Context;
import org.apache.log4j.Logger;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.mvc.jsp.JspMvcFeature;

public class PrivateWebApplication extends ResourceConfig {
    private static final Logger LOGGER = Logger.getLogger(PublicWebApplication.class.getName());

    public PrivateWebApplication(@Context ServletContext servletContext) {
        HttpClient httpCLient = HttpClient.getInstance();
        Messages messages = Messages.getInstance(null);

        try {
            SearchEngineConfig.createInstance(httpCLient, servletContext, messages);
            try (SearchClient searchClient = new ESClient()) {
                SearchUtils.deleteOrphanIndexes(searchClient);
            } catch (Exception ex) {
                LOGGER.error(
                    "An exception occurred while deleting orphan search indexes.", ex);
            }
        } catch (Exception ex) {
            LOGGER.error("Could not load the eAtlas search engine configuration.", ex);
        }

        this.packages("au.gov.aims.eatlas.searchengine.admin.rest");
        this.property(JspMvcFeature.TEMPLATE_BASE_PATH, "/WEB-INF/jsp");
        this.register(JspMvcFeature.class);
    }
}
