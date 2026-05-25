package com.arrivalos.domain.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.arrivalos.domain.model.Concierge;
import com.arrivalos.domain.model.ConciergeTripAccess;
import com.arrivalos.domain.model.Trip;

public interface ConciergeTripAccessRepository extends JpaRepository<ConciergeTripAccess, UUID> {

    @EntityGraph(attributePaths = {"trip", "trip.assignedConcierge", "concierge"})
    Optional<ConciergeTripAccess> findByTokenHash(String tokenHash);

    List<ConciergeTripAccess> findByTripAndConciergeOrderByCreatedAtDesc(Trip trip, Concierge concierge);

    @Modifying
    @Query("""
            update ConciergeTripAccess access
            set access.revokedAt = :revokedAt
            where access.trip = :trip
              and access.revokedAt is null
            """)
    int revokeUnrevokedByTrip(@Param("trip") Trip trip, @Param("revokedAt") Instant revokedAt);
}
