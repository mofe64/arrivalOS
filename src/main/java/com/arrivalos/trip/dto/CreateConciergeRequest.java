package com.arrivalos.trip.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateConciergeRequest(
        @NotBlank
        @Size(max = 160)
        String fullName,

        @NotBlank
        @Size(max = 40)
        String phone,

        @Size(max = 500)
        String photoUrl,

        @NotBlank
        @Size(max = 80)
        String publicId) {
}
