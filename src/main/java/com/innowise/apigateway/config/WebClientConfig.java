package com.innowise.apigateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Value("${services.auth.url}")
    private String authUrl;

    @Value("${services.user.url}")
    private String userUrl;

    @Value("${services.service-key}")
    private String serviceKey;

    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder()
                .defaultHeader("X-Service-Key", serviceKey)
                .defaultHeader("X-Service-Name", "api-gateway");
    }

    @Bean
    public WebClient authServiceWebClient(WebClient.Builder builder) {
        return builder
                .baseUrl(authUrl)
                .build();
    }

    @Bean
    public WebClient userServiceWebClient(WebClient.Builder builder) {
        return builder
                .baseUrl(userUrl)
                .build();
    }
}
