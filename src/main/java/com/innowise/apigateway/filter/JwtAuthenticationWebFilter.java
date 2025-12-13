package com.innowise.apigateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@Component
public class JwtAuthenticationWebFilter implements WebFilter {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${services.service-key}")
    private String serviceKey;

    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/v1/auth/register",
            "/api/v1/auth/login",
            "/actuator"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        if (isPublicPath(path)) {
            log.debug("Public path accessed: {}", path);
            ServerHttpRequest modifiedRequest = request
                    .mutate()
                    .header("X-Service-Key", serviceKey)
                    .build();
            return chain.filter(exchange.mutate().request(modifiedRequest).build());
        }

        if (!request.getHeaders().containsKey(HttpHeaders.AUTHORIZATION)) {
            log.warn("Missing Authorization header for path: {}", path);
            return chain.filter(exchange);
        }

        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Invalid Authorization header format for path: {}", path);
            return chain.filter(exchange);
        }

        String token = authHeader.substring(7);

        try {
            Claims claims = extractClaims(token);
            List<SimpleGrantedAuthority> authorities = List.of(
                    new SimpleGrantedAuthority("ROLE_" + claims.get("role").toString())
            );

            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    claims.get("userId"),
                    null,
                    authorities
            );

            ServerHttpRequest modifiedRequest = exchange.getRequest()
                    .mutate()
                    .header("X-User-Id", claims.get("userId").toString())
                    .header("X-User-Email", claims.get("email").toString())
                    .header("X-User-Role", claims.get("role").toString())
                    .header("X-Service-Key", serviceKey)
                    .build();

            ServerWebExchange modifiedExchange = exchange.mutate()
                    .request(modifiedRequest)
                    .build();

            log.debug("Successfully authenticated user {} for path: {}",
                    claims.get("userId"), path);

            return chain.filter(modifiedExchange)
                    .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication));
        } catch (Exception e) {
            log.error("Token validation error: {}", e.getMessage());
            return chain.filter(exchange);
        }

    }

    private boolean isPublicPath(String path) {
        return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
    }

    private Claims extractClaims(String token) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
