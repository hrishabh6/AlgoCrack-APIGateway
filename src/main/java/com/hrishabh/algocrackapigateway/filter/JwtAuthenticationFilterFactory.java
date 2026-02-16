package com.hrishabh.algocrackapigateway.filter;

import com.hrishabh.algocrackapigateway.security.JwtUtil;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Spring Cloud Gateway filter for JWT authentication.
 * 
 * Applied to protected routes via config:
 * filters:
 * - name: JwtAuthentication
 * 
 * Extracts JWT from:
 * 1. Authorization: Bearer <token> header
 * 2. jwtToken cookie (backward compatibility with Auth Service)
 * 
 * On valid token:
 * - Adds X-User-Email header to downstream request
 * - Adds X-User-Id header (if userId claim present)
 * - Adds X-User-Role header (if role claim present)
 * 
 * On invalid/missing token:
 * - Returns 401 Unauthorized with JSON error body
 */
@Component
public class JwtAuthenticationFilterFactory
        extends AbstractGatewayFilterFactory<JwtAuthenticationFilterFactory.Config> {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilterFactory.class);
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String COOKIE_NAME = "jwtToken";

    private final JwtUtil jwtUtil;

    public JwtAuthenticationFilterFactory(JwtUtil jwtUtil) {
        super(Config.class);
        this.jwtUtil = jwtUtil;
    }

    @Override
    public String name() {
        return "JwtAuthentication";
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();

            // 1. Extract token from header or cookie
            String token = extractToken(request);

            if (token == null) {
                log.debug("No JWT found in request to {}", request.getPath());
                return onUnauthorized(exchange, "Missing authentication token");
            }

            // 2. Validate token
            if (!jwtUtil.isTokenValid(token)) {
                log.debug("Invalid JWT for request to {}", request.getPath());
                return onUnauthorized(exchange, "Invalid or expired token");
            }

            // 3. Extract claims and add trusted headers for downstream services
            try {
                Claims claims = jwtUtil.validateToken(token);
                ServerHttpRequest.Builder mutatedRequest = request.mutate();

                // Always add email (subject)
                String email = claims.getSubject();
                if (email != null) {
                    mutatedRequest.header("X-User-Email", email);
                }

                // Add userId if present in claims
                Object userId = claims.get("userId");
                if (userId != null) {
                    mutatedRequest.header("X-User-Id", userId.toString());
                }

                // Add role if present in claims
                Object role = claims.get("role");
                if (role != null) {
                    mutatedRequest.header("X-User-Role", role.toString());
                }

                // Mark request as internally trusted
                mutatedRequest.header("X-Internal-Call", "true");

                // Remove the original Authorization header so downstream services
                // don't try to re-validate
                mutatedRequest.headers(headers -> headers.remove(HttpHeaders.AUTHORIZATION));

                log.debug("JWT validated for user: {} on path: {}", email, request.getPath());

                return chain.filter(exchange.mutate().request(mutatedRequest.build()).build());

            } catch (Exception e) {
                log.error("Error processing JWT: {}", e.getMessage());
                return onUnauthorized(exchange, "Token processing error");
            }
        };
    }

    /**
     * Extract JWT token from Authorization header or cookie.
     */
    private String extractToken(ServerHttpRequest request) {
        // Try Authorization header first
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            return authHeader.substring(BEARER_PREFIX.length());
        }

        // Fall back to cookie
        HttpCookie cookie = request.getCookies().getFirst(COOKIE_NAME);
        if (cookie != null) {
            return cookie.getValue();
        }

        return null;
    }

    /**
     * Return 401 Unauthorized with JSON error body.
     */
    private Mono<Void> onUnauthorized(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().add(HttpHeaders.CONTENT_TYPE, "application/json");

        String body = String.format("{\"error\":\"Unauthorized\",\"message\":\"%s\"}", message);
        byte[] bytes = body.getBytes();

        return response.writeWith(
                Mono.just(response.bufferFactory().wrap(bytes)));
    }

    /**
     * Empty config class — no filter-specific config needed.
     */
    public static class Config {
        // No custom configuration required
    }
}
