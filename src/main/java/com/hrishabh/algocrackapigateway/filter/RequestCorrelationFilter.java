package com.hrishabh.algocrackapigateway.filter;

import com.hrishabh.algocrackapigateway.logging.LoggingConstants;
import com.hrishabh.algocrackapigateway.logging.StructuredLogger;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * RequestCorrelationFilter generates a unique correlation ID for each request
 * and propagates it through the entire request-response lifecycle.
 * 
 * This filter:
 * 1. Generates or retrieves a request ID (correlation ID)
 * 2. Adds it to request headers for downstream services
 * 3. Logs incoming requests with structured context
 * 4. Captures response details for logging
 */
@Component
@Order(-100)  // Run early in the filter chain
public class RequestCorrelationFilter implements GlobalFilter {

    private static final String REQUEST_ID_HEADER = "X-Request-ID";
    private static final String REQUEST_ID_ATTRIBUTE = "requestId";
    private static final String REQUEST_START_TIME = "requestStartTime";
    
    private final StructuredLogger logger;

    public RequestCorrelationFilter() {
        this.logger = new StructuredLogger(RequestCorrelationFilter.class, "APIGateway");
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Record request start time (final so it can be used in lambdas)
        final long startTime = System.currentTimeMillis();
        
        // Get or generate request ID (final so it can be used in lambdas)
        String requestIdTemp = exchange.getRequest().getHeaders().getFirst(REQUEST_ID_HEADER);
        final String requestId = requestIdTemp != null ? requestIdTemp : StructuredLogger.generateRequestId();
        
        // Store requestId and start time in exchange attributes for later access
        exchange.getAttributes().put(REQUEST_ID_ATTRIBUTE, requestId);
        exchange.getAttributes().put(REQUEST_START_TIME, startTime);
        
        // Add request ID to outgoing request headers (for downstream services)
        ServerHttpRequest modifiedRequest = exchange.getRequest().mutate()
            .header(REQUEST_ID_HEADER, requestId)
            .build();
        
        // Log incoming request
        logIncomingRequest(exchange.getRequest(), requestId);
        
        // Continue the filter chain and handle the response
        return chain.filter(exchange.mutate().request(modifiedRequest).build())
            .doFinally(signalType -> {
                // Log response (both success and error cases)
                long duration = System.currentTimeMillis() - startTime;
                logOutgoingResponse(exchange.getResponse(), requestId, duration);
                
                // Add request ID to response headers for client correlation
                exchange.getResponse().getHeaders().add(REQUEST_ID_HEADER, requestId);
            })
            .onErrorMap(throwable -> {
                // Log error and propagate the exception
                long duration = System.currentTimeMillis() - startTime;
                logErrorResponse(exchange.getResponse(), requestId, duration, throwable);
                return throwable;
            });
    }

    /**
     * Logs incoming HTTP request with structured context.
     */
    private void logIncomingRequest(ServerHttpRequest request, String requestId) {
        String method = request.getMethod() != null ? request.getMethod().toString() : "UNKNOWN";
        String path = request.getURI().getPath();
        String userAgent = request.getHeaders().getFirst("User-Agent");
        String remoteIp = getRemoteIp(request);
        
        logger.logRequest(
            requestId,
            method,
            path,
            userAgent != null ? userAgent : "unknown",
            remoteIp,
            LoggingConstants.TYPE, "REQUEST"
        );
    }

    /**
     * Logs outgoing HTTP response with status code and duration.
     */
    private void logOutgoingResponse(ServerHttpResponse response, String requestId, long durationMs) {
        int statusCode = response.getStatusCode() != null ? response.getStatusCode().value() : 200;
        
        logger.logResponse(
            requestId,
            statusCode,
            durationMs
        );
    }

    /**
     * Logs error response with exception details.
     */
    private void logErrorResponse(ServerHttpResponse response, String requestId, long durationMs, Throwable throwable) {
        int statusCode = response.getStatusCode() != null ? response.getStatusCode().value() : 500;
        
        logger.error(
            "Request failed: " + throwable.getMessage(),
            throwable,
            LoggingConstants.REQUEST_ID, requestId,
            LoggingConstants.HTTP_STATUS, statusCode,
            LoggingConstants.EVENT_TYPE, LoggingConstants.EventType.ERROR,
            LoggingConstants.DURATION_MS, durationMs,
            LoggingConstants.TYPE, LoggingConstants.getHttpStatusType(statusCode)
        );
    }

    /**
     * Extracts the remote IP from the request, checking for X-Forwarded-For header first.
     */
    private String getRemoteIp(ServerHttpRequest request) {
        String xForwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        if (request.getRemoteAddress() != null) {
            return request.getRemoteAddress().getAddress().getHostAddress();
        }
        
        return "unknown";
    }
}
