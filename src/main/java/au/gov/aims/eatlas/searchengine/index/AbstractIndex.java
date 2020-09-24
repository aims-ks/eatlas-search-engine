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
import org.apache.lucene.search.Query;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;

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

    public List<E> search(ESClient client, Query query) throws IOException {
        SearchResponse response = client.search(this.getSearchRequest(query));
        // TODO Create a list of entities from the response
        return null;
    }

    // Low level

    public IndexRequest getIndexRequest(E entity) {
        return new IndexRequest(this.getIndex())
            .id(entity.getId())
            .source(entity.toJSON());
    }

    public GetRequest getGetRequest(String id) {
        return new GetRequest(this.getIndex())
            .id(id);
    }

    public SearchRequest getSearchRequest(Query query) {
        return null;
    }
}
