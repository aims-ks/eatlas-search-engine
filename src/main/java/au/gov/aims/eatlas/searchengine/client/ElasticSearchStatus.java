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
package au.gov.aims.eatlas.searchengine.client;

import co.elastic.clients.elasticsearch._types.HealthStatus;

import java.util.ArrayList;
import java.util.List;

public class ElasticSearchStatus {
    private final boolean reachable;
    private HealthStatus healthStatus;
    private final List<String> warnings;
    private List<String> indexes;
    private Exception exception;

    public ElasticSearchStatus(boolean reachable) {
        this.reachable = reachable;
        this.warnings = new ArrayList<>();
    }

    public boolean isReachable() {
        return this.reachable;
    }

    public void setHealthStatus(HealthStatus healthStatus) {
        this.healthStatus = healthStatus;
    }

    public HealthStatus getHealthStatus() {
        return this.healthStatus;
    }

    public void addWarning(String warning) {
        this.warnings.add(warning);
    }

    public List<String> getWarnings() {
        return this.warnings;
    }

    public List<String> getIndexes() {
        return this.indexes;
    }

    public void setIndexes(List<String> indexes) {
        this.indexes = indexes;
    }

    public Exception getException() {
        return this.exception;
    }

    public void setException(Exception exception) {
        this.exception = exception;
    }
}
