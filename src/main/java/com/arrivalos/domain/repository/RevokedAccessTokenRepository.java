package com.arrivalos.domain.repository;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.arrivalos.domain.model.RevokedAccessToken;

public interface RevokedAccessTokenRepository extends JpaRepository<RevokedAccessToken, UUID> {

    boolean existsByTokenIdAndExpiresAtAfter(String tokenId, Instant now);
}
