package com.kadaibook.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Size(max = 20) String phone,
            @NotBlank @Size(min = 8, max = 100) String password,
            @NotBlank @Size(max = 120) String displayName
    ) {
    }

    public record LoginRequest(
            @NotBlank @Size(max = 20) String phone,
            @NotBlank @Size(max = 100) String password
    ) {
    }

    public record UserDto(UUID id, String phone, String displayName, String role) {
    }

    public record AuthResponse(String token, Instant expiresAt, UserDto user) {
    }
}
