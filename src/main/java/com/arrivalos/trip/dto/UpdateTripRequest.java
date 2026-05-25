package com.arrivalos.trip.dto;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.Size;

public record UpdateTripRequest(
        @Size(max = 40) String flightNumber,
        @Size(max = 120) String arrivalAirport,
        @Size(max = 80) String arrivalTerminal,
        @Size(max = 500) String meetingPoint,
        Instant scheduledArrivalAt,
        Instant actualArrivalAt,
        UUID assignedConciergeId) {
}
