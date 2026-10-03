package com.hrishabh.algocrackapigateway.filter;

import com.hrishabh.algocrackapigateway.security.JwtUtil;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterFactoryTest {

    private final JwtUtil jwtUtil = mock(JwtUtil.class);
    private final GatewayFilter filter =
            new JwtAuthenticationFilterFactory(jwtUtil).apply(new JwtAuthenticationFilterFactory.Config());

    private ServerHttpRequest forward(MockServerHttpRequest request) {
        AtomicReference<ServerHttpRequest> downstream = new AtomicReference<>();
        filter.filter(MockServerWebExchange.from(request), exchange -> {
            downstream.set(exchange.getRequest());
            return Mono.empty();
        }).block();
        return downstream.get();
    }

    private Claims claims(String subject, Object userId) {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn(subject);
        when(claims.get("userId")).thenReturn(userId);
        when(claims.get("role")).thenReturn("USER");
        return claims;
    }

    @Test
    void clientSuppliedUserIdIsDroppedWhenTokenHasNoUserIdClaim() {
        when(jwtUtil.isTokenValid("tok")).thenReturn(true);
        Claims claims = claims("alice@example.com", null);
        when(jwtUtil.validateToken("tok")).thenReturn(claims);

        ServerHttpRequest downstream = forward(MockServerHttpRequest.get("/api/v1/problem-lists")
                .header(HttpHeaders.AUTHORIZATION, "Bearer tok")
                .header("X-User-Id", "victim")
                .build());

        assertThat(downstream.getHeaders().get("X-User-Id")).isNull();
        assertThat(downstream.getHeaders().getFirst("X-User-Email")).isEqualTo("alice@example.com");
        assertThat(downstream.getHeaders().get(HttpHeaders.AUTHORIZATION)).isNull();
    }

    @Test
    void userIdComesOnlyFromToken() {
        when(jwtUtil.isTokenValid("tok")).thenReturn(true);
        Claims claims = claims("alice@example.com", "alice-id");
        when(jwtUtil.validateToken("tok")).thenReturn(claims);

        ServerHttpRequest downstream = forward(MockServerHttpRequest.get("/api/v1/problem-lists")
                .header(HttpHeaders.AUTHORIZATION, "Bearer tok")
                .header("X-User-Id", "victim")
                .header("X-User-Role", "ADMIN")
                .build());

        assertThat(downstream.getHeaders().get("X-User-Id")).containsExactly("alice-id");
        assertThat(downstream.getHeaders().get("X-User-Role")).containsExactly("USER");
    }

    @Test
    void missingTokenIsUnauthorized() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/problem-lists").header("X-User-Id", "victim").build());

        filter.filter(exchange, e -> Mono.error(new AssertionError("must not forward"))).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
