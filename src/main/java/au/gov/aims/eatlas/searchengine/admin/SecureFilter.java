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

import au.gov.aims.eatlas.searchengine.entity.User;
import au.gov.aims.eatlas.searchengine.rest.LoginPage;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.ws.rs.core.UriBuilder;
import java.io.IOException;

/**
 * Secure filter, used to protect every admin pages.
 */
public class SecureFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        if (request == null || !(request instanceof HttpServletRequest) ||
                response == null || !(response instanceof HttpServletResponse)) {

            throw new IllegalArgumentException("A page was requested using an unsupported protocol.");
        }

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        SearchEngineConfig config = SearchEngineConfig.getInstance();

        User loggedUser = null;

        HttpSession session = httpRequest.getSession(true);
        if (session != null) {
            String loggedUsername = (String)session.getAttribute(LoginPage.LOGGED_USER_KEY);
            if (loggedUsername != null && !loggedUsername.isEmpty()) {
                User user = config.getUser();
                if (user != null && loggedUsername.equals(user.getUsername())) {
                    loggedUser = user;
                }
            }
        }

        if (loggedUser == null) {
            // Redirect to the login page.
            UriBuilder uriBuilder = UriBuilder.fromPath(httpRequest.getContextPath());
            uriBuilder.path("/public/login");
            httpResponse.sendRedirect(uriBuilder.build().toString());

        } else {
            chain.doFilter(httpRequest, httpResponse);
        }
    }
}
