package com.arrivalos.domain.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "account_invitations")
public class AccountInvitation extends BaseEntity {

    @Column(name = "full_name", nullable = false, length = 180)
    private String fullName;

    @Column(nullable = false, length = 180)
    private String email;

    @Column(length = 40)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 40)
    private AccountType accountType;

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invited_by_user_id", nullable = false)
    private AppUser invitedBy;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    protected AccountInvitation() {
    }

    public AccountInvitation(
            String fullName,
            String email,
            String phone,
            AccountType accountType,
            String tokenHash,
            AppUser invitedBy,
            Instant expiresAt) {
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        this.accountType = accountType;
        this.tokenHash = tokenHash;
        this.invitedBy = invitedBy;
        this.expiresAt = expiresAt;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public AccountType getAccountType() {
        return accountType;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public AppUser getInvitedBy() {
        return invitedBy;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getAcceptedAt() {
        return acceptedAt;
    }

    public void setAcceptedAt(Instant acceptedAt) {
        this.acceptedAt = acceptedAt;
    }

    public boolean isAccepted() {
        return acceptedAt != null;
    }
}
