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

import au.gov.aims.eatlas.searchengine.admin.rest.Messages;
import au.gov.aims.eatlas.searchengine.client.SearchClient;
import au.gov.aims.eatlas.searchengine.client.ESClient;
import au.gov.aims.eatlas.searchengine.client.SearchUtils;
import au.gov.aims.eatlas.searchengine.entity.Bbox;
import au.gov.aims.eatlas.searchengine.entity.Entity;
import au.gov.aims.eatlas.searchengine.index.WktUtils;
import au.gov.aims.eatlas.searchengine.search.ErrorMessage;
import au.gov.aims.eatlas.searchengine.search.IndexSummary;
import au.gov.aims.eatlas.searchengine.search.SearchResult;
import au.gov.aims.eatlas.searchengine.search.SearchResults;
import au.gov.aims.eatlas.searchengine.search.Summary;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.GeoShapeRelation;
import co.elastic.clients.elasticsearch._types.InlineScript;
import co.elastic.clients.elasticsearch._types.Script;
import co.elastic.clients.elasticsearch._types.ScriptLanguage;
import co.elastic.clients.elasticsearch._types.query_dsl.FunctionScore;
import co.elastic.clients.elasticsearch._types.query_dsl.FunctionScoreQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.GeoShapeFieldQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.GeoShapeQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import co.elastic.clients.elasticsearch._types.query_dsl.ScriptScoreFunction;
import co.elastic.clients.elasticsearch.core.CountRequest;
import co.elastic.clients.elasticsearch.core.CountResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Highlight;
import co.elastic.clients.elasticsearch.core.search.HighlightField;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import co.elastic.clients.json.JsonData;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.elasticsearch.client.RestClient;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.ParseException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
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
import java.util.Arrays;
import java.util.List;

@Path("/search/v1")
public class Search {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response search(
            @Context HttpServletRequest httpRequest,
            @QueryParam("q") String q,
            @QueryParam("start") Integer start,
            @QueryParam("hits") Integer hits,
            @QueryParam("wkt") String wkt, // Well Known Text, used for GIS search
            @QueryParam("idx") List<String> idx, // List of indexes used for the summary
            @QueryParam("fidx") List<String> fidx // List of indexes to filter the search results (optional)
    ) {
        HttpSession session = httpRequest.getSession(true);
        Messages messages = Messages.getInstance(session);

        CacheControl noCache = new CacheControl();
        noCache.setNoCache(true);

        if (idx == null) {
            Response.Status status = Response.Status.BAD_REQUEST;
            ErrorMessage errorMessage = new ErrorMessage()
                .setErrorMessage("Invalid request. Missing parameter idx")
                .setStatus(status);
            return Response.status(status).entity(errorMessage.toString()).cacheControl(noCache).build();
        }


        SearchResults results = null;
        try {
            results = paginationSearch(q, start, hits, wkt, idx, fidx, messages);

        } catch(Exception ex) {
            String errorMessageStr = String.format("An exception occurred during the search: %s", ex.getMessage());
            messages.addMessage(Messages.Level.ERROR, errorMessageStr, ex);
            Response.Status status = Response.Status.INTERNAL_SERVER_ERROR;
            ErrorMessage errorMessage = new ErrorMessage()
                .setErrorMessage(errorMessageStr)
                .setStatus(status);
            return Response.status(status).entity(errorMessage.toString()).cacheControl(noCache).build();
        }

        if (results == null) {
            String errorMessageStr = "The search engine returned and empty response";
            messages.addMessage(Messages.Level.ERROR, errorMessageStr);
            Response.Status status = Response.Status.BAD_REQUEST;
            ErrorMessage errorMessage = new ErrorMessage()
                .setErrorMessage(errorMessageStr)
                .setStatus(status);
            return Response.status(status).entity(errorMessage.toString()).cacheControl(noCache).build();
        }

        String responseTxt = results.toString();

        // Return the JSON array with an OK status.
        return Response.ok(responseTxt).cacheControl(noCache).build();
    }

    /**
     * Perform a search, with paging.
     * @param q The search query. Set to null or empty to get all the documents in the index.
     * @param start The index of the first element. Default: 0.
     * @param hits Number of search results per page. Default: 10.
     * @param idx List of indexes used for the search summary. Used to notify the user how many search results are found on each index.
     * @param fidx List of indexes to filter the search results (optional). Default: returns the results for all the index listed in idx.
     * @param messages Messages instance, used to notify the user of errors, warnings, etc.
     * @return A SearchResults object containing a summary of the search (number of search results in each index) and the list of search results.
     * @throws IOException If something goes wrong with ElasticSearch.
     */
    public static SearchResults paginationSearch(
            String q,
            Integer start,
            Integer hits,
            String wkt,
            List<String> idx,  // List of indexes used for the summary
            List<String> fidx, // List of indexes to filter the search results (optional, default: list all search results for idx)
            Messages messages
    ) throws IOException, ParseException {
        if (
            idx == null || idx.isEmpty()
        ) {
            return null;
        }

        if (start == null) {
            start = 0;
        }
        if (hits == null) {
            hits = 10;
        }

        SearchResults results = new SearchResults();

        try(
                RestClient restClient = SearchUtils.buildRestClient();

                // Create the transport with a Jackson mapper
                ElasticsearchTransport transport = new RestClientTransport(
                        restClient, new JacksonJsonpMapper());

                // And create the API client
                SearchClient client = new ESClient(new ElasticsearchClient(transport))
        ) {
            String[] idxArray = idx.toArray(new String[0]);

            Summary searchSummary = Search.searchSummary(client, q, wkt, idxArray);
            results.setSummary(searchSummary);

            List<SearchResult> searchResults;
            if (fidx != null && !fidx.isEmpty()) {
                searchResults = Search.search(client, messages, q, wkt, start, hits, fidx.toArray(new String[0]));
            } else {
                searchResults = Search.search(client, messages, q, wkt, start, hits, idxArray);
            }

            results.setSearchResults(searchResults);
        }

        return results;
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
    public static Summary searchSummary(SearchClient client, String searchText, String wkt, String ... indexes)
            throws IOException, ParseException {

        Summary summary = new Summary();

        long totalCount = 0;
        for (String index : indexes) {
            CountResponse response = client.count(Search.getSearchSummaryRequest(searchText, wkt, index));
            long count = response.count();
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

    public static CountRequest getSearchSummaryRequest(String searchText, String wkt, String ... indexes) throws ParseException {
        // Create a search query here
        SearchRequest.Builder sourceBuilder = Search.getBaseSearchQuery(searchText, wkt);

        return new CountRequest.Builder()
                .index(Arrays.asList(indexes))
                .ignoreUnavailable(true)
                .allowNoIndices(true)
                .query(sourceBuilder.build().query())
                .build();
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
    public static List<SearchResult> search(SearchClient client, Messages messages, String searchText, String wkt, int from, int size, String ... indexes)
            throws IOException, ParseException {

        SearchResponse<Entity> response = client.search(Search.getSearchRequest(searchText, wkt, from, size, indexes));

        //LOGGER.debug(String.format("Search response for \"%s\" in \"%s\", indexes %s:%n%s",
        //    searchText, attribute, Arrays.toString(indexes), response.toString()));

        List<SearchResult> results = new ArrayList<>();

        HitsMetadata<Entity> hits = response.hits();
        for (Hit<Entity> hit : hits.hits()) {
            Entity entity = hit.source();

            if (entity != null) {
                results.add(new SearchResult()
                    .setId(entity.getId())
                    .setIndex(entity.getIndex())
                    .setScore(hit.score())
                    .addHighlights(hit.highlight())
                    .setEntity(entity)
                );
            } else {
                messages.addMessage(Messages.Level.ERROR,
                        String.format("Search entity is null: %s", hit));
            }
        }

        return results;
    }

    /**
     * Search in "document" and "title".
     * The title have a 2x ranking boost.
     */
    public static SearchRequest getSearchRequest(String searchText, String wkt, int from, int size, String ... indexes) throws ParseException {
        // Used to highlight search results in the field that was used with the search
        Highlight.Builder highlightBuilder = new Highlight.Builder()
                .preTags("<strong class=\"search-highlight\">")
                .postTags("</strong>")
                .fields("document", new HighlightField.Builder().build());

        // https://discuss.elastic.co/t/8-1-0-java-client-searchrequest-example/299640
        return Search.getBaseSearchQuery(searchText, wkt)
                .from(from) // Used to continue the search (get next page)
                .size(size) // Number of results to return. Default = 10
                .highlight(highlightBuilder.build())
                .index(Arrays.asList(indexes))
                .ignoreUnavailable(true)
                .allowNoIndices(true)
                .build();
    }

    /**
     * Build the search query.
     * This is done here to ensure both the search and the summary
     *   are based on the same query.
     * GEO Queries
     *   https://www.elastic.co/guide/en/elasticsearch/reference/current/geo-queries.html
     */
    private static SearchRequest.Builder getBaseSearchQuery(String searchText, String wkt) throws ParseException {
        ArrayList<Query> queries = new ArrayList<>();

        // Build the text query
        //   The query used to match the words typed in the search field by the user
        if (searchText != null && !searchText.isEmpty()) {
            // Search in document and title by default.
            // User can still specify a field using "field:keyword".
            // Example: dataSourceName:legacy
            List<String> defaultSearchFields = new ArrayList<>();
            defaultSearchFields.add("title");
            defaultSearchFields.add("document");

            Query textQuery = QueryBuilders.queryString()
                    .query(searchText)
                    .fields(defaultSearchFields)
                    .build()
                    ._toQuery();

            queries.add(textQuery);
        }

        // Build the WKT query
        //   The query used to filter by GEO coordinates, polygons, bbox, etc.
        if (wkt != null && !wkt.isEmpty()) {
            // GeoLocation = Single point.
            // GeoLocation geoLocation = new GeoLocation.Builder().text(wkt).build();

            // WktGeoBounds = WKT bounding box
            // WktGeoBounds bounds = new WktGeoBounds.Builder().wkt(wkt).build();

            Query wktQuery = new GeoShapeQuery.Builder()
                    .shape(new GeoShapeFieldQuery.Builder()
                            .shape(JsonData.of(wkt))
                            .relation(GeoShapeRelation.Intersects)
                            .build())
                    .field("wkt")
                    .build()
                    ._toQuery();

            queries.add(wktQuery);

            Geometry geometry = WktUtils.wktToGeometry(wkt);
            if (geometry != null) {
                double searchArea = geometry.getArea();
                Bbox searchBbox = new Bbox(geometry);

                // Rank using bounding box intersection area
                // BBOX from the search:
                //     North: sr_n
                //     East: sr_e
                //     South: sr_s
                //     West: sr_w
                //     Search area = sr_area
                //
                // BBOX found in the index:
                //     North: ix_n
                //     East: ix_e
                //     South: ix_s
                //     West: ix_w
                //     Area in the index: ix_area
                //
                // Calculate intersection area of BBOX:
                //     Idea: (min(North) - max(South)) * (min(East) - max(West))
                //     Code: (min(sr_n, ix_n) - max(sr_s, ix_s)) * (min(sr_e, ix_e) - max(sr_w, ix_w))
                //
                // If the polygons do not intersect, we want the area to be 0:
                //     intersect_area = max(0, (min(sr_n, ix_n) - max(sr_s, ix_s))) * max(0, (min(sr_e, ix_e) - max(sr_w, ix_w)))
                //
                // Smaller polygon with more intersection should rank higher
                //     not_intersect_area = max(ix_area, sr_area) - intersect_area
                //
                // Smaller "not_intersect_area" is better. Higher number rank higher, so we need to "invert" the number
                //     by subtracting with the area of the whole world (64800):
                //     64800 - not_intersect_area
                String rankingFormula = "(64800 - (Math.max(params.searchBbox.area, doc['wktBbox.area'].value) - Math.max(0, (Math.min(params.searchBbox.north, doc['wktBbox.north'].value) - Math.max(params.searchBbox.south, doc['wktBbox.south'].value))) * Math.max(0, (Math.min(params.searchBbox.east, doc['wktBbox.east'].value) - Math.max(params.searchBbox.west, doc['wktBbox.west'].value))))) * _score";

                // Rank using search area difference
                //String rankingFormula = "(64800 - Math.abs(doc['wktArea'].value - params.searchArea)) * _score";

                Query scoreQuery = new FunctionScoreQuery.Builder()
                        .functions(new FunctionScore.Builder()
                                .scriptScore(new ScriptScoreFunction.Builder()
                                        .script(new Script.Builder()
                                                .inline(new InlineScript.Builder()
                                                        .lang(ScriptLanguage.Painless)
                                                        .source(rankingFormula)
                                                        .params("searchArea", JsonData.of(searchArea))
                                                        .params("searchBbox", JsonData.of(searchBbox))
                                                        .build())
                                                .build())
                                        .build())
                                .build())
                        .build()
                        ._toQuery();

                queries.add(scoreQuery);
            }
        }

        // Create a search query using the queries above:
        //   The text query and the WKT query
        Query query;
        if (queries.isEmpty()) {
            // There is no query.
            // Create a query that returns every document in the index.
            query = QueryBuilders.matchAll().build()._toQuery();
        } else if (queries.size() == 1) {
            // There is only one query.
            // Execute that one.
            query = queries.get(0);
        } else {
            // There are multiple queries.
            // Join them using a boolean query.
            query = QueryBuilders.bool().must(queries).build()._toQuery();
        }

        return new SearchRequest.Builder()
                .query(query)
                .timeout("60s"); // Wild guess, there is no doc, no example for this!!
    }
}
