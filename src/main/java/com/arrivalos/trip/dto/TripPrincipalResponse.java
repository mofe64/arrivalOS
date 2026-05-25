package com.arrivalos.trip.dto;

import java.util.UUID;

import com.arrivalos.domain.model.TripPrincipal;

public record TripPrincipalResponse(
        UUID id,
        UUID userAccountId,
        String fullName,
        String phone,
        String photoUrl,
        boolean primaryContact,
        int sequenceNumber) {

    public static TripPrincipalResponse from(TripPrincipal principal) {
        return new TripPrincipalResponse(
                principal.getId(),
                principal.getUserAccount() == null ? null : principal.getUserAccount().getId(),
                principal.getFullName(),
                principal.getPhone(),
                principal.getPhotoUrl(),
                principal.isPrimaryContact(),
                principal.getSequenceNumber());
    }
}
