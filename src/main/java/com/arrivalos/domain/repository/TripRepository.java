package com.arrivalos.domain.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.arrivalos.domain.model.Trip;
import com.arrivalos.domain.model.TripStatus;

public interface TripRepository extends JpaRepository<Trip, UUID> {

    List<Trip> findByStatusInOrderByUpdatedAtDesc(List<TripStatus> statuses);

    List<Trip> findAllByOrderByUpdatedAtDesc();
}
