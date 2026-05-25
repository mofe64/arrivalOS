package com.arrivalos.trip.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.arrivalos.domain.model.Trip;
import com.arrivalos.domain.model.TripStatus;

public record AdminPrincipalTripResponse(
        UUID id,
        String flightNumber,
        String arrivalAirport,
        String arrivalTerminal,
        Instant scheduledArrivalAt,
        TripStatus status,
        ConciergeResponse concierge,
        List<WatcherResponse> watchers) {

    public static AdminPrincipalTripResponse from(
            Trip trip,
            ConciergeResponse concierge,
            List<WatcherResponse> watchers) {
        return new AdminPrincipalTripResponse(
                trip.getId(),
                trip.getFlightNumber(),
                trip.getArrivalAirport(),
                trip.getArrivalTerminal(),
                trip.getScheduledArrivalAt(),
                trip.getStatus(),
                concierge,
                watchers);
    }
}
