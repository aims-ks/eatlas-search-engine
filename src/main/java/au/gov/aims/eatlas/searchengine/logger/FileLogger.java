package au.gov.aims.eatlas.searchengine.logger;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class FileLogger extends AbstractLogger {
    private static final Logger LOGGER = Logger.getLogger(FileLogger.class.getName());

    private final File messagesFile;

    public FileLogger(File messagesFile) {
        if (messagesFile == null) {
            throw new IllegalArgumentException("File is null");
        }

        this.messagesFile = messagesFile;
    }

    public void save() {
        if (this.isDirty()) {
            try {
                FileUtils.write(this.messagesFile, this.toString(4), "UTF-8");
                super.save();
            } catch (IOException ex) {
                LOGGER.error(String.format("Error while saving the FileLogger: %s", this.messagesFile.getAbsolutePath()), ex);
            }
        }
    }

    public void load() {
        if (this.messagesFile.exists()) {
            try {
                String content = FileUtils.readFileToString(this.messagesFile, "UTF-8");
                this.loadJSON(new JSONObject(content));
            } catch (IOException ex) {
                LOGGER.error(String.format("Error while loading the FileLogger: %s", this.messagesFile.getAbsolutePath()), ex);
            }
        }
    }

    public void addMessage(Message messageObj) {
        List<Message> messages = this.getMessages();
        messages.add(messageObj);
        this.setDirty(true);
    }
}
