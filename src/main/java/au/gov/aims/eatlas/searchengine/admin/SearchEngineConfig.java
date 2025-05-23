/*
 *  Copyright (C) 2021 Australian Institute of Marine Science
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
package au.gov.aims.eatlas.searchengine.admin;

import au.gov.aims.eatlas.searchengine.HttpClient;
import au.gov.aims.eatlas.searchengine.logger.AbstractLogger;
import au.gov.aims.eatlas.searchengine.index.AbstractIndexer;
import au.gov.aims.eatlas.searchengine.logger.Level;
import jakarta.servlet.ServletContext;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.List;

public class SearchEngineConfig {
    private static final long DEFAULT_GLOBAL_THUMBNAIL_TTL = 30; // TTL, in days
    private static final long DEFAULT_GLOBAL_BROKEN_THUMBNAIL_TTL = 1; // TTL, in days
    private static final int DEFAULT_NUMBER_OF_SHARDS = 1;
    private static final int DEFAULT_NUMBER_OF_REPLICAS = 0;
    private static final int DEFAULT_THUMBNAIL_WIDTH = 200;
    private static final int DEFAULT_THUMBNAIL_HEIGHT = 150;

    private static final int RANDOM_TOKEN_LENGTH = 12;

    // CONFIG_FILE_PROPERTY can be set in many ways (same as GeoServer)
    // * Servlet context parameter (web.xml)
    // * Java system property (tomcat/bin/setenv.sh)
    //     Add this line to CATALINA_OPTS variable (replace <path to the config file> with the desired absolute path to the config folder)
    //     -DEATLAS_SEARCH_ENGINE_CONFIG_FILE=<path to the config file>
    //     NOTE: If the web app is deployed under a different name, the variable name will change subsequently.
    // * Global environment variable (/etc/environment, /etc/profile, /etc/bash.bashrc or the user equivalent)
    // NOTE: Don't forget to restart tomcat after setting this variable.
    private static final String CONFIG_FILE_PROPERTY = "{WEBAPP-NAME}_CONFIG_FILE";

    private static SearchEngineConfig instance;
    private HttpClient httpClient;

    private long lastModified;
    private final File configFile;

    // Values saved in the config file
    private List<String> elasticSearchUrls; // http://localhost:9200, http://localhost:9300

    private Integer elasticSearchNumberOfShards;
    private Integer elasticSearchNumberOfReplicas;

    private long globalThumbnailTTL = DEFAULT_GLOBAL_THUMBNAIL_TTL; // TTL, in days
    private long globalBrokenThumbnailTTL = DEFAULT_GLOBAL_BROKEN_THUMBNAIL_TTL; // TTL, in days
    private String imageCacheDirectory;
    private int thumbnailWidth;
    private int thumbnailHeight;
    // When thumbnails settings are changed, all thumbnails are invalidated.
    private long lastThumbnailSettingChangeDate = -1;
    // Used to craft URL to preview images
    private String searchEngineBaseUrl;
    private List<AbstractIndexer<?>> indexers;

    private SearchEngineConfig(HttpClient httpClient, File configFile, AbstractLogger logger) throws IOException {
        this.httpClient = httpClient;
        this.configFile = configFile;
        this.reload(logger);
    }

    // For internal use (rest.WebApplication)
    public static SearchEngineConfig createInstance(HttpClient httpClient, ServletContext context, AbstractLogger logger) throws Exception {
        return createInstance(
                httpClient,
                SearchEngineConfig.findConfigFile(context, logger),
                "eatlas_search_engine_default.json", logger);
    }

    // For internal use (unit tests)
    public static SearchEngineConfig createInstance(
            HttpClient httpClient, File configFile, String configFileResourcePath, AbstractLogger logger) throws Exception {

        File privateConfigFile = SearchEngineConfig.findPrivateConfigFile(configFile);
        if (SearchEngineConfig.checkPrivateConfigFile(privateConfigFile, true, logger)) {
            SearchEnginePrivateConfig.createInstance(privateConfigFile, logger);
        }

        File stateFile = SearchEngineConfig.findStateFile(configFile);
        if (SearchEngineConfig.checkStateFile(stateFile, true, logger)) {
            SearchEngineState.createInstance(stateFile);
        }

        instance = null;
        if (SearchEngineConfig.checkConfigFile(configFile, configFileResourcePath, true, logger)) {
            instance = new SearchEngineConfig(httpClient, configFile, logger);
        }

        return instance;
    }

    public static SearchEngineConfig getInstance() {
        return instance;
    }

    public void reload(AbstractLogger logger) throws IOException {
        this.indexers = new ArrayList<>();
        this.elasticSearchUrls = null;
        if (this.configFile != null && this.configFile.canRead()) {
            // Set lastModified to config file last modified
            this.lastModified = this.configFile.lastModified();

            // Reload config from config file
            String jsonStr = FileUtils.readFileToString(this.configFile, StandardCharsets.UTF_8);
            JSONObject json = (jsonStr == null || jsonStr.isEmpty()) ? new JSONObject() : new JSONObject(jsonStr);
            this.loadJSON(json, logger);
        }
    }

    public void save() throws Exception {
        if (this.configFile == null) {
            // This should not happen
            throw new IllegalStateException("The configuration file is null.");
        }
        if (!this.configFile.canWrite()) {
            throw new IOException(String.format("The configuration file is not writable: %s",
                    this.configFile.getAbsolutePath()));
        }

        synchronized (this.configFile) {
            // If config file was modified since last load, throw java.util.ConcurrentModificationException
            if (this.configFile.lastModified() > this.lastModified) {
                throw new ConcurrentModificationException(
                    String.format("Configuration file %s was externally modified since last load.", this.configFile));
            }

            // Save config in config file
            JSONObject json = this.toJSON();
            FileUtils.write(this.configFile, json.toString(2), StandardCharsets.UTF_8);

            // Set this.lastModified to config file last modified
            this.lastModified = this.configFile.lastModified();
        }
    }

    public List<AbstractIndexer<?>> getIndexers() {
        return this.indexers;
    }

    public List<AbstractIndexer<?>> getEnabledIndexers() {
        List<AbstractIndexer<?>> enabledIndexers = new ArrayList<>();
        for (AbstractIndexer<?> indexer : this.indexers) {
            if (indexer.isEnabled()) {
                enabledIndexers.add(indexer);
            }
        }
        return enabledIndexers;
    }

    public AbstractIndexer<?> getIndexer(String index) {
        if (index != null) {
            for (AbstractIndexer<?> foundIndexer : this.indexers) {
                if (index.equals(foundIndexer.getIndex())) {
                    return foundIndexer;
                }
            }
        }
        return null;
    }

    public void addIndexer(AbstractIndexer<?> indexer) {
        this.indexers.add(indexer);
    }

    public AbstractIndexer<?> removeIndexer(String index) throws Exception {
        AbstractIndexer<?> foundIndexer = this.getIndexer(index);
        if (foundIndexer != null) {
            return this.removeIndexer(foundIndexer) ? foundIndexer : null;
        }
        return null;
    }

    public boolean removeIndexer(AbstractIndexer<?> indexer) throws Exception {
        if (indexer != null) {
            SearchEngineState searchEngineState = SearchEngineState.getInstance();
            searchEngineState.removeIndexerState(indexer.getIndex());
            return this.indexers.remove(indexer);
        }
        return false;
    }

    public List<String> getElasticSearchUrls() {
        return this.elasticSearchUrls;
    }

    public void setElasticSearchUrls(List<String> elasticSearchUrls) {
        this.elasticSearchUrls = elasticSearchUrls;
    }

    public void addElasticSearchUrl(String elasticSearchUrl) {
        if (this.elasticSearchUrls == null) {
            this.elasticSearchUrls = new ArrayList<>();
        }
        this.elasticSearchUrls.add(elasticSearchUrl);
    }

    public String getImageCacheDirectory() {
        return this.imageCacheDirectory;
    }

    public String getLogCacheDirectory() {
        return this.imageCacheDirectory + "/log";
    }

    public void setImageCacheDirectory(String imageCacheDirectory) {
        this.imageCacheDirectory = imageCacheDirectory;
    }

    public int getThumbnailWidth() {
        return this.thumbnailWidth;
    }

    public int getThumbnailHeight() {
        return this.thumbnailHeight;
    }

    public void setThumbnailDimensions(Integer thumbnailWidth, Integer thumbnailHeight, AbstractLogger logger) {
        int oldWidth = this.getThumbnailWidth();
        int newWidth = thumbnailWidth == null ? DEFAULT_THUMBNAIL_WIDTH : thumbnailWidth;

        int oldHeight = this.getThumbnailHeight();
        int newHeight = thumbnailHeight == null ? DEFAULT_THUMBNAIL_HEIGHT : thumbnailHeight;

        if (oldWidth != newWidth || oldHeight != newHeight) {
            // Invalidate all thumbnails
            this.lastThumbnailSettingChangeDate = System.currentTimeMillis();
            logger.addMessage(Level.WARNING, "All thumbnails invalidated. Re-index for changes to take effect.");
        }

        this.thumbnailWidth = newWidth;
        this.thumbnailHeight = newHeight;
    }

    public long getLastThumbnailSettingChangeDate() {
        return this.lastThumbnailSettingChangeDate;
    }

    public String getSearchEngineBaseUrl() {
        return this.searchEngineBaseUrl;
    }

    public void setSearchEngineBaseUrl(String searchEngineBaseUrl) {
        this.searchEngineBaseUrl = searchEngineBaseUrl;
    }

    public int getElasticSearchNumberOfShards() {
        return this.elasticSearchNumberOfShards == null ?
                DEFAULT_NUMBER_OF_SHARDS :
                this.elasticSearchNumberOfShards;
    }

    public void setElasticSearchNumberOfShards(Integer elasticSearchNumberOfShards) {
        this.elasticSearchNumberOfShards = elasticSearchNumberOfShards;
    }

    public int getElasticSearchNumberOfReplicas() {
        return this.elasticSearchNumberOfReplicas == null ?
                DEFAULT_NUMBER_OF_REPLICAS :
                this.elasticSearchNumberOfReplicas;
    }

    public void setElasticSearchNumberOfReplicas(Integer elasticSearchNumberOfReplicas) {
        this.elasticSearchNumberOfReplicas = elasticSearchNumberOfReplicas;
    }

    public long getGlobalThumbnailTTL() {
        return this.globalThumbnailTTL;
    }

    public void setGlobalThumbnailTTL(Long globalThumbnailTTL) {
        this.globalThumbnailTTL = globalThumbnailTTL == null ? DEFAULT_GLOBAL_THUMBNAIL_TTL : globalThumbnailTTL;
    }

    public long getGlobalBrokenThumbnailTTL() {
        return this.globalBrokenThumbnailTTL;
    }

    public void setGlobalBrokenThumbnailTTL(Long globalBrokenThumbnailTTL) {
        this.globalBrokenThumbnailTTL = globalBrokenThumbnailTTL == null ? DEFAULT_GLOBAL_BROKEN_THUMBNAIL_TTL : globalBrokenThumbnailTTL;
    }

    public File getConfigFile() {
        return this.configFile;
    }

    // Find config file
    public static File findConfigFile(ServletContext context, AbstractLogger logger) {
        if (context == null) {
            return null;
        }

        String configFilePathStr = SearchEngineConfig.getConfigFilePropertyValue(context);
        if (configFilePathStr == null) {
            logger.addMessage(Level.ERROR,
                    String.format("Configuration file not found. Setup the parameter: %s", getConfigFileProperty(context)));
            return null;
        }

        return new File(configFilePathStr);
    }

    // Find private config file
    public static File findPrivateConfigFile(File configFile) {
        String configFilepathWithoutExtension = FilenameUtils.removeExtension(configFile.getAbsolutePath());
        String privateFilepath = configFilepathWithoutExtension + "_private.json";

        return new File(privateFilepath);
    }

    // Find state file
    public static File findStateFile(File configFile) {
        String configFilepathWithoutExtension = FilenameUtils.removeExtension(configFile.getAbsolutePath());
        String stateFilepath = configFilepathWithoutExtension + "_state.json";

        return new File(stateFilepath);
    }

    private static boolean checkConfigFile(File configFile, String resourcePath, boolean create, AbstractLogger logger) {
        return checkFile(configFile, resourcePath, create, "Configuration", logger);
    }
    private static boolean checkPrivateConfigFile(File privateConfigFile, boolean create, AbstractLogger logger) {
        return checkFile(privateConfigFile, null, create, "Private config", logger);
    }
    private static boolean checkStateFile(File stateFile, boolean create, AbstractLogger logger) {
        return checkFile(stateFile, null, create, "State", logger);
    }
    private static boolean checkFile(File file, String resourcePath, boolean create, String fileType, AbstractLogger logger) {
        if (!file.exists()) {
            if (create) {
                File parentDir = file.getParentFile();
                if (!parentDir.exists() && !parentDir.mkdirs()) {
                    logger.addMessage(Level.ERROR,
                            String.format("%s file not found, parent directory does not exist and can not be created: %s",
                            fileType,
                            parentDir));
                    return false;
                }
                if (!parentDir.exists()) {
                    // Should not happen
                    logger.addMessage(Level.ERROR,
                            String.format("%s file not found, parent directory does not exist and can not be created: %s",
                            fileType,
                            parentDir));
                    return false;
                }
                if (!parentDir.isDirectory()) {
                    logger.addMessage(Level.ERROR,
                            String.format("%s file not found, parent directory exists but is not a directory: %s",
                            fileType,
                            parentDir));
                    return false;
                }
                if (resourcePath != null) {
                    try {
                        if (!createDefaultFile(file, resourcePath)) {
                            logger.addMessage(Level.ERROR,
                                    String.format("%s file not found, default %s resource not found: %s",
                                    fileType,
                                    file, resourcePath));
                            return false;
                        }
                    } catch(Exception ex) {
                        logger.addMessage(Level.ERROR,
                                String.format("Error occurred while creating default %s file: %s",
                                fileType,
                                file), ex);
                        return false;
                    }
                } else {
                    try {
                        file.createNewFile();
                    } catch(Exception ex) {
                        logger.addMessage(Level.ERROR,
                                String.format("Error occurred while creating empty %s file: %s",
                                fileType,
                                file), ex);
                        return false;
                    }
                }
            } else {
                logger.addMessage(Level.ERROR,
                        String.format("%s file not found: %s",
                        fileType,
                        file));
                return false;
            }
        }

        if (!file.exists()) {
            logger.addMessage(Level.ERROR,
                    String.format("%s file not found and can't be created: %s",
                    fileType,
                    file));
            return false;
        }

        if (file.isDirectory()) {
            logger.addMessage(Level.ERROR,
                    String.format("%s file exists but it's a directory: %s",
                    fileType,
                    file));
            return false;
        }
        if (!file.isFile()) {
            logger.addMessage(Level.ERROR,
                    String.format("%s file exists but it's not a regular file: %s",
                    fileType,
                    file));
            return false;
        }
        if (!file.canRead()) {
            logger.addMessage(Level.ERROR,
                    String.format("%s file exists but it's not readable: %s",
                    fileType,
                    file));
            return false;
        }
        if (!file.canWrite()) {
            // Not writable? Throw a warning and let the add run in read only mode.
            logger.addMessage(Level.WARNING,
                    String.format("%s file exists but it's not writable: %s",
                    fileType,
                    file));
        }

        return true;
    }

    private static boolean createDefaultFile(File file, String resourcePath) throws IOException {
        // Copy default config from app resources
        URL inputUrl = SearchEngineConfig.class.getClassLoader().getResource(resourcePath);
        if (inputUrl == null) {
            return false;
        }

        FileUtils.copyURLToFile(inputUrl, file);
        return true;
    }

    // Similar to what GeoServer do
    public static String getConfigFilePropertyValue(ServletContext context) {
        if (context == null) { return null; }

        // web.xml
        String configFilePathStr = context.getInitParameter(getConfigFileProperty(context));

        // Can be used to set the variable in java, for a Unit Test.
        if (configFilePathStr == null || configFilePathStr.isEmpty()) {
            configFilePathStr = System.getProperty(getConfigFileProperty(context));
        }

        // tomcat/bin/setenv.sh  or  .bashrc
        if (configFilePathStr == null || configFilePathStr.isEmpty()) {
            configFilePathStr = System.getenv(getConfigFileProperty(context));
        }

        if (configFilePathStr != null && !configFilePathStr.isEmpty()) {
            return configFilePathStr.trim();
        }
        return null;
    }

    public static String getConfigFileProperty(ServletContext context) {
        String webappName = context.getContextPath().replace("/", "");
        return CONFIG_FILE_PROPERTY.replace("{WEBAPP-NAME}", webappName.toUpperCase());
    }

    public JSONObject toJSON() {
        JSONArray jsonElasticSearchUrls = new JSONArray();
        if (this.elasticSearchUrls != null && !this.elasticSearchUrls.isEmpty()) {
            for (String elasticSearchUrl : this.elasticSearchUrls) {
                jsonElasticSearchUrls.put(elasticSearchUrl);
            }
        }

        JSONArray jsonIndexers = new JSONArray();
        if (this.indexers != null && !this.indexers.isEmpty()) {
            for (AbstractIndexer<?> indexer : this.indexers) {
                jsonIndexers.put(indexer.toJSON());
            }
        }

        return new JSONObject()
                .put("elasticSearchUrls", jsonElasticSearchUrls)
                .put("elasticSearchNumberOfShards", this.getElasticSearchNumberOfShards())
                .put("elasticSearchNumberOfReplicas", this.getElasticSearchNumberOfReplicas())
                .put("globalThumbnailTTL", this.globalThumbnailTTL)
                .put("globalBrokenThumbnailTTL", this.globalBrokenThumbnailTTL)
                .put("imageCacheDirectory", this.imageCacheDirectory)
                .put("thumbnailWidth", this.thumbnailWidth)
                .put("thumbnailHeight", this.thumbnailHeight)
                .put("lastThumbnailSettingChangeDate", this.lastThumbnailSettingChangeDate)
                .put("searchEngineBaseUrl", this.searchEngineBaseUrl)
                .put("indexers", jsonIndexers);
    }

    private void loadJSON(JSONObject json, AbstractLogger logger) {
        this.globalThumbnailTTL = json.optLong("globalThumbnailTTL", DEFAULT_GLOBAL_THUMBNAIL_TTL);
        this.globalBrokenThumbnailTTL = json.optLong("globalBrokenThumbnailTTL", DEFAULT_GLOBAL_BROKEN_THUMBNAIL_TTL);
        this.imageCacheDirectory = json.optString("imageCacheDirectory", null);
        this.thumbnailWidth = json.optInt("thumbnailWidth", DEFAULT_THUMBNAIL_WIDTH);
        this.thumbnailHeight = json.optInt("thumbnailHeight", DEFAULT_THUMBNAIL_HEIGHT);
        this.lastThumbnailSettingChangeDate = json.optInt("lastThumbnailSettingChangeDate", -1);
        this.searchEngineBaseUrl = json.optString("searchEngineBaseUrl", null);

        JSONArray jsonElasticSearchUrls = json.optJSONArray("elasticSearchUrls");
        if (jsonElasticSearchUrls != null) {
            for (int i=0; i<jsonElasticSearchUrls.length(); i++) {
                String elasticSearchUrl = jsonElasticSearchUrls.optString(i, null);
                if (elasticSearchUrl != null) {
                    this.addElasticSearchUrl(elasticSearchUrl);
                }
            }
        }

        JSONArray jsonIndexers = json.optJSONArray("indexers");
        if (jsonIndexers != null) {
            for (int i=0; i<jsonIndexers.length(); i++) {
                JSONObject jsonIndexer = jsonIndexers.optJSONObject(i);
                AbstractIndexer<?> indexer = AbstractIndexer.fromJSON(this.httpClient, jsonIndexer, this, logger);
                if (indexer != null) {
                    this.addIndexer(indexer);
                }
            }
        }
    }

    @Override
    public String toString() {
        return this.toJSON().toString(2);
    }
}
