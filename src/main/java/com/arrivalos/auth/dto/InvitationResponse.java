package com.arrivalos.auth.dto;

import java.time.Instant;
import java.util.UUID;

import com.arrivalos.domain.model.AccountInvitation;
import com.arrivalos.domain.model.AccountType;

public record InvitationResponse(
        UUID id,
        String fullName,
        String email,
        String phone,
        AccountType accountType,
        Instant expiresAt,
        boolean accepted) {

    public static InvitationResponse from(AccountInvitation invitation) {
        return new InvitationResponse(
                invitation.getId(),
                invitation.getFullName(),
                invitation.getEmail(),
                invitation.getPhone(),
                invitation.getAccountType(),
                invitation.getExpiresAt(),
                invitation.isAccepted());
    }
}
