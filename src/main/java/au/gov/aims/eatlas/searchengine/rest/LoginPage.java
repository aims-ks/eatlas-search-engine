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
package au.gov.aims.eatlas.searchengine.rest;

import au.gov.aims.eatlas.searchengine.admin.SearchEngineConfig;
import au.gov.aims.eatlas.searchengine.admin.rest.Messages;
import au.gov.aims.eatlas.searchengine.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import org.glassfish.jersey.server.mvc.Viewable;

import java.util.HashMap;
import java.util.Map;

@Path("/")
public class LoginPage {
    public static final String LOGGED_USER_KEY = "logged.user";

    @GET
    @Path("/login")
    @Produces(MediaType.TEXT_HTML)
    public Viewable loginPage(
        @Context HttpServletRequest httpRequest
    ) {
        HttpSession session = httpRequest.getSession(true);
        Messages messages = Messages.getInstance(session);

        SearchEngineConfig config = SearchEngineConfig.getInstance();

        Map<String, Object> model = new HashMap<>();
        model.put("messages", messages);
        model.put("config", config);

        // Load the template: src/main/webapp/WEB-INF/jsp/login.jsp
        return new Viewable("/login", model);
    }

    @POST
    @Path("/login")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public Response login(
        @Context HttpServletRequest httpRequest,
        @FormParam("username") String username,
        @FormParam("password") String password
    ) {
        HttpSession session = httpRequest.getSession(true);
        Messages messages = Messages.getInstance(session);

        User user = this.authenticate(username, password, messages);

        if (user == null) {
            // Invalid username / password.
            // Return a 403 response and reload the login page.
            return Response.status(Response.Status.FORBIDDEN).entity(this.loginPage(httpRequest)).build();
        } else {
            // Successful login.
            // Redirect to the admin default page (the Dashboard page).
            session.setAttribute(LOGGED_USER_KEY, user.getUsername());
            UriBuilder uriBuilder = UriBuilder.fromPath(httpRequest.getContextPath());
            uriBuilder.path("/admin");
            return Response.temporaryRedirect(uriBuilder.build()).build();
        }
    }

    @GET
    @Path("/logout")
    @Produces(MediaType.TEXT_HTML)
    public Response logout(
        @Context HttpServletRequest httpRequest
    ) {
        HttpSession session = httpRequest.getSession(true);

        session.removeAttribute(LOGGED_USER_KEY);
        session.invalidate();

        // Successful logout.
        // Redirect to the login page.
        UriBuilder uriBuilder = UriBuilder.fromPath(httpRequest.getContextPath());
        uriBuilder.path("/public/login");
        return Response.temporaryRedirect(uriBuilder.build()).build();
    }

    private User authenticate(String username, String password, Messages messages) {
        SearchEngineConfig config = SearchEngineConfig.getInstance();

        boolean formFilled = true;
        if (username == null || username.isEmpty()) {
            messages.addMessage(Messages.Level.ERROR, "Please, enter a username.");
            formFilled = false;
        }
        if (password == null || password.isEmpty()) {
            messages.addMessage(Messages.Level.ERROR, "Please, enter a password.");
            formFilled = false;
        }

        if (!formFilled) {
            return null;
        }

        // There is only one user.
        User user = config.getUser();

        // Hide "verifyPassword" messages. We do NOT want to give any clues to the user attempting to login.
        Messages hiddenMessages = Messages.getInstance(null);
        if (!username.equals(user.getUsername()) || !user.verifyPassword(password, hiddenMessages)) {
            messages.addMessage(Messages.Level.ERROR, "Invalid username / password.");
            return null;
        }

        return user;
    }
}
