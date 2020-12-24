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
package au.gov.aims.eatlas.searchengine.servlet;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Random;

public class Search extends HttpServlet {
    private static final Logger LOGGER = Logger.getLogger(Search.class.getName());

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        this.performTask(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        this.performTask(request, response);
    }

    private void performTask(HttpServletRequest request, HttpServletResponse response) throws IOException {

        response.setContentType("application/json");
        response.setStatus(HttpServletResponse.SC_OK);

        JSONObject jsonResponse = this.getFakeSearchResults();

        String responseTxt = jsonResponse.toString();
        LOGGER.log(Level.DEBUG, responseTxt);

        ServletUtils.sendResponse(response, responseTxt);

        /*
        if (error) {
            response.setContentType("text/plain");
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);

            String responseTxt = "Oops...";
            LOGGER.log(Level.WARN, responseTxt);

            ServletUtils.sendResponse(response, responseTxt);
        }
        */
    }


    /**
     * @deprecated Used to test, do not forget to delete!
     */
    @Deprecated
    public JSONObject getFakeSearchResults() {
        JSONObject resultNode4 = new JSONObject()
            .put("link", "http://localhost:9090/node/4")
            .put("index", "eatlas_drupal")
            .put("title", "A guide to Indigenous science, management and governance of Australian coastal waters")
            .put("score", 23)
            .put("snippet", "In response to today’s modern day challenges, many Indigenous groups have developed their own")
            .put("thumbnail", "https://cdn131.picsart.com/339459399004201.jpg?to=crop&r=256")
            .put("langcode", "en");

        JSONObject resultGoogle = new JSONObject()
            .put("link", "https://google.com")
            .put("index", "eatlas_extlinks")
            .put("title", "Google search engine")
            .put("score", 12)
            .put("snippet", "Google Search, I'm Feeling Lucky")
            .put("thumbnail", "https://www.google.com/logos/doodles/2020/december-holidays-days-2-30-6753651837108830.3-law.gif")
            .put("langcode", "en");

        JSONArray results = new JSONArray();
        results.put(resultNode4);
        results.put(resultGoogle);

        Random random = new Random();
        for (int i=0; i<20; i++) {
            results.put(this.getRandomGoogleSearchResult(random));
        }

        return new JSONObject()
            .put("summary", new JSONObject()
                .put("hits", results.length()) // Number of search result found
                .put("indexes", new JSONObject()
                    .put("eatlas_drupal", new JSONObject()
                        .put("hits", 1)
                    )
                    .put("eatlas_extlinks", new JSONObject()
                        .put("hits", results.length() - 1)
                    )
                )
                .put("page", 1) // Current page
                .put("pages", 1) // Number of pages
            )
            .put("results", results);
    }

    private JSONObject getRandomGoogleSearchResult(Random random) {
        String randomSearchTerm = this.getRandomWord(random);

        return new JSONObject()
            .put("link", String.format("https://www.google.com/search?q=%s", randomSearchTerm))
            .put("index", "eatlas_extlinks")
            .put("title", String.format("Google search for %s", randomSearchTerm))
            .put("score", 12)
            .put("snippet", String.format("Google search result for random word %s", randomSearchTerm))
            .put("thumbnail", "https://www.google.com/logos/doodles/2020/december-holidays-days-2-30-6753651837108830.3-law.gif")
            .put("langcode", "en");
    }

    private String getRandomWord(Random random) {
        // Word of length 3 through 10.
        char[] word = new char[random.nextInt(8)+3];
        for(int i = 0; i < word.length; i++) {
            word[i] = (char)('a' + random.nextInt(26));
        }
        return new String(word);
    }
}
