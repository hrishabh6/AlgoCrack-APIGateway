package com.hrishabh.algocrackapigateway.logging;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.logstash.logback.argument.StructuredArguments;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * StructuredLogger provides a consistent, JSON-serializable logging interface.
 * All logs are emitted as JSON with structured fields for easy parsing and dashboarding.
 * 
 * Example usage:
 *   StructuredLogger logger = new StructuredLogger(MyClass.class);
 *   logger.info("User logged in", 
 *       LoggingConstants.USER_ID, "user123",
 *       LoggingConstants.EVENT_TYPE, LoggingConstants.EventType.AUTH,
 *       "provider", "google");
 */
public class StructuredLogger {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final int MAX_STRING_LENGTH = 500;
    private final Logger slf4jLogger;
    private final String serviceName;
    private final String component;

    public StructuredLogger(Class<?> clazz, String serviceName) {
        this.slf4jLogger = LoggerFactory.getLogger(clazz);
        this.serviceName = serviceName;
        this.component = clazz.getSimpleName();
    }

    public StructuredLogger(Class<?> clazz, String serviceName, String component) {
        this.slf4jLogger = LoggerFactory.getLogger(clazz);
        this.serviceName = serviceName;
        this.component = component;
    }

    /**
     * Logs an info-level structured message with key-value pairs.
     * Fields are passed as alternating key-value arguments.
     */
    public void info(String message, Object... fields) {
        logAtLevel("INFO", message, fields);
    }

    /**
     * Logs a warning-level structured message.
     */
    public void warn(String message, Object... fields) {
        logAtLevel("WARN", message, fields);
    }

    /**
     * Logs an error-level structured message.
     */
    public void error(String message, Object... fields) {
        logAtLevel("ERROR", message, fields);
    }

    /**
     * Logs an error with exception details.
     */
    public void error(String message, Throwable throwable, Object... fields) {
        Map<String, Object> logEntry = buildLogEntry("ERROR", message, fields);
        if (throwable != null) {
            logEntry.put(LoggingConstants.EXCEPTION_CLASS, throwable.getClass().getName());
            logEntry.put(LoggingConstants.ERROR_MESSAGE, sanitizeValue(LoggingConstants.ERROR_MESSAGE, throwable.getMessage()));
        }
        slf4jLogger.error(message, StructuredArguments.entries(logEntry), throwable);
    }

    /**
     * Logs a debug-level structured message.
     */
    public void debug(String message, Object... fields) {
        if (slf4jLogger.isDebugEnabled()) {
            logAtLevel("DEBUG", message, fields);
        }
    }

    /**
     * Logs a request with standard HTTP context.
     */
    public void logRequest(String requestId, String method, String path, String userAgent, String remoteIp, Object... additionalFields) {
        Map<String, Object> logEntry = buildLogEntry("INFO", "Incoming HTTP request", additionalFields);
        logEntry.put(LoggingConstants.REQUEST_ID, requestId);
        logEntry.put(LoggingConstants.EVENT_TYPE, LoggingConstants.EventType.REQUEST);
        logEntry.put(LoggingConstants.HTTP_METHOD, method);
        logEntry.put(LoggingConstants.HTTP_PATH, path);
        logEntry.put(LoggingConstants.USER_AGENT, userAgent);
        logEntry.put(LoggingConstants.REMOTE_IP, remoteIp);
        slf4jLogger.info("Incoming HTTP request", StructuredArguments.entries(logEntry));
    }

    /**
     * Logs a response with HTTP status and timing.
     */
    public void logResponse(String requestId, int statusCode, long durationMs, Object... additionalFields) {
        Map<String, Object> logEntry = buildLogEntry("INFO", "HTTP response", additionalFields);
        logEntry.put(LoggingConstants.REQUEST_ID, requestId);
        logEntry.put(LoggingConstants.EVENT_TYPE, LoggingConstants.EventType.RESPONSE);
        logEntry.put(LoggingConstants.HTTP_STATUS, statusCode);
        logEntry.put(LoggingConstants.TYPE, LoggingConstants.getHttpStatusType(statusCode));
        logEntry.put(LoggingConstants.DURATION_MS, durationMs);
        slf4jLogger.info("HTTP response", StructuredArguments.entries(logEntry));
    }

    /**
     * Generates a new request correlation ID.
     */
    public static String generateRequestId() {
        return UUID.randomUUID().toString();
    }

    /**
     * Internal method to log at a specific level.
     */
    private void logAtLevel(String level, String message, Object... fields) {
        Map<String, Object> logEntry = buildLogEntry(level, message, fields);
        
        switch (level) {
            case "ERROR":
                slf4jLogger.error(message, StructuredArguments.entries(logEntry));
                break;
            case "WARN":
                slf4jLogger.warn(message, StructuredArguments.entries(logEntry));
                break;
            case "DEBUG":
                slf4jLogger.debug(message, StructuredArguments.entries(logEntry));
                break;
            case "INFO":
            default:
                slf4jLogger.info(message, StructuredArguments.entries(logEntry));
                break;
        }
    }

    /**
     * Builds a log entry map with standard fields and custom fields.
     */
    private Map<String, Object> buildLogEntry(String level, String message, Object... fields) {
        Map<String, Object> logEntry = new LinkedHashMap<>();
        
        logEntry.put(LoggingConstants.COMPONENT, component);
        logEntry.putIfAbsent(LoggingConstants.TYPE, level);
        logEntry.putIfAbsent(LoggingConstants.EVENT_TYPE, LoggingConstants.EventType.LIFECYCLE);
        
        // Add custom fields (key-value pairs)
        if (fields != null && fields.length > 0) {
            if (fields.length % 2 != 0) {
                throw new IllegalArgumentException("Fields must be passed as key-value pairs");
            }
            for (int i = 0; i < fields.length; i += 2) {
                String key = String.valueOf(fields[i]);
                Object value = sanitizeValue(key, fields[i + 1]);
                logEntry.put(key, value);
            }
        }
        
        return logEntry;
    }

    /**
     * Serializes a log entry map to JSON string.
     */
    private String serializeToJson(Map<String, Object> logEntry) {
        try {
            return objectMapper.writeValueAsString(logEntry);
        } catch (Exception e) {
            // Fallback if JSON serialization fails
            return logEntry.toString();
        }
    }

    private Object sanitizeValue(String key, Object value) {
        if (value == null) {
            return null;
        }
        String normalizedKey = key == null ? "" : key.toLowerCase();
        if (isSensitiveKey(normalizedKey)) {
            return "[REDACTED]";
        }
        if (value instanceof String stringValue && stringValue.length() > MAX_STRING_LENGTH) {
            return stringValue.substring(0, MAX_STRING_LENGTH) + "...[truncated]";
        }
        return value;
    }

    private boolean isSensitiveKey(String key) {
        return key.contains("authorization")
                || key.contains("password")
                || key.equals("token")
                || key.endsWith("_token")
                || key.contains("jwt")
                || key.contains("cookie")
                || key.equals("code")
                || key.equals("body")
                || key.equals("payload")
                || key.equals("testcase")
                || key.equals("testcases")
                || key.equals("input")
                || key.equals("output");
    }
}
