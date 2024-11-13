package au.gov.aims.eatlas.searchengine.logger;

import java.util.List;

public class MemoryLogger extends AbstractLogger {
    public void addMessage(Message messageObj) {
        List<Message> messages = this.getMessages();
        messages.add(messageObj);
    }
}
