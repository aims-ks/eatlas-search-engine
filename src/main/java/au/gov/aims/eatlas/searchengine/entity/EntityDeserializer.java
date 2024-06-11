/*
 *  Copyright (C) 2022 Australian Institute of Marine Science
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
package au.gov.aims.eatlas.searchengine.entity;

import au.gov.aims.eatlas.searchengine.admin.SearchEngineConfig;
import au.gov.aims.eatlas.searchengine.admin.rest.Messages;
import au.gov.aims.eatlas.searchengine.index.AbstractIndexer;
import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import org.json.JSONObject;

import java.io.IOException;

public class EntityDeserializer extends StdDeserializer<Entity> {
    // The constructor is called by Jackson:
    //     at com.fasterxml.jackson.databind.util.ClassUtil.createInstance(ClassUtil.java:566)
    //     at com.fasterxml.jackson.databind.deser.DefaultDeserializationContext.deserializerInstance(DefaultDeserializationContext.java:234)
    //     at com.fasterxml.jackson.databind.deser.DeserializerCache.findDeserializerFromAnnotation(DeserializerCache.java:431)
    public EntityDeserializer() {
        super((Class<?>)null);
    }

    @Override
    public Entity deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JacksonException {
        JsonNode node = jsonParser.getCodec().readTree(jsonParser);
        String index = node.get("index").asText();

        if (index != null) {
            SearchEngineConfig config = SearchEngineConfig.getInstance();

            Messages messages = Messages.getInstance(null);
            for (AbstractIndexer<?> indexer : config.getIndexers()) {
                if (index.equals(indexer.getIndex())) {
                    // The loader takes a JSONObject. Creates one from the parser.
                    return indexer.load(new JSONObject(node.toString()), messages);
                }
            }
        }

        return null;
    }
}
