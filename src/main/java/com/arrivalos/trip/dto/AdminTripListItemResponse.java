package com.arrivalos.trip.dto;

import java.time.Instant;
import java.util.UUID;

import com.arrivalos.domain.model.TripStatus;

public record AdminTripListItemResponse(
        UUID id,
        String flightNumber,
        String arrivalAirport,
        String arrivalTerminal,
        Instant scheduledArrivalAt,
        TripStatus status,
        TripPrincipalResponse primaryPrincipal,
        int principalCount,
        ConciergeResponse assignedConcierge,
        int watcherCount,
        TimelineEventResponse lastTimelineEvent,
        Instant lastUpdatedAt,
        boolean stale,
        long emailSentNotificationCount,
        long emailFailedNotificationCount) {
}
