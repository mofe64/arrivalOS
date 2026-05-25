package com.arrivalos.auth;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import com.arrivalos.domain.model.AccountType;
import com.arrivalos.domain.model.AppUser;

@Service
public class JwtService implements InitializingBean {

    private static final int MINIMUM_HS256_SECRET_BYTES = 32;
    private static final String ACCESS_USE = "access";
    private static final String REFRESH_USE = "refresh";

    private final byte[] secretBytes;
    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;

    public JwtService(@Value("${arrivalos.auth.jwt-secret}") String secret) {
        this.secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        SecretKeySpec secretKey = new SecretKeySpec(secretBytes, "HmacSHA256");
        ImmutableSecret<SecurityContext> secretSource = new ImmutableSecret<>(secretKey);
        this.jwtEncoder = new NimbusJwtEncoder(secretSource);
        this.jwtDecoder = NimbusJwtDecoder.withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    @Override
    public void afterPropertiesSet() {
        if (secretBytes.length < MINIMUM_HS256_SECRET_BYTES) {
            throw new IllegalStateException("arrivalos.auth.jwt-secret must be at least 32 bytes for HS256");
        }
    }

    public String createAccessToken(AppUser user, Instant issuedAt, Instant expiresAt) {
        return createToken(user, UUID.randomUUID().toString(), ACCESS_USE, issuedAt, expiresAt);
    }

    public String createRefreshToken(AppUser user, String tokenId, Instant issuedAt, Instant expiresAt) {
        return createToken(user, tokenId, REFRESH_USE, issuedAt, expiresAt);
    }

    public JwtClaims parseAccessToken(String token) {
        JwtClaims claims = parse(token);
        if (!ACCESS_USE.equals(claims.tokenUse())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid access token");
        }
        if (!claims.expiresAt().isAfter(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Access token expired");
        }
        return claims;
    }

    public JwtClaims parseRefreshToken(String token) {
        JwtClaims claims = parse(token);
        if (!REFRESH_USE.equals(claims.tokenUse())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token");
        }
        if (!claims.expiresAt().isAfter(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token expired");
        }
        return claims;
    }

    private String createToken(AppUser user, String tokenId, String tokenUse, Instant issuedAt, Instant expiresAt) {
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .id(tokenId)
                .subject(user.getId().toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("email", user.getEmail())
                .claim("fullName", user.getFullName())
                .claim("role", user.getAccountType().name())
                .claim("accountType", user.getAccountType().name())
                .claim("tokenUse", tokenUse)
                .build();

        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private JwtClaims parse(String token) {
        try {
            Jwt jwt = jwtDecoder.decode(token);
            return new JwtClaims(
                    jwt.getId(),
                    UUID.fromString(jwt.getSubject()),
                    jwt.getClaimAsString("email"),
                    jwt.getClaimAsString("fullName"),
                    AccountType.valueOf(jwt.getClaimAsString("accountType")),
                    jwt.getClaimAsString("tokenUse"),
                    jwt.getIssuedAt(),
                    jwt.getExpiresAt());
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw unauthorized("Invalid token");
        }
    }

    private ResponseStatusException unauthorized(String reason) {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, reason);
    }
}
