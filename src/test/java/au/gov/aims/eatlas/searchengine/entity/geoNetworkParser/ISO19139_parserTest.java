package au.gov.aims.eatlas.searchengine.entity.geoNetworkParser;

import au.gov.aims.eatlas.searchengine.entity.GeoNetworkRecord;
import au.gov.aims.eatlas.searchengine.index.GeoNetworkIndexer;
import au.gov.aims.eatlas.searchengine.logger.AbstractLogger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class ISO19139_parserTest {

    @Mock
    private AbstractLogger mockLogger;

    private AutoCloseable mocks;

    private DocumentBuilder xmlParser;
    private ISO19139_parser parser;

    @BeforeEach
    void setUp() throws ParserConfigurationException {
        this.mocks = MockitoAnnotations.openMocks(this);

        // Initialize the DocumentBuilder
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        this.xmlParser = factory.newDocumentBuilder();

        this.parser = new ISO19139_parser();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (this.mocks != null) {
            this.mocks.close();
        }
    }

    @Test
    public void testParseRecord() throws IOException, SAXException {
        String index = "gn-test";
        String geoNetworkVersion = "3.0";
        String metadataSchema = "iso191139";

        String fileName = "geonetworkRecords/geonetwork2/iso19139-mcp_65f61d2d-fe4e-48e5-8c6e-fab08450ef75.xml";
        try (
                InputStream recordInputStream = getClass().getClassLoader()
                        .getResourceAsStream(fileName)
        ) {
            Document xmlRecord = this.xmlParser.parse(recordInputStream);
            Element rootElement = xmlRecord.getDocumentElement();

            GeoNetworkIndexer indexer = new GeoNetworkIndexer(null, index, index, "http://eatlas-geonetwork/geonetwork", "https://eatlas.org.au/geonetwork", geoNetworkVersion);
            GeoNetworkRecord geoNetworkRecord = new GeoNetworkRecord(indexer, null, metadataSchema, geoNetworkVersion);
            parser.parseRecord(indexer, geoNetworkRecord, rootElement, this.mockLogger);

            Assertions.assertEquals("65f61d2d-fe4e-48e5-8c6e-fab08450ef75", geoNetworkRecord.getId(), "Wrong ID");
            Assertions.assertEquals("18386963-6960-4eb9-889b-d0964069ce13", geoNetworkRecord.getParentUUID(), "Wrong parent ID");
            Assertions.assertEquals("Climate change doubles sedimentation-induced coral recruit mortality (NESP TWQ 5.2, AIMS, JCU and AIMS@JCU)", geoNetworkRecord.getTitle(), "Wrong title");
            Assertions.assertEquals(LocalDate.parse("2021-01-07"), geoNetworkRecord.getPublishedOn(), "Wrong publishedOn date");
            Assertions.assertEquals("https://eatlas.org.au/geonetwork/srv/eng/resources.get?uuid=65f61d2d-fe4e-48e5-8c6e-fab08450ef75&fname=Coral-recruits.png&access=public", geoNetworkRecord.getThumbnailUrl().toString(), "Wrong thumbnail URL");
            Assertions.assertEquals("en", geoNetworkRecord.getLangcode(), "Wrong langcode");
            Assertions.assertEquals("POLYGON ((142.119140625 -9.931640625, 144.228515625 -9.84375, 144.4921875 -12.832031250000002, 145.810546875 -13.798828125, 147.12890625 -17.490234375, 153.45703125 -20.830078125, 153.80859375 -24.521484375, 151.083984375 -24.521484375, 148.447265625 -21.005859375000004, 146.337890625 -19.599609375, 144.755859375 -14.94140625, 143.61328125000003 -14.765625, 142.3828125 -11.77734375, 142.119140625 -9.931640625))", geoNetworkRecord.getWkt(), "Wrong WKT");
            Assertions.assertEquals(41.899108886718714, geoNetworkRecord.getWktArea(), "Wrong WKTArea");
            Assertions.assertEquals("{\n" +
                    "    \"area\": 171.57468795776367,\n" +
                    "    \"east\": 153.80859375,\n" +
                    "    \"south\": -24.521484375,\n" +
                    "    \"north\": -9.84375,\n" +
                    "    \"west\": 142.119140625\n" +
                    "}", geoNetworkRecord.getWktBbox().toString().trim(), "Wrong WKT bounding box");

            String document = geoNetworkRecord.getDocument();
            Assertions.assertTrue(document.contains("This dataset consists of one spreadsheet, which shows the survival"), "The document should contain the abstract");
            Assertions.assertTrue(document.contains("Point of contact"), "The document should contain a point of contact");
            Assertions.assertTrue(document.contains("Principal investigator"), "The document should contain principal investigators");
            Assertions.assertTrue(document.contains("Excel workbook + image + Metadata [Zip 1810 kB]"), "The document should contain online resources");

            Assertions.assertEquals("https://eatlas.org.au/data/uuid/65f61d2d-fe4e-48e5-8c6e-fab08450ef75", geoNetworkRecord.getLink().toString());
        }
    }

    // Method providing test arguments for testParseWKT
    static Stream<Arguments> provideXmlFilesAndExpectedWktResults() {
        return Stream.of(
                Arguments.of("geonetworkRecords/geonetwork2/iso19139-mcp_65f61d2d-fe4e-48e5-8c6e-fab08450ef75.xml",
                        "65f61d2d-fe4e-48e5-8c6e-fab08450ef75",
                        "iso19139.mcp",
                        "POLYGON ((142.119140625 -9.931640625, 144.228515625 -9.84375, 144.4921875 -12.832031250000002, 145.810546875 -13.798828125, 147.12890625 -17.490234375, 153.45703125 -20.830078125, 153.80859375 -24.521484375, 151.083984375 -24.521484375, 148.447265625 -21.005859375000004, 146.337890625 -19.599609375, 144.755859375 -14.94140625, 143.61328125000003 -14.765625, 142.3828125 -11.77734375, 142.119140625 -9.931640625))"),
                Arguments.of("geonetworkRecords/geonetwork2/iso19139-mcp_87263960-92f0-4836-b8c5-8486660ddfe0.xml",
                        "87263960-92f0-4836-b8c5-8486660ddfe0",
                        "iso19139.mcp",
                        "POLYGON ((146.5157 -24.4984, 146.5157 -13.7908, 156.4019 -13.7908, 156.4019 -24.4984, 146.5157 -24.4984))"),
                Arguments.of("geonetworkRecords/geonetwork2/iso19139-mcp_ce58a4c2-c993-434a-aa57-62cfa919a2ab.xml",
                        "ce58a4c2-c993-434a-aa57-62cfa919a2ab",
                        "iso19139.mcp",
                        "POLYGON ((142.385 -24.167, 142.385 -10.412, 153.328 -10.412, 153.328 -24.167, 142.385 -24.167))"),
                Arguments.of("geonetworkRecords/geonetwork2/iso19139_96C8FB9D-C3C4-11DE-3FCD-EA228444A7C1.xml",
                        "{96C8FB9D-C3C4-11DE-3FCD-EA228444A7C1}",
                        "iso19139",
                        "POLYGON ((151.129 -24.002, 151.129 -23.76, 151.411 -23.76, 151.411 -24.002, 151.129 -24.002))")
        );
    }

    @ParameterizedTest
    @MethodSource("provideXmlFilesAndExpectedWktResults")
    public void testParseWkt(String fileName, String metadataRecordUUID, String metadataSchema, String expectedWkt) throws SAXException, IOException {
        String index = "gn-test";
        String geoNetworkVersion = "3.0";

        try (
                InputStream recordInputStream = getClass().getClassLoader()
                        .getResourceAsStream(fileName)
        ) {
            Document document = this.xmlParser.parse(recordInputStream);
            Element rootElement = document.getDocumentElement();

            GeoNetworkIndexer indexer = new GeoNetworkIndexer(null, index, index, "http://eatlas-geonetwork/geonetwork", "https://eatlas.org.au/geonetwork", geoNetworkVersion);
            GeoNetworkRecord geoNetworkRecord = new GeoNetworkRecord(indexer, metadataRecordUUID, metadataSchema, geoNetworkVersion);
            this.parser.parseRecord(indexer, geoNetworkRecord, rootElement, this.mockLogger);

            Assertions.assertEquals(expectedWkt, geoNetworkRecord.getWkt(), "Wrong WKT");
        }
    }

    // Method providing test arguments for testParsePublicationDate
    static Stream<Arguments> provideDateFormatsAndExpectedDateResults() {
        return Stream.of(
                Arguments.of("<gco:DateTime>2024-11-22T06:25:49.269Z</gco:DateTime>", "2024-11-22"),
                Arguments.of("<gco:DateTime>2024-07-10T23:01:17.06Z</gco:DateTime>", "2024-07-10"),
                Arguments.of("<gco:DateTime>2024-11-22T06:31:25.6Z</gco:DateTime>", "2024-11-22"),
                Arguments.of("<gco:DateTime>2024-10-15T14:00:00Z</gco:DateTime>", "2024-10-15"),
                Arguments.of("<gco:DateTime>2024-08-28T22:56:09</gco:DateTime>", "2024-08-28"),
                Arguments.of("<gco:Date>2024-09-03</gco:Date>", "2024-09-03"),
                Arguments.of("<gco:DateTime>2021-08-13</gco:DateTime>", "2021-08-13"),
                Arguments.of("<gco:DateTime>Invalid DateT00:00:00</gco:DateTime>", "2021-01-06"), // Use creation date
                Arguments.of("<gco:Date />", "2021-01-06")  // Use creation date
        );
    }

    @ParameterizedTest
    @MethodSource("provideDateFormatsAndExpectedDateResults")
    void testParsePublishedOn_dateFormats(String dateXML, String expectedDateString) throws IOException, SAXException {
        String filePath = "geonetworkRecords/geonetwork2/iso19139-mcp_65f61d2d-fe4e-48e5-8c6e-fab08450ef75.xml";
        String index = "gn-test";
        String geoNetworkVersion = "3.0";
        String metadataSchema = "iso191139";

        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(filePath)) {
            // Ensure the file was found
            assertNotNull(inputStream, "XML file not found in resources!");

            // Convert InputStream to String
            String xmlAsString = new BufferedReader(new InputStreamReader(inputStream))
                    .lines()
                    .collect(Collectors.joining("\n"));

            String recordXmlString = xmlAsString.replace("<gco:Date>2021-01-07</gco:Date>", dateXML);

            // Re-create the InputStream for parsing
            InputStream reParsedStream = new ByteArrayInputStream(recordXmlString.getBytes());
            Document document = this.xmlParser.parse(reParsedStream);
            Element rootElement = document.getDocumentElement();

            GeoNetworkIndexer indexer = new GeoNetworkIndexer(null, index, index, "http://eatlas-geonetwork/geonetwork", "https://eatlas.org.au/geonetwork", geoNetworkVersion);
            GeoNetworkRecord geoNetworkRecord = new GeoNetworkRecord(indexer, null, metadataSchema, geoNetworkVersion);
            this.parser.parseRecord(indexer, geoNetworkRecord, rootElement, this.mockLogger);

            Assertions.assertEquals(LocalDate.parse(expectedDateString, DateTimeFormatter.ISO_DATE),
                    geoNetworkRecord.getPublishedOn(), "Wrong publishedOn date");
        }
    }

    // Method providing test arguments for testParsePublicationDate
    static Stream<Arguments> provideXmlFileAndExpectedDateResults() {
        return Stream.of(
                Arguments.of("geonetworkRecords/geonetwork2/iso19139-mcp_65f61d2d-fe4e-48e5-8c6e-fab08450ef75.xml", "2021-01-07"), // publication date
                Arguments.of("geonetworkRecords/geonetwork2/iso19139-mcp_87263960-92f0-4836-b8c5-8486660ddfe0.xml", "2011-10-31"), // no publication date, but creation date
                Arguments.of("geonetworkRecords/geonetwork2/iso19139-mcp_65f61d2d-fe4e-48e5-8c6e-fab08450ef75_no-citation-date.xml", "2021-01-18") // no publication or creation date, use metadata timestamp
        );
    }

    @ParameterizedTest
    @MethodSource("provideXmlFileAndExpectedDateResults")
    void testParsePublishedOn_dateTypes(String fileName, String expectedDateString) throws IOException, SAXException {
        String index = "gn-test";
        String geoNetworkVersion = "3.0";
        String metadataSchema = "iso191139";

        try (
                InputStream recordInputStream = getClass().getClassLoader().getResourceAsStream(fileName)
        ) {
            Document document = this.xmlParser.parse(recordInputStream);
            Element rootElement = document.getDocumentElement();

            GeoNetworkIndexer indexer = new GeoNetworkIndexer(null, index, index, "http://eatlas-geonetwork/geonetwork", "https://eatlas.org.au/geonetwork", geoNetworkVersion);
            GeoNetworkRecord geoNetworkRecord = new GeoNetworkRecord(indexer, null, metadataSchema, geoNetworkVersion);
            this.parser.parseRecord(indexer, geoNetworkRecord, rootElement, this.mockLogger);

            Assertions.assertEquals(LocalDate.parse(expectedDateString, DateTimeFormatter.ISO_DATE),
                    geoNetworkRecord.getPublishedOn(), "Wrong publishedOn date");
        }
    }
}
