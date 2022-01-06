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

import au.gov.aims.eatlas.searchengine.client.ESClient;
import au.gov.aims.eatlas.searchengine.client.ESRestHighLevelClient;
import au.gov.aims.eatlas.searchengine.search.ErrorMessage;
import au.gov.aims.eatlas.searchengine.search.IndexSummary;
import au.gov.aims.eatlas.searchengine.search.SearchResult;
import au.gov.aims.eatlas.searchengine.search.SearchResults;
import au.gov.aims.eatlas.searchengine.search.Summary;
import org.apache.http.HttpHost;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.core.CountRequest;
import org.elasticsearch.client.core.CountResponse;
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.json.JSONObject;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.CacheControl;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Path("/search/v1")
public class Search {
    private static final Logger LOGGER = Logger.getLogger(Search.class.getName());

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response search(
            @Context HttpServletRequest servletRequest,
            @QueryParam("q") String q,
            @QueryParam("start") Integer start,
            @QueryParam("hits") Integer hits,
            @QueryParam("idx") List<String> idx, // List of indexes used for the summary
            @QueryParam("fidx") List<String> fidx // List of indexes to filter the search results (optional)
    ) {
        LOGGER.log(Level.WARN, "q: " + q);
        LOGGER.log(Level.WARN, "start: " + start);
        LOGGER.log(Level.WARN, "hits: " + hits);
        if (idx != null && !idx.isEmpty()) {
            for (int i=0; i<idx.size(); i++) {
                LOGGER.log(Level.WARN, "idx["+i+"]: " + idx.get(i));
            }
        }
        if (fidx != null && !fidx.isEmpty()) {
            for (int i=0; i<fidx.size(); i++) {
                LOGGER.log(Level.WARN, "fidx["+i+"]: " + fidx.get(i));
            }
        }

        if (start == null) {
            start = 0;
        }
        if (hits == null) {
            hits = 10;
        }

        // Disable cache DURING DEVELOPMENT!
        CacheControl noCache = new CacheControl();
        noCache.setNoCache(true);

        if (q == null) {
            Response.Status status = Response.Status.BAD_REQUEST;
            ErrorMessage errorMessage = new ErrorMessage()
                .setErrorMessage("Invalid request. Missing parameter q")
                .setStatus(status);
            return Response.status(status).entity(errorMessage.toString()).cacheControl(noCache).build();
        }
        if (idx == null) {
            Response.Status status = Response.Status.BAD_REQUEST;
            ErrorMessage errorMessage = new ErrorMessage()
                .setErrorMessage("Invalid request. Missing parameter idx")
                .setStatus(status);
            return Response.status(status).entity(errorMessage.toString()).cacheControl(noCache).build();
        }

        SearchResults results = new SearchResults();

        try (ESClient client = new ESRestHighLevelClient(new RestHighLevelClient(
                RestClient.builder(
                        new HttpHost("localhost", 9200, "http"),
                        new HttpHost("localhost", 9300, "http"))))) {

            String[] idxArray = idx.toArray(new String[0]);

            Summary searchSummary = Search.searchSummary(client, q, idxArray);
            results.setSummary(searchSummary);

            List<SearchResult> searchResults;
            if (fidx != null && !fidx.isEmpty()) {
                searchResults = Search.search(client, q, start, hits, fidx.toArray(new String[0]));
            } else {
                searchResults = Search.search(client, q, start, hits, idxArray);
            }

            results.setSearchResults(searchResults);

        } catch(Exception ex) {
            String errorMessageStr = String.format("An exception occurred during the search: %s", ex.getMessage());
            LOGGER.log(Level.ERROR, errorMessageStr, ex);
            Response.Status status = Response.Status.INTERNAL_SERVER_ERROR;
            ErrorMessage errorMessage = new ErrorMessage()
                .setErrorMessage(errorMessageStr)
                .setStatus(status);
            return Response.status(status).entity(errorMessage.toString()).cacheControl(noCache).build();
        }

        String responseTxt = results.toString();
        LOGGER.log(Level.DEBUG, responseTxt);

        // Return the JSON array with a OK status.
        return Response.ok(responseTxt).cacheControl(noCache).build();
    }

    /**
     * Summary is done using the count API to get
     *   around the 10'000 limit of the search API.
     * NOTE: It sends one query per index, but it's
     *   much faster than doing a search, since
     *   the search API requires a "size" parameter (default 10).
     *   Getting a full count using the search will likely
     *   result in many more search requests than one
     *   count request per index.
     */
    public static Summary searchSummary(ESClient client, String needle, String ... indexes)
            throws IOException {

        Summary summary = new Summary();

        long totalCount = 0;
        for (String index : indexes) {
            CountResponse response = client.count(Search.getSearchSummaryRequest(needle, index));
            long count = response.getCount();
            if (count > 0) {
                totalCount += count;

                IndexSummary indexSummary = new IndexSummary()
                    .setIndex(index)
                    .setHits(count);
                summary.putIndexSummary(indexSummary);
            }
        }

        summary.setHits(totalCount);

        return summary;
    }

    public static CountRequest getSearchSummaryRequest(String needle, String ... indexes) {
        SearchSourceBuilder sourceBuilder = Search.getBaseSearchQuery(needle)
            .fetchSource(false);

        return new CountRequest()
            .indices(indexes)
            .indicesOptions(Search.getSearchIndicesOptions())
            .query(sourceBuilder.query());
    }

    /**
     * ElasticSearch have 3 search API:
     * - Search API
     * - Scroll API
     * - Search after API
     *
     * They all have their ups and downs.
     * - Search API: Limited to 10'000 results
     * - Scroll API: Save results info on the server as a "scroll" object. The "scroll" needs to be
     *     deleted once we are done with it. It can only save a certain number of scrolls.
     *     Each page gives the "scroll ID" of the next page, making it not possible to arbitrary seek to a given page.
     *     All those issues makes it not suitable for stateless search like Drupal.
     * - Search after API: This one is stateless, but requires to sort the results using a field from
     *     the index, therefore the results can't be order in "score" order. We might get the least
     *     relevant result first. Also, we need to provide the value of the last record to get the
     *     next page, making it impossible to seek pages.
     *
     * I'm using the Search API because that's the only one which is applicable to Drupal.
     *     I'm pretty sure no user will try to go pass the 10'000th result
     *     (page 1000, if we display 10 results per page).
     *
     * https://medium.com/everything-full-stack/elasticsearch-scroll-search-e92eb29bf773
     */
    public static List<SearchResult> search(ESClient client, String needle, int from, int size, String ... indexes)
            throws IOException {

        SearchResponse response = client.search(Search.getSearchRequest(needle, from, size, indexes));

        //LOGGER.debug(String.format("Search response for \"%s\" in \"%s\", indexes %s:%n%s",
        //    needle, attribute, Arrays.toString(indexes), response.toString()));

        List<SearchResult> results = new ArrayList<>();

        SearchHits hits = response.getHits();
        for (SearchHit hit : hits.getHits()) {
            JSONObject jsonEntity = new JSONObject(hit.getSourceAsMap());

            results.add(new SearchResult()
                .setId(hit.getId())
                .setIndex(hit.getIndex())
                .setScore(hit.getScore())
                .addHighlights(hit.getHighlightFields())
                .setEntity(jsonEntity)
            );
        }

        return results;
    }

    /**
     * Search in "document" and "title".
     * The title have a 2x ranking boost.
     */
    public static SearchRequest getSearchRequest(String needle, int from, int size, String ... indexes) {
        // Used to highlight search results in the field that was used with the search
        HighlightBuilder highlightBuilder = new HighlightBuilder()
            .preTags("<strong>")
            .postTags("</strong>")
            .field("document");

        SearchSourceBuilder sourceBuilder = Search.getBaseSearchQuery(needle)
            .from(from) // Used to continue the search (get next page)
            .size(size) // Number of results to return. Default = 10
            .fetchSource(true)
            .highlighter(highlightBuilder);

        return new SearchRequest()
            .indices(indexes)
            .indicesOptions(Search.getSearchIndicesOptions())
            .source(sourceBuilder);
    }

    /**
     * Build the search query.
     * This is done here to ensure both the search and the summary
     *   are based on the same query.
     */
    private static SearchSourceBuilder getBaseSearchQuery(String needle) {
        // Search in document and title by default.
        // User can still specified a field using "field:keyword".
        // Example: dataSourceName:legacy
        Map<String, Float> defaultSearchFields = new HashMap<String, Float>();
        defaultSearchFields.put("title", 2f);
        defaultSearchFields.put("document", 1f);

        return new SearchSourceBuilder()
            .query(QueryBuilders.queryStringQuery(needle).fields(defaultSearchFields))
            .timeout(new TimeValue(60, TimeUnit.SECONDS));
    }

    /**
     * Return search indexes options.
     * This is used to prevent the search from crashing
     *   when the client refer to an empty (or non-existent)
     *   index.
     */
    private static IndicesOptions getSearchIndicesOptions() {
        return IndicesOptions.fromOptions(
            true, // ignoreUnavailable
            true,  // allowNoIndices
            false, // expandToOpenIndices
            false // expandToCloseIndices
        );
    }
}
