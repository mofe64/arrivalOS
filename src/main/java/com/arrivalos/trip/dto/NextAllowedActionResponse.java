package com.arrivalos.trip.dto;

import com.arrivalos.domain.model.TimelineEventType;

public record NextAllowedActionResponse(
        TimelineEventType eventType,
        String checkpointName,
        String label) {
}
