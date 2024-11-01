package au.gov.aims.eatlas.searchengine.logger;

import jakarta.servlet.http.HttpSession;

import java.util.List;

public class SessionLogger extends AbstractLogger {
    private static final int MAX_CONSUME_MESSAGE = 100;

    private final HttpSession session;

    private SessionLogger(HttpSession session) {
        this.session = session;
    }

    public static AbstractLogger getInstance(HttpSession session) {
        if (session == null) {
            throw new IllegalArgumentException("Session is null");
        }

        AbstractLogger loggerInstance = (AbstractLogger)session.getAttribute("logger");
        if (loggerInstance == null) {
            loggerInstance = new SessionLogger(session);
            session.setAttribute("logger", loggerInstance);
        }

        return loggerInstance;
    }

    public void save() {
        this.session.setAttribute("logger", this);
        super.save();
    }

    public void addMessage(Message messageObj) {
        List<Message> messages = this.getMessages();
        messages.add(messageObj);
        this.save();
    }

    public List<Message> consume() {
        return consume(MAX_CONSUME_MESSAGE);
    }
    public List<Message> consume(int max) {
        List<Message> messages = this.getMessages();
        if (messages.size() > max) {
            List<Message> deletedMessages = messages.subList(0, max-1);
            this.setMessages(messages.subList(max, messages.size() - 1));
            this.save();
            return deletedMessages;
        }

        this.setMessages(null);
        this.save();
        return messages;
    }
}
