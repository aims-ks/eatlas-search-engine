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

import au.gov.aims.eatlas.searchengine.admin.rest.Messages;
import org.json.JSONObject;
import org.locationtech.jts.io.ParseException;

public abstract class AbstractDrupalEntity extends Entity {

    protected AbstractDrupalEntity() {}

    public AbstractDrupalEntity(JSONObject jsonApiNode, Messages messages) {

        // TODO Implement WKT
        String wkt = "BBOX (142.5, 153.0, -10.5, -22.5)";
        try {
            this.setWktAndArea(wkt);
        } catch(ParseException ex) {
            Messages.Message message = messages.addMessage(Messages.Level.WARNING, "Invalid WKT", ex);
            message.addDetail(wkt);
        }

    }

}
