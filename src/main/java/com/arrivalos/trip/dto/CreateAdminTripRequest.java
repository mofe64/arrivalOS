package com.arrivalos.trip.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record CreateAdminTripRequest(
        @NotBlank @Size(max = 40) String flightNumber,
        @NotBlank @Size(max = 120) String arrivalAirport,
        @Size(max = 80) String arrivalTerminal,
        @Size(max = 500) String meetingPoint,
        Instant scheduledArrivalAt,
        Instant actualArrivalAt,
        @NotEmpty @Valid List<TripPrincipalRequest> principals,
        @Valid List<TripWatcherRequest> watchers,
        UUID assignedConciergeId,
        @Valid List<TripCheckpointRequest> checkpoints) {

    public record TripPrincipalRequest(
            UUID userAccountId,
            @Size(max = 180) String fullName,
            @Size(max = 40) String phone,
            @Size(max = 500) String photoUrl,
            Boolean primaryContact) {
    }

    public record TripWatcherRequest(
            @NotBlank @Size(max = 180) String fullName,
            @NotBlank @Email @Size(max = 180) String email,
            @Size(max = 40) String phone) {
    }

    public record TripCheckpointRequest(
            @NotBlank @Size(max = 120) String name) {
    }
}
