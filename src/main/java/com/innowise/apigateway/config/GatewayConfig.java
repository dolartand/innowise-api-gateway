package com.innowise.apigateway.config;

import com.innowise.apigateway.filter.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class GatewayConfig {

    private final JwtAuthenticationFilter filter;

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("auth-service", r -> r
                        .path("/api/v1/auth/**")
                        .filters(f -> f.filter(filter.apply(
                                new JwtAuthenticationFilter.Config())))
                        .uri("lb://auth-service"))
                .route("user-service", r -> r
                        .path("/api/v1/users/**")
                        .filters(f -> f.filter(filter.apply(
                                new JwtAuthenticationFilter.Config())))
                        .uri("lb://user-service"))
                .route("order-service", r -> r
                        .path("/api/v1/orders/**", "/api/v1/items/**")
                        .filters(f -> f.filter(filter.apply(
                                new JwtAuthenticationFilter.Config())))
                        .uri("lb://order-service"))
                .build();
    }
}
