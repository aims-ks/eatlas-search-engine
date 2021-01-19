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
package au.gov.aims.eatlas.searchengine.index;

import au.gov.aims.eatlas.searchengine.client.ESClient;
import au.gov.aims.eatlas.searchengine.entity.Entity;
import au.gov.aims.eatlas.searchengine.search.SearchResult;
import org.apache.log4j.Logger;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

// TODO Move search outside. Search should be allowed to be run against any number of indexes at once
public abstract class AbstractIndex<E extends Entity> {
    private static final Logger LOGGER = Logger.getLogger(AbstractIndex.class.getName());

    private String index;

    public AbstractIndex(String index) {
        this.index = index;
    }

    public abstract E load(JSONObject json);
    public abstract void harvest();

    public String getIndex() {
        return this.index;
    }

    public IndexResponse index(ESClient client, E entity) throws IOException {
         return client.index(this.getIndexRequest(entity));
    }

    public E get(ESClient client, String id) throws IOException {
        GetResponse response = client.get(this.getGetRequest(id));
        return this.load(new JSONObject(response.getSource()));
    }

    public List<SearchResult> search(ESClient client, String attribute, String needle, int from, int size)
            throws IOException {

        SearchResponse response = client.search(this.getSearchRequest(attribute, needle, from, size));
        LOGGER.debug(String.format("Search response for \"%s\" in \"%s\", index %s:%n%s",
            needle, attribute, this.getIndex(), response.toString()));

        List<SearchResult> results = new ArrayList<>();
        SearchHits hits = response.getHits();
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

    // Low level

    public IndexRequest getIndexRequest(E entity) {
        return new IndexRequest(this.getIndex())
            .id(entity.getId())
            .source(IndexUtils.JSONObjectToMap(entity.toJSON()));
    }

    public GetRequest getGetRequest(String id) {
        return new GetRequest(this.getIndex())
            .id(id);
    }

    public SearchRequest getSearchRequest(String attribute, String needle, int from, int size) {
        // Used to highlight search results in the field that was used with the search
        HighlightBuilder highlightBuilder = new HighlightBuilder()
            .field(attribute);

        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder()
            .query(QueryBuilders.matchQuery(attribute, needle))
            .from(from) // Used to continue the search (get next page)
            .size(size) // Number of results to return. Default = 10
            .timeout(new TimeValue(60, TimeUnit.SECONDS))
            .highlighter(highlightBuilder);

        return new SearchRequest()
            .indices(this.getIndex())
            .source(sourceBuilder);
    }
}
