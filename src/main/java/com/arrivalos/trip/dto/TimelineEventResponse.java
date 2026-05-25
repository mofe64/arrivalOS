package com.arrivalos.trip.dto;

import java.time.Instant;
import java.util.UUID;

import com.arrivalos.domain.model.ActorType;
import com.arrivalos.domain.model.TimelineEvent;
import com.arrivalos.domain.model.TimelineEventType;

public record TimelineEventResponse(
        UUID id,
        TimelineEventType eventType,
        ActorType actorType,
        UUID actorId,
        String checkpointName,
        String note,
        String idempotencyKey,
        Instant occurredAt,
        Instant offlineCreatedAt) {

    public static TimelineEventResponse from(TimelineEvent event) {
        return new TimelineEventResponse(
                event.getId(),
                event.getEventType(),
                event.getActorType(),
                event.getActorId(),
                event.getCheckpointName(),
                event.getNote(),
                event.getIdempotencyKey(),
                event.getOccurredAt(),
                event.getOfflineCreatedAt());
    }
}
