package com.arrivalos.trip.dto;

import java.util.UUID;

import com.arrivalos.domain.model.TripStatus;
import com.arrivalos.trip.TripTransitionResult;

public record TripTransitionResponse(
        UUID tripId,
        TripStatus status,
        TimelineEventResponse event,
        boolean duplicate) {

    public static TripTransitionResponse from(TripTransitionResult result) {
        return new TripTransitionResponse(
                result.trip().getId(),
                result.trip().getStatus(),
                TimelineEventResponse.from(result.event()),
                result.duplicate());
    }
}
