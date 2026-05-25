package com.arrivalos.trip.dto;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

public record CreateConciergeAccessLinkRequest(
        @NotNull
        UUID conciergeId,

        @NotNull
        @Future
        Instant expiresAt) {
}
