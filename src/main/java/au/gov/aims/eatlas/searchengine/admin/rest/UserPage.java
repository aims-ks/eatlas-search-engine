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
import au.gov.aims.eatlas.searchengine.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import org.glassfish.jersey.server.mvc.Viewable;

import java.util.HashMap;
import java.util.Map;

@Path("/user")
public class UserPage {

    @GET
    @Produces(MediaType.TEXT_HTML)
    public Viewable userPage(
        @Context HttpServletRequest httpRequest
    ) {
        HttpSession session = httpRequest.getSession(true);
        Messages messages = Messages.getInstance(session);

        SearchEngineConfig config = SearchEngineConfig.getInstance();

        Map<String, Object> model = new HashMap<>();
        model.put("messages", messages);
        model.put("config", config);

        // Load the template: src/main/webapp/WEB-INF/jsp/user.jsp
        return new Viewable("/user", model);
    }

    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public Viewable saveSettings(
        @Context HttpServletRequest httpRequest,
        MultivaluedMap<String, String> form
    ) {
        HttpSession session = httpRequest.getSession(true);
        Messages messages = Messages.getInstance(session);

        this.save(form, messages);

        return userPage(httpRequest);
    }

    private void save(MultivaluedMap<String, String> form, Messages messages) {
        SearchEngineConfig config = SearchEngineConfig.getInstance();
        User user = config.getUser();

        if (user == null) {
            // This should not happen
            messages.addMessage(Messages.Level.ERROR, "The admin user was not loaded.");
            return;
        }

        user.setUsername(FormUtils.getFormStringValue(form, "username"));
        user.setFirstName(FormUtils.getFormStringValue(form, "first-name"));
        user.setLastName(FormUtils.getFormStringValue(form, "last-name"));
        user.setEmail(FormUtils.getFormStringValue(form, "email"));

        // Change password
        String password = FormUtils.getFormStringValue(form, "password");
        String repassword = FormUtils.getFormStringValue(form, "repassword");

        boolean passwordChanged = false;
        if (password != null && repassword != null) {
            if (password.equals(repassword)) {
                user.setPassword(password, messages);
                passwordChanged = true;
            } else {
                messages.addMessage(Messages.Level.WARNING, "The password was not changed. Passwords do not match.");
            }
        }

        if (!user.validate()) {
            // This should only happen when the user modify the form using the browser's developer tool,
            // or if the browser doesn't support HTML 5.
            messages.addMessage(Messages.Level.ERROR,
                "Form validation failed.");
        } else {
            try {
                config.save();
                if (passwordChanged) {
                    messages.addMessage(Messages.Level.INFO, "Password successfully changed.");
                }
            } catch (Exception ex) {
                messages.addMessage(Messages.Level.ERROR,
                    "An exception occurred while saving the user information.", ex);
                if (passwordChanged) {
                    messages.addMessage(Messages.Level.ERROR, "Password was changed, but will be reset next time the configuration file is reloaded.");
                }
            }
        }
    }
}
