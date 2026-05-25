package com.arrivalos.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.arrivalos.domain.model.Trip;
import com.arrivalos.domain.model.TripCheckpoint;

public interface TripCheckpointRepository extends JpaRepository<TripCheckpoint, UUID> {

    List<TripCheckpoint> findByTripOrderBySequenceNumberAsc(Trip trip);

    Optional<TripCheckpoint> findByTripAndNameIgnoreCase(Trip trip, String name);
}
