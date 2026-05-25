package com.arrivalos.auth;

import java.util.List;
import java.util.Optional;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.arrivalos.domain.model.AppUser;
import com.arrivalos.domain.repository.AppUserRepository;
import com.arrivalos.domain.repository.RevokedAccessTokenRepository;

@Service
public class BearerTokenAuthenticator {

    private final JwtService jwtService;
    private final AppUserRepository appUserRepository;
    private final RevokedAccessTokenRepository revokedAccessTokenRepository;

    public BearerTokenAuthenticator(
            JwtService jwtService,
            AppUserRepository appUserRepository,
            RevokedAccessTokenRepository revokedAccessTokenRepository) {
        this.jwtService = jwtService;
        this.appUserRepository = appUserRepository;
        this.revokedAccessTokenRepository = revokedAccessTokenRepository;
    }

    @Transactional
    public Optional<Authentication> authenticate(String rawToken) {
        JwtClaims claims = jwtService.parseAccessToken(rawToken);
        if (revokedAccessTokenRepository.existsByTokenIdAndExpiresAtAfter(claims.tokenId(), java.time.Instant.now())) {
            return Optional.empty();
        }
        return appUserRepository.findById(claims.subject())
                .filter(AppUser::isActive)
                .filter(user -> user.getPasswordChangedAt() == null || !claims.issuedAt().isBefore(user.getPasswordChangedAt()))
                .map(user -> (Authentication) new UsernamePasswordAuthenticationToken(
                        user,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + user.getAccountType().name()))));
    }
}
