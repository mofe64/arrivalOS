package com.arrivalos.domain.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.arrivalos.domain.model.Concierge;

public interface ConciergeRepository extends JpaRepository<Concierge, UUID> {

    List<Concierge> findByActiveTrueOrderByFullNameAsc();

    List<Concierge> findAllByOrderByFullNameAsc();

    boolean existsByPublicId(String publicId);
}
