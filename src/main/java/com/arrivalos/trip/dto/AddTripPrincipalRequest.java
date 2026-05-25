package com.arrivalos.trip.dto;

import java.util.UUID;

import jakarta.validation.constraints.Size;

public record AddTripPrincipalRequest(
        UUID userAccountId,
        @Size(max = 180) String fullName,
        @Size(max = 40) String phone,
        @Size(max = 500) String photoUrl,
        Boolean primaryContact) {
}
