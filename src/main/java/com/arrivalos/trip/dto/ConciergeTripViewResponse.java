package com.arrivalos.trip.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.arrivalos.domain.model.TripStatus;

public record ConciergeTripViewResponse(
        UUID id,
        String flightNumber,
        String arrivalAirport,
        String arrivalTerminal,
        String meetingPoint,
        Instant scheduledArrivalAt,
        Instant actualArrivalAt,
        TripStatus status,
        List<TripPrincipalResponse> principals,
        ConciergeResponse concierge,
        List<TripCheckpointResponse> checkpoints,
        List<TimelineEventResponse> timelineEvents,
        int watcherCount,
        TripCheckpointResponse currentCheckpoint,
        NextAllowedActionResponse nextAllowedAction) {
}
