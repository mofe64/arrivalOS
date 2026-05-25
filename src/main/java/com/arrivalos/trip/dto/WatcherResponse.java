package com.arrivalos.trip.dto;

import java.util.UUID;

import com.arrivalos.domain.model.NotificationChannel;
import com.arrivalos.domain.model.Watcher;

public record WatcherResponse(
        UUID id,
        String fullName,
        String email,
        String phone,
        NotificationChannel notificationChannel) {

    public static WatcherResponse from(Watcher watcher) {
        return new WatcherResponse(
                watcher.getId(),
                watcher.getFullName(),
                watcher.getEmail(),
                watcher.getPhone(),
                watcher.getNotificationChannel());
    }
}
