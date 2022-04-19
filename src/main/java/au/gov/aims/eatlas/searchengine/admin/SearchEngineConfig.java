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

import au.gov.aims.eatlas.searchengine.index.AbstractIndexer;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.servlet.ServletContext;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.List;

public class SearchEngineConfig {
    private static final Logger LOGGER = Logger.getLogger(SearchEngineConfig.class.getName());

    private static final long DEFAULT_GLOBAL_THUMBNAIL_TTL = 30; // TTL, in days
    private static final long DEFAULT_GLOBAL_BROKEN_THUMBNAIL_TTL = 1; // TTL, in days

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

    private long lastModified;
    private final File configFile;

    // Values saved in the config file
    private long globalThumbnailTTL = DEFAULT_GLOBAL_THUMBNAIL_TTL; // TTL, in days
    private long globalBrokenThumbnailTTL = DEFAULT_GLOBAL_BROKEN_THUMBNAIL_TTL; // TTL, in days
    private String imageCacheDirectory;
    private List<AbstractIndexer> indexers;

    private SearchEngineConfig(File configFile) throws IOException {
        this.configFile = configFile;
        this.reload();
    }

    // For internal use (rest.WebApplication)
    public static SearchEngineConfig createInstance(ServletContext context) throws IOException {
        return createInstance(
                SearchEngineConfig.getConfigFile(context),
                "eatlas_search_engine_default.json");
    }

    // For internal use (tests)
    public static SearchEngineConfig createInstance(
            File configFile, String configFileResourcePath) throws IOException {

        File stateFile = SearchEngineConfig.getStateFile(configFile);
        if (SearchEngineConfig.checkStateFile(stateFile, true)) {
            SearchEngineState.createInstance(stateFile);
        }

        instance = null;
        if (SearchEngineConfig.checkConfigFile(configFile, configFileResourcePath, true)) {
            instance = new SearchEngineConfig(configFile);
        }

        return instance;
    }

    public static SearchEngineConfig getInstance() {
        return instance;
    }

    public void reload() throws IOException {
        if (this.configFile != null && this.configFile.canRead()) {
            // Reload config from config file
            String jsonStr = FileUtils.readFileToString(this.configFile, StandardCharsets.UTF_8);
            JSONObject json = (jsonStr == null || jsonStr.isEmpty()) ? new JSONObject() : new JSONObject(jsonStr);
            this.loadJSON(json);

            // Set lastModified to config file last modified
            this.lastModified = this.configFile.lastModified();
        }
    }

    public void save() throws IOException {
        if (this.configFile == null) {
            // This should not happen
            throw new IllegalStateException("The configuration file is null.");
        }
        if (!this.configFile.canWrite()) {
            throw new IllegalStateException(String.format("The configuration file is not writable: %s",
                    this.configFile.getAbsolutePath()));
        }

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

    public List<AbstractIndexer> getIndexers() {
        return this.indexers;
    }

    public AbstractIndexer getIndexer(String index) {
        if (index != null && this.indexers != null) {
            for (AbstractIndexer foundIndexer : this.indexers) {
                if (index.equals(foundIndexer.getIndex())) {
                    return foundIndexer;
                }
            }
        }
        return null;
    }

    public void addIndexer(AbstractIndexer indexer) {
        if (this.indexers == null) {
            this.indexers = new ArrayList<>();
        }
        this.indexers.add(indexer);
    }

    public AbstractIndexer removeIndexer(String index) throws IOException {
        AbstractIndexer foundIndexer = this.getIndexer(index);
        if (foundIndexer != null) {
            return this.removeIndexer(foundIndexer) ? foundIndexer : null;
        }
        return null;
    }

    public boolean removeIndexer(AbstractIndexer indexer) throws IOException {
        if (indexer != null && this.indexers != null) {
            SearchEngineState searchEngineState = SearchEngineState.getInstance();
            searchEngineState.removeIndexerState(indexer.getIndex());
            return this.indexers.remove(indexer);
        }
        return false;
    }

    public String getImageCacheDirectory() {
        return this.imageCacheDirectory;
    }

    public void setImageCacheDirectory(String imageCacheDirectory) {
        this.imageCacheDirectory = imageCacheDirectory;
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

    // Find config file
    public static File getConfigFile(ServletContext context) {
        if (context == null) {
            return null;
        }

        String configFilePathStr = SearchEngineConfig.getConfigFilePropertyValue(context);
        if (configFilePathStr == null) {
            LOGGER.error(String.format("Configuration file not found. Setup the parameter: %s", getConfigFileProperty(context)));
            return null;
        }

        return new File(configFilePathStr);
    }

    // Find state file
    public static File getStateFile(File configFile) {
        String configFilepathWithoutExtension = FilenameUtils.removeExtension(configFile.getAbsolutePath());
        String stateFilepath = configFilepathWithoutExtension + "_state.json";

        return new File(stateFilepath);
    }

    private static boolean checkConfigFile(File configFile, String resourcePath, boolean create) {
        return checkFile(configFile, resourcePath, create, "Configuration");
    }
    private static boolean checkStateFile(File stateFile, boolean create) {
        return checkFile(stateFile, null, create, "State");
    }
    private static boolean checkFile(File file, String resourcePath, boolean create, String fileType) {
        if (!file.exists()) {
            if (create) {
                File parentDir = file.getParentFile();
                if (!parentDir.exists() && !parentDir.mkdirs()) {
                    LOGGER.error(String.format("%s file not found, parent directory does not exist and can not be created: %s",
                            fileType,
                            parentDir));
                    return false;
                }
                if (!parentDir.exists()) {
                    // Should not happen
                    LOGGER.error(String.format("%s file not found, parent directory does not exist and can not be created: %s",
                            fileType,
                            parentDir));
                    return false;
                }
                if (!parentDir.isDirectory()) {
                    LOGGER.error(String.format("%s file not found, parent directory exists but is not a directory: %s",
                            fileType,
                            parentDir));
                    return false;
                }
                if (resourcePath != null) {
                    try {
                        if (!createDefaultFile(file, resourcePath)) {
                            LOGGER.error(String.format("%s file not found %s, default %s resource not found: %s",
                                    fileType, fileType,
                                    file, resourcePath));
                            return false;
                        }
                    } catch(Exception ex) {
                        LOGGER.error(String.format("Error occurred while creating default %s file: %s",
                                fileType,
                                file), ex);
                        return false;
                    }
                } else {
                    try {
                        file.createNewFile();
                    } catch(Exception ex) {
                        LOGGER.error(String.format("Error occurred while creating empty %s file: %s",
                                fileType,
                                file), ex);
                        return false;
                    }
                }
            } else {
                LOGGER.error(String.format("%s file not found: %s",
                        fileType,
                        file));
                return false;
            }
        }

        if (!file.exists()) {
            LOGGER.error(String.format("%s file not found and can't be created: %s",
                    fileType,
                    file));
            return false;
        }

        if (file.isDirectory()) {
            LOGGER.error(String.format("%s file exists but it's a directory: %s",
                    fileType,
                    file));
            return false;
        }
        if (!file.isFile()) {
            LOGGER.error(String.format("%s file exists but it's not a regular file: %s",
                    fileType,
                    file));
            return false;
        }
        if (!file.canRead()) {
            LOGGER.error(String.format("%s file exists but it's not readable: %s",
                    fileType,
                    file));
            return false;
        }
        if (!file.canWrite()) {
            // Not writable? Throw a warning and let the add run in read only mode.
            LOGGER.warn(String.format("%s file exists but it's not writable: %s",
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
        JSONArray jsonIndexers = new JSONArray();
        if (this.indexers != null && !this.indexers.isEmpty()) {
            for (AbstractIndexer indexer : this.indexers) {
                jsonIndexers.put(indexer.toJSON());
            }
        }

        return new JSONObject()
            .put("globalThumbnailTTL", this.globalThumbnailTTL)
            .put("globalBrokenThumbnailTTL", this.globalBrokenThumbnailTTL)
            .put("imageCacheDirectory", this.imageCacheDirectory)
            .put("indexers", jsonIndexers);
    }

    private void loadJSON(JSONObject json) {
        this.globalThumbnailTTL = json.optLong("globalThumbnailTTL", DEFAULT_GLOBAL_THUMBNAIL_TTL);
        this.globalBrokenThumbnailTTL = json.optLong("globalBrokenThumbnailTTL", DEFAULT_GLOBAL_BROKEN_THUMBNAIL_TTL);
        this.imageCacheDirectory = json.optString("imageCacheDirectory", null);

        JSONArray jsonIndexers = json.optJSONArray("indexers");
        if (jsonIndexers != null) {
            for (int i=0; i<jsonIndexers.length(); i++) {
                JSONObject jsonIndexer = jsonIndexers.optJSONObject(i);
                AbstractIndexer indexer = AbstractIndexer.fromJSON(jsonIndexer);
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
