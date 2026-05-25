package com.arrivalos.trip.dto;

import java.time.Instant;
import java.util.UUID;

import com.arrivalos.domain.model.TripStatus;

public record PrincipalTripSummaryResponse(
        UUID id,
        String flightNumber,
        String arrivalAirport,
        String arrivalTerminal,
        Instant scheduledArrivalAt,
        Instant actualArrivalAt,
        TripStatus status,
        ConciergeResponse concierge,
        String meetingPoint,
        TimelineEventResponse lastTimelineEvent,
        Instant lastUpdatedAt,
        TripCheckpointResponse currentCheckpoint) {
}
