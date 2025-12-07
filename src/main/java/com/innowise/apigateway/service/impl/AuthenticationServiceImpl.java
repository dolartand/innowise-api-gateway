package com.innowise.apigateway.service.impl;

import com.innowise.apigateway.dto.AuthResponse;
import com.innowise.apigateway.dto.RegisterRequest;
import com.innowise.apigateway.exception.RegistrationException;
import com.innowise.apigateway.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthenticationServiceImpl implements AuthService {

    @Qualifier("authServiceWebClient")
    private final WebClient authServiceWebClient;

    @Qualifier("userServiceWebClient")
    private final WebClient userServiceWebClient;

    @Override
    public Mono<AuthResponse> register(RegisterRequest request) {
        log.info("Start registration for user with email: {}", request.email());

        return createUserInUserService(request)
                .flatMap(userId -> createCredentialsInAuthService(request, userId)
                        .onErrorResume(authError -> {
                            log.error("Failed to create credentials in auth service, rollback user creation: {}",
                                    authError.getMessage());
                            return rollbackUserCreation(userId)
                                    .then(Mono.error(new RegistrationException(
                                            "Registration failed: Could not create authentication credentials. " +
                                                    "User creation has been rolled back."
                                    )));
                        })
                )
                .doOnSuccess(response -> log.info("Successfully completed registration for user: {}",
                        response.userId()))
                .doOnError(error -> log.error("Registration failed: {}", error.getMessage()));
    }

    private Mono<Long> createUserInUserService(RegisterRequest request) {
        log.debug("Creating user in user service with email: {}", request.email());

        Map<String, Object> userRequest = Map.of(
                "name", request.name(),
                "surname", request.surname(),
                "birthDate", request.birthDate().toString(),
                "email", request.email(),
                "active", true
        );

        return userServiceWebClient
                .post()
                .uri("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(userRequest)
                .retrieve()
                .onStatus(
                        status -> status.value() == 409,
                        response -> Mono.error(new RegistrationException(
                                "User with this email already exists"))
                )
                .onStatus(
                        HttpStatusCode::isError,
                        response -> response.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(new RegistrationException(
                                        "Failed to create user in user service: " + body)))
                )
                .bodyToMono(Map.class)
                .map(response -> {
                    Object isObj = response.get("id");
                    if (isObj instanceof Integer) {
                        return ((Integer) isObj).longValue();
                    } else if (isObj instanceof Long) {
                        return (Long) isObj;
                    }
                    throw new RegistrationException("Invalid user ID format returned from User Service");
                })
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
                        .filter(throwable -> !(throwable instanceof RegistrationException))
                        .doBeforeRetry(retrySignal -> log.warn(
                                "Retry user creation, attempt: {}", retrySignal.totalRetries() + 1))
                )
                .doOnSuccess(userId -> log.info("Successfully created user in User Service with id: {}", userId))
                .doOnError(error -> log.error("Failed to create user in user service: {}", error.getMessage()));
    }

    private Mono<AuthResponse> createCredentialsInAuthService(RegisterRequest request, Long userId) {
        log.debug("Creating credentials in Auth Service for userId: {}", userId);

        Map<String, Object> authRequest = Map.of(
                "name", request.name(),
                "surname", request.surname(),
                "birthDate", request.birthDate().toString(),
                "email", request.email(),
                "password", request.password()
        );

        return authServiceWebClient
                .post()
                .uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(authRequest)
                .retrieve()
                .onStatus(
                        HttpStatusCode::isError,
                        response -> response.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(new RegistrationException(
                                        "Failed to create credentials in Auth Service: " + body)))
                )
                .bodyToMono(AuthResponse.class)
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
                        .filter(throwable -> !(throwable instanceof RegistrationException))
                        .doBeforeRetry(retrySignal -> log.warn(
                                "Retrying credentials creation, attempt: {}",
                                retrySignal.totalRetries() + 1))
                )
                .doOnSuccess(response -> log.info(
                        "Successfully created credentials in Auth Service for userId: {}", userId))
                .doOnError(error -> log.error(
                        "Failed to create credentials in Auth Service: {}", error.getMessage()));
    }

    private Mono<Void> rollbackUserCreation(Long userId) {
        log.warn("Rolling back user creation for userId: {}", userId);

        return userServiceWebClient
                .delete()
                .uri("/api/v1/users/{id}", userId)
                .retrieve()
                .bodyToMono(Void.class)
                .doOnSuccess(v -> log.info("Successfully rolled back user creation for userId: {}", userId))
                .doOnError(error -> log.error(
                        "Failed to rollback user creation for userId: {}. Error: {}",
                        userId, error.getMessage()))
                .onErrorResume(error -> {
                    log.error("Rollback failed but continuing with error propagation");
                    return Mono.empty();
                });
    }
}
