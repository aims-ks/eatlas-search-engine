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
package au.gov.aims.eatlas.searchengine.logger;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class AbstractLogger {
    private List<Message> messages;
    private boolean dirty = false;

    public Message addMessage(Level level, String message) {
        Message messageObj = new Message(level, message);
        this.addMessage(messageObj);
        return messageObj;
    }
    public Message addMessage(Level level, String message, Throwable exception) {
        Message messageObj = new Message(level, message, exception);
        this.addMessage(messageObj);
        return messageObj;
    }

    public abstract void addMessage(Message messageObj);

    public void save() {
        this.dirty = false;
    }

    protected boolean isDirty() {
        return this.dirty;
    }
    protected void setDirty(boolean dirty) {
        this.dirty = dirty;
    }

    public void clear() {
        if (this.messages != null) {
            this.messages.clear();
            this.dirty = true;
        }
    }

    public List<Message> getMessages() {
        if (this.messages == null) {
            this.messages = Collections.synchronizedList(new ArrayList<>());
        }
        return this.messages;
    }
    protected void setMessages(List<Message> messages) {
        this.messages = messages;
        this.dirty = true;
    }

    public boolean isEmpty() {
        return this.messages == null || this.messages.isEmpty();
    }

    public JSONObject toJSON() {
        JSONArray messagesJson = new JSONArray();
        if (this.messages != null && !this.messages.isEmpty()) {
            for (Message message : this.messages) {
                messagesJson.put(message.toJSON());
            }
        }
        return new JSONObject().put("messages", messagesJson);
    }

    public void loadJSON(JSONObject json) {
        this.messages = null;
        if (json != null) {
            JSONArray messagesJson = json.optJSONArray("messages");
            if (messagesJson != null) {
                for (int i=0; i<messagesJson.length(); i++) {
                    JSONObject messageJson = messagesJson.optJSONObject(i);
                    Message messageObj = Message.fromJSON(messageJson);
                    if (messageObj != null) {
                        this.addMessage(messageObj);
                    }
                }
            }
        }
    }

    public String toString(int indentFactor) {
        return this.toJSON().toString(indentFactor);
    }

    @Override
    public String toString() {
        return this.toString(2);
    }
}
