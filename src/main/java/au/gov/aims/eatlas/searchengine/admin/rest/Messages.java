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

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class Messages {
    private List<Message> messages;

    private static Messages instance;

    private Messages() {}

    public static Messages getInstance() {
        if (instance == null) {
            instance = new Messages();
        }
        return instance;
    }

    public void addMessages(Level level, String message) {
        if (this.messages == null) {
            this.messages = new ArrayList<>();
        }

        this.messages.add(new Message(level, message));
    }

    public List<Message> getMessages() {
        return this.messages;
    }

    public void clear() {
        this.messages = null;
    }

    public enum Level {
        INFO   ("info"),
        WARNING("warning"),
        ERROR  ("error");

        private String cssClass;

        Level(String cssClass) {
            this.cssClass = cssClass;
        }

        public String getCssClass() {
            return this.cssClass;
        }
    }

    public class Message {
        private long timestamp;
        private Level level;
        private String message;

        public Message(Level level, String message) {
            this.timestamp = System.currentTimeMillis();
            this.level = level;
            this.message = message;
        }

        public long getTimestamp() {
            return this.timestamp;
        }

        public Date getDate() {
            return new Date(this.timestamp);
        }

        public Level getLevel() {
            return this.level;
        }

        public void setLevel(Level level) {
            this.level = level;
        }

        public String getMessage() {
            return this.message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}
