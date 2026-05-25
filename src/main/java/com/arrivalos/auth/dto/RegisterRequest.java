package com.arrivalos.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(max = 180) String fullName,
        @NotBlank @Email @Size(max = 180) String email,
        @Size(max = 40) String phone,
        @NotBlank @Size(min = 8, max = 120) String password) {
}
