package com.arrivalos.trip.dto;

import java.time.Instant;

import com.arrivalos.domain.model.TimelineEventType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ConciergeTripTransitionRequest(
        @NotBlank
        String accessToken,

        @NotNull
        TimelineEventType eventType,

        @Size(max = 120)
        String checkpointName,

        @Size(max = 1000)
        String note,

        @NotBlank
        @Size(max = 120)
        String idempotencyKey,

        Instant occurredAt,

        Instant offlineCreatedAt) {
}
