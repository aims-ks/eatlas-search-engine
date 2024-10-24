package au.gov.aims.eatlas.searchengine;

import java.util.Objects;

public class MockHttpRequest {
    private final String url;
    private final String data;

    public MockHttpRequest(String url, String data) {
        this.url = url;
        this.data = data;
    }

    public MockHttpRequest(String url) {
        this(url, null);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || this.getClass() != o.getClass()) {
            return false;
        }

        MockHttpRequest that = (MockHttpRequest) o;
        return Objects.equals(this.url, that.url) &&
            Objects.equals(this.normaliseData(this.data), this.normaliseData(that.data));
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.url, this.normaliseData(this.data));
    }

    private String normaliseData(String data) {
        // Remove all whitespaces in the data, to make comparison easier.
        return data == null ? null : data.trim().replaceAll("\\s+", "");
    }
}
