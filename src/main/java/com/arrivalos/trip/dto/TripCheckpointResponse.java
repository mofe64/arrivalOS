package com.arrivalos.trip.dto;

import java.time.Instant;
import java.util.UUID;

import com.arrivalos.domain.model.CheckpointStatus;
import com.arrivalos.domain.model.TripCheckpoint;

public record TripCheckpointResponse(
        UUID id,
        String name,
        int sequenceNumber,
        CheckpointStatus status,
        Instant startedAt,
        Instant completedAt,
        Instant skippedAt) {

    public static TripCheckpointResponse from(TripCheckpoint checkpoint) {
        return new TripCheckpointResponse(
                checkpoint.getId(),
                checkpoint.getName(),
                checkpoint.getSequenceNumber(),
                checkpoint.getStatus(),
                checkpoint.getStartedAt(),
                checkpoint.getCompletedAt(),
                checkpoint.getSkippedAt());
    }
}
