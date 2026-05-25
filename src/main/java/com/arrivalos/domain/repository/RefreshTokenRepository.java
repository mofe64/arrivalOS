package com.arrivalos.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.arrivalos.domain.model.AppUser;
import com.arrivalos.domain.model.RefreshToken;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenId(String tokenId);

    List<RefreshToken> findByUserAndRevokedAtIsNull(AppUser user);
}
