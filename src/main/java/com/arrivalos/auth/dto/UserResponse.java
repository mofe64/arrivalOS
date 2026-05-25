package com.arrivalos.auth.dto;

import java.util.UUID;

import com.arrivalos.domain.model.AccountType;

public record UserResponse(
        UUID id,
        String fullName,
        String email,
        String phone,
        AccountType accountType,
        boolean active,
        boolean emailVerified) {
}
