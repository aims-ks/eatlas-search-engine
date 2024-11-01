package au.gov.aims.eatlas.searchengine.logger;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class MessageException {
    private final String message;
    private final String className;
    private final List<String> stackTrace;
    private final MessageException cause;

    public MessageException(Throwable exception) {
        this.message = exception.getMessage();
        this.className = exception.getClass().getName();
        this.stackTrace = new ArrayList<String>();

        for (StackTraceElement stackTraceElement: exception.getStackTrace()) {
            String stackTraceElementStr = stackTraceElement == null ? null : stackTraceElement.toString();
            if (stackTraceElementStr != null && !stackTraceElementStr.isEmpty()) {
                this.stackTrace.add(stackTraceElementStr);
            }
        }

        Throwable cause = exception.getCause();
        this.cause = cause == null ? null : new MessageException(cause);
    }

    // For deserialisation
    private MessageException(String message, String className, List<String> stackTrace, MessageException cause) {
        this.message = message;
        this.className = className;
        this.stackTrace = stackTrace;
        this.cause = cause;
    }

    public String getMessage() {
        return this.message;
    }

    public String getClassName() {
        return this.className;
    }

    public List<String> getStackTrace() {
        return this.stackTrace;
    }

    public MessageException getCause() {
        return this.cause;
    }

    public JSONObject toJSON() {
        JSONObject json = new JSONObject();
        json.put("message", this.message);
        json.put("className", this.className);

        if (this.stackTrace != null && !this.stackTrace.isEmpty()) {
            JSONArray jsonStackTrace = new JSONArray();
            for (String stackTraceElement : this.stackTrace) {
                if (stackTraceElement != null && !stackTraceElement.isEmpty()) {
                    jsonStackTrace.put(stackTraceElement);
                }
            }
            json.put("stackTrace", jsonStackTrace);
        }

        if (this.cause != null) {
            json.put("cause", this.cause.toJSON());
        }

        return json;
    }

    public static MessageException fromJSON(JSONObject json) {
        if (json == null) {
            return null;
        }

        String message = json.optString("message", null);
        String className = json.optString("className", null);

        List<String> stackTrace = new ArrayList<String>();
        JSONArray jsonStackTrace = json.optJSONArray("stackTrace");
        if (jsonStackTrace != null) {
            for (int i=0; i<jsonStackTrace.length(); i++) {
                String stackTraceElement = jsonStackTrace.optString(i, null);
                if (stackTraceElement != null && !stackTraceElement.isEmpty()) {
                    stackTrace.add(stackTraceElement);
                }
            }
        }

        MessageException cause = null;
        JSONObject jsonCause = json.optJSONObject("cause");
        if (jsonCause != null) {
            cause = MessageException.fromJSON(jsonCause);
        }

        return new MessageException(message, className, stackTrace, cause);
    }

    public String toString(int indentFactor) {
        return this.toJSON().toString(indentFactor);
    }

    @Override
    public String toString() {
        return this.toString(2);
    }
}
