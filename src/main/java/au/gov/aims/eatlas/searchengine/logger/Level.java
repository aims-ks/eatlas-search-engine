package au.gov.aims.eatlas.searchengine.logger;

public enum Level {
    INFO   ("info"),
    WARNING("warning"),
    ERROR  ("error");

    private final String cssClass;

    Level(String cssClass) {
        this.cssClass = cssClass;
    }

    public String getCssClass() {
        return this.cssClass;
    }
}
