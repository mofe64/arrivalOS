package com.arrivalos.trip.dto;

import java.time.Instant;
import java.util.UUID;

public record ConciergeAccessLinkResponse(
        UUID tripId,
        UUID conciergeId,
        String token,
        String updateUrl,
        Instant expiresAt) {
}
