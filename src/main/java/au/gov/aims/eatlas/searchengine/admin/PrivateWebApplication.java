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
import au.gov.aims.eatlas.searchengine.logger.ConsoleLogger;
import au.gov.aims.eatlas.searchengine.logger.AbstractLogger;
import au.gov.aims.eatlas.searchengine.client.ESClient;
import au.gov.aims.eatlas.searchengine.client.SearchClient;
import au.gov.aims.eatlas.searchengine.client.SearchUtils;
import au.gov.aims.eatlas.searchengine.rest.PublicWebApplication;
import jakarta.servlet.ServletContext;
import jakarta.ws.rs.core.Context;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.mvc.jsp.JspMvcFeature;

public class PrivateWebApplication extends ResourceConfig {
    private static final Logger LOGGER = LogManager.getLogger(PrivateWebApplication.class.getName());

    public PrivateWebApplication(@Context ServletContext servletContext) {
        HttpClient httpCLient = HttpClient.getInstance();
        AbstractLogger logger = ConsoleLogger.getInstance();

        SearchEngineConfig config = null;
        try {
            config = SearchEngineConfig.createInstance(httpCLient, servletContext, logger);
        } catch (Exception ex) {
            LOGGER.error("Could not load the eAtlas search engine configuration.", ex);
        }

        if (config != null) {
            try {
                SearchClient searchClient = ESClient.getInstance();
                if (searchClient.isHealthy()) {
                    SearchUtils.deleteOrphanIndexes(searchClient);
                } else {
                    LOGGER.warn("The Elastic Search server is not healthy (not running or status red).");
                }
            } catch (Exception ex) {
                LOGGER.error(
                    "An exception occurred while deleting orphan search indexes.", ex);
            }
        }

        this.packages("au.gov.aims.eatlas.searchengine.admin.rest");
        this.property(JspMvcFeature.TEMPLATE_BASE_PATH, "/WEB-INF/jsp");
        this.register(JspMvcFeature.class);
    }
}
