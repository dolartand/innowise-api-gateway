package com.innowise.apigateway.service;

import com.innowise.apigateway.dto.AuthResponse;
import com.innowise.apigateway.dto.RegisterRequest;
import reactor.core.publisher.Mono;

public interface AuthService {
    Mono<AuthResponse> register(RegisterRequest request);
}
