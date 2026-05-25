package com.arrivalos.trip;

import java.time.Instant;
import java.util.UUID;

import com.arrivalos.domain.model.ActorType;
import com.arrivalos.domain.model.TimelineEventType;

public record TripTransitionCommand(
        UUID tripId,
        TimelineEventType eventType,
        ActorType actorType,
        UUID actorId,
        String checkpointName,
        String note,
        String idempotencyKey,
        Instant occurredAt,
        Instant offlineCreatedAt) {
}
