package au.gov.aims.eatlas.searchengine.admin;

import au.gov.aims.eatlas.searchengine.HttpClient;
import au.gov.aims.eatlas.searchengine.entity.User;
import au.gov.aims.eatlas.searchengine.index.AbstractIndexer;
import au.gov.aims.eatlas.searchengine.logger.AbstractLogger;
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
import java.util.Random;

public class SearchEnginePrivateConfig {
    private static final int RANDOM_TOKEN_LENGTH = 12;

    private static SearchEnginePrivateConfig instance;

    private long lastModified;
    private final File privateConfigFile;

    private User user;
    private String reindexToken;

    private SearchEnginePrivateConfig(File privateConfigFile, AbstractLogger logger) throws IOException {
        this.privateConfigFile = privateConfigFile;
        this.reload(logger);
    }

    // For internal use
    public static SearchEnginePrivateConfig createInstance(File privateConfigFile, AbstractLogger logger) throws Exception {
        instance = new SearchEnginePrivateConfig(privateConfigFile, logger);
        return instance;
    }

    public static SearchEnginePrivateConfig getInstance() {
        return instance;
    }

    public void reload(AbstractLogger logger) throws IOException {
        if (this.privateConfigFile != null && this.privateConfigFile.canRead()) {
            // Set lastModified to config file last modified
            this.lastModified = this.privateConfigFile.lastModified();

            // Reload config from config file
            String jsonStr = FileUtils.readFileToString(this.privateConfigFile, StandardCharsets.UTF_8);
            JSONObject json = (jsonStr == null || jsonStr.isEmpty()) ? new JSONObject() : new JSONObject(jsonStr);
            this.loadJSON(json, logger);
        }
    }

    public void save() throws Exception {
        if (this.privateConfigFile == null) {
            // This should not happen
            throw new IllegalStateException("The private configuration file is null.");
        }
        if (!this.privateConfigFile.canWrite()) {
            throw new IOException(String.format("The private configuration file is not writable: %s",
                    this.privateConfigFile.getAbsolutePath()));
        }

        synchronized (this.privateConfigFile) {
            // If config file was modified since last load, throw java.util.ConcurrentModificationException
            if (this.privateConfigFile.lastModified() > this.lastModified) {
                throw new ConcurrentModificationException(
                    String.format("Private configuration file %s was externally modified since last load.", this.privateConfigFile));
            }

            // Save config in config file
            JSONObject json = this.toJSON();
            FileUtils.write(this.privateConfigFile, json.toString(2), StandardCharsets.UTF_8);

            // Set this.lastModified to config file last modified
            this.lastModified = this.privateConfigFile.lastModified();
        }
    }

    public String getReindexToken() {
        return this.reindexToken;
    }

    public void setReindexToken(String reindexToken) {
        if (reindexToken != null && !reindexToken.isEmpty()) {
            this.reindexToken = reindexToken;
        }
    }

    public File getPrivateConfigFile() {
        return this.privateConfigFile;
    }

    public User getUser() {
        return this.user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public JSONObject toJSON() {
        return new JSONObject()
                .put("user", this.user == null ? null : this.user.toJSON())
                .put("reindexToken", this.reindexToken);
    }

    private void loadJSON(JSONObject json, AbstractLogger logger) {
        JSONObject jsonUser = json.optJSONObject("user");
        if (this.user == null) {
            this.user = new User(jsonUser, logger);
        } else {
            this.user.loadJSON(jsonUser, logger);
        }
        this.reindexToken = json.optString("reindexToken", null);

        if (this.reindexToken == null || this.reindexToken.isEmpty() || this.user.isModified()) {
            if (this.reindexToken == null || this.reindexToken.isEmpty()) {
                this.reindexToken = SearchEnginePrivateConfig.generateRandomToken(SearchEnginePrivateConfig.RANDOM_TOKEN_LENGTH);
            }
            try {
                this.save();
                this.user.setModified(false);
            } catch (Exception ex) {
                logger.addMessage(Level.ERROR,
                        String.format("Error occurred while saving the private configuration file: %s",
                        this.getPrivateConfigFile()), ex);
            }
        }
    }

    // Inspired from:
    //     https://stackoverflow.com/questions/41107/how-to-generate-a-random-alpha-numeric-string#41156
    public static String generateRandomToken(int length) {
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
