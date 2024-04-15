package au.gov.aims.eatlas.searchengine.admin.rest;

import au.gov.aims.eatlas.searchengine.admin.SearchEngineConfig;
import org.glassfish.jersey.server.mvc.Viewable;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Path("/help")
public class HelpPage {

    @GET
    @Produces(MediaType.TEXT_HTML)
    public Viewable helpPage(
        @Context HttpServletRequest httpRequest
    ) {
        HttpSession session = httpRequest.getSession(true);
        Messages messages = Messages.getInstance(session);

        SearchEngineConfig config = SearchEngineConfig.getInstance();

        Map<String, Object> model = new HashMap<>();
        model.put("messages", messages);
        model.put("config", config);

        String elasticSearchUrl = "http://localhost:9200";

        List<String> elasticSearchUrls = config.getElasticSearchUrls();
        if (elasticSearchUrls != null && !elasticSearchUrls.isEmpty()) {
            elasticSearchUrl = elasticSearchUrls.get(0);
        }
        model.put("elasticSearchUrl", elasticSearchUrl);

        // Load the template: src/main/webapp/WEB-INF/jsp/help.jsp
        return new Viewable("/help", model);
    }
}
