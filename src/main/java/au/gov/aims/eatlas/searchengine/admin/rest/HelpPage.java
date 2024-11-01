package au.gov.aims.eatlas.searchengine.admin.rest;

import au.gov.aims.eatlas.searchengine.admin.SearchEngineConfig;
import au.gov.aims.eatlas.searchengine.logger.AbstractLogger;
import au.gov.aims.eatlas.searchengine.logger.SessionLogger;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import org.glassfish.jersey.server.mvc.Viewable;

import java.util.HashMap;
import java.util.Map;

@Path("/help")
public class HelpPage {

    @GET
    @Produces(MediaType.TEXT_HTML)
    public Viewable helpPage(
        @Context HttpServletRequest httpRequest
    ) {
        HttpSession session = httpRequest.getSession(true);
        AbstractLogger logger = SessionLogger.getInstance(session);

        SearchEngineConfig config = SearchEngineConfig.getInstance();

        Map<String, Object> model = new HashMap<>();
        model.put("title", "Help");
        model.put("helpActive", "active");
        model.put("logger", logger);
        model.put("config", config);

        String elasticSearchUrl = "http://localhost:9200";
        /*
        // We can get the Elastic Search URL from the config,
        //   but it will likely contain the Docker service name for the domain name,
        //   which is not very useful outside of Docker.
        List<String> elasticSearchUrls = config.getElasticSearchUrls();
        if (elasticSearchUrls != null && !elasticSearchUrls.isEmpty()) {
            elasticSearchUrl = elasticSearchUrls.get(0);
        }
        */
        model.put("elasticSearchUrl", elasticSearchUrl);

        // Load the template: src/main/webapp/WEB-INF/jsp/help.jsp
        return new Viewable("/help", model);
    }
}
