package au.gov.aims.eatlas.searchengine.entity.geoNetworkParser;

@FunctionalInterface
public interface GeoNetworkParserFactory {
    AbstractParser getParser(String metadataSchema);
}
