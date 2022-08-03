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

import java.io.IOException;

public class UnsupportedWktException extends IOException {
    private String wkt;

    public UnsupportedWktException(String wkt) {
        super();
        this.wkt = wkt;
    }

    public UnsupportedWktException(String message, String wkt) {
        super(message);
        this.wkt = wkt;
    }

    public UnsupportedWktException(String message, String wkt, Throwable cause) {
        super(message, cause);
        this.wkt = wkt;
    }

    public String getWkt() {
        return wkt;
    }
}
