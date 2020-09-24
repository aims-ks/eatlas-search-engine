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
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

// TODO Move search outside. Search should be allowed to be run against any number of indexes at once
public abstract class AbstractIndex<E extends Entity> {
    public abstract String getIndex();
    public abstract E load(JSONObject json);

    public IndexResponse index(ESClient client, E entity) throws IOException {
         return client.index(this.getIndexRequest(entity));
    }

    public E get(ESClient client, String id) throws IOException {
        GetResponse response = client.get(this.getGetRequest(id));
        return this.load(new JSONObject(response.getSource()));
    }

    public List<E> search(ESClient client, String attribute, String needle) throws IOException {
System.out.println("SEARCH FOR: " + needle + " IN " + attribute);
        SearchResponse response = client.search(this.getSearchRequest(attribute, needle));

        SearchHits hits = response.getHits();
System.out.println("NB FOUND: " + hits.getTotalHits().value);
        for (SearchHit hit : hits.getHits()) {
            System.out.println("FOUND: " + hit.getId());
        }
        // TODO Create a list of entities from the response
        return null;
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

    public SearchRequest getSearchRequest(String attribute, String needle) {
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        sourceBuilder.query(QueryBuilders.matchQuery(attribute, needle));
        sourceBuilder.from(0);
        sourceBuilder.size(5);
        sourceBuilder.timeout(new TimeValue(60, TimeUnit.SECONDS));

        SearchRequest searchRequest = new SearchRequest();
        searchRequest.indices(this.getIndex());
        searchRequest.source(sourceBuilder);

        return searchRequest;
    }
}
