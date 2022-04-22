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
package au.gov.aims.eatlas.searchengine.admin.rest;

import javax.ws.rs.core.MultivaluedMap;
import java.util.ArrayList;
import java.util.List;

public class FormUtils {
    public static String getFormStringValue(MultivaluedMap<String, String> form, String key) {
        return FormUtils.sanitiseString(form.getFirst(key));
    }

    public static List<String> getFormStringValues(MultivaluedMap<String, String> form, String key) {
        List<String> rawValues = form.get(key);
        if (rawValues == null || rawValues.isEmpty()) {
            return null;
        }

        List<String> values = new ArrayList<>();
        for (String rwaValue : rawValues) {
            String value = FormUtils.sanitiseString(rwaValue);
            if (value != null) {
                values.add(value);
            }
        }

        return values;
    }

    public static Long getFormLongValue(MultivaluedMap<String, String> form, String key) {
        String value = FormUtils.getFormStringValue(form, key);
        return value == null ? null : Long.parseLong(value);
    }

    public static boolean getFormBooleanValue(MultivaluedMap<String, String> form, String key) {
        String value = FormUtils.getFormStringValue(form, key);
        return value != null;
    }

    public static String sanitiseString(String str) {
        if (str == null) {
            return null;
        }
        str = str.trim();
        return str.isEmpty() ? null : str;
    }
}
