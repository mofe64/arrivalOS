package com.arrivalos.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "watchers")
public class Watcher extends BaseEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @Column(name = "full_name", nullable = false, length = 180)
    private String fullName;

    @Column(length = 180)
    private String email;

    @Column(length = 40)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_channel", nullable = false, length = 40)
    private NotificationChannel notificationChannel = NotificationChannel.WHATSAPP;

    protected Watcher() {
    }

    public Watcher(
            Trip trip,
            String fullName,
            String email,
            String phone,
            NotificationChannel notificationChannel) {
        this.trip = trip;
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        this.notificationChannel = notificationChannel;
    }

    public Trip getTrip() {
        return trip;
    }

    public void setTrip(Trip trip) {
        this.trip = trip;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public NotificationChannel getNotificationChannel() {
        return notificationChannel;
    }

    public void setNotificationChannel(NotificationChannel notificationChannel) {
        this.notificationChannel = notificationChannel;
    }
}
