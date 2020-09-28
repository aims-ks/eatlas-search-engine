package au.gov.aims.eatlas.searchengine.index;

import org.elasticsearch.common.text.Text;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Simple object used to hold a simplified search result in memory.
 */
public class SearchResult {
    private final String index;
    private final String id;

    private List<String> highlights;
    private final float score;

    public SearchResult(String index, String id, float score) {
        this.index = index;
        this.id = id;
        this.score = score;
    }

    public String getIndex() {
        return this.index;
    }

    public String getId() {
        return this.id;
    }

    public List<String> getHighlights() {
        return this.highlights;
    }

    public void addHighlight(String highlight) {
        if (this.highlights == null) {
            this.highlights = new ArrayList<String>();
        }
        this.highlights.add(highlight);
    }

    // Helper
    public void addHighlights(Map<String, HighlightField> highlightsMap) {
        if (highlightsMap != null) {
            for (HighlightField highlightField : highlightsMap.values()) {
                Text[] fragments = highlightField.fragments();
                if (fragments != null) {
                    for (Text fragment : fragments) {
                        this.addHighlight(fragment.string());
                    }
                }
            }
        }
    }

    public String getHighlight() {
        return this.getHighlight(" […] ");
    }

    public String getHighlight(String delimiter) {
        if (this.highlights == null) {
            return null;
        }
        return String.join(delimiter, this.highlights);
    }

    public float getScore() {
        return this.score;
    }
}
