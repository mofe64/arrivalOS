package com.arrivalos.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.arrivalos.domain.model.TimelineEvent;
import com.arrivalos.domain.model.Trip;

public interface TimelineEventRepository extends JpaRepository<TimelineEvent, UUID> {

    List<TimelineEvent> findByTripOrderByOccurredAtAsc(Trip trip);

    List<TimelineEvent> findByTripOrderByOccurredAtDesc(Trip trip);

    Optional<TimelineEvent> findByTripAndIdempotencyKey(Trip trip, String idempotencyKey);
}
