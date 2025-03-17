/*
 *  Copyright (C) 2022 Australian Institute of Marine Science
 *
 *  Contact: Gael Lafond <g.lafond@aims.gov.au>
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package au.gov.aims.eatlas.searchengine.entity;

import au.gov.aims.eatlas.searchengine.entity.geoNetworkParser.AbstractParser;
import au.gov.aims.eatlas.searchengine.entity.geoNetworkParser.GeoNetworkParserFactory;
import au.gov.aims.eatlas.searchengine.index.GeoNetworkIndexer;
import au.gov.aims.eatlas.searchengine.logger.AbstractLogger;
import au.gov.aims.eatlas.searchengine.logger.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class GeoNetworkRecordTest {

    private GeoNetworkIndexer indexer;
    private GeoNetworkRecord geoNetworkRecord;

    @Mock
    private GeoNetworkParserFactory mockParserFactory;

    @Mock
    private AbstractParser mockParser;

    @Mock
    private Document mockDocument;

    @Mock
    private Element mockRootElement;

    @Mock
    private AbstractLogger mockLogger;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        this.mocks = MockitoAnnotations.openMocks(this);

        String index = "gn-test";
        this.indexer = new GeoNetworkIndexer(null, index, index, "http://eatlas-geonetwork/geonetwork", "https://eatlas.org.au/geonetwork", "3.0");

        this.geoNetworkRecord = new GeoNetworkRecord(
                this.indexer,
                "65f61d2d-fe4e-48e5-8c6e-fab08450ef75",
                "iso19139.mcp",
                "3.0");

        // Mock the document's behavior
        Mockito.when(this.mockDocument.getDocumentElement()).thenReturn(this.mockRootElement);

        // Mock the root element's behavior
        Mockito.doNothing().when(this.mockRootElement).normalize(); // normalize() does nothing when called

        // Set the mock parser factory
        Mockito.when(this.mockParserFactory.getParser("iso19139.mcp")).thenReturn(this.mockParser);
        this.geoNetworkRecord.setGeoNetworkParserFactory(this.mockParserFactory);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (this.mocks != null) {
            this.mocks.close();
        }
    }

    @Test
    void testParseRecord_NoMetadataSchema() {
        this.geoNetworkRecord.setMetadataSchema(null);

        this.geoNetworkRecord.parseRecord(this.mockDocument, this.mockLogger);

        // Verify logger behavior
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        Mockito.verify(this.mockLogger).addMessage(Mockito.eq(Level.WARNING), messageCaptor.capture());
        Assertions.assertTrue(messageCaptor.getValue().contains("has no defined metadata schema."));
    }

    @Test
    void testParseRecord_NoXmlMetadataRecord() {
        this.geoNetworkRecord.parseRecord(null, this.mockLogger);

        // Verify logger behavior
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        Mockito.verify(this.mockLogger).addMessage(Mockito.eq(Level.WARNING), messageCaptor.capture());
        Assertions.assertTrue(messageCaptor.getValue().contains("has no metadata record."));
    }

    @Test
    void testParseRecord_NoRootElement() {
        Mockito.when(this.mockDocument.getDocumentElement()).thenReturn(null);
        this.geoNetworkRecord.parseRecord(this.mockDocument, this.mockLogger);

        // Verify logger behavior
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        Mockito.verify(this.mockLogger).addMessage(Mockito.eq(Level.WARNING), messageCaptor.capture());
        Assertions.assertTrue(messageCaptor.getValue().contains("has no root in its metadata document."));
    }

    @Test
    void testParseRecord_UnsupportedSchema() {
        this.geoNetworkRecord.setMetadataSchema("unsupported-schema");
        Mockito.when(this.mockDocument.getDocumentElement()).thenReturn(this.mockRootElement);

        this.geoNetworkRecord.parseRecord(this.mockDocument, this.mockLogger);

        // Verify logger behavior
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        Mockito.verify(this.mockLogger).addMessage(Mockito.eq(Level.WARNING), messageCaptor.capture());
        Assertions.assertTrue(messageCaptor.getValue().contains("unsupported schema"));
    }

    @Test
    void testParseRecord_SupportedSchemaWithParser() {
        Mockito.when(this.mockDocument.getDocumentElement()).thenReturn(this.mockRootElement);

        this.geoNetworkRecord.parseRecord(this.mockDocument, this.mockLogger);

        // Verify parser behavior
        Mockito.verify(this.mockParser).parseRecord(
                Mockito.eq(this.indexer),
                Mockito.eq(this.geoNetworkRecord),
                Mockito.eq(this.mockRootElement),
                Mockito.eq(this.mockLogger));
    }

}
