package au.gov.aims.eatlas.searchengine.entity.geoNetworkParser;

import au.gov.aims.eatlas.searchengine.entity.GeoNetworkRecord;
import au.gov.aims.eatlas.searchengine.index.GeoNetworkIndexer;
import au.gov.aims.eatlas.searchengine.logger.AbstractLogger;
import au.gov.aims.eatlas.searchengine.logger.ConsoleLogger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.net.URL;

public class AbstractParserTest {

    @Test
    public void testGetPublicMetadataLinkWithPublicUrl() {
        AbstractLogger logger = ConsoleLogger.getInstance();

        String index = "unit_test";
        String geoNetworkVersion = "3.0";
        String metadataSchema = "iso19115-3.2018";

        GeoNetworkIndexer indexer = new GeoNetworkIndexer(null, index, index,
            "http://eatlas-geonetwork/geonetwork",
            "https://eatlas.org.au/geonetwork",
            geoNetworkVersion);

        GeoNetworkRecord record = new GeoNetworkRecord(indexer,
            "12345678-abcd-1234-abcd-1234567890ab",
            metadataSchema, geoNetworkVersion);

        // No point of truth URL
        URL metadataUrlNoPOT = AbstractParser.getPublicMetadataLink(indexer, record, null, logger);
        Assertions.assertEquals(
                "https://eatlas.org.au/geonetwork/srv/eng/catalog.search#/metadata/12345678-abcd-1234-abcd-1234567890ab",
                metadataUrlNoPOT.toString(),
                "Wrong metadata URL, when the indexer is configured with a public URL and the record has no point of truth.");

        // With external point of truth URL
        URL metadataUrlExternalPOT = AbstractParser.getPublicMetadataLink(indexer, record, "https://eatlas.org.au/data/12345678-abcd-1234-abcd-1234567890ab", logger);
        Assertions.assertEquals(
                "https://eatlas.org.au/data/12345678-abcd-1234-abcd-1234567890ab",
                metadataUrlExternalPOT.toString(),
                "Wrong metadata URL, when the indexer is configured with a public URL and the record has an external point of truth.");

        // With local point of truth URL
        URL metadataUrlLocalPOT = AbstractParser.getPublicMetadataLink(indexer, record, "http://eatlas-geonetwork/geonetwork/srv/eng/catalog.search#/metadata/12345678-abcd-1234-abcd-1234567890ab", logger);
        Assertions.assertEquals(
                "https://eatlas.org.au/geonetwork/srv/eng/catalog.search#/metadata/12345678-abcd-1234-abcd-1234567890ab",
                metadataUrlLocalPOT.toString(),
                "Wrong metadata URL, when the indexer is configured with a public URL and the record has an internal point of truth.");
    }

    @Test
    public void testGetPublicMetadataLinkWithoutPublicUrl() {
        AbstractLogger logger = ConsoleLogger.getInstance();

        String index = "unit_test";
        String geoNetworkVersion = "3.0";
        String metadataSchema = "iso19115-3.2018";

        GeoNetworkIndexer indexer = new GeoNetworkIndexer(null, index, index,
            "http://eatlas-geonetwork/geonetwork",
            null,
            geoNetworkVersion);

        GeoNetworkRecord record = new GeoNetworkRecord(indexer,
            "12345678-abcd-1234-abcd-1234567890ab",
            metadataSchema, geoNetworkVersion);

        // No point of truth URL
        URL metadataUrlNoPOT = AbstractParser.getPublicMetadataLink(indexer, record, null, logger);
        Assertions.assertEquals(
                "http://eatlas-geonetwork/geonetwork/srv/eng/catalog.search#/metadata/12345678-abcd-1234-abcd-1234567890ab",
                metadataUrlNoPOT.toString(),
                "Wrong metadata URL, when the indexer is configured without a public URL and the record has no point of truth.");

        // With external point of truth URL
        URL metadataUrlExternalPOT = AbstractParser.getPublicMetadataLink(indexer, record, "https://eatlas.org.au/data/12345678-abcd-1234-abcd-1234567890ab", logger);
        Assertions.assertEquals(
                "https://eatlas.org.au/data/12345678-abcd-1234-abcd-1234567890ab",
                metadataUrlExternalPOT.toString(),
                "Wrong metadata URL, when the indexer is configured without a public URL and the record has an external point of truth.");

        // With local point of truth URL
        URL metadataUrlLocalPOT = AbstractParser.getPublicMetadataLink(indexer, record, "http://eatlas-geonetwork/geonetwork/srv/eng/catalog.search#/metadata/12345678-abcd-1234-abcd-1234567890ab", logger);
        Assertions.assertEquals(
                "http://eatlas-geonetwork/geonetwork/srv/eng/catalog.search#/metadata/12345678-abcd-1234-abcd-1234567890ab",
                metadataUrlLocalPOT.toString(),
                "Wrong metadata URL, when the indexer is configured without a public URL and the record has an internal point of truth.");
    }
}
