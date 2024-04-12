/*
 *  Copyright (C) 2023 Australian Institute of Marine Science
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
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;

public class Bbox {
    private Double north;
    private Double east;
    private Double south;
    private Double west;

    public Bbox() {}

    public Bbox(Envelope envelope) {
        if (envelope != null) {
            this.north = envelope.getMaxY();
            this.east = envelope.getMaxX();
            this.south = envelope.getMinY();
            this.west = envelope.getMinX();
        }
    }

    public Bbox(Geometry geometry) {
        this(geometry == null ? null : geometry.getEnvelopeInternal());
    }

    public Double getNorth() {
        return this.north;
    }

    public void setNorth(Double north) {
        this.north = north;
    }

    public Double getEast() {
        return this.east;
    }

    public void setEast(Double east) {
        this.east = east;
    }

    public Double getSouth() {
        return this.south;
    }

    public void setSouth(Double south) {
        this.south = south;
    }

    public Double getWest() {
        return this.west;
    }

    public void setWest(Double west) {
        this.west = west;
    }

    public Double getArea() {
        return (this.north == null || this.east == null || this.south == null || this.west == null) ?
            null :
            (this.north - this.south) * (this.east - this.west);
    }

    public JSONObject toJSON() {
        return new JSONObject()
            .put("north", this.getNorth())
            .put("east", this.getEast())
            .put("south", this.getSouth())
            .put("west", this.getWest())
            .put("area", this.getArea());
    }

    protected void loadJSON(JSONObject json, Messages messages) {
        if (json != null) {
            if (json.has("north")) {
                this.setNorth(json.optDouble("north"));
            }
            if (json.has("east")) {
                this.setEast(json.optDouble("east"));
            }
            if (json.has("south")) {
                this.setSouth(json.optDouble("south"));
            }
            if (json.has("west")) {
                this.setWest(json.optDouble("west"));
            }
        }
    }

    @Override
    public String toString() {
        JSONObject json = this.toJSON();
        return json == null ? null : json.toString(4);
    }
}
