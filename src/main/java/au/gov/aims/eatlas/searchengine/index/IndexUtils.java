/*
 *  Copyright (C) 2020 Australian Institute of Marine Science
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
package au.gov.aims.eatlas.searchengine.index;

import au.gov.aims.eatlas.searchengine.admin.SearchEngineConfig;
import au.gov.aims.eatlas.searchengine.logger.AbstractLogger;
import au.gov.aims.eatlas.searchengine.client.SearchClient;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class IndexUtils {
    private static final Logger LOGGER = LogManager.getLogger(IndexUtils.class.getName());

    // JDOM tutorial:
    //     https://www.tutorialspoint.com/java_xml/java_dom_parse_document.htm
    private static final DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
    static {
        // Disable the loading of external entities, for security reasons
        IndexUtils.setDocumentBuilderFactoryFeature(
            "http://apache.org/xml/features/disallow-doctype-decl", true);
        IndexUtils.setDocumentBuilderFactoryFeature(
            "http://xml.org/sax/features/external-general-entities", false);
        IndexUtils.setDocumentBuilderFactoryFeature(
            "http://xml.org/sax/features/external-parameter-entities", false);
    }

    public static Map<String, Object> JSONObjectToMap(JSONObject jsonObject) {
        if (jsonObject == null || jsonObject.isEmpty()) {
            return null;
        }

        Map<String, Object> map = new HashMap<>();

        for (String key : jsonObject.keySet()) {
            Object value = jsonObject.opt(key);

            if (value instanceof JSONObject) {
                value = IndexUtils.JSONObjectToMap((JSONObject)value);
            } else if (value instanceof JSONArray) {
                value = IndexUtils.JSONArrayToArray((JSONArray)value);
            }

            map.put(key, value);
        }

        return map;
    }

    public static Object[] JSONArrayToArray(JSONArray jsonArray) {
        if (jsonArray == null || jsonArray.isEmpty()) {
            return null;
        }

        int length = jsonArray.length();
        Object[] array = new Object[length];

        for (int i=0; i<length; i++) {
            Object value = jsonArray.opt(i);

            if (value instanceof JSONObject) {
                value = IndexUtils.JSONObjectToMap((JSONObject)value);
            } else if (value instanceof JSONArray) {
                value = IndexUtils.JSONArrayToArray((JSONArray)value);
            }

            array[i] = value;
        }

        return array;
    }

    // Helper methods to help parsing XML

    private static void setDocumentBuilderFactoryFeature(String feature, boolean value) {
        try {
            IndexUtils.documentBuilderFactory.setFeature(feature, value);
        } catch (ParserConfigurationException ex) {
            // The settings are unlikely to fail...
            LOGGER.error(String.format("Error occurred while setting the XML parser factory's setting: %s",
                feature), ex);
        }
    }
    public static DocumentBuilder getNewXMLParser() throws ParserConfigurationException {
        synchronized (IndexUtils.documentBuilderFactory) {
            return IndexUtils.documentBuilderFactory.newDocumentBuilder();
        }
    }

    // Get the first children that match one of the tag name provided
    public static Element getXMLChild(Node parent, String ... tagNames) {
        List<Element> children = getXMLChildren(parent, tagNames);
        if (children != null && !children.isEmpty()) {
            return children.get(0);
        }

        return null;
    }

    // Get all the children of the first matching tag name provided
    // NOTE: The multiple tagNames is used to support "gmd" and "mcd" records at the same time.
    //     Example: getXMLChildren(parent, "gmd:MD_DataIdentification", "mcp:MD_DataIdentification")
    public static List<Element> getXMLChildren(Node parent, String ... tagNames) {
        List<Element> children = new ArrayList<Element>();

        if (tagNames == null || tagNames.length == 0 ||
                !(parent instanceof Element)) {
            return children;
        }

        Element parentElement = (Element) parent;
        for (String tagName : tagNames) {
            NodeList nameList = parentElement.getElementsByTagName(tagName);
            if (nameList != null) {
                for (int i=0; i<nameList.getLength(); i++) {
                    Node child = nameList.item(i);
                    if (child instanceof Element) {
                        children.add((Element) child);
                    }
                }
                if (!children.isEmpty()) {
                    return children;
                }
            }
        }

        return children;
    }

    // Specific to GeoNetwork
    public static String parseCharacterString(Node node) {
        if (node == null) {
            return null;
        }

        Element child = IndexUtils.getXMLChild(node, "gco:CharacterString");
        return parseText(child);
    }

    public static String parseText(Node node) {
        if (node == null) {
            return null;
        }
        String str = node.getTextContent();
        if (str == null) {
            return null;
        }
        String trimmed = str.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static String parseAttribute(Node node, String attribute) {
        if (node == null) {
            return null;
        }
        NamedNodeMap attributeMap = node.getAttributes();
        if (attributeMap != null) {
            Node attributeNode = attributeMap.getNamedItem(attribute);
            return parseText(attributeNode);
        }

        return null;
    }

    public static JSONObject internalReindex(SearchClient searchClient, boolean full) throws IOException {
        return IndexUtils.internalReindex(searchClient, full, null);
    }

    // Method used for debugging.
    // Use the method signature without the logger
    public static JSONObject internalReindex(SearchClient searchClient, boolean full, AbstractLogger logger) throws IOException {
        SearchEngineConfig config = SearchEngineConfig.getInstance();

        // Reindex
        if (config != null) {
            List<AbstractIndexer<?>> indexers = config.getIndexers();
            if (indexers != null && !indexers.isEmpty()) {
                for (AbstractIndexer<?> indexer : indexers) {
                    if (indexer.isEnabled()) {
                        // If the logger hasn't been defined, use the indexer respective file logger.
                        AbstractLogger fileLogger = null;
                        if (logger == null) {
                            fileLogger = indexer.getFileLogger();
                            fileLogger.clear();
                        }

                        indexer.index(searchClient, full, fileLogger == null ? logger : fileLogger);
                    }
                }
            }
        }

        return new JSONObject()
            .put("status", "success");
    }
}
