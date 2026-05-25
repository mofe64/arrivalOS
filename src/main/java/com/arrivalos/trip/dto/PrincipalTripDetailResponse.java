package com.arrivalos.trip.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.arrivalos.domain.model.TripStatus;

public record PrincipalTripDetailResponse(
        UUID id,
        String flightNumber,
        String arrivalAirport,
        String arrivalTerminal,
        Instant scheduledArrivalAt,
        Instant actualArrivalAt,
        TripStatus status,
        List<TripPrincipalResponse> principals,
        ConciergeResponse concierge,
        String meetingPoint,
        List<TripCheckpointResponse> checkpoints,
        List<TimelineEventResponse> timelineEvents,
        Instant lastUpdatedAt,
        TripCheckpointResponse currentCheckpoint) {
}
