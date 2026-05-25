package com.arrivalos.auth.dto;

import java.time.Instant;
import java.util.UUID;

import com.arrivalos.domain.model.AccountType;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        Instant accessTokenExpiresAt,
        Instant refreshTokenExpiresAt,
        UserResponse user) {

    public record UserResponse(
            UUID id,
            String fullName,
            String email,
            String phone,
            AccountType accountType,
            boolean emailVerified) {
    }
}
