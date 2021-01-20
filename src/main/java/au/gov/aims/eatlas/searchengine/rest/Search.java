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
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.unit.TimeValue;
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
import java.util.List;
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

        // TODO Run elastic search first! Run main to test
        //   https://hub.docker.com/_/elasticsearch
        try (ESClient client = new ESRestHighLevelClient(new RestHighLevelClient(
                RestClient.builder(
                        new HttpHost("localhost", 9200, "http"),
                        new HttpHost("localhost", 9300, "http"))))) {

            String[] idxArray = idx.toArray(new String[0]);
            List<SearchResult> searchSummary = Search.searchSummary(client, "document", q, idxArray);

            List<SearchResult> searchResults;
            if (fidx != null && !fidx.isEmpty()) {
                searchResults = Search.search(client, "document", q, start, hits, fidx.toArray(new String[0]));
            } else {
                searchResults = Search.search(client, "document", q, start, hits, idxArray);
            }


            results.setSearchResults(searchResults);

            results.setSummary(new Summary()
                .setHits(searchSummary.size())
                .setStart(start)

                // TODO Loop through "idx"
                .putIndexSummary(new IndexSummary()
                    .setIndex("eatlas_article")
                    .setHits(this.countIndexResults(searchSummary, "eatlas_article")))
                .putIndexSummary(new IndexSummary()
                    .setIndex("eatlas_extlink")
                    .setHits(this.countIndexResults(searchSummary, "eatlas_extlink")))
                .putIndexSummary(new IndexSummary()
                    .setIndex("eatlas_layer")
                    .setHits(this.countIndexResults(searchSummary, "eatlas_layer")))
                .putIndexSummary(new IndexSummary()
                    .setIndex("eatlas_broken")
                    .setHits(this.countIndexResults(searchSummary, "eatlas_broken")))
            );

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

    // TODO Find a better way to do this
    private long countIndexResults(List<SearchResult> resultList, String index) {
        long count = 0;
        if (index != null) {
            index = index.trim();
            if (!index.isEmpty()) {
                for (SearchResult result : resultList) {
                    if (index.equals(result.getIndex())) {
                        count++;
                    }
                }
            }
        }
        return count;
    }


    public static List<SearchResult> searchSummary(ESClient client, String attribute, String needle, String ... indexes)
            throws IOException {

        SearchResponse response = client.search(Search.getSearchSummaryRequest(attribute, needle, indexes));

        //LOGGER.debug(String.format("Search response for \"%s\" in \"%s\", indexes %s:%n%s",
        //    needle, attribute, Arrays.toString(indexes), response.toString()));

        List<SearchResult> results = new ArrayList<>();
        SearchHits hits = response.getHits();
        // TODO Check if there is a way to get results per indexes. Otherwise, do a search per index
        for (SearchHit hit : hits.getHits()) {
            results.add(new SearchResult()
                .setId(hit.getId())
                .setIndex(hit.getIndex())
                .setScore(hit.getScore())
                .addHighlights(hit.getHighlightFields())
            );
        }

        return results;
    }

    public static SearchRequest getSearchSummaryRequest(String attribute, String needle, String ... indexes) {
        // TODO request only stats??
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder()
            .query(QueryBuilders.matchQuery(attribute, needle))
            .timeout(new TimeValue(60, TimeUnit.SECONDS));

        return new SearchRequest()
            .indices(indexes)
            .source(sourceBuilder);
    }


    public static List<SearchResult> search(ESClient client, String attribute, String needle, int from, int size, String ... indexes)
            throws IOException {

        SearchResponse response = client.search(Search.getSearchRequest(attribute, needle, from, size, indexes));

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

                .setLangcode(jsonEntity.optString("langcode", null))
                .setTitle(jsonEntity.optString("title", null))
                .setDocument(jsonEntity.optString("document", null))
                .setLink(jsonEntity.optString("link", null))
                .setThumbnail(jsonEntity.optString("thumbnail", null))
            );
        }

        return results;
    }

    public static SearchRequest getSearchRequest(String attribute, String needle, int from, int size, String ... indexes) {
        // Used to highlight search results in the field that was used with the search
        HighlightBuilder highlightBuilder = new HighlightBuilder()
            .preTags("<strong>")
            .postTags("</strong>")
            .field(attribute);

        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder()
            .query(QueryBuilders.matchQuery(attribute, needle))
            .from(from) // Used to continue the search (get next page)
            .size(size) // Number of results to return. Default = 10
            .fetchSource(true)
            .timeout(new TimeValue(60, TimeUnit.SECONDS))
            .highlighter(highlightBuilder);

        return new SearchRequest()
            .indices(indexes)
            .source(sourceBuilder);
    }
}
