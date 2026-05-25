package com.arrivalos.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.arrivalos.domain.model.Trip;
import com.arrivalos.domain.model.Watcher;

public interface WatcherRepository extends JpaRepository<Watcher, UUID> {

    List<Watcher> findByTripOrderByCreatedAtAsc(Trip trip);

    Optional<Watcher> findByTripAndEmailIgnoreCase(Trip trip, String email);
}
