package com.arrivalos.trip.dto;

import java.util.List;
import java.util.UUID;

import com.arrivalos.domain.model.AppUser;

public record AdminPrincipalSummaryResponse(
        UUID id,
        String fullName,
        String email,
        String phone,
        boolean active,
        boolean emailVerified,
        List<AdminPrincipalTripResponse> trips) {

    public static AdminPrincipalSummaryResponse from(AppUser principal, List<AdminPrincipalTripResponse> trips) {
        return new AdminPrincipalSummaryResponse(
                principal.getId(),
                principal.getFullName(),
                principal.getEmail(),
                principal.getPhone(),
                principal.isActive(),
                principal.isEmailVerified(),
                trips);
    }
}
