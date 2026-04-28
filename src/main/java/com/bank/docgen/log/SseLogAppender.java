// src/main/java/com/bank/docgen/log/SseLogAppender.java
package com.bank.docgen.log;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.bank.docgen.util.SpringContextHolder;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 自定义 Logback Appender，把日志转发到 SSE。
 */
public class SseLogAppender extends AppenderBase<ILoggingEvent> {

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
                    .withZone(ZoneId.systemDefault());

    @Override
    protected void append(ILoggingEvent event) {
        try {
            LogBroadcaster broadcaster = SpringContextHolder.getBean(LogBroadcaster.class);
            if (broadcaster == null) {
                return;
            }

            String time = FMT.format(Instant.ofEpochMilli(event.getTimeStamp()));
            String level = event.getLevel().toString();
            String logger = shortenLogger(event.getLoggerName());
            String message = event.getFormattedMessage();

            String line = String.format("%s %-5s [%s] %s", time, level, logger, message);

            // 如果有异常堆栈
            if (event.getThrowableProxy() != null) {
                line += "\n" + getStackTrace(event);
            }

            broadcaster.broadcast(line);
        } catch (Exception ignored) {
            // appender 内部不能抛异常
        }
    }

    private String shortenLogger(String name) {
        if (name == null) return "";
        int idx = name.lastIndexOf('.');
        return idx > 0 ? name.substring(idx + 1) : name;
    }

    private String getStackTrace(ILoggingEvent event) {
        StringBuilder sb = new StringBuilder();
        var proxy = event.getThrowableProxy();
        if (proxy != null) {
            sb.append(proxy.getClassName()).append(": ").append(proxy.getMessage()).append("\n");
            if (proxy.getStackTraceElementProxyArray() != null) {
                int limit = Math.min(proxy.getStackTraceElementProxyArray().length, 10);
                for (int i = 0; i < limit; i++) {
                    sb.append("    ").append(proxy.getStackTraceElementProxyArray()[i].toString()).append("\n");
                }
                if (proxy.getStackTraceElementProxyArray().length > 10) {
                    sb.append("    ... ").append(proxy.getStackTraceElementProxyArray().length - 10).append(" more\n");
                }
            }
        }
        return sb.toString();
    }
}