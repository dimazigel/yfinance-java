package io.github.dimazigel.yfinance.testsupport;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.slf4j.LoggerFactory;

/** Captures a logger's events (down to DEBUG) for the duration of a test; restores its level on close. */
public final class LogCapture implements AutoCloseable {

    private final Logger logger;
    private final Level previousLevel;
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    private LogCapture(String name) {
        logger = (Logger) LoggerFactory.getLogger(name);
        previousLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        appender.start();
        logger.addAppender(appender);
    }

    public static LogCapture of(Class<?> type) {
        return new LogCapture(type.getName());
    }

    /** Captures every logger under the library's root package. */
    public static LogCapture ofLibrary() {
        return new LogCapture("io.github.dimazigel.yfinance");
    }

    public List<ILoggingEvent> events() {
        return List.copyOf(appender.list);
    }

    public List<String> messages(Level level) {
        return events().stream().filter(e -> e.getLevel() == level).map(ILoggingEvent::getFormattedMessage).toList();
    }

    @Override
    public void close() {
        logger.detachAppender(appender);
        logger.setLevel(previousLevel);
    }
}
