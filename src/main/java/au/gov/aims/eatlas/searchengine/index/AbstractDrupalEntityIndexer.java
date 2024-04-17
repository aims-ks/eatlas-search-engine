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

import au.gov.aims.eatlas.searchengine.admin.rest.Messages;
import au.gov.aims.eatlas.searchengine.client.SearchClient;
import au.gov.aims.eatlas.searchengine.entity.AbstractDrupalEntity;
import au.gov.aims.eatlas.searchengine.entity.Entity;
import au.gov.aims.eatlas.searchengine.entity.EntityUtils;
import au.gov.aims.eatlas.searchengine.rest.ImageCache;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import org.apache.http.client.utils.URIBuilder;
import org.apache.log4j.Logger;
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
    private static final Logger LOGGER = Logger.getLogger(AbstractDrupalEntityIndexer.class.getName());
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
            String index,
            String drupalUrl,
            String drupalVersion,
            String drupalEntityType,
            String drupalBundleId,
            String drupalPreviewImageField,
            String drupalIndexedFields,
            String drupalGeoJSONField) {

        super(index);
        this.drupalUrl = drupalUrl;
        this.drupalVersion = drupalVersion;
        this.drupalEntityType = drupalEntityType;
        this.drupalBundleId = drupalBundleId;
        this.drupalPreviewImageField = drupalPreviewImageField;
        this.drupalIndexedFields = drupalIndexedFields;
        this.drupalGeoJSONField = drupalGeoJSONField;
    }

    public abstract E createDrupalEntity(JSONObject jsonApiEntity, Map<String, JSONObject> jsonIncluded, Messages messages);
    public abstract E getIndexedDrupalEntity(SearchClient client, String id, Messages messages);
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
    protected E harvestEntity(SearchClient client, String entityUUID, Messages messages) {
        // First, request the node without includes (to find out the node's structure)
        URIBuilder uriBuilder = this.buildDrupalApiEntityUrl(entityUUID, messages);
        if (uriBuilder == null) {
            return null;
        }

        JSONObject jsonResponse = this.getJsonResponse(entityUUID, uriBuilder, messages);
        return this.harvestEntity(client, jsonResponse, entityUUID, messages);
    }

    protected E harvestEntity(SearchClient client, JSONObject jsonResponse, String entityUUID, Messages messages) {
        E drupalEntity = null;
        if (jsonResponse != null) {
            JSONObject jsonApiEntity = jsonResponse.optJSONObject("data");
            List<String> includes = this.getIncludes(jsonApiEntity);

            if (includes == null || includes.isEmpty()) {
                // No field needs to be included in the query.
                // No need to send another query, just use the previous response. It contains all the information we need.
                drupalEntity = this.harvestEntityWithIncludes(client, jsonResponse, messages);
            } else {
                // Now that we know what fields need to be included in the request,
                // request the node again, with the includes.
                URIBuilder uriWithIncludesBuilder = this.buildDrupalApiEntityUrlWithIncludes(entityUUID, includes, messages);
                if (uriWithIncludesBuilder == null) {
                    return null;
                }

                JSONObject jsonResponseWithIncludes = this.getJsonResponse(entityUUID, uriWithIncludesBuilder, messages);
                drupalEntity = this.harvestEntityWithIncludes(client, jsonResponseWithIncludes, messages);
            }
        }

        return drupalEntity;
    }


    protected JSONObject getJsonResponse(String entityUUID, URIBuilder uriBuilder, Messages messages) {
        String url;
        try {
            url = uriBuilder.build().toURL().toString();
        } catch(Exception ex) {
            // Should not happen
            messages.addMessage(Messages.Level.ERROR,
                    String.format("Invalid Drupal URL. Exception occurred while building a URL starting with: %s", this.getDrupalApiUrlBase()), ex);
            return null;
        }

        String responseStr = null;
        try {
            responseStr = EntityUtils.harvestGetURL(url, messages);
        } catch(Exception ex) {
            messages.addMessage(Messages.Level.WARNING, String.format("Exception occurred while requesting the Drupal %s, type: %s, UUID: %s",
                    this.getDrupalEntityType(), this.getDrupalBundleId(), entityUUID), ex);
        }

        if (responseStr != null && !responseStr.isEmpty()) {
            JSONObject jsonResponse = new JSONObject(responseStr);

            JSONArray jsonErrors = jsonResponse.optJSONArray("errors");
            if (jsonErrors != null && !jsonErrors.isEmpty()) {
                this.handleDrupalApiErrors(jsonErrors, messages);
            } else {
                return jsonResponse;
            }
        }

        return null;
    }

    protected E harvestEntityWithIncludes(SearchClient client, JSONObject jsonResponse, Messages messages) {
        JSONObject jsonApiEntity = jsonResponse.optJSONObject("data");
        JSONArray jsonIncludedArray = jsonResponse.optJSONArray("included");

        Map<String, JSONObject> jsonIncluded = parseJsonIncluded(jsonIncludedArray);

        E drupalEntity = this.createDrupalEntity(jsonApiEntity, jsonIncluded, messages);

        if (this.parseJsonDrupalEntity(client, jsonApiEntity, jsonIncluded, drupalEntity, messages)) {
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
            SearchClient client,
            JSONObject jsonApiEntity,
            Map<String, JSONObject> jsonIncluded,
            E drupalEntity,
            Messages messages) {

        this.updateThumbnail(client, jsonApiEntity, jsonIncluded, drupalEntity, messages);
        this.updateGeoJSON(client, jsonApiEntity, jsonIncluded, drupalEntity, messages);
        return true;
    }

    @Override
    protected void internalIndex(SearchClient client, Long lastHarvested, Messages messages) {
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

        URIBuilder uriBuilder = this.buildDrupalApiPageUrl(page, sort, messages);
        String url = null;
        try {
            url = uriBuilder.build().toURL().toString();
        } catch(Exception ex) {
            // Should not happen
            messages.addMessage(Messages.Level.ERROR,
                    String.format("Invalid Drupal URL. Exception occurred while building a URL starting with: %s", this.getDrupalApiUrlBase()), ex);
            return;
        }

        do {
            entityFound = 0;
            String responseStr = null;
            try {
                responseStr = EntityUtils.harvestGetURL(url, messages);
            } catch(Exception ex) {
                if (!crashed) {
                    messages.addMessage(Messages.Level.WARNING, String.format("Exception occurred while requesting a page of Drupal %s, type: %s",
                            this.getDrupalEntityType(), this.getDrupalBundleId()), ex);
                }
                crashed = true;
            }
            if (responseStr != null && !responseStr.isEmpty()) {
                JSONObject jsonResponse = new JSONObject(responseStr);

                JSONArray jsonErrors = jsonResponse.optJSONArray("errors");
                if (jsonErrors != null && !jsonErrors.isEmpty()) {
                    this.handleDrupalApiErrors(jsonErrors, messages);
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
                            client, messages, jsonApiEntity, usedThumbnails, page+1, i+1, entityFound);

                        threadPool.execute(thread);
                    }
                }

                // Get the URL of the next page
                // NOTE: Use links/next/href. If not present, end as been reached
                JSONObject linksJson = jsonResponse.optJSONObject("links");
                JSONObject nextJson = linksJson == null ? null : linksJson.optJSONObject("next");
                url = nextJson == null ? null : nextJson.optString("href", null);
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
            messages.addMessage(Messages.Level.ERROR, String.format("The indexation for %s type %s was interrupted",
                    this.getDrupalEntityType(), this.getDrupalBundleId()), ex);
        }

        // Only cleanup when we are doing a full harvest
        if (!crashed && fullHarvest) {
            this.cleanUp(client, harvestStart, usedThumbnails, String.format("Drupal %s type %s",
                    this.getDrupalEntityType(), this.getDrupalBundleId()), messages);
        }
    }

    public String getDrupalApiUrlBase() {
        return String.format("%s/jsonapi/%s/%s", this.getDrupalUrl(), this.getDrupalEntityType(), this.getDrupalBundleId());
    }

    public URIBuilder buildDrupalApiEntityUrl(String entityUUID, Messages messages) {
        String urlBase = this.getDrupalApiUrlBase();
        URIBuilder uriBuilder;
        try {
            uriBuilder = new URIBuilder(String.format("%s/%s", urlBase, entityUUID));
        } catch(URISyntaxException ex) {
            messages.addMessage(Messages.Level.ERROR,
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
    public URIBuilder buildDrupalApiPageUrl(int page, String sort, Messages messages) {
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
            messages.addMessage(Messages.Level.ERROR,
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

    public URIBuilder buildDrupalApiEntityUrlWithIncludes(String entityUUID, List<String> includes, Messages messages) {
        String urlBase = this.getDrupalApiUrlBase();
        URIBuilder uriBuilder;
        try {
            uriBuilder = new URIBuilder(String.format("%s/%s", urlBase, entityUUID));
        } catch(URISyntaxException ex) {
            messages.addMessage(Messages.Level.ERROR,
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

    public void handleDrupalApiErrors(JSONArray jsonErrors, Messages messages) {
        // Handle errors returned by Drupal.
        for (int i=0; i<jsonErrors.length(); i++) {
            JSONObject jsonError = jsonErrors.optJSONObject(i);
            String errorTitle = jsonError.optString("title", "Untitled error");
            String errorDetail = jsonError.optString("detail", "No details");

            messages.addMessage(Messages.Level.ERROR,
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
                        textChunks.add(EntityUtils.extractHTMLTextContent(jsonBody.optString("processed", null)));
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
                texts.add(EntityUtils.extractHTMLTextContent(ckeditorText));
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

    public void updateThumbnail(SearchClient client, JSONObject jsonApiEntity, Map<String, JSONObject> jsonIncluded, E drupalEntity, Messages messages) {
        URL baseUrl = AbstractDrupalEntity.getDrupalBaseUrl(jsonApiEntity, messages);
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
                        messages.addMessage(Messages.Level.WARNING,
                                String.format("Exception occurred while creating a thumbnail URL for Drupal %s type %s, id: %s",
                                        this.getDrupalEntityType(),
                                        this.getDrupalBundleId(),
                                        drupalEntity.getId()), ex);
                    }
                    drupalEntity.setThumbnailUrl(thumbnailUrl);

                    // Create the thumbnail if it's missing or outdated
                    E oldEntity = this.getIndexedDrupalEntity(client, drupalEntity.getId(), messages);
                    if (drupalEntity.isThumbnailOutdated(oldEntity, this.getSafeThumbnailTTL(), this.getSafeBrokenThumbnailTTL(), messages)) {
                        try {
                            File cachedThumbnailFile = ImageCache.cache(thumbnailUrl, this.getIndex(), drupalEntity.getId(), messages);
                            if (cachedThumbnailFile != null) {
                                drupalEntity.setCachedThumbnailFilename(cachedThumbnailFile.getName());
                            }
                        } catch(Exception ex) {
                            messages.addMessage(Messages.Level.WARNING,
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

    public void updateGeoJSON(SearchClient client, JSONObject jsonApiEntity, Map<String, JSONObject> jsonIncluded, E drupalEntity, Messages messages) {
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
                    Messages.Message message = messages.addMessage(Messages.Level.WARNING,
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
                Messages.Message message = messages.addMessage(Messages.Level.WARNING,
                        "Invalid GeoJSON",
                        ex);
                message.addDetail(geoJSON);
            }
        }
    }

    // Thread class, which does typical entity indexing (node, media, etc).
    // It can be extended in the indexer and used as a starting point.
    public class DrupalEntityIndexerThread extends Thread {
        private final SearchClient client;
        private final Messages messages;
        private final JSONObject jsonApiEntity;
        private final Set<String> usedThumbnails;
        private final int page;
        private final int current;
        private final int pageTotal;
        private E drupalEntity;

        public DrupalEntityIndexerThread(
                SearchClient client,
                Messages messages,
                JSONObject jsonApiEntity,
                Set<String> usedThumbnails,
                int page, int current, int pageTotal
        ) {
            this.client = client;
            this.messages = messages;
            this.jsonApiEntity = jsonApiEntity;
            this.usedThumbnails = usedThumbnails;
            this.page = page;
            this.current = current;
            this.pageTotal = pageTotal;
        }

        public SearchClient getClient() {
            return this.client;
        }

        public Messages getMessages() {
            return this.messages;
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
                    this.client, jsonResponse, entityUUID, this.messages);


            if (this.drupalEntity != null) {
                if (this.usedThumbnails != null) {
                    String thumbnailFilename = this.drupalEntity.getCachedThumbnailFilename();
                    if (thumbnailFilename != null) {
                        this.usedThumbnails.add(thumbnailFilename);
                    }
                }

                try {
                    IndexResponse indexResponse = AbstractDrupalEntityIndexer.this.indexEntity(this.client, this.drupalEntity, this.messages);

                    // NOTE: We don't know how many entities (or pages of entities) there is.
                    //     We index until we reach the bottom of the barrel...
                    LOGGER.debug(String.format("[Page %d: %d/%d] Indexing Drupal %s type %s, id: %s, index response status: %s",
                            this.page, this.current, this.pageTotal,
                            AbstractDrupalEntityIndexer.this.getDrupalEntityType(),
                            AbstractDrupalEntityIndexer.this.getDrupalBundleId(),
                            this.drupalEntity.getId(),
                            indexResponse.result()));
                } catch(Exception ex) {
                    this.messages.addMessage(Messages.Level.WARNING,
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
