package com.arrivalos.auth;

import java.time.Instant;
import java.util.UUID;

import com.arrivalos.domain.model.AccountType;

public record JwtClaims(
        String tokenId,
        UUID subject,
        String email,
        String fullName,
        AccountType accountType,
        String tokenUse,
        Instant issuedAt,
        Instant expiresAt) {
}
