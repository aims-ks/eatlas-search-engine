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
import org.apache.log4j.Logger;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Map;

// TODO Move search outside. Search should be allowed to be run against any number of indexes at once
public abstract class AbstractIndexer<E extends Entity> {
    private static final Logger LOGGER = Logger.getLogger(AbstractIndexer.class.getName());

    private String index;

    public AbstractIndexer(String index) {
        this.index = index;
    }

    public abstract void harvest(Long lastModified) throws Exception;
    public abstract E load(JSONObject json);

    public String getIndex() {
        return this.index;
    }

    public IndexResponse index(ESClient client, E entity) throws IOException {
        entity.setLastIndexed(System.currentTimeMillis());
        return client.index(this.getIndexRequest(entity));
    }

    public E get(ESClient client, String id) throws IOException {
        JSONObject jsonEntity = AbstractIndexer.get(client, this.index, id);
        if (jsonEntity != null) {
            return this.load(jsonEntity);
        }
        return null;
    }

    public static JSONObject get(ESClient client, String index, String id) throws IOException {
        GetResponse response = client.get(AbstractIndexer.getGetRequest(index, id));
        if (response == null) {
            return null;
        }

        Map<String, Object> sourceMap = response.getSource();
        if (sourceMap == null || sourceMap.isEmpty()) {
            return null;
        }

        return new JSONObject(response.getSource());
    }

    // Low level

    public IndexRequest getIndexRequest(E entity) {
        return new IndexRequest(this.getIndex())
            .id(entity.getId())
            .source(IndexUtils.JSONObjectToMap(entity.toJSON()));
    }

    public static GetRequest getGetRequest(String index, String id) {
        return new GetRequest(index)
            .id(id);
    }
}
