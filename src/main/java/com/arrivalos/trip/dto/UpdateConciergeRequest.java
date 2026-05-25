package com.arrivalos.trip.dto;

import jakarta.validation.constraints.Size;

public record UpdateConciergeRequest(
        @Size(max = 160) String fullName,
        @Size(max = 40) String phone,
        @Size(max = 500) String photoUrl,
        Boolean active) {
}
