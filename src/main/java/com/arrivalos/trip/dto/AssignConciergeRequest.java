package com.arrivalos.trip.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record AssignConciergeRequest(
        @NotNull
        UUID conciergeId) {
}
