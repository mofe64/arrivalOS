package com.arrivalos.domain.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "revoked_access_tokens")
public class RevokedAccessToken extends BaseEntity {

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(name = "token_id", nullable = false, unique = true, length = 80)
    private String tokenId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at", nullable = false)
    private Instant revokedAt;

    protected RevokedAccessToken() {
    }

    public RevokedAccessToken(AppUser user, String tokenId, Instant expiresAt, Instant revokedAt) {
        this.user = user;
        this.tokenId = tokenId;
        this.expiresAt = expiresAt;
        this.revokedAt = revokedAt;
    }

    public AppUser getUser() {
        return user;
    }

    public String getTokenId() {
        return tokenId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }
}
