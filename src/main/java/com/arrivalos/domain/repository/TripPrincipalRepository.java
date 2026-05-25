package com.arrivalos.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.arrivalos.domain.model.AppUser;
import com.arrivalos.domain.model.Trip;
import com.arrivalos.domain.model.TripPrincipal;

public interface TripPrincipalRepository extends JpaRepository<TripPrincipal, UUID> {

    List<TripPrincipal> findByTripOrderBySequenceNumberAsc(Trip trip);

    Optional<TripPrincipal> findFirstByTripAndPrimaryContactTrue(Trip trip);

    List<TripPrincipal> findByUserAccountOrderByCreatedAtDesc(AppUser userAccount);

    boolean existsByTripAndUserAccount(Trip trip, AppUser userAccount);
}
