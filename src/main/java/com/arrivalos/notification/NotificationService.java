package com.arrivalos.notification;

import java.util.Collection;

import com.arrivalos.domain.model.Trip;
import com.arrivalos.domain.model.Watcher;
import com.arrivalos.trip.TripTransitionResult;

public interface NotificationService {

    void notifyTripCreated(Trip trip, Collection<Watcher> watchers);

    void notifyWatcherAdded(Trip trip, Watcher watcher);

    void notifyTimelineEvent(TripTransitionResult result);
}
