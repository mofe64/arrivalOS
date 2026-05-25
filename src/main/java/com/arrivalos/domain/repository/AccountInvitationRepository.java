package com.arrivalos.domain.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.arrivalos.domain.model.AccountInvitation;

public interface AccountInvitationRepository extends JpaRepository<AccountInvitation, UUID> {

    Optional<AccountInvitation> findByTokenHash(String tokenHash);

    boolean existsByEmailIgnoreCaseAndAcceptedAtIsNull(String email);
}
