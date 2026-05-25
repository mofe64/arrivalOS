package com.arrivalos.trip;

import com.arrivalos.domain.model.TimelineEvent;
import com.arrivalos.domain.model.Trip;

public record TripTransitionResult(
        Trip trip,
        TimelineEvent event,
        boolean duplicate) {
}
