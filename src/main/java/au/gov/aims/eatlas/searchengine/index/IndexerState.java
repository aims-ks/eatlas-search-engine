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
package au.gov.aims.eatlas.searchengine.index;

import org.json.JSONObject;

import java.util.Date;

public class IndexerState {
    // Timestamp of the last re-indexation
    private Long lastIndexed;

    // Runtime of the last re-indexation, in millisecond
    private Long lastIndexRuntime;

    // Number of document in the index.
    // NOTE: Use "long" because ElasticSearch count returns long.
    private Long count;

    public void setLastIndexed(Long lastIndexed) {
        this.lastIndexed = lastIndexed;
    }

    public Long getLastIndexed() {
        return this.lastIndexed;
    }

    public Date getLastIndexedDate() {
        return new Date(this.lastIndexed);
    }

    public void setLastIndexRuntime(Long lastIndexRuntime) {
        this.lastIndexRuntime = lastIndexRuntime;
    }

    public Long getLastIndexRuntime() {
        return this.lastIndexRuntime;
    }

    public String getLastIndexRuntimeFormatted() {
        long seconds = this.lastIndexRuntime / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;

        if (hours > 0) {
            return String.format("%d hour %d min %d sec", hours, minutes % 60, seconds % 60);
        }

        if (minutes > 0) {
            return String.format("%d min %d sec", minutes, seconds % 60);
        }

        return String.format("%d sec", seconds);
    }

    public Long getCount() {
        return this.count;
    }

    public void setCount(Long count) {
        this.count = count;
    }

    public JSONObject toJSON() {
        return new JSONObject()
                .put("lastIndexed", this.lastIndexed)
                .put("lastIndexRuntime", this.lastIndexRuntime)
                .put("count", this.count);
    }

    public static IndexerState fromJSON(JSONObject json) {
        if (json == null) {
            return null;
        }

        IndexerState indexerState = new IndexerState();

        Long lastIndexed = null;
        if (json.has("lastIndexed")) {
            lastIndexed = json.optLong("lastIndexed", -1);
        }
        indexerState.setLastIndexed(lastIndexed);

        Long lastIndexRuntime = null;
        if (json.has("lastIndexRuntime")) {
            lastIndexRuntime = json.optLong("lastIndexRuntime", -1);
        }
        indexerState.setLastIndexRuntime(lastIndexRuntime);

        Long count = null;
        if (json.has("count")) {
            count = json.optLong("count", -1);
        }
        indexerState.setCount(count);

        return indexerState;
    }
}
