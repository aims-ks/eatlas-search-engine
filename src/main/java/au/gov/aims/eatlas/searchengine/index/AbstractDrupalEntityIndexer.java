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
package au.gov.aims.eatlas.searchengine.index;

import au.gov.aims.eatlas.searchengine.HttpClient;
import au.gov.aims.eatlas.searchengine.logger.AbstractLogger;
import au.gov.aims.eatlas.searchengine.client.SearchClient;
import au.gov.aims.eatlas.searchengine.entity.AbstractDrupalEntity;
import au.gov.aims.eatlas.searchengine.entity.Entity;
import au.gov.aims.eatlas.searchengine.logger.Level;
import au.gov.aims.eatlas.searchengine.logger.Message;
import au.gov.aims.eatlas.searchengine.rest.ImageCache;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import org.apache.http.client.utils.URIBuilder;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.json.JSONArray;
import org.json.JSONObject;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.geojson.GeoJsonReader;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public abstract class AbstractDrupalEntityIndexer<E extends Entity> extends AbstractIndexer<E> {
    private static final int THREAD_POOL_SIZE = 10;

    // Number of Drupal entity to index per page.
    //     Larger number = less request, more RAM
    private static final int INDEX_PAGE_SIZE = 50;

    private String drupalUrl;
    private String drupalVersion;
    private String drupalEntityType; // Entity type. Example: node, media, user, etc
    private String drupalBundleId; // Content type (node type) or media type. Example: article, image, etc
    private String drupalPreviewImageField;
    private String drupalIndexedFields;
    private String drupalGeoJSONField;

    public AbstractDrupalEntityIndexer(
            HttpClient httpClient,
            String index,
            String drupalUrl,
            String drupalVersion,
            String drupalEntityType,
            String drupalBundleId,
            String drupalPreviewImageField,
            String drupalIndexedFields,
            String drupalGeoJSONField) {

        super(httpClient, index);
        this.drupalUrl = drupalUrl;
        this.drupalVersion = drupalVersion;
        this.drupalEntityType = drupalEntityType;
        this.drupalBundleId = drupalBundleId;
        this.drupalPreviewImageField = drupalPreviewImageField;
        this.drupalIndexedFields = drupalIndexedFields;
        this.drupalGeoJSONField = drupalGeoJSONField;
    }

    public abstract E createDrupalEntity(JSONObject jsonApiEntity, Map<String, JSONObject> jsonIncluded, AbstractLogger logger);
    public abstract E getIndexedDrupalEntity(SearchClient searchClient, String id, AbstractLogger logger);
    public abstract String getHarvestSort(boolean fullHarvest);

    protected JSONObject getJsonBase() {
        return super.getJsonBase()
            .put("drupalUrl", this.drupalUrl)
            .put("drupalVersion", this.drupalVersion)
            .put("drupalPreviewImageField", this.drupalPreviewImageField)
            .put("drupalIndexedFields", this.drupalIndexedFields)
            .put("drupalGeoJSONField", this.drupalGeoJSONField);
    }

    @Override
    public boolean validate() {
        if (!super.validate()) {
            return false;
        }
        if (this.drupalUrl == null || this.drupalUrl.isEmpty()) {
            return false;
        }
        if (this.drupalEntityType == null || this.drupalEntityType.isEmpty()) {
            return false;
        }
        if (this.drupalBundleId == null || this.drupalBundleId.isEmpty()) {
            return false;
        }
        return true;
    }

    @Override
    protected E harvestEntity(SearchClient searchClient, String entityUUID, AbstractLogger logger) {
        // First, request the node without includes (to find out the node's structure)
        URIBuilder uriBuilder = this.buildDrupalApiEntityUrl(entityUUID, logger);
        if (uriBuilder == null) {
            return null;
        }

        JSONObject jsonResponse = this.getJsonResponse(entityUUID, uriBuilder, logger);
        return this.harvestEntity(searchClient, jsonResponse, entityUUID, logger);
    }

    protected E harvestEntity(SearchClient searchClient, JSONObject jsonResponse, String entityUUID, AbstractLogger logger) {
        E drupalEntity = null;
        if (jsonResponse != null) {
            JSONObject jsonApiEntity = jsonResponse.optJSONObject("data");
            List<String> includes = this.getIncludes(jsonApiEntity);

            if (includes == null || includes.isEmpty()) {
                // No field needs to be included in the query.
                // No need to send another query, just use the previous response. It contains all the information we need.
                drupalEntity = this.harvestEntityWithIncludes(searchClient, jsonResponse, logger);
            } else {
                // Now that we know what fields need to be included in the request,
                // request the node again, with the includes.
                URIBuilder uriWithIncludesBuilder = this.buildDrupalApiEntityUrlWithIncludes(entityUUID, includes, logger);
                if (uriWithIncludesBuilder == null) {
                    return null;
                }

                JSONObject jsonResponseWithIncludes = this.getJsonResponse(entityUUID, uriWithIncludesBuilder, logger);
                drupalEntity = this.harvestEntityWithIncludes(searchClient, jsonResponseWithIncludes, logger);
            }
        }

        return drupalEntity;
    }


    protected JSONObject getJsonResponse(String entityUUID, URIBuilder uriBuilder, AbstractLogger logger) {
        HttpClient httpClient = this.getHttpClient();
        String url;
        try {
            url = uriBuilder.build().toURL().toString();
        } catch(Exception ex) {
            // Should not happen
            logger.addMessage(Level.ERROR,
                    String.format("Invalid Drupal URL. Exception occurred while building a URL starting with: %s", this.getDrupalApiUrlBase()), ex);
            return null;
        }

        HttpClient.Response response = null;
        try {
            response = httpClient.getRequest(url, logger);
        } catch(Exception ex) {
            logger.addMessage(Level.WARNING, String.format("Exception occurred while requesting the Drupal %s, type: %s, UUID: %s",
                    this.getDrupalEntityType(), this.getDrupalBundleId(), entityUUID), ex);
        }

        if (response != null) {
            JSONObject jsonResponse = response.jsonBody();
            if (jsonResponse != null && !jsonResponse.isEmpty()) {
                JSONArray jsonErrors = jsonResponse.optJSONArray("errors");
                if (jsonErrors != null && !jsonErrors.isEmpty()) {
                    this.handleDrupalApiErrors(jsonErrors, logger);
                } else {
                    return jsonResponse;
                }
            }
        }

        return null;
    }

    protected E harvestEntityWithIncludes(SearchClient searchClient, JSONObject jsonResponse, AbstractLogger logger) {
        JSONObject jsonApiEntity = jsonResponse.optJSONObject("data");
        JSONArray jsonIncludedArray = jsonResponse.optJSONArray("included");

        Map<String, JSONObject> jsonIncluded = parseJsonIncluded(jsonIncludedArray);

        E drupalEntity = this.createDrupalEntity(jsonApiEntity, jsonIncluded, logger);

        if (this.parseJsonDrupalEntity(searchClient, jsonApiEntity, jsonIncluded, drupalEntity, logger)) {
            return drupalEntity;
        }

        return null;
    }

    public static Map<String, JSONObject> parseJsonIncluded(JSONArray jsonIncludedArray) {
        Map<String, JSONObject> jsonIncluded = new HashMap<>();
        if (jsonIncludedArray != null) {
            for (int i=0; i<jsonIncludedArray.length(); i++) {
                JSONObject jsonIncludedEl = jsonIncludedArray.optJSONObject(i);
                if (jsonIncludedEl != null) {
                    String uuid = jsonIncludedEl.optString("id", null);
                    if (uuid != null) {
                        jsonIncluded.put(uuid, jsonIncludedEl);
                    }
                }
            }
        }
        return jsonIncluded;
    }

    // Overwrite in subclasses when more work needs to be done.
    // Return false to prevent the entity from been indexed.
    protected boolean parseJsonDrupalEntity(
            SearchClient searchClient,
            JSONObject jsonApiEntity,
            Map<String, JSONObject> jsonIncluded,
            E drupalEntity,
            AbstractLogger logger) {

        this.updateThumbnail(searchClient, jsonApiEntity, jsonIncluded, drupalEntity, logger);
        this.updateGeoJSON(searchClient, jsonApiEntity, jsonIncluded, drupalEntity, logger);
        return true;
    }

    @Override
    protected void internalIndex(SearchClient searchClient, Long lastHarvested, AbstractLogger logger) {
        HttpClient httpClient = this.getHttpClient();
        boolean fullHarvest = lastHarvested == null;
        long harvestStart = System.currentTimeMillis();

        Set<String> usedThumbnails = null;
        if (fullHarvest) {
            usedThumbnails = Collections.synchronizedSet(new HashSet<String>());

            // There is no easy way to know how many entities needs indexing.
            // Use the total number of entities we have in the index, by looking at the last indexed count in the state.
            IndexerState state = this.getState();
            Long total = null;
            if (state != null) {
                total = state.getCount();
            }
            this.setTotal(total);
        }

        ThreadPoolExecutor threadPool = (ThreadPoolExecutor) Executors.newFixedThreadPool(THREAD_POOL_SIZE);

        long totalFound = 0;
        int entityFound, page = 0;
        boolean stop = false;
        boolean crashed = false;
        String sort = this.getHarvestSort(fullHarvest);

        URIBuilder uriBuilder = this.buildDrupalApiPageUrl(page, sort, logger);
        String url = null;
        try {
            url = uriBuilder.build().toURL().toString();
        } catch(Exception ex) {
            // Should not happen
            logger.addMessage(Level.ERROR,
                    String.format("Invalid Drupal URL. Exception occurred while building a URL starting with: %s", this.getDrupalApiUrlBase()), ex);
            return;
        }

        do {
            entityFound = 0;
            HttpClient.Response response = null;
            try {
                response = httpClient.getRequest(url, logger);
            } catch(Exception ex) {
                if (!crashed) {
                    logger.addMessage(Level.WARNING, String.format("Exception occurred while requesting a page of Drupal %s, type: %s",
                            this.getDrupalEntityType(), this.getDrupalBundleId()), ex);
                }
                crashed = true;
            }
            JSONObject jsonResponse = response == null ? null : response.jsonBody();
            if (jsonResponse != null && !jsonResponse.isEmpty()) {
                JSONArray jsonErrors = jsonResponse.optJSONArray("errors");
                if (jsonErrors != null && !jsonErrors.isEmpty()) {
                    this.handleDrupalApiErrors(jsonErrors, logger);
                    crashed = true;
                } else {
                    JSONArray jsonEntities = jsonResponse.optJSONArray("data");

                    entityFound = jsonEntities == null ? 0 : jsonEntities.length();
                    totalFound += entityFound;
                    if (fullHarvest) {
                        if (this.getTotal() != null && this.getTotal() < totalFound) {
                            this.setTotal(totalFound);
                        }
                    } else {
                        this.setTotal(totalFound);
                    }

                    for (int i=0; i<entityFound; i++) {
                        JSONObject jsonApiEntity = jsonEntities.optJSONObject(i);

                        // Stop the harvest as soon as we find a Drupal node with a last modified date older
                        //     than the last harvest.
                        //     The nodes are ordered by modified dates.
                        // NOTE: Drupal last modified date (aka changed date) are rounded to second,
                        //     and can be a bit off. Use a 10s margin for safety.
                        Long lastModified = AbstractDrupalEntityIndexer.parseLastModified(jsonApiEntity);
                        if (!fullHarvest &&
                                lastHarvested != null && lastModified != null &&
                                lastModified < lastHarvested + 10000) {

                            stop = true;
                            break;
                        }

                        Thread thread = new DrupalEntityIndexerThread(
                                searchClient, logger, jsonApiEntity, usedThumbnails,
                                page+1, i+1, entityFound);

                        threadPool.execute(thread);
                    }
                }

                // Get the URL of the next page
                // NOTE: Use links/next/href. If not present, end as been reached
                JSONObject linksJson = jsonResponse.optJSONObject("links");
                JSONObject nextJson = linksJson == null ? null : linksJson.optJSONObject("next");
                url = nextJson == null ? null : nextJson.optString("href", null);
            } else {
                stop = true;
            }
            page++;

        // while:
        //     !stop: Stop explicitly set to true, because we found an entity that was not modified since last harvest.
        //     !crashed: An exception occurred or an error message was sent by Drupal.
        //     url != null: No next URL found in the JSON response.
        } while(!stop && !crashed && url != null);

        threadPool.shutdown();
        try {
            threadPool.awaitTermination(1, TimeUnit.HOURS);
        } catch(InterruptedException ex) {
            logger.addMessage(Level.ERROR, String.format("The indexation for %s type %s was interrupted",
                    this.getDrupalEntityType(), this.getDrupalBundleId()), ex);
        }

        // Only cleanup when we are doing a full harvest
        if (!crashed && fullHarvest) {
            this.cleanUp(searchClient, harvestStart, usedThumbnails, String.format("Drupal %s type %s",
                    this.getDrupalEntityType(), this.getDrupalBundleId()), logger);
        }
    }

    public String getDrupalApiUrlBase() {
        return String.format("%s/jsonapi/%s/%s", this.getDrupalUrl(), this.getDrupalEntityType(), this.getDrupalBundleId());
    }

    public URIBuilder buildDrupalApiEntityUrl(String entityUUID, AbstractLogger logger) {
        String urlBase = this.getDrupalApiUrlBase();
        URIBuilder uriBuilder;
        try {
            uriBuilder = new URIBuilder(String.format("%s/%s", urlBase, entityUUID));
        } catch(URISyntaxException ex) {
            logger.addMessage(Level.ERROR,
                    String.format("Invalid Drupal URL. Exception occurred while building the URL: %s", urlBase), ex);
            return null;
        }
        uriBuilder.setParameter("filter[status]", "1");

        return uriBuilder;
    }

    // sort
    //   For media:
    //     To get all: drupal_internal__mid
    //     To get latest: -changed,drupal_internal__mid
    //   For nodes:
    //     To get all: drupal_internal__nid
    //     To get latest: -changed,drupal_internal__nid
    public URIBuilder buildDrupalApiPageUrl(int page, String sort, AbstractLogger logger) {
        // Ordered by lastModified (changed).
        // If the parameter lastHarvested is set, harvest entities (nodes)
        //     until we found an entity that was last modified before the lastHarvested parameter.
        // Example:
        //     "http://localhost:9090/jsonapi/node/article?include=field_image&sort=-changed&page[limit]=100&page[offset]=0&filter[status]=1&filter[field_prepress]=0"
        //     "http://localhost:9090/jsonapi/media/image?include=thumbnail&sort=-changed&page[limit]=100&page[offset]=0&filter[status]=1&filter[field_private_media_page]=0"
        // Filter out unpublished entities (only useful when logged in): filter[status]=1
        String urlBase = this.getDrupalApiUrlBase();
        URIBuilder uriBuilder;
        try {
            uriBuilder = new URIBuilder(urlBase);
        } catch(URISyntaxException ex) {
            logger.addMessage(Level.ERROR,
                    String.format("Invalid Drupal URL. Exception occurred while building the URL: %s", urlBase), ex);
            return null;
        }

        if (sort != null) {
            uriBuilder.setParameter("sort", "-changed");
        }
        uriBuilder.setParameter("page[limit]", String.format("%d", INDEX_PAGE_SIZE));
        uriBuilder.setParameter("page[offset]", String.format("%d", page * INDEX_PAGE_SIZE));
        uriBuilder.setParameter("filter[status]", "1");

        return uriBuilder;
    }

    protected List<String> getIncludes(JSONObject jsonApiEntity) {
        List<String> includes = new ArrayList<>();

        // Parse jsonApiEntity to find out which fields need to be included in the query

        // Preview image field
        String previewImageFieldType = AbstractDrupalEntityIndexer.getPreviewImageType(jsonApiEntity, this.getDrupalPreviewImageField());
        if (previewImageFieldType != null) {
            if ("media--image".equals(previewImageFieldType)) {
                // include=field_preview,field_preview.field_media_image
                includes.add(this.drupalPreviewImageField);
                includes.add(this.drupalPreviewImageField + ".field_media_image");
            } else if ("file--file".equals(previewImageFieldType)) {
                // include=field_preview
                includes.add(this.drupalPreviewImageField);
            }
        }

        // Indexed fields (i.e. node body)
        List<String> indexedFields = AbstractDrupalEntityIndexer.splitIndexedFields(this.getDrupalIndexedFields());
        if (!indexedFields.isEmpty()) {
            for (String indexedField : indexedFields) {
                String indexedFieldType = AbstractDrupalEntityIndexer.getFieldType(jsonApiEntity, indexedField);
                if (indexedFieldType != null) {
                    if ("paragraph".equals(indexedFieldType)) {
                        includes.add(indexedField);
                    }
                }
            }
        }

        return includes;
    }

    public URIBuilder buildDrupalApiEntityUrlWithIncludes(String entityUUID, List<String> includes, AbstractLogger logger) {
        String urlBase = this.getDrupalApiUrlBase();
        URIBuilder uriBuilder;
        try {
            uriBuilder = new URIBuilder(String.format("%s/%s", urlBase, entityUUID));
        } catch(URISyntaxException ex) {
            logger.addMessage(Level.ERROR,
                    String.format("Invalid Drupal URL. Exception occurred while building the URL: %s", urlBase), ex);
            return null;
        }

        if (includes != null && !includes.isEmpty()) {
            // Add the include parameter.
            // Example:
            //     include=field_preview,field_preview.field_media_image
            uriBuilder.setParameter("include", String.join(",", includes));
        }

        uriBuilder.setParameter("filter[status]", "1");

        return uriBuilder;
    }

    public void handleDrupalApiErrors(JSONArray jsonErrors, AbstractLogger logger) {
        // Handle errors returned by Drupal.
        for (int i=0; i<jsonErrors.length(); i++) {
            JSONObject jsonError = jsonErrors.optJSONObject(i);
            String errorTitle = jsonError.optString("title", "Untitled error");
            String errorDetail = jsonError.optString("detail", "No details");

            logger.addMessage(Level.ERROR,
                    String.format("An error occurred during the indexation of %s type %s - %s: %s",
                            this.getDrupalEntityType(), this.getDrupalBundleId(),
                            errorTitle, errorDetail));
        }
    }

    public static String getPreviewImageType(JSONObject jsonApiEntity, String previewImageField) {
        if (previewImageField == null || previewImageField.isEmpty()) {
            return null;
        }
        // Returns: relationships.field_preview.data.type
        JSONObject jsonRelationships = jsonApiEntity == null ? null : jsonApiEntity.optJSONObject("relationships");
        JSONObject jsonRelFieldImage = jsonRelationships == null ? null : jsonRelationships.optJSONObject(previewImageField);
        JSONObject jsonRelFieldImageData = jsonRelFieldImage == null ? null : jsonRelFieldImage.optJSONObject("data");
        return jsonRelFieldImageData == null ? null : jsonRelFieldImageData.optString("type", null);
    }

    public static String getPreviewImageUUID(JSONObject jsonApiEntity, String previewImageField) {
        if (previewImageField == null || previewImageField.isEmpty()) {
            return null;
        }
        // Returns: relationships.field_preview.data.id
        JSONObject jsonRelationships = jsonApiEntity == null ? null : jsonApiEntity.optJSONObject("relationships");
        JSONObject jsonRelFieldImage = jsonRelationships == null ? null : jsonRelationships.optJSONObject(previewImageField);
        JSONObject jsonRelFieldImageData = jsonRelFieldImage == null ? null : jsonRelFieldImage.optJSONObject("data");
        return jsonRelFieldImageData == null ? null : jsonRelFieldImageData.optString("id", null);
    }

    protected List<String> getIndexedFieldsContent(JSONObject jsonApiEntity, Map<String, JSONObject> jsonIncluded) {
        // Indexed fields (i.e. body)
        List<String> textChunks = new ArrayList<>();
        List<String> indexedFields = AbstractDrupalEntityIndexer.splitIndexedFields(this.getDrupalIndexedFields());
        if (!indexedFields.isEmpty()) {
            JSONObject jsonAttributes = jsonApiEntity.optJSONObject("attributes");

            for (String indexedField : indexedFields) {
                // Standard body field
                if ("body".equals(indexedField)) {
                    JSONObject jsonBody = jsonAttributes == null ? null : jsonAttributes.optJSONObject(indexedField);
                    if (jsonBody != null) {
                        textChunks.add(HttpClient.extractHTMLTextContent(jsonBody.optString("processed", null)));
                    }
                } else {
                    // Other fields
                    String fieldType = AbstractDrupalEntityIndexer.getFieldType(jsonApiEntity, indexedField);
                    if (fieldType == null) {
                        Object jsonField = null;
                        // Look for the field in attributes
                        // NOTE: Media fields tends to be in attributes. Node fields tends to be in relationships.
                        if (jsonAttributes != null) {
                            jsonField = jsonAttributes.opt(indexedField);
                        }
                        // Look for the field in relationships
                        if (jsonField == null) {
                            JSONObject jsonRelationships = jsonApiEntity.optJSONObject("relationships");
                            jsonField = jsonRelationships == null ? null : jsonRelationships.opt(indexedField);
                        }
                        if (jsonField != null) {
                            List<String> texts = this.parseFieldTexts(jsonField);
                            if (texts != null && !texts.isEmpty()) {
                                textChunks.addAll(texts);
                            }
                        }
                    } else if ("paragraph".equals(fieldType)) {
                        // The field is type Paragraphs.
                        // It contains an array of Paragraph fields.
                        // Loop through each paragraph field, extract the texts and add them to the list of chunks.
                        List<String> paragraphsTexts = this.parseParagraphsField(jsonApiEntity, jsonIncluded, indexedField);
                        if (paragraphsTexts != null && !paragraphsTexts.isEmpty()) {
                            textChunks.addAll(paragraphsTexts);
                        }
                    }
                }
            }
        }

        return textChunks;
    }

    private List<String> parseParagraphsField(JSONObject jsonApiEntity, Map<String, JSONObject> jsonIncluded, String field) {
        List<String> texts = new ArrayList<>();

        // Returns: relationships.field_preview.data.id
        JSONObject jsonRelationships = jsonApiEntity.optJSONObject("relationships");
        JSONObject jsonRelField = jsonRelationships == null ? null : jsonRelationships.optJSONObject(field);
        JSONArray jsonRelFieldDataArray = jsonRelField == null ? null : jsonRelField.optJSONArray("data");
        if (jsonRelFieldDataArray != null) {
            for (int i=0; i<jsonRelFieldDataArray.length(); i++) {
                JSONObject jsonRelFieldData = jsonRelFieldDataArray.optJSONObject(i);
                String jsonRelFieldDataUUID = jsonRelFieldData == null ? null : jsonRelFieldData.optString("id", null);
                List<String> paragraphTexts = this.parseParagraphFromJsonIncluded(jsonRelFieldDataUUID, jsonIncluded);
                if (paragraphTexts != null && !paragraphTexts.isEmpty()) {
                    texts.addAll(paragraphTexts);
                }
            }
        }

        return texts;
    }

    private List<String> parseParagraphFromJsonIncluded(String paragraphUUID, Map<String, JSONObject> jsonIncluded) {
        List<String> texts = new ArrayList<>();

        JSONObject jsonParagraph = jsonIncluded.get(paragraphUUID);
        if (jsonParagraph != null) {
            JSONObject jsonParAttributes = jsonParagraph.optJSONObject("attributes");
            if (jsonParAttributes != null) {
                for (String key : jsonParAttributes.keySet()) {
                    if (key != null && key.startsWith("field_")) {
                        List<String> paragraphFieldTexts = this.parseFieldTexts(jsonParAttributes.opt(key));
                        if (paragraphFieldTexts != null && !paragraphFieldTexts.isEmpty()) {
                            texts.addAll(paragraphFieldTexts);
                        }
                    }
                }
            }
        }

        return texts;
    }

    private List<String> parseFieldTexts(Object fieldObj) {
        List<String> texts = new ArrayList<>();

        if (fieldObj instanceof JSONArray) {
            JSONArray fieldArray = (JSONArray)fieldObj;
            for (int i=0; i<fieldArray.length(); i++) {
                Object fieldSubObj = fieldArray.opt(i);
                texts.addAll(this.parseFieldTexts(fieldSubObj));
            }

        } else if (fieldObj instanceof JSONObject) {
            JSONObject jsonField = (JSONObject)fieldObj;
            String ckeditorText = jsonField.optString("processed", null);
            if (ckeditorText != null) {
                texts.add(HttpClient.extractHTMLTextContent(ckeditorText));
            }

        } else if (fieldObj instanceof String) {
            texts.add((String)fieldObj);
        }

        return texts;
    }

    public static String getFieldType(JSONObject jsonApiEntity, String field) {
        if (field == null || field.isEmpty()) {
            return null;
        }
        // Returns null if: relationships.field_body == null
        JSONObject jsonRelationships = jsonApiEntity == null ? null : jsonApiEntity.optJSONObject("relationships");
        JSONObject jsonRelField = jsonRelationships == null ? null : jsonRelationships.optJSONObject(field);
        Object jsonRelFieldDataObj = jsonRelField == null ? null : jsonRelField.opt("data");
        if (jsonRelFieldDataObj == null) {
            return null;
        }

        // Type: Paragraph
        if (jsonRelFieldDataObj instanceof JSONArray) {
            JSONArray jsonRelFieldDataArray = (JSONArray)jsonRelFieldDataObj;
            if (jsonRelFieldDataArray.isEmpty()) {
                return null;
            }
            JSONObject jsonRelFieldDataEl = jsonRelFieldDataArray.optJSONObject(0);
            String jsonRelFieldDataElType = jsonRelFieldDataEl.optString("type", null);
            if (jsonRelFieldDataElType != null && jsonRelFieldDataElType.startsWith("paragraph--")) {
                return "paragraph";
            }
        }

        return null;
    }

    public static String findPreviewImageRelativePath(String previewImageFieldType, String imageUUID, Map<String, JSONObject> jsonIncluded) {
        if (imageUUID == null || jsonIncluded == null) {
            return null;
        }

        JSONObject jsonImage = jsonIncluded.get(imageUUID);
        if (jsonImage != null) {
            if ("media--image".equals(previewImageFieldType)) {
                // File the file UUID associated with the media UUID,
                // then resolve the URL of the file UUID using recursion.
                JSONObject jsonIncludeRelationships = jsonImage.optJSONObject("relationships");
                JSONObject jsonIncludeRelationshipsFieldMediaImage = jsonIncludeRelationships == null ? null : jsonIncludeRelationships.optJSONObject("field_media_image");
                JSONObject jsonIncludeRelationshipsFieldMediaImageData = jsonIncludeRelationshipsFieldMediaImage == null ? null : jsonIncludeRelationshipsFieldMediaImage.optJSONObject("data");
                String fileUUID = jsonIncludeRelationshipsFieldMediaImageData == null ? null : jsonIncludeRelationshipsFieldMediaImageData.optString("id", null);
                return AbstractDrupalEntityIndexer.findPreviewImageRelativePath("file", fileUUID, jsonIncluded);
            } else {
                JSONObject jsonIncludeAttributes = jsonImage.optJSONObject("attributes");
                JSONObject jsonIncludeAttributesUri = jsonIncludeAttributes == null ? null : jsonIncludeAttributes.optJSONObject("uri");
                return jsonIncludeAttributesUri == null ? null : jsonIncludeAttributesUri.optString("url", null);
            }
        }

        return null;
    }

    public static List<String> splitIndexedFields(String indexedFieldsStr) {
        List<String> indexedFieldList = new ArrayList<String>();
        if (indexedFieldsStr != null && !indexedFieldsStr.isEmpty()) {
            for (String indexedField : indexedFieldsStr.split(",")) {
                indexedField = indexedField.trim();
                if (!indexedField.isEmpty()) {
                    indexedFieldList.add(indexedField);
                }
            }
        }
        return indexedFieldList;
    }

    public static Long parseLastModified(JSONObject jsonApiEntity) {
        JSONObject jsonAttributes = jsonApiEntity.optJSONObject("attributes");
        String changedDateStr = jsonAttributes == null ? null : jsonAttributes.optString("changed", null);
        if (changedDateStr != null && !changedDateStr.isEmpty()) {
            DateTimeFormatter dateParser = ISODateTimeFormat.dateTimeNoMillis();
            DateTime changedDate = dateParser.parseDateTime(changedDateStr);
            if (changedDate != null) {
                return changedDate.getMillis();
            }
        }

        return null;
    }

    public String getDrupalUrl() {
        return this.drupalUrl;
    }

    public void setDrupalUrl(String drupalUrl) {
        this.drupalUrl = drupalUrl;
    }

    public String getDrupalVersion() {
        return this.drupalVersion;
    }

    public void setDrupalVersion(String drupalVersion) {
        this.drupalVersion = drupalVersion;
    }

    public String getDrupalEntityType() {
        return this.drupalEntityType;
    }

    public void setDrupalEntityType(String drupalEntityType) {
        this.drupalEntityType = drupalEntityType;
    }

    public String getDrupalBundleId() {
        return this.drupalBundleId;
    }

    public void setDrupalBundleId(String drupalBundleId) {
        this.drupalBundleId = drupalBundleId;
    }

    public String getDrupalPreviewImageField() {
        return this.drupalPreviewImageField;
    }

    public void setDrupalPreviewImageField(String drupalPreviewImageField) {
        this.drupalPreviewImageField = drupalPreviewImageField;
    }

    public String getDrupalIndexedFields() {
        return this.drupalIndexedFields;
    }

    public void setDrupalIndexedFields(String drupalIndexedFields) {
        this.drupalIndexedFields = drupalIndexedFields;
    }

    public String getDrupalGeoJSONField() {
        return this.drupalGeoJSONField;
    }

    public void setDrupalGeoJSONField(String drupalGeoJSONField) {
        this.drupalGeoJSONField = drupalGeoJSONField;
    }

    public void updateThumbnail(SearchClient searchClient, JSONObject jsonApiEntity, Map<String, JSONObject> jsonIncluded, E drupalEntity, AbstractLogger logger) {
        HttpClient httpClient = this.getHttpClient();
        URL baseUrl = AbstractDrupalEntity.getDrupalBaseUrl(jsonApiEntity, logger);
        String previewImageFieldType = AbstractDrupalEntityIndexer.getPreviewImageType(jsonApiEntity, this.getDrupalPreviewImageField());
        if (previewImageFieldType != null && baseUrl != null && this.getDrupalPreviewImageField() != null) {
            String previewImageUUID = AbstractDrupalEntityIndexer.getPreviewImageUUID(jsonApiEntity, this.getDrupalPreviewImageField());
            if (previewImageUUID != null) {
                String previewImageRelativePath = AbstractDrupalEntityIndexer.findPreviewImageRelativePath(previewImageFieldType, previewImageUUID, jsonIncluded);
                if (previewImageRelativePath != null) {
                    URL thumbnailUrl = null;
                    try {
                        thumbnailUrl = new URL(baseUrl, previewImageRelativePath);
                    } catch(Exception ex) {
                        logger.addMessage(Level.WARNING,
                                String.format("Exception occurred while creating a thumbnail URL for Drupal %s type %s, id: %s",
                                        this.getDrupalEntityType(),
                                        this.getDrupalBundleId(),
                                        drupalEntity.getId()), ex);
                    }
                    drupalEntity.setThumbnailUrl(thumbnailUrl);

                    // Create the thumbnail if it's missing or outdated
                    E oldEntity = this.getIndexedDrupalEntity(searchClient, drupalEntity.getId(), logger);
                    if (drupalEntity.isThumbnailOutdated(oldEntity, this.getSafeThumbnailTTL(), this.getSafeBrokenThumbnailTTL(), logger)) {
                        try {
                            File cachedThumbnailFile = ImageCache.cache(httpClient, thumbnailUrl, this.getIndex(), drupalEntity.getId(), logger);
                            if (cachedThumbnailFile != null) {
                                drupalEntity.setCachedThumbnailFilename(cachedThumbnailFile.getName());
                            }
                        } catch(Exception ex) {
                            logger.addMessage(Level.WARNING,
                                    String.format("Exception occurred while creating a thumbnail for Drupal %s type %s, id: %s",
                                            AbstractDrupalEntityIndexer.this.getDrupalEntityType(),
                                            AbstractDrupalEntityIndexer.this.getDrupalBundleId(),
                                            drupalEntity.getId()), ex);
                        }
                        drupalEntity.setThumbnailLastIndexed(System.currentTimeMillis());
                    } else {
                        drupalEntity.useCachedThumbnail(oldEntity);
                    }
                }
            }
        }
    }

    public void updateGeoJSON(SearchClient searchClient, JSONObject jsonApiEntity, Map<String, JSONObject> jsonIncluded, E drupalEntity, AbstractLogger logger) {
        if (this.getDrupalGeoJSONField() != null) {
            // Extract WKT from jsonApiEntity (node / media)
            JSONObject jsonAttributes = jsonApiEntity == null ? null : jsonApiEntity.optJSONObject("attributes");
            String geoJSON = jsonAttributes == null ? null : jsonAttributes.optString(this.getDrupalGeoJSONField(), null);

            if (geoJSON != null) {
                geoJSON = geoJSON.trim();
                if (geoJSON.isEmpty()) {
                    geoJSON = null;
                }
            }

            // Parse the GeoJSON into a JTS Geometry using JTS IO
            Geometry geometry = null;
            if (geoJSON != null) {
                GeoJsonReader reader = new GeoJsonReader();
                try {
                    geometry = reader.read(geoJSON);
                } catch(ParseException ex) {
                    Message message = logger.addMessage(Level.WARNING,
                            "Exception while parsing GeoJSON",
                            ex);
                    message.addDetail(geoJSON);
                }
            }

            try {
                if (geometry == null) {
                    drupalEntity.setWktAndAttributes(AbstractIndexer.DEFAULT_WKT);
                } else {
                    drupalEntity.setWktAndAttributes(geometry);
                }
            } catch(ParseException ex) {
                Message message = logger.addMessage(Level.WARNING,
                        "Invalid GeoJSON",
                        ex);
                message.addDetail(geoJSON);
            }
        }
    }

    // Thread class, which does typical entity indexing (node, media, etc).
    // It can be extended in the indexer and used as a starting point.
    public class DrupalEntityIndexerThread extends Thread {
        private final SearchClient searchClient;
        private final AbstractLogger logger;
        private final JSONObject jsonApiEntity;
        private final Set<String> usedThumbnails;
        private final int page;
        private final int current;
        private final int pageTotal;
        private E drupalEntity;

        public DrupalEntityIndexerThread(
                SearchClient searchClient,
                AbstractLogger logger,
                JSONObject jsonApiEntity,
                Set<String> usedThumbnails,
                int page, int current, int pageTotal
        ) {
            this.searchClient = searchClient;
            this.logger = logger;
            this.jsonApiEntity = jsonApiEntity;
            this.usedThumbnails = usedThumbnails;
            this.page = page;
            this.current = current;
            this.pageTotal = pageTotal;
        }

        public SearchClient getSearchClient() {
            return this.searchClient;
        }

        public AbstractLogger getLogger() {
            return this.logger;
        }

        public E getDrupalEntity() {
            return this.drupalEntity;
        }

        @Override
        public void run() {
            JSONObject jsonResponse = new JSONObject()
                    .put("data", this.jsonApiEntity);

            String entityUUID = this.jsonApiEntity == null ? null : this.jsonApiEntity.optString("id", null);

            this.drupalEntity = AbstractDrupalEntityIndexer.this.harvestEntity(
                    this.searchClient, jsonResponse, entityUUID, this.logger);


            if (this.drupalEntity != null) {
                if (this.usedThumbnails != null) {
                    String thumbnailFilename = this.drupalEntity.getCachedThumbnailFilename();
                    if (thumbnailFilename != null) {
                        this.usedThumbnails.add(thumbnailFilename);
                    }
                }

                try {
                    IndexResponse indexResponse = AbstractDrupalEntityIndexer.this.indexEntity(this.searchClient, this.drupalEntity, this.logger);

                    // NOTE: We don't know how many entities (or pages of entities) there is.
                    //     We index until we reach the bottom of the barrel...
                    this.logger.addMessage(Level.INFO, String.format("[Page %d: %d/%d] Indexing Drupal %s type %s, id: %s, index response status: %s",
                            this.page, this.current, this.pageTotal,
                            AbstractDrupalEntityIndexer.this.getDrupalEntityType(),
                            AbstractDrupalEntityIndexer.this.getDrupalBundleId(),
                            this.drupalEntity.getId(),
                            indexResponse.result()));
                } catch(Exception ex) {
                    this.logger.addMessage(Level.WARNING,
                            String.format("Exception occurred while indexing a Drupal %s type %s, id: %s",
                                    AbstractDrupalEntityIndexer.this.getDrupalEntityType(),
                                    AbstractDrupalEntityIndexer.this.getDrupalBundleId(),
                                    this.drupalEntity.getId()), ex);
                }
            }

            AbstractDrupalEntityIndexer.this.incrementCompleted();
        }
    }
}
