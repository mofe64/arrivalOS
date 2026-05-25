package com.arrivalos.trip.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelTripRequest(
        @Size(max = 1000) String note,
        @NotBlank @Size(max = 120) String idempotencyKey) {
}
