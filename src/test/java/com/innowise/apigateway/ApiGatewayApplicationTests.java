package com.innowise.apigateway;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@SpringBootTest(webEnvironment =  SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiGatewayApplicationTests {

    @LocalServerPort
    private int port;

    @Autowired
    private WebTestClient webClient;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${services.service-key}")
    private String serviceKey;

    @DynamicPropertySource
    static void configureDynamicProperties(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "test-secret-key-that-is-long-enough-for-hs256-algorithm-minimum-32-bytes");
        registry.add("service.api.key", () -> "test-service-key");
    }

    private String generateJwtToken(Long userId, String email, String role) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .claim("userId", userId)
                .claim("email", email)
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(key)
                .compact();
    }

    private String generateExpiredToken(Long userId, String email, String role) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets. UTF_8));
        return Jwts.builder()
                .claim("userId", userId)
                .claim("email", email)
                .claim("role", role)
                .issuedAt(new Date(System.currentTimeMillis() - 7200000))
                .expiration(new Date(System.currentTimeMillis() - 3600000))
                .signWith(key)
                .compact();
    }

    @Nested
    @DisplayName("Public endpoints tests")
    class PublicEndpointsTests {

        @Test
        @DisplayName("should allow access to /api/v1/auth/login without token")
        void shouldAllowLoginWithoutToken() {
            webClient. post()
                    .uri("/api/v1/auth/login")
                    .exchange()
                    .expectStatus().isNotFound();
        }

        @Test
        @DisplayName("should allow access to /api/v1/auth/register without token")
        void shouldAllowRegisterWithoutToken() {
            webClient.post()
                    .uri("/api/v1/auth/register")
                    .exchange()
                    .expectStatus().isNotFound();
        }

        @Test
        @DisplayName("should allow access to /actuator/health without token")
        void shouldAllowActuatorWithoutToken() {
            webClient.get()
                    . uri("/actuator/health")
                    .exchange()
                    .expectStatus().isOk();
        }
    }

    @Nested
    @DisplayName("Private endpoints tests")
    class PrivateEndpointsTests {

        @Test
        @DisplayName("should return 401 when no auth header")
        void shouldReturn401WhenNoAuthHeader() {
            webClient.get()
                    .uri("/api/v1/users/1")
                    .exchange()
                    .expectStatus().isUnauthorized();
        }

        @Test
        @DisplayName("should return 401 when invalid token format")
        void shouldReturn401WhenInvalidTokenFormat() {
            webClient.get()
                    .uri("/api/v1/users/1")
                    .header("Authorization", "Invalid token")
                    .exchange()
                    .expectStatus().isUnauthorized();
        }

        @Test
        @DisplayName("should return 401 when token is expired")
        void shouldReturn401WhenTokenExpired() {
            String expiredToken = generateExpiredToken(1L, "test@example.com", "USER");

            webClient.get()
                    .uri("/api/v1/users/1")
                    .header("Authorization", "Bearer " + expiredToken)
                    .exchange()
                    .expectStatus().isUnauthorized();
        }

        @Test
        @DisplayName("should return 401 when token signature is invalid")
        void shouldReturn401WhenInvalidSignature() {
            String invalidToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VySWQiOjEsImVtYWlsIjoidGVzdEBleGFtcGxlLmNvbSIsInJvbGUiOiJVU0VSIn0.invalid_signature";

            webClient.get()
                    .uri("/api/v1/users/1")
                    .header("Authorization", "Bearer " + invalidToken)
                    .exchange()
                    .expectStatus().isUnauthorized();
        }
    }

}
