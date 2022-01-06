package au.gov.aims.eatlas.searchengine.search;

import jakarta.ws.rs.core.Response;
import org.json.JSONObject;

public class ErrorMessage {
    private String errorMessage;
    private int statusCode;

    public String getErrorMessage() {
        return this.errorMessage;
    }

    public ErrorMessage setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
        return this;
    }

    public int getStatusCode() {
        return this.statusCode;
    }

    public ErrorMessage setStatus(Response.Status status) {
        if (status != null) {
            this.statusCode = status.getStatusCode();
        }
        return this;
    }

    public ErrorMessage setStatusCode(int statusCode) {
        this.statusCode = statusCode;
        return this;
    }

    public JSONObject toJSON() {
        return new JSONObject()
            .put("errorMessage", this.errorMessage)
            .put("statusCode", this.statusCode);
    }

    @Override
    public String toString() {
        return this.toJSON().toString();
    }
}
