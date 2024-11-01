package au.gov.aims.eatlas.searchengine.logger;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class Message {
    private final long timestamp;
    private Level level;
    private String message;
    private List<String> details;
    private MessageException exception;

    public Message(Level level, String message) {
        this(level, message, null);
    }

    public Message(Level level, String message, Throwable exception) {
        this.timestamp = System.currentTimeMillis();
        this.level = level;
        this.message = message;
        this.exception = exception == null ? null : new MessageException(exception);
    }

    private Message(long timestamp, Level level, String message, List<String> details, MessageException exception) {
        this.timestamp = timestamp;
        this.level = level;
        this.message = message;
        this.details = details;
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

    public void addDetail(String detail) {
        if (detail != null && !detail.isEmpty()) {
            if (this.details == null) {
                this.details = new ArrayList<String>();
            }
            this.details.add(detail);
        }
    }

    public List<String> getDetails() {
        return this.details;
    }

    public MessageException getException() {
        return this.exception;
    }

    // Helper function, because JSTL template do not have a "while" loop.
    public List<MessageException> getCauses() {
        List<MessageException> causes = new ArrayList<MessageException>();
        if (this.exception != null) {
            MessageException cause = this.exception.getCause();
            while (cause != null) {
                causes.add(cause);
                cause = cause.getCause();
            }
        }
        return causes;
    }

    public void setException(Throwable exception) {
        if (exception == null) {
            this.exception = null;
        } else {
            this.setException(new MessageException(exception));
        }
    }

    public void setException(MessageException messageException) {
        this.exception = messageException;
    }

    public JSONObject toJSON() {
        JSONObject messageJson = new JSONObject()
                .put("level", this.level.name())
                .put("message", this.message)
                .put("timestamp", this.timestamp)
                .put("date", this.getDate().toString());

        if (this.exception != null) {
            messageJson.put("exception", this.exception.toJSON());
        }

        if (this.details != null && !this.details.isEmpty()) {
            JSONArray detailsJson = new JSONArray();
            for (String detail : this.details) {
                if (detail != null && !detail.isEmpty()) {
                    detailsJson.put(detail);
                }
            }
            messageJson.put("details", detailsJson);
        }

        return messageJson;
    }

    public static Message fromJSON(JSONObject json) {
        if (json == null) {
            return null;
        }

        long timestamp = json.optLong("timestamp", 0);

        String levelStr = json.optString("level", null);
        Level level = levelStr == null ? null : Level.valueOf(levelStr);

        String message = json.optString("message", null);

        List<String> details = null;
        JSONArray detailsJson = json.optJSONArray("details");
        if (detailsJson != null) {
            details = new ArrayList<String>();
            for (int i=0; i<detailsJson.length(); i++) {
                String detailStr = detailsJson.optString(i, null);
                if (detailStr != null && !detailStr.isEmpty()) {
                    details.add(detailStr);
                }
            }
        }

        MessageException exception = null;
        JSONObject jsonException = json.optJSONObject("exception");
        if (jsonException != null) {
            exception = MessageException.fromJSON(jsonException);
        }

        return new Message(timestamp, level, message, details, exception);
    }

    public String toString(int indentFactor) {
        return this.toJSON().toString(indentFactor);
    }

    @Override
    public String toString() {
        return this.toString(2);
    }
}
