package com.arrivalos.auth;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.arrivalos.auth.dto.AuthResponse;
import com.arrivalos.auth.dto.AuthResponse.UserResponse;
import com.arrivalos.auth.dto.AcceptInvitationRequest;
import com.arrivalos.auth.dto.CreateInvitationRequest;
import com.arrivalos.auth.dto.ForgotPasswordRequest;
import com.arrivalos.auth.dto.InvitationResponse;
import com.arrivalos.auth.dto.LoginRequest;
import com.arrivalos.auth.dto.LogoutRequest;
import com.arrivalos.auth.dto.RefreshTokenRequest;
import com.arrivalos.auth.dto.RegisterRequest;
import com.arrivalos.auth.dto.ResetPasswordRequest;
import com.arrivalos.auth.dto.TokenRequest;
import com.arrivalos.domain.model.AccountInvitation;
import com.arrivalos.domain.model.AccountType;
import com.arrivalos.domain.model.AppUser;
import com.arrivalos.domain.model.EmailVerificationToken;
import com.arrivalos.domain.model.PasswordResetToken;
import com.arrivalos.domain.model.RefreshToken;
import com.arrivalos.domain.model.RevokedAccessToken;
import com.arrivalos.domain.repository.AccountInvitationRepository;
import com.arrivalos.domain.repository.AppUserRepository;
import com.arrivalos.domain.repository.EmailVerificationTokenRepository;
import com.arrivalos.domain.repository.PasswordResetTokenRepository;
import com.arrivalos.domain.repository.RefreshTokenRepository;
import com.arrivalos.domain.repository.RevokedAccessTokenRepository;
import com.arrivalos.email.AccountEmailService;

@Service
public class AuthService {

    private static final int ACCESS_TOKEN_TTL_MINUTES = 15;
    private static final int REFRESH_TOKEN_TTL_DAYS = 30;
    private static final int EMAIL_VERIFICATION_TTL_HOURS = 24;
    private static final int PASSWORD_RESET_TTL_MINUTES = 30;
    private static final int INVITATION_TTL_DAYS = 7;

    private final AccountInvitationRepository accountInvitationRepository;
    private final AppUserRepository appUserRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final RevokedAccessTokenRepository revokedAccessTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenHasher tokenHasher;
    private final SecureTokenGenerator secureTokenGenerator;
    private final JwtService jwtService;
    private final AccountEmailService accountEmailService;

    public AuthService(
            AccountInvitationRepository accountInvitationRepository,
            AppUserRepository appUserRepository,
            RefreshTokenRepository refreshTokenRepository,
            EmailVerificationTokenRepository emailVerificationTokenRepository,
            PasswordResetTokenRepository passwordResetTokenRepository,
            RevokedAccessTokenRepository revokedAccessTokenRepository,
            PasswordEncoder passwordEncoder,
            TokenHasher tokenHasher,
            SecureTokenGenerator secureTokenGenerator,
            JwtService jwtService,
            AccountEmailService accountEmailService) {
        this.accountInvitationRepository = accountInvitationRepository;
        this.appUserRepository = appUserRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.emailVerificationTokenRepository = emailVerificationTokenRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.revokedAccessTokenRepository = revokedAccessTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenHasher = tokenHasher;
        this.secureTokenGenerator = secureTokenGenerator;
        this.jwtService = jwtService;
        this.accountEmailService = accountEmailService;
    }

    @Transactional
    public InvitationResponse createInvitation(AppUser inviter, CreateInvitationRequest request) {
        requireAdmin(inviter);

        String email = normalizeEmail(request.email());
        if (appUserRepository.existsByEmailIgnoreCase(email)
                || accountInvitationRepository.existsByEmailIgnoreCaseAndAcceptedAtIsNull(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered or invited");
        }

        Instant now = Instant.now();
        String rawToken = secureTokenGenerator.generate();
        AccountInvitation invitation = accountInvitationRepository.save(new AccountInvitation(
                request.fullName().trim(),
                email,
                trimToNull(request.phone()),
                request.accountType(),
                tokenHasher.hash(rawToken),
                inviter,
                now.plus(INVITATION_TTL_DAYS, ChronoUnit.DAYS)));
        accountEmailService.sendInvitationEmail(invitation.getFullName(), invitation.getEmail(), rawToken);
        return InvitationResponse.from(invitation);
    }

    @Transactional
    public AuthResponse acceptInvitation(AcceptInvitationRequest request) {
        Instant now = Instant.now();
        AccountInvitation invitation = accountInvitationRepository.findByTokenHash(tokenHasher.hash(request.token()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid invitation token"));
        if (invitation.isAccepted() || !invitation.getExpiresAt().isAfter(now)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid invitation token");
        }
        if (appUserRepository.existsByEmailIgnoreCase(invitation.getEmail())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered");
        }

        AppUser user = new AppUser(
                invitation.getFullName(),
                invitation.getEmail(),
                passwordEncoder.encode(request.password()),
                invitation.getAccountType());
        user.setPhone(invitation.getPhone());
        user.setEmailVerified(true);
        user.setEmailVerifiedAt(now);
        user = appUserRepository.save(user);
        invitation.setAcceptedAt(now);
        return issueToken(user);
    }

    @Transactional
    public AuthResponse register(AccountType accountType, RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (appUserRepository.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered");
        }

        AppUser user = new AppUser(
                request.fullName().trim(),
                email,
                passwordEncoder.encode(request.password()),
                accountType);
        user.setPhone(trimToNull(request.phone()));
        user = appUserRepository.save(user);
        sendVerificationEmail(user);

        return issueToken(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        AppUser user = appUserRepository.findByEmailIgnoreCase(email)
                .filter(AppUser::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }
        if (!user.isEmailVerified()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Email verification required");
        }

        return issueToken(user);
    }

    private void requireAdmin(AppUser user) {
        if (user == null || user.getAccountType() != AccountType.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        }
    }

    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {
        JwtClaims claims = jwtService.parseRefreshToken(request.refreshToken());
        RefreshToken storedRefreshToken = refreshTokenRepository.findByTokenId(claims.tokenId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));
        Instant now = Instant.now();
        if (storedRefreshToken.isRevoked()
                || storedRefreshToken.isExpired(now)
                || !storedRefreshToken.getTokenHash().equals(tokenHasher.hash(request.refreshToken()))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token");
        }

        AppUser user = storedRefreshToken.getUser();
        if (!user.isActive()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token");
        }
        if (wasPasswordChangedAfterTokenIssued(user, claims.issuedAt())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token");
        }

        storedRefreshToken.setRevokedAt(now);
        storedRefreshToken.setLastUsedAt(now);
        return issueToken(user);
    }

    @Transactional
    public void logout(AppUser authenticatedUser, String accessToken, LogoutRequest request) {
        Instant now = Instant.now();
        JwtClaims accessClaims = jwtService.parseAccessToken(accessToken);
        if (!accessClaims.subject().equals(authenticatedUser.getId())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid access token");
        }
        if (!revokedAccessTokenRepository.existsByTokenIdAndExpiresAtAfter(accessClaims.tokenId(), now)) {
            revokedAccessTokenRepository.save(new RevokedAccessToken(
                    authenticatedUser,
                    accessClaims.tokenId(),
                    accessClaims.expiresAt(),
                    now));
        }

        String refreshToken = request == null ? null : trimToNull(request.refreshToken());
        if (refreshToken != null) {
            revokeRefreshToken(authenticatedUser, refreshToken, now);
        }
    }

    @Transactional
    public void verifyEmail(TokenRequest request) {
        Instant now = Instant.now();
        EmailVerificationToken token = emailVerificationTokenRepository.findByTokenHash(tokenHasher.hash(request.token()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid verification token"));
        if (token.isConsumed() || token.isExpired(now)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid verification token");
        }

        AppUser user = token.getUser();
        user.setEmailVerified(true);
        user.setEmailVerifiedAt(now);
        token.setConsumedAt(now);
        consumeOutstandingVerificationTokens(user, now);
    }

    @Transactional
    public void resendVerificationEmail(AppUser user) {
        if (user.isEmailVerified()) {
            return;
        }
        sendVerificationEmail(user);
    }

    @Transactional
    public void requestPasswordReset(ForgotPasswordRequest request) {
        String email = normalizeEmail(request.email());
        appUserRepository.findByEmailIgnoreCase(email)
                .filter(AppUser::isActive)
                .ifPresent(this::sendPasswordResetEmail);
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        Instant now = Instant.now();
        PasswordResetToken token = passwordResetTokenRepository.findByTokenHash(tokenHasher.hash(request.token()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid password reset token"));
        if (token.isConsumed() || token.isExpired(now)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid password reset token");
        }

        AppUser user = token.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setPasswordChangedAt(now);
        token.setConsumedAt(now);
        consumeOutstandingPasswordResetTokens(user, now);
        revokeOutstandingRefreshTokens(user, now);
    }

    private AuthResponse issueToken(AppUser user) {
        Instant issuedAt = Instant.now();
        Instant accessTokenExpiresAt = issuedAt.plus(ACCESS_TOKEN_TTL_MINUTES, ChronoUnit.MINUTES);
        Instant refreshTokenExpiresAt = issuedAt.plus(REFRESH_TOKEN_TTL_DAYS, ChronoUnit.DAYS);
        String refreshTokenId = UUID.randomUUID().toString();
        String accessToken = jwtService.createAccessToken(user, issuedAt, accessTokenExpiresAt);
        String refreshToken = jwtService.createRefreshToken(user, refreshTokenId, issuedAt, refreshTokenExpiresAt);
        refreshTokenRepository.save(new RefreshToken(
                user,
                refreshTokenId,
                tokenHasher.hash(refreshToken),
                refreshTokenExpiresAt));

        return new AuthResponse(
                accessToken,
                refreshToken,
                "Bearer",
                accessTokenExpiresAt,
                refreshTokenExpiresAt,
                new UserResponse(
                        user.getId(),
                        user.getFullName(),
                        user.getEmail(),
                        user.getPhone(),
                        user.getAccountType(),
                        user.isEmailVerified()));
    }

    public boolean isAccessTokenRevoked(JwtClaims claims) {
        return revokedAccessTokenRepository.existsByTokenIdAndExpiresAtAfter(claims.tokenId(), Instant.now());
    }

    public boolean wasPasswordChangedAfterTokenIssued(AppUser user, Instant issuedAt) {
        return user.getPasswordChangedAt() != null && issuedAt.isBefore(user.getPasswordChangedAt());
    }

    private void sendVerificationEmail(AppUser user) {
        Instant now = Instant.now();
        consumeOutstandingVerificationTokens(user, now);
        String rawToken = secureTokenGenerator.generate();
        emailVerificationTokenRepository.save(new EmailVerificationToken(
                user,
                tokenHasher.hash(rawToken),
                now.plus(EMAIL_VERIFICATION_TTL_HOURS, ChronoUnit.HOURS)));
        accountEmailService.sendVerificationEmail(user, rawToken);
    }

    private void sendPasswordResetEmail(AppUser user) {
        Instant now = Instant.now();
        consumeOutstandingPasswordResetTokens(user, now);
        String rawToken = secureTokenGenerator.generate();
        passwordResetTokenRepository.save(new PasswordResetToken(
                user,
                tokenHasher.hash(rawToken),
                now.plus(PASSWORD_RESET_TTL_MINUTES, ChronoUnit.MINUTES)));
        accountEmailService.sendPasswordResetEmail(user, rawToken);
    }

    private void revokeRefreshToken(AppUser authenticatedUser, String rawRefreshToken, Instant now) {
        JwtClaims claims = jwtService.parseRefreshToken(rawRefreshToken);
        if (!claims.subject().equals(authenticatedUser.getId())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token");
        }

        RefreshToken storedRefreshToken = refreshTokenRepository.findByTokenId(claims.tokenId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));
        if (!storedRefreshToken.getTokenHash().equals(tokenHasher.hash(rawRefreshToken))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token");
        }
        if (!storedRefreshToken.isRevoked()) {
            storedRefreshToken.setRevokedAt(now);
            storedRefreshToken.setLastUsedAt(now);
        }
    }

    private void revokeOutstandingRefreshTokens(AppUser user, Instant now) {
        List<RefreshToken> tokens = refreshTokenRepository.findByUserAndRevokedAtIsNull(user);
        for (RefreshToken token : tokens) {
            token.setRevokedAt(now);
        }
    }

    private void consumeOutstandingVerificationTokens(AppUser user, Instant now) {
        List<EmailVerificationToken> tokens = emailVerificationTokenRepository.findByUserAndConsumedAtIsNull(user);
        for (EmailVerificationToken token : tokens) {
            token.setConsumedAt(now);
        }
    }

    private void consumeOutstandingPasswordResetTokens(AppUser user, Instant now) {
        List<PasswordResetToken> tokens = passwordResetTokenRepository.findByUserAndConsumedAtIsNull(user);
        for (PasswordResetToken token : tokens) {
            token.setConsumedAt(now);
        }
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
