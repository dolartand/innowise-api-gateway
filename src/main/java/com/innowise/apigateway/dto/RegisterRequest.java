package com.innowise.apigateway.dto;

import jakarta.validation.constraints.*;
import lombok.Builder;

import java.time.LocalDate;

@Builder
public record RegisterRequest(
        @NotBlank(message = "Name is required")
        @Size(min = 3, message = "Name must be at least 3 characters long")
        String name,

        @NotBlank(message = "Surname is required")
        @Size(min = 3, message = "Surname must be at least 3 characters long")
        String surname,

        @NotNull(message = "Birth date is required")
        @Past(message = "Birth date must be in past")
        LocalDate birthDate,

        @NotBlank(message = "Email is required")
        @Email(message = "Email should be valid")
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 8, message = "Password must be at least 8 characters long")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).*$",
                message = "Password must contain at least one uppercase letter, one lowercase letter, and one digit"
        )
        String password
) {
}