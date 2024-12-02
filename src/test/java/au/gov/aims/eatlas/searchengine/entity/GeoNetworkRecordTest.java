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
import au.gov.aims.eatlas.searchengine.entity.geoNetworkParser.ISO19139_parser;
import au.gov.aims.eatlas.searchengine.logger.AbstractLogger;
import au.gov.aims.eatlas.searchengine.logger.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

public class GeoNetworkRecordTest {
    
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
        mocks = MockitoAnnotations.openMocks(this);
        geoNetworkRecord = new GeoNetworkRecord(
                "gn-test",
                "65f61d2d-fe4e-48e5-8c6e-fab08450ef75",
                "iso19139.mcp",
                "3.0");

        // Mock the document's behavior
        when(mockDocument.getDocumentElement()).thenReturn(mockRootElement);

        // Mock the root element's behavior
        doNothing().when(mockRootElement).normalize(); // normalize() does nothing when called

        // Set the mock parser factory
        when(mockParserFactory.getParser("iso19139.mcp")).thenReturn(mockParser);
        geoNetworkRecord.setGeoNetworkParserFactory(mockParserFactory);
    }
    
    @AfterEach
    void tearDown() throws Exception {
        if (mocks != null) {
            mocks.close();
        }
    }

    @Test
    void testParseRecord_NoMetadataSchema() {
        geoNetworkRecord.setMetadataSchema(null);

        geoNetworkRecord.parseRecord("https://eatlas.org.au/geonetwork", mockDocument, mockLogger);

        // Verify logger behavior
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockLogger).addMessage(eq(Level.WARNING), messageCaptor.capture());
        assertTrue(messageCaptor.getValue().contains("has no defined metadata schema."));
    }

    @Test
    void testParseRecord_NoXmlMetadataRecord() {
        geoNetworkRecord.parseRecord("https://eatlas.org.au/geonetwork", null, mockLogger);

        // Verify logger behavior
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockLogger).addMessage(eq(Level.WARNING), messageCaptor.capture());
        assertTrue(messageCaptor.getValue().contains("has no metadata record."));
    }

    @Test
    void testParseRecord_NoRootElement() {
        when(mockDocument.getDocumentElement()).thenReturn(null);
        geoNetworkRecord.parseRecord("https://eatlas.org.au/geonetwork", mockDocument, mockLogger);

        // Verify logger behavior
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockLogger).addMessage(eq(Level.WARNING), messageCaptor.capture());
        assertTrue(messageCaptor.getValue().contains("has no root in its metadata document."));
    }

    @Test
    void testParseRecord_UnsupportedSchema() {
        geoNetworkRecord.setMetadataSchema("unsupported-schema");
        when(mockDocument.getDocumentElement()).thenReturn(mockRootElement);

        geoNetworkRecord.parseRecord("https://eatlas.org.au/geonetwork", mockDocument, mockLogger);

        // Verify logger behavior
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockLogger).addMessage(eq(Level.WARNING), messageCaptor.capture());
        assertTrue(messageCaptor.getValue().contains("unsupported schema"));
    }

    @Test
    void testParseRecord_SupportedSchemaWithParser() {
        when(mockDocument.getDocumentElement()).thenReturn(mockRootElement);

        geoNetworkRecord.parseRecord("https://eatlas.org.au/geonetwork", mockDocument, mockLogger);

        // Verify parser behavior
        verify(mockParser).parseRecord(eq(geoNetworkRecord), eq("https://eatlas.org.au/geonetwork"), 
                eq(mockRootElement), eq(mockLogger));
    }
    
}
