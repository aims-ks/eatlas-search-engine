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

import org.apache.log4j.Logger;

import javax.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class Messages {
    private static final Logger LOGGER = Logger.getLogger(Messages.class.getName());

    private List<Message> messages;

    private HttpSession session;

    private Messages(HttpSession session) {
        this.session = session;
    }

    public static Messages getInstance(HttpSession session) {
        if (session == null) {
            return new Messages(null);
        }

        Messages messagesInstance = (Messages)session.getAttribute("messages");
        if (messagesInstance == null) {
            messagesInstance = new Messages(session);
            session.setAttribute("messages", messagesInstance);
        }

        return messagesInstance;
    }

    private void save() {
        if (this.session != null) {
            this.session.setAttribute("messages", this);
        }
    }

    public void addMessage(Level level, String message) {
        this.addMessage(level, message, null);
    }

    public void addMessage(Level level, String message, Throwable exception) {
        if (this.session != null) {
            if (this.messages == null) {
                this.messages = Collections.synchronizedList(new ArrayList<>());
            }

            this.messages.add(new Message(level, message, exception));
            this.save();
        } else {
            // There is no session. No one will ever see those messages. Better display them in the console.
            switch (level) {
                case INFO:
                    LOGGER.info(message, exception);
                    break;

                case WARNING:
                    LOGGER.warn(message, exception);
                    break;

                case ERROR:
                default:
                    LOGGER.error(message, exception);
                    break;
            }
        }
    }

    public List<Message> getMessages() {
        return this.messages;
    }

    public List<Message> consume() {
        List<Message> deletedMessages = this.messages;
        this.messages = null;
        this.save();
        return deletedMessages;
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

    public static class Message {
        private long timestamp;
        private Level level;
        private String message;
        private Throwable exception;

        public Message(Level level, String message, Throwable exception) {
            this.timestamp = System.currentTimeMillis();
            this.level = level;
            this.message = message;
            this.exception = exception;
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

        public Throwable getException() {
            return this.exception;
        }

        public void setException(Throwable exception) {
            this.exception = exception;
        }
    }
}
