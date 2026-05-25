package com.arrivalos.trip.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.arrivalos.domain.model.Trip;
import com.arrivalos.domain.model.TripStatus;

public record AdminTripDetailResponse(
        UUID id,
        String flightNumber,
        String arrivalAirport,
        String arrivalTerminal,
        Instant scheduledArrivalAt,
        Instant actualArrivalAt,
        TripStatus status,
        List<TripPrincipalResponse> principals,
        List<WatcherResponse> watchers,
        ConciergeResponse concierge,
        List<TripCheckpointResponse> checkpoints,
        List<TimelineEventResponse> timelineEvents,
        List<NotificationAttemptResponse> notificationAttempts,
        Instant lastUpdatedAt,
        TripCheckpointResponse currentCheckpoint,
        String meetingPoint) {

    public static AdminTripDetailResponse from(
            Trip trip,
            List<TripPrincipalResponse> principals,
            List<WatcherResponse> watchers,
            ConciergeResponse concierge,
            List<TripCheckpointResponse> checkpoints,
            List<TimelineEventResponse> timelineEvents,
            List<NotificationAttemptResponse> notificationAttempts,
            Instant lastUpdatedAt,
            TripCheckpointResponse currentCheckpoint) {
        return new AdminTripDetailResponse(
                trip.getId(),
                trip.getFlightNumber(),
                trip.getArrivalAirport(),
                trip.getArrivalTerminal(),
                trip.getScheduledArrivalAt(),
                trip.getActualArrivalAt(),
                trip.getStatus(),
                principals,
                watchers,
                concierge,
                checkpoints,
                timelineEvents,
                notificationAttempts,
                lastUpdatedAt,
                currentCheckpoint,
                trip.getMeetingPoint());
    }
}
