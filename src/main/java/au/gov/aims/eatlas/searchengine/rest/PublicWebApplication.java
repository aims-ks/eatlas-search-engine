/*
 *  Copyright (C) 2020 Australian Institute of Marine Science
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

import au.gov.aims.eatlas.searchengine.HttpClient;
import au.gov.aims.eatlas.searchengine.admin.SearchEngineConfig;
import au.gov.aims.eatlas.searchengine.logger.ConsoleLogger;
import au.gov.aims.eatlas.searchengine.logger.AbstractLogger;
import jakarta.servlet.ServletContext;
import jakarta.ws.rs.core.Context;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.mvc.jsp.JspMvcFeature;

public class PublicWebApplication extends ResourceConfig {
    private static final Logger LOGGER = LogManager.getLogger(PublicWebApplication.class.getName());

    public PublicWebApplication(@Context ServletContext servletContext) {
        HttpClient httpClient = HttpClient.getInstance();
        AbstractLogger logger = ConsoleLogger.getInstance();

        try {
            SearchEngineConfig.createInstance(httpClient, servletContext, logger);
        } catch (Exception ex) {
            LOGGER.error("The eAtlas search engine could not load its configuration.", ex);
        }

        this.packages("au.gov.aims.eatlas.searchengine.rest");
        this.property(JspMvcFeature.TEMPLATE_BASE_PATH, "/WEB-INF/jsp");
        this.register(JspMvcFeature.class);
    }
}
