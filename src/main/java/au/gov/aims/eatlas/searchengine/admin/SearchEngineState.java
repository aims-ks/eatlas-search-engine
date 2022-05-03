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
package au.gov.aims.eatlas.searchengine.admin;

import au.gov.aims.eatlas.searchengine.index.IndexerState;
import org.apache.commons.io.FileUtils;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class SearchEngineState {

    private static SearchEngineState instance;

    private long lastModified;
    private File stateFile;

    // Key = index
    private Map<String, IndexerState> indexerStates;

    private SearchEngineState(File stateFile) throws Exception {
        this.stateFile = stateFile;
        this.reload();
    }

    public static SearchEngineState createInstance(File stateFile) throws Exception {
        instance = new SearchEngineState(stateFile);
        return instance;
    }

    public static SearchEngineState getInstance() {
        return instance;
    }

    public void reload() throws Exception {
        this.indexerStates = null;
        if (this.stateFile != null && this.stateFile.canRead()) {
            // Reload config from config file
            String jsonStr = FileUtils.readFileToString(this.stateFile, StandardCharsets.UTF_8);
            JSONObject json = (jsonStr == null || jsonStr.isEmpty()) ? new JSONObject() : new JSONObject(jsonStr);
            this.loadJSON(json);

            // Set lastModified to config file last modified
            this.lastModified = this.stateFile.lastModified();

            this.deleteOrphanStates();
        }
    }

    public void deleteOrphanStates() throws Exception {
        SearchEngineConfig config = SearchEngineConfig.getInstance();

        if (config != null && this.indexerStates != null) {
            boolean modified = false;

            // Create a copy of the list of index, to prevent "concurrent modification exception".
            Set<String> indexes = new HashSet<>(this.indexerStates.keySet());
            for (String index : indexes) {
                if (config.getIndexer(index) == null) {
                    this.indexerStates.remove(index);
                    modified = true;
                }
            }

            if (modified) {
                this.save();
            }
        }
    }

    public void save() throws Exception {
        if (this.stateFile == null) {
            throw new IllegalStateException("State file is not defined.");
        } else if (!this.stateFile.canWrite()) {
            throw new IOException(String.format("State file is not writable: %s", stateFile.getAbsolutePath()));
        } else {
            // If config file was modified since last load, throw java.util.ConcurrentModificationException
            if (this.stateFile.lastModified() > this.lastModified) {
                throw new ConcurrentModificationException(
                    String.format("State file %s was externally modified since last load.", this.stateFile));
            }

            // Save config in config file
            JSONObject json = this.toJSON();
            FileUtils.write(this.stateFile, json.toString(2), StandardCharsets.UTF_8);

            // Set this.lastModified to config file last modified
            this.lastModified = this.stateFile.lastModified();
        }
    }

    public IndexerState getIndexerState(String index) {
        return this.indexerStates == null ? null : this.indexerStates.get(index);
    }

    public IndexerState getOrAddIndexerState(String index) {
        IndexerState state = this.getIndexerState(index);
        if (state == null) {
            state = new IndexerState();
            this.addIndexerState(index, state);
            // No need to save, there is no state value worth saving yet
        }

        return state;
    }

    public void addIndexerState(String index, IndexerState indexerState) {
        if (this.indexerStates == null) {
            this.indexerStates = new HashMap<>();
        }
        this.indexerStates.put(index, indexerState);
    }

    public IndexerState removeIndexerState(String index) throws Exception {
        if (this.indexerStates == null) {
            return null;
        }

        IndexerState removed = this.indexerStates.remove(index);
        if (removed != null) {
            this.save();
        }
        return removed;
    }

    public File getStateFile() {
        return this.stateFile;
    }

    public JSONObject toJSON() {
        JSONObject jsonIndexerStates = new JSONObject();
        if (this.indexerStates != null && !this.indexerStates.isEmpty()) {
            for (Map.Entry<String, IndexerState> indexerStateEntry : this.indexerStates.entrySet()) {
                jsonIndexerStates.put(indexerStateEntry.getKey(), indexerStateEntry.getValue().toJSON());
            }
        }

        return new JSONObject()
            .put("indexerStates", jsonIndexerStates);
    }

    private void loadJSON(JSONObject json) {
        JSONObject jsonIndexerStates = json.optJSONObject("indexerStates");
        if (jsonIndexerStates != null) {
            for (String index : jsonIndexerStates.keySet()) {
                JSONObject jsonIndexerState = jsonIndexerStates.optJSONObject(index);
                IndexerState indexerState = IndexerState.fromJSON(jsonIndexerState);
                if (indexerState != null) {
                    this.addIndexerState(index, indexerState);
                }
            }
        }
    }

    @Override
    public String toString() {
        return this.toJSON().toString(2);
    }
}
