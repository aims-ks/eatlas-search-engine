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

import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.mvc.jsp.JspMvcFeature;

import javax.servlet.ServletContext;
import javax.ws.rs.core.Context;
import java.io.IOException;

public class AppConfig extends ResourceConfig {

    public AppConfig(@Context ServletContext servletContext) {
        try {
            SearchEngineConfig.createInstance(servletContext);
        } catch (IOException e) {
            e.printStackTrace();
        }

        this.packages("au.gov.aims.eatlas.searchengine.admin.rest");
        this.property(JspMvcFeature.TEMPLATE_BASE_PATH, "/WEB-INF/jsp");
        this.register(JspMvcFeature.class);
    }
}
