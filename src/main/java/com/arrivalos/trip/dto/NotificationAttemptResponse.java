package com.arrivalos.trip.dto;

import java.time.Instant;
import java.util.UUID;

import com.arrivalos.domain.model.NotificationAttempt;
import com.arrivalos.domain.model.NotificationChannel;
import com.arrivalos.domain.model.NotificationStatus;
import com.arrivalos.domain.model.RecipientType;

public record NotificationAttemptResponse(
        UUID id,
        RecipientType recipientType,
        UUID recipientId,
        NotificationChannel channel,
        String provider,
        NotificationStatus status,
        String failureReason,
        Instant sentAt,
        Instant deliveredAt,
        Instant createdAt) {

    public static NotificationAttemptResponse from(NotificationAttempt attempt) {
        return new NotificationAttemptResponse(
                attempt.getId(),
                attempt.getRecipientType(),
                attempt.getRecipientId(),
                attempt.getChannel(),
                attempt.getProvider(),
                attempt.getStatus(),
                attempt.getFailureReason(),
                attempt.getSentAt(),
                attempt.getDeliveredAt(),
                attempt.getCreatedAt());
    }
}
