package com.arrivalos.trip.dto;

import java.util.UUID;

import com.arrivalos.domain.model.Trip;

public record TripConciergeAssignmentResponse(
        UUID tripId,
        ConciergeResponse concierge) {

    public static TripConciergeAssignmentResponse from(Trip trip) {
        return new TripConciergeAssignmentResponse(
                trip.getId(),
                ConciergeResponse.from(trip.getAssignedConcierge()));
    }
}
