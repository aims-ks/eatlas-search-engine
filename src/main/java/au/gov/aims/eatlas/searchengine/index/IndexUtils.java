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

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class IndexUtils {

    public static Map<String, Object> JSONObjectToMap(JSONObject jsonObject) {
        if (jsonObject == null || jsonObject.isEmpty()) {
            return null;
        }

        Map<String, Object> map = new HashMap<>();

        for (String key : jsonObject.keySet()) {
            Object value = jsonObject.opt(key);

            if (value instanceof JSONObject) {
                value = IndexUtils.JSONObjectToMap((JSONObject)value);
            } else if (value instanceof JSONArray) {
                value = IndexUtils.JSONArrayToArray((JSONArray)value);
            }

            map.put(key, value);
        }

        return map;
    }

    public static Object[] JSONArrayToArray(JSONArray jsonArray) {
        if (jsonArray == null || jsonArray.isEmpty()) {
            return null;
        }

        int length = jsonArray.length();
        Object[] array = new Object[length];

        for (int i=0; i<length; i++) {
            Object value = jsonArray.opt(i);

            if (value instanceof JSONObject) {
                value = IndexUtils.JSONObjectToMap((JSONObject)value);
            } else if (value instanceof JSONArray) {
                value = IndexUtils.JSONArrayToArray((JSONArray)value);
            }

            array[i] = value;
        }

        return array;
    }
}
