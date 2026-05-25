package com.arrivalos.domain.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "trips")
public class Trip extends BaseEntity {

    @Column(name = "flight_number", nullable = false, length = 40)
    private String flightNumber;

    @Column(name = "arrival_airport", nullable = false, length = 120)
    private String arrivalAirport;

    @Column(name = "arrival_terminal", length = 80)
    private String arrivalTerminal;

    @Column(name = "meeting_point", length = 500)
    private String meetingPoint;

    @Column(name = "scheduled_arrival_at")
    private Instant scheduledArrivalAt;

    @Column(name = "actual_arrival_at")
    private Instant actualArrivalAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 60)
    private TripStatus status = TripStatus.CREATED;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_concierge_id")
    private Concierge assignedConcierge;

    protected Trip() {
    }

    public Trip(String flightNumber, String arrivalAirport) {
        this.flightNumber = flightNumber;
        this.arrivalAirport = arrivalAirport;
    }

    public String getFlightNumber() {
        return flightNumber;
    }

    public void setFlightNumber(String flightNumber) {
        this.flightNumber = flightNumber;
    }

    public String getArrivalAirport() {
        return arrivalAirport;
    }

    public void setArrivalAirport(String arrivalAirport) {
        this.arrivalAirport = arrivalAirport;
    }

    public String getArrivalTerminal() {
        return arrivalTerminal;
    }

    public void setArrivalTerminal(String arrivalTerminal) {
        this.arrivalTerminal = arrivalTerminal;
    }

    public String getMeetingPoint() {
        return meetingPoint;
    }

    public void setMeetingPoint(String meetingPoint) {
        this.meetingPoint = meetingPoint;
    }

    public Instant getScheduledArrivalAt() {
        return scheduledArrivalAt;
    }

    public void setScheduledArrivalAt(Instant scheduledArrivalAt) {
        this.scheduledArrivalAt = scheduledArrivalAt;
    }

    public Instant getActualArrivalAt() {
        return actualArrivalAt;
    }

    public void setActualArrivalAt(Instant actualArrivalAt) {
        this.actualArrivalAt = actualArrivalAt;
    }

    public TripStatus getStatus() {
        return status;
    }

    public void setStatus(TripStatus status) {
        this.status = status;
    }

    public Concierge getAssignedConcierge() {
        return assignedConcierge;
    }

    public void setAssignedConcierge(Concierge assignedConcierge) {
        this.assignedConcierge = assignedConcierge;
    }
}
