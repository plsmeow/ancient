package tech.onetap.util.log;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public class ClientLogBuffer {

    private static final int MAX_LINES = 600;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private static final ArrayDeque<String> lines = new ArrayDeque<>();
    private static volatile boolean attached;

    public static void attach() {
        if (attached) return;
        synchronized (ClientLogBuffer.class) {
            if (attached) return;
            try {
                LoggerContext context = (LoggerContext) LogManager.getContext(false);

                Appender appender = new AbstractAppender("AncientLogBuffer", (Filter) null, null, true, Property.EMPTY_ARRAY) {
                    @Override
                    public void append(LogEvent event) {
                        push("[" + LocalTime.now().format(TIME_FORMAT) + "] ["
                                + event.getThreadName() + "/" + event.getLevel().name() + "]: "
                                + event.getMessage().getFormattedMessage());
                    }
                };
                appender.start();
                context.getConfiguration().addAppender(appender);
                context.getRootLogger().addAppender(appender);
                context.updateLoggers();
                attached = true;
            } catch (Throwable ignored) {
            }
        }
    }

    private static void push(String line) {
        synchronized (lines) {
            if (lines.size() >= MAX_LINES) lines.pollFirst();
            lines.addLast(line);
        }
    }

    public static List<String> tail(int count) {
        synchronized (lines) {
            List<String> snapshot = new ArrayList<>(lines);
            if (snapshot.size() <= count) return snapshot;
            return snapshot.subList(snapshot.size() - count, snapshot.size());
        }
    }
}
