package com.innowise.apigateway.controller;

import com.innowise.apigateway.dto.AuthResponse;
import com.innowise.apigateway.dto.LoginRequest;
import com.innowise.apigateway.dto.RegisterRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    @Qualifier("authServiceWebClient")
    private final WebClient authServiceWebClient;

    /**
     * Registration using Gateway
     * @param request data for register
     * @return 201 CREATED
     */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        log.info("Registration request received from email: {}", request.email());
        return authServiceWebClient
                .post()
                .uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(AuthResponse.class)
                .doOnSuccess(response -> log.info("Successfully registered user: {}", response.email()))
                .doOnError(error -> log.error("Registration failed: {}", error.getMessage()));
    }

    @PostMapping("/login")
    public Mono<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("Login request received from email: {}", request.email());

        return authServiceWebClient
                .post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(AuthResponse.class)
                .doOnSuccess(response -> log.info("Successfully logged in user with email: {}",
                        response.email()))
                .doOnError(error -> log.error("Login failed for email: {}", request.email()));
    }
}
