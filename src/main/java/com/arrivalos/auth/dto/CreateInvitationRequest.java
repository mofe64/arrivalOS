package com.arrivalos.auth.dto;

import com.arrivalos.domain.model.AccountType;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateInvitationRequest(
        @NotBlank @Size(max = 180) String fullName,
        @NotBlank @Email @Size(max = 180) String email,
        @Size(max = 40) String phone,
        @NotNull AccountType accountType) {
}
