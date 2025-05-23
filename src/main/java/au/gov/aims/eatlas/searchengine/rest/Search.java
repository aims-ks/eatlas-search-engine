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

import au.gov.aims.eatlas.searchengine.admin.SearchEngineConfig;
import au.gov.aims.eatlas.searchengine.index.AbstractIndexer;
import au.gov.aims.eatlas.searchengine.logger.AbstractLogger;
import au.gov.aims.eatlas.searchengine.logger.Level;
import au.gov.aims.eatlas.searchengine.logger.SessionLogger;
import au.gov.aims.eatlas.searchengine.client.SearchClient;
import au.gov.aims.eatlas.searchengine.client.ESClient;
import au.gov.aims.eatlas.searchengine.entity.Bbox;
import au.gov.aims.eatlas.searchengine.entity.Entity;
import au.gov.aims.eatlas.searchengine.index.WktUtils;
import au.gov.aims.eatlas.searchengine.search.ErrorMessage;
import au.gov.aims.eatlas.searchengine.search.IndexSummary;
import au.gov.aims.eatlas.searchengine.search.SearchResult;
import au.gov.aims.eatlas.searchengine.search.SearchResults;
import au.gov.aims.eatlas.searchengine.search.Summary;
import co.elastic.clients.elasticsearch._types.GeoShapeRelation;
import co.elastic.clients.elasticsearch._types.Script;
import co.elastic.clients.elasticsearch._types.ScriptLanguage;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.ParseException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Path("/search/v1")
public class Search {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response search(
            @Context HttpServletRequest httpRequest,
            @QueryParam("q") String q,
            @QueryParam("start") Integer start, // The index of the first element (offset)
            @QueryParam("hits") Integer hits, // Results per page
            @QueryParam("wkt") String wkt, // Well Known Text, used for GIS search
            @QueryParam("sorts") List<String> sorts, // Sort fields and their order
            @QueryParam("idx") List<String> idx, // List of indexes used for the summary
            @QueryParam("fidx") List<String> fidx // List of indexes to filter the search results (optional)
    ) {
        HttpSession session = httpRequest.getSession(true);
        AbstractLogger logger = SessionLogger.getInstance(session);

        // Add support for PHP
        if (idx == null || idx.isEmpty()) {
            idx = ServletUtils.parsePHPMultiValueQueryParameter(httpRequest, "idx");
        }
        if (fidx == null || fidx.isEmpty()) {
            fidx = ServletUtils.parsePHPMultiValueQueryParameter(httpRequest, "fidx");
        }
        if (sorts == null || sorts.isEmpty()) {
            sorts = ServletUtils.parsePHPMultiValueQueryParameter(httpRequest, "sorts");
        }

        if (idx.isEmpty()) {
            Response.Status status = Response.Status.BAD_REQUEST;
            ErrorMessage errorMessage = new ErrorMessage()
                .setErrorMessage("Invalid request. Missing parameter idx")
                .setStatus(status);
            return Response.status(status).entity(errorMessage.toString()).cacheControl(ServletUtils.getNoCacheControl()).build();
        }

        List<SortOptions> sortOptionsList = new ArrayList<>();
        // Process each string
        for (String sortCriteria : sorts) {
            String[] parts = sortCriteria.split(":");
            if (parts.length == 2) {
                String field = parts[0];
                String order = parts[1];

                // Determine sort order
                SortOrder sortOrder = "DESC".equalsIgnoreCase(order) ? SortOrder.Desc : SortOrder.Asc;

                // Create SortOptions and add to the list
                SortOptions sortOption = SortOptions.of(so -> so.field(f -> f.field(field).order(sortOrder)));
                sortOptionsList.add(sortOption);
            }
        }


        SearchResults results = null;
        try {
            SearchClient searchClient = ESClient.getInstance();
            results = Search.paginationSearch(searchClient, q, start, hits, wkt, sortOptionsList, idx, fidx, logger);

        } catch(Exception ex) {
            String errorMessageStr = String.format("An exception occurred during the search: %s", ex.getMessage());
            logger.addMessage(Level.ERROR, errorMessageStr, ex);
            Response.Status status = Response.Status.INTERNAL_SERVER_ERROR;
            ErrorMessage errorMessage = new ErrorMessage()
                .setErrorMessage(errorMessageStr)
                .setStatus(status);
            return Response.status(status).entity(errorMessage.toString()).cacheControl(ServletUtils.getNoCacheControl()).build();
        }

        if (results == null) {
            String errorMessageStr = "The search engine returned and empty response";
            logger.addMessage(Level.ERROR, errorMessageStr);
            Response.Status status = Response.Status.BAD_REQUEST;
            ErrorMessage errorMessage = new ErrorMessage()
                .setErrorMessage(errorMessageStr)
                .setStatus(status);
            return Response.status(status).entity(errorMessage.toString()).cacheControl(ServletUtils.getNoCacheControl()).build();
        }

        String responseTxt = results.toString();

        // Return the JSON array with an OK status.
        return Response.ok(responseTxt).cacheControl(ServletUtils.getNoCacheControl()).build();
    }

    /**
     * Perform a search, with paging.
     * @param searchClient The Elastic Search client, used to perform the search.
     * @param q The search query. Set to null or empty to get all the documents in the index.
     * @param start The index of the first element. Default: 0.
     * @param hits Number of search results per page. Default: 10.
     * @param wkt GeoJSON polygon, to filter results. Default: null.
     * @param idx List of indexes used for the search summary. Used to notify the user how many search results are found on each index.
     * @param fidx List of indexes to filter the search results (optional). Default: returns the results for all the index listed in idx.
     * @param logger AbstractLogger instance, used to notify the user of errors, warnings, etc.
     * @return A SearchResults object containing a summary of the search (number of search results in each index) and the list of search results.
     * @throws IOException If something goes wrong with ElasticSearch.
     */
    public static SearchResults paginationSearch(
            SearchClient searchClient,
            String q,
            Integer start,
            Integer hits,
            String wkt,
            List<SortOptions> sortOptionsList,  // List of sort fields
            List<String> idx,    // List of indexes used for the summary
            List<String> fidx,   // List of indexes to filter the search results (optional, default: list all search results for idx)
            AbstractLogger logger
    ) throws IOException, ParseException {
        if (idx == null || idx.isEmpty()) {
            return null;
        }

        if (start == null) {
            start = 0;
        }
        if (hits == null) {
            hits = 10;
        }

        SearchResults results = new SearchResults();

        Summary searchSummary = Search.searchSummary(searchClient, q, wkt, hits, idx, fidx);
        results.setSummary(searchSummary);

        List<SearchResult> searchResults;
        if (fidx != null && !fidx.isEmpty()) {
            searchResults = Search.search(searchClient, logger, q, wkt, sortOptionsList, start, hits, fidx.toArray(new String[0]));
        } else {
            searchResults = Search.search(searchClient, logger, q, wkt, sortOptionsList, start, hits, idx.toArray(new String[0]));
        }

        results.setSearchResults(searchResults);

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
    public static Summary searchSummary(SearchClient searchClient, String searchText, String wkt, int hitsPerPage, List<String> indexes, List<String> filteredIndexes)
            throws IOException, ParseException {

        Summary summary = new Summary();

        long filteredCount = 0;
        long totalCount = 0;
        for (String index : indexes) {
            CountResponse response = searchClient.count(Search.getSearchSummaryRequest(searchText, wkt, index));
            long count = response.count();
            if (count > 0) {
                totalCount += count;

                if (filteredIndexes == null || filteredIndexes.isEmpty() || filteredIndexes.contains(index)) {
                    filteredCount += count;
                }

                SearchEngineConfig config = SearchEngineConfig.getInstance();
                AbstractIndexer<?> indexer = config == null ? null : config.getIndexer(index);

                IndexSummary indexSummary = new IndexSummary()
                    .setIndex(index)
                    .setIndexName(indexer == null ? "Unnamed" : indexer.getIndexName())
                    .setHits(count);
                summary.putIndexSummary(indexSummary);
            }
        }

        summary.setHits(filteredCount);
        summary.setTotalHits(totalCount);
        summary.setHitsPerPage(hitsPerPage);

        // Calculate the number of pages,
        // by exploiting Java's integer division.
        // It's faster and more accurate (no floating point error) than:
        //   (long)Math.ceil((double)filteredCount / hitsPerPage)
        summary.setPages((filteredCount + hitsPerPage - 1) / hitsPerPage);

        return summary;
    }

    public static CountRequest getSearchSummaryRequest(String searchText, String wkt, String ... indexes) throws ParseException {
        // Create a search query here
        SearchRequest.Builder sourceBuilder = Search.getBaseSearchQuery(searchText, wkt);

        return new CountRequest.Builder()
                .index(List.of(indexes))
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
    public static List<SearchResult> search(SearchClient searchClient, AbstractLogger logger, String searchText,
                                            String wkt, List<SortOptions> sortOptionsList, int from, int size,
                                            String ... indexes)
            throws IOException, ParseException {

        SearchResponse<Entity> response = searchClient.search(
                Search.getSearchRequest(searchText, wkt, sortOptionsList, from, size, indexes));

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
                logger.addMessage(Level.ERROR,
                        String.format("Search entity is null: %s", hit));
            }
        }

        return results;
    }

    /**
     * Search in "document" and "title".
     * The title have a 2x ranking boost.
     */
    public static SearchRequest getSearchRequest(String searchText, String wkt, List<SortOptions> sortOptionsList,
                                                 int from, int size, String ... indexes) throws ParseException {
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
                .sort(sortOptionsList)
                .index(List.of(indexes))
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
            defaultSearchFields.add("id");

            // ElasticSearch QueryStringQuery class can be used for raw query.
            // It's very powerful, but also very brittle. Any illegal usage of
            //   a reserved character results in a syntax error:
            //   https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-query-string-query.html#_reserved_characters
            // The SimpleQueryStringQuery class offer a powerful syntax
            //   that most search (such as Google) uses.
            // It supports query characters like: ", +, |, -
            Query textQuery = QueryBuilders.simpleQueryString()
                    .query(searchText)
                    .fields(defaultSearchFields)
                    .build()
                    ._toQuery();

            queries.add(textQuery);
        }

        // Build the WKT query
        //   The query used to filter by GEO coordinates, polygons, bbox, etc.
        if (wkt != null && !wkt.isEmpty()) {
            String fixedWkt = WktUtils.fixWkt(wkt);
            // GeoLocation = Single point.
            // GeoLocation geoLocation = new GeoLocation.Builder().text(wkt).build();

            // WktGeoBounds = WKT bounding box
            // WktGeoBounds bounds = new WktGeoBounds.Builder().wkt(wkt).build();

            Query wktQuery = new GeoShapeQuery.Builder()
                    .shape(new GeoShapeFieldQuery.Builder()
                            .shape(JsonData.of(fixedWkt))
                            .relation(GeoShapeRelation.Intersects)
                            .build())
                    .field("wkt")
                    .build()
                    ._toQuery();

            queries.add(wktQuery);

            Geometry geometry = WktUtils.wktToGeometry(fixedWkt);
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
                                                .lang(ScriptLanguage.Painless)
                                                .source(rankingFormula)
                                                .params("searchArea", JsonData.of(searchArea))
                                                .params("searchBbox", JsonData.of(searchBbox))
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
