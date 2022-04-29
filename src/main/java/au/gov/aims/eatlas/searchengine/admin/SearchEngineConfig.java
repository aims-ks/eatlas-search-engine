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

import au.gov.aims.eatlas.searchengine.admin.rest.Messages;
import au.gov.aims.eatlas.searchengine.index.AbstractIndexer;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
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
import java.util.Random;

public class SearchEngineConfig {
    private static final long DEFAULT_GLOBAL_THUMBNAIL_TTL = 30; // TTL, in days
    private static final long DEFAULT_GLOBAL_BROKEN_THUMBNAIL_TTL = 1; // TTL, in days
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

    private long lastModified;
    private final File configFile;

    // Values saved in the config file
    private List<String> elasticSearchUrls; // http://localhost:9200, http://localhost:9300

    private long globalThumbnailTTL = DEFAULT_GLOBAL_THUMBNAIL_TTL; // TTL, in days
    private long globalBrokenThumbnailTTL = DEFAULT_GLOBAL_BROKEN_THUMBNAIL_TTL; // TTL, in days
    private String imageCacheDirectory;
    private List<AbstractIndexer> indexers;

    private String reindexToken;

    private SearchEngineConfig(File configFile, Messages messages) throws IOException {
        this.configFile = configFile;
        this.reload(messages);
    }

    // For internal use (rest.WebApplication)
    public static SearchEngineConfig createInstance(ServletContext context, Messages messages) throws IOException {
        return createInstance(
                SearchEngineConfig.findConfigFile(context, messages),
                "eatlas_search_engine_default.json", messages);
    }

    // For internal use (unit tests)
    public static SearchEngineConfig createInstance(
            File configFile, String configFileResourcePath, Messages messages) throws IOException {

        File stateFile = SearchEngineConfig.findStateFile(configFile);
        if (SearchEngineConfig.checkStateFile(stateFile, true, messages)) {
            SearchEngineState.createInstance(stateFile);
        }

        instance = null;
        if (SearchEngineConfig.checkConfigFile(configFile, configFileResourcePath, true, messages)) {
            instance = new SearchEngineConfig(configFile, messages);
        }

        return instance;
    }

    public static SearchEngineConfig getInstance() {
        return instance;
    }

    public void reload(Messages messages) throws IOException {
        this.indexers = null;
        this.elasticSearchUrls = null;
        if (this.configFile != null && this.configFile.canRead()) {
            // Set lastModified to config file last modified
            this.lastModified = this.configFile.lastModified();

            // Reload config from config file
            String jsonStr = FileUtils.readFileToString(this.configFile, StandardCharsets.UTF_8);
            JSONObject json = (jsonStr == null || jsonStr.isEmpty()) ? new JSONObject() : new JSONObject(jsonStr);
            this.loadJSON(json, messages);
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

    public String getReindexToken() {
        return this.reindexToken;
    }

    public void setReindexToken(String reindexToken) {
        if (reindexToken != null && !reindexToken.isEmpty()) {
            this.reindexToken = reindexToken;
        }
    }

    public File getConfigFile() {
        return this.configFile;
    }

    // Find config file
    public static File findConfigFile(ServletContext context, Messages messages) {
        if (context == null) {
            return null;
        }

        String configFilePathStr = SearchEngineConfig.getConfigFilePropertyValue(context);
        if (configFilePathStr == null) {
            messages.addMessage(Messages.Level.ERROR,
                    String.format("Configuration file not found. Setup the parameter: %s", getConfigFileProperty(context)));
            return null;
        }

        return new File(configFilePathStr);
    }

    // Find state file
    public static File findStateFile(File configFile) {
        String configFilepathWithoutExtension = FilenameUtils.removeExtension(configFile.getAbsolutePath());
        String stateFilepath = configFilepathWithoutExtension + "_state.json";

        return new File(stateFilepath);
    }

    private static boolean checkConfigFile(File configFile, String resourcePath, boolean create, Messages messages) {
        return checkFile(configFile, resourcePath, create, "Configuration", messages);
    }
    private static boolean checkStateFile(File stateFile, boolean create, Messages messages) {
        return checkFile(stateFile, null, create, "State", messages);
    }
    private static boolean checkFile(File file, String resourcePath, boolean create, String fileType, Messages messages) {
        if (!file.exists()) {
            if (create) {
                File parentDir = file.getParentFile();
                if (!parentDir.exists() && !parentDir.mkdirs()) {
                    messages.addMessage(Messages.Level.ERROR,
                            String.format("%s file not found, parent directory does not exist and can not be created: %s",
                            fileType,
                            parentDir));
                    return false;
                }
                if (!parentDir.exists()) {
                    // Should not happen
                    messages.addMessage(Messages.Level.ERROR,
                            String.format("%s file not found, parent directory does not exist and can not be created: %s",
                            fileType,
                            parentDir));
                    return false;
                }
                if (!parentDir.isDirectory()) {
                    messages.addMessage(Messages.Level.ERROR,
                            String.format("%s file not found, parent directory exists but is not a directory: %s",
                            fileType,
                            parentDir));
                    return false;
                }
                if (resourcePath != null) {
                    try {
                        if (!createDefaultFile(file, resourcePath)) {
                            messages.addMessage(Messages.Level.ERROR,
                                    String.format("%s file not found %s, default %s resource not found: %s",
                                    fileType, fileType,
                                    file, resourcePath));
                            return false;
                        }
                    } catch(Exception ex) {
                        messages.addMessage(Messages.Level.ERROR,
                                String.format("Error occurred while creating default %s file: %s",
                                fileType,
                                file), ex);
                        return false;
                    }
                } else {
                    try {
                        file.createNewFile();
                    } catch(Exception ex) {
                        messages.addMessage(Messages.Level.ERROR,
                                String.format("Error occurred while creating empty %s file: %s",
                                fileType,
                                file), ex);
                        return false;
                    }
                }
            } else {
                messages.addMessage(Messages.Level.ERROR,
                        String.format("%s file not found: %s",
                        fileType,
                        file));
                return false;
            }
        }

        if (!file.exists()) {
            messages.addMessage(Messages.Level.ERROR,
                    String.format("%s file not found and can't be created: %s",
                    fileType,
                    file));
            return false;
        }

        if (file.isDirectory()) {
            messages.addMessage(Messages.Level.ERROR,
                    String.format("%s file exists but it's a directory: %s",
                    fileType,
                    file));
            return false;
        }
        if (!file.isFile()) {
            messages.addMessage(Messages.Level.ERROR,
                    String.format("%s file exists but it's not a regular file: %s",
                    fileType,
                    file));
            return false;
        }
        if (!file.canRead()) {
            messages.addMessage(Messages.Level.ERROR,
                    String.format("%s file exists but it's not readable: %s",
                    fileType,
                    file));
            return false;
        }
        if (!file.canWrite()) {
            // Not writable? Throw a warning and let the add run in read only mode.
            messages.addMessage(Messages.Level.WARNING,
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
            for (AbstractIndexer indexer : this.indexers) {
                jsonIndexers.put(indexer.toJSON());
            }
        }

        return new JSONObject()
            .put("elasticSearchUrls", jsonElasticSearchUrls)
            .put("globalThumbnailTTL", this.globalThumbnailTTL)
            .put("globalBrokenThumbnailTTL", this.globalBrokenThumbnailTTL)
            .put("imageCacheDirectory", this.imageCacheDirectory)
            .put("reindexToken", this.reindexToken)
            .put("indexers", jsonIndexers);
    }

    private void loadJSON(JSONObject json, Messages messages) {
        this.globalThumbnailTTL = json.optLong("globalThumbnailTTL", DEFAULT_GLOBAL_THUMBNAIL_TTL);
        this.globalBrokenThumbnailTTL = json.optLong("globalBrokenThumbnailTTL", DEFAULT_GLOBAL_BROKEN_THUMBNAIL_TTL);
        this.imageCacheDirectory = json.optString("imageCacheDirectory", null);
        this.reindexToken = json.optString("reindexToken", null);

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
                AbstractIndexer indexer = AbstractIndexer.fromJSON(jsonIndexer, messages);
                if (indexer != null) {
                    this.addIndexer(indexer);
                }
            }
        }

        if (this.reindexToken == null || this.reindexToken.isEmpty()) {
            this.reindexToken = SearchEngineConfig.generateRandomToken(SearchEngineConfig.RANDOM_TOKEN_LENGTH);
            try {
                this.save();
            } catch (IOException ex) {
                messages.addMessage(Messages.Level.ERROR,
                        String.format("Error occurred while saving the configuration file: %s",
                        this.getConfigFile()), ex);
            }
        }
    }

    // Inspired from:
    //     https://stackoverflow.com/questions/41107/how-to-generate-a-random-alpha-numeric-string#41156
    private static String generateRandomToken(int length) {
        String upper = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String lower = upper.toLowerCase();
        String digits = "0123456789";
        String alphanum = upper + lower + digits;
        Random random = new Random();

        char[] buffer = new char[length];
        char[] symbols = alphanum.toCharArray();
        for (int idx = 0; idx < buffer.length; ++idx) {
            buffer[idx] = symbols[random.nextInt(symbols.length)];
        }
        return new String(buffer);
    }

    @Override
    public String toString() {
        return this.toJSON().toString(2);
    }
}
