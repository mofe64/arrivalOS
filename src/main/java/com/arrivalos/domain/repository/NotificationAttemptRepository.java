package com.arrivalos.domain.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.arrivalos.domain.model.NotificationAttempt;
import com.arrivalos.domain.model.NotificationStatus;
import com.arrivalos.domain.model.Trip;

public interface NotificationAttemptRepository extends JpaRepository<NotificationAttempt, UUID> {

    List<NotificationAttempt> findByTripOrderByCreatedAtAsc(Trip trip);

    List<NotificationAttempt> findByTripAndStatusOrderByCreatedAtAsc(Trip trip, NotificationStatus status);

    long countByTripAndStatus(Trip trip, NotificationStatus status);

    List<NotificationAttempt> findByStatusOrderByCreatedAtAsc(NotificationStatus status);
}
