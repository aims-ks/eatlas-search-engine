package au.gov.aims.eatlas.searchengine.entity.geoNetworkParser;

import java.util.HashMap;
import java.util.Map;

public class DefaultGeoNetworkParserFactory implements GeoNetworkParserFactory {

    private final Map<String, AbstractParser> parsers = new HashMap<>();

    public DefaultGeoNetworkParserFactory() {
        // Register parsers for each schema
        parsers.put("iso19139", new ISO19139_parser());
        parsers.put("iso19139.anzlic", new ISO19139_parser());
        parsers.put("iso19139.mcp", new ISO19139_parser());
        parsers.put("iso19139.mcp-1.4", new ISO19139_parser());
        parsers.put("iso19115-3.2018", new ISO19115_3_2018_parser());
    }

    @Override
    public AbstractParser getParser(String metadataSchema) {
        return parsers.get(metadataSchema); // Returns null if schema is unsupported
    }
}
