package com.arrivalos.trip.dto;

import java.util.UUID;

import com.arrivalos.domain.model.Concierge;

public record ConciergeResponse(
        UUID id,
        String fullName,
        String phone,
        String photoUrl,
        String publicId,
        boolean active) {

    public static ConciergeResponse from(Concierge concierge) {
        return new ConciergeResponse(
                concierge.getId(),
                concierge.getFullName(),
                concierge.getPhone(),
                concierge.getPhotoUrl(),
                concierge.getPublicId(),
                concierge.isActive());
    }
}
