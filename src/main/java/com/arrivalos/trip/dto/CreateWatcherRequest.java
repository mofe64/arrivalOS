package com.arrivalos.trip.dto;

import com.arrivalos.domain.model.NotificationChannel;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateWatcherRequest(
        @NotBlank @Size(max = 180) String fullName,
        @Email @Size(max = 180) String email,
        @Size(max = 40) String phone,
        NotificationChannel notificationChannel) {
}
