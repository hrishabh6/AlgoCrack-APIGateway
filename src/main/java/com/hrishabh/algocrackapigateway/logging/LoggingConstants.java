package com.hrishabh.algocrackapigateway.logging;

/**
 * Centralized logging constants and field names for structured logging.
 * Provides a unified contract for all microservices to follow.
 * 
 * Usage: Use these constants in StructuredLogger when logging events
 */
public class LoggingConstants {

    // ==================== Standard Fields ====================
    
    public static final String TIMESTAMP = "timestamp";
    public static final String LEVEL = "level";
    public static final String MESSAGE = "message";
    public static final String SERVICE = "service";
    public static final String ENVIRONMENT = "environment";
    
    // ==================== Correlation & Tracing ====================
    
    public static final String REQUEST_ID = "request_id";
    public static final String TRACE_ID = "trace_id";
    public static final String SPAN_ID = "span_id";
    public static final String USER_ID = "user_id";
    public static final String SESSION_ID = "session_id";
    
    // ==================== Request Context ====================
    
    public static final String HTTP_METHOD = "http_method";
    public static final String HTTP_PATH = "http_path";
    public static final String HTTP_STATUS = "http_status";
    public static final String DURATION_MS = "duration_ms";
    public static final String REMOTE_IP = "remote_ip";
    public static final String USER_AGENT = "user_agent";
    
    // ==================== Business Context ====================
    
    public static final String SUBMISSION_ID = "submission_id";
    public static final String QUESTION_ID = "question_id";
    public static final String EXECUTION_ID = "execution_id";
    public static final String JUDGE_ID = "judge_id";
    public static final String LANGUAGE = "language";
    public static final String PROVIDER = "provider";
    
    // ==================== Error/Event Context ====================
    
    public static final String EVENT_TYPE = "event_type";
    public static final String ERROR_MESSAGE = "error_message";
    public static final String ERROR_CODE = "error_code";
    public static final String EXCEPTION_CLASS = "exception_class";
    public static final String STACK_TRACE = "stack_trace";
    
    // ==================== Microservice Tags ====================
    
    public static final String KUBE_MICRO = "kube_micro";  // e.g., "AuthService", "SubmissionService"
    public static final String TYPE = "type";               // e.g., "Error", "Warning", "Info", "Request", "Response"
    public static final String COMPONENT = "component";     // e.g., "Controller", "Service", "Filter", "Worker"
    
    // ==================== Performance/Resource ====================
    
    public static final String MEMORY_MB = "memory_mb";
    public static final String CPU_PERCENT = "cpu_percent";
    public static final String DB_QUERY_MS = "db_query_ms";
    public static final String CACHE_HIT = "cache_hit";
    
    // ==================== Common Event Types ====================
    
    public static class EventType {
        public static final String REQUEST = "REQUEST";
        public static final String RESPONSE = "RESPONSE";
        public static final String ERROR = "ERROR";
        public static final String AUTH = "AUTH";
        public static final String SUBMISSION = "SUBMISSION";
        public static final String EXECUTION = "EXECUTION";
        public static final String DATABASE = "DATABASE";
        public static final String CACHE = "CACHE";
        public static final String EXTERNAL_CALL = "EXTERNAL_CALL";
        public static final String LIFECYCLE = "LIFECYCLE";
    }
    
    // ==================== HTTP Status Categories ====================
    
    public static class HttpStatusType {
        public static final String SUCCESS = "SUCCESS";         // 2xx
        public static final String REDIRECT = "REDIRECT";       // 3xx
        public static final String CLIENT_ERROR = "CLIENT_ERROR"; // 4xx
        public static final String SERVER_ERROR = "SERVER_ERROR"; // 5xx
    }
    
    // ==================== Helper Methods ====================
    
    public static String getHttpStatusType(int statusCode) {
        if (statusCode >= 200 && statusCode < 300) {
            return HttpStatusType.SUCCESS;
        } else if (statusCode >= 300 && statusCode < 400) {
            return HttpStatusType.REDIRECT;
        } else if (statusCode >= 400 && statusCode < 500) {
            return HttpStatusType.CLIENT_ERROR;
        } else if (statusCode >= 500) {
            return HttpStatusType.SERVER_ERROR;
        }
        return "UNKNOWN";
    }
}
