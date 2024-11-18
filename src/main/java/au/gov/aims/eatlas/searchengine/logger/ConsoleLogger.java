package au.gov.aims.eatlas.searchengine.logger;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class ConsoleLogger extends AbstractLogger {
    private static final Logger LOGGER = LogManager.getLogger(ConsoleLogger.class.getName());
    private static final ConsoleLogger instance = new ConsoleLogger();

    private ConsoleLogger() { }

    public static ConsoleLogger getInstance() {
        return instance;
    }

    public void addMessage(Message messageObj) {
        Level level = messageObj.getLevel();
        MessageException exception = messageObj.getException();
        String messageStr = messageObj.getMessage();

        // There is no session. No one will ever see those messages. Better display them in the console.
        String logMessage = messageStr;
        if (exception != null) {
            logMessage = String.format("%s%n%s", messageStr, exception.toString(4));
        }
        switch (level) {
            case INFO:
                LOGGER.info(logMessage);
                break;

            case WARNING:
                LOGGER.warn(logMessage);
                break;

            case ERROR:
            default:
                LOGGER.error(logMessage);
                break;
        }
    }
}
