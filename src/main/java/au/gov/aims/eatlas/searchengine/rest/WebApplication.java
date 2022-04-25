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

import au.gov.aims.eatlas.searchengine.admin.SearchEngineConfig;
import au.gov.aims.eatlas.searchengine.client.SearchUtils;
import org.apache.log4j.Logger;

import javax.servlet.ServletContext;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import java.io.IOException;

public class WebApplication extends Application {
    private static final Logger LOGGER = Logger.getLogger(WebApplication.class.getName());

    public WebApplication(@Context ServletContext servletContext) {
        try {
            SearchEngineConfig.createInstance(servletContext);
            try {
                SearchUtils.deleteOrphanIndexes();
            } catch (IOException ex) {
                LOGGER.error(
                    "An exception occurred while deleting orphan search indexes.", ex);
            }
        } catch (IOException ex) {
            LOGGER.error("The eAtlas search engine could not load its configuration.", ex);
        }
    }
}
