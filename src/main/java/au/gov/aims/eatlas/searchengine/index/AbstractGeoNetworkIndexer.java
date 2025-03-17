package au.gov.aims.eatlas.searchengine.index;

import au.gov.aims.eatlas.searchengine.HttpClient;
import au.gov.aims.eatlas.searchengine.entity.Entity;
import org.json.JSONObject;

public abstract class AbstractGeoNetworkIndexer<E extends Entity> extends AbstractIndexer<E> {
    private String geoNetworkUrl;
    private String geoNetworkPublicUrl;
    private String geoNetworkVersion;

    public AbstractGeoNetworkIndexer(
        HttpClient httpClient,
        String index,
        String indexName,
        String geoNetworkUrl,
        String geoNetworkPublicUrl,
        String geoNetworkVersion) {

        super(httpClient, index, indexName);
        this.geoNetworkUrl = geoNetworkUrl;
        this.geoNetworkPublicUrl = geoNetworkPublicUrl;
        this.geoNetworkVersion = geoNetworkVersion;
    }

    protected JSONObject getJsonBase() {
        return super.getJsonBase()
            .put("geoNetworkUrl", this.geoNetworkUrl)
            .put("geoNetworkPublicUrl", this.geoNetworkPublicUrl)
            .put("geoNetworkVersion", this.geoNetworkVersion);
    }

    @Override
    public boolean validate() {
        if (!super.validate()) {
            return false;
        }
        if (this.geoNetworkUrl == null || this.geoNetworkUrl.isEmpty()) {
            return false;
        }
        return true;
    }

    public String getGeoNetworkUrl() {
        return this.geoNetworkUrl;
    }

    public void setGeoNetworkUrl(String geoNetworkUrl) {
        this.geoNetworkUrl = geoNetworkUrl;
    }

    public String getGeoNetworkPublicUrl() {
        return this.geoNetworkPublicUrl;
    }

    public void setGeoNetworkPublicUrl(String geoNetworkPublicUrl) {
        this.geoNetworkPublicUrl = geoNetworkPublicUrl;
    }

    public String getGeoNetworkVersion() {
        return this.geoNetworkVersion;
    }

    public void setGeoNetworkVersion(String geoNetworkVersion) {
        this.geoNetworkVersion = geoNetworkVersion;
    }
}
