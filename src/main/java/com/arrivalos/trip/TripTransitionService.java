package com.arrivalos.trip;

public interface TripTransitionService {

    TripTransitionResult transition(TripTransitionCommand command);
}
