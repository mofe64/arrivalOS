package com.arrivalos.auth;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.arrivalos.auth.dto.AuthResponse;
import com.arrivalos.auth.dto.AcceptInvitationRequest;
import com.arrivalos.auth.dto.ForgotPasswordRequest;
import com.arrivalos.auth.dto.LoginRequest;
import com.arrivalos.auth.dto.LogoutRequest;
import com.arrivalos.auth.dto.MessageResponse;
import com.arrivalos.auth.dto.RefreshTokenRequest;
import com.arrivalos.auth.dto.RegisterRequest;
import com.arrivalos.auth.dto.ResetPasswordRequest;
import com.arrivalos.auth.dto.TokenRequest;
import com.arrivalos.auth.dto.UserResponse;
import com.arrivalos.domain.model.AccountType;
import com.arrivalos.domain.model.AppUser;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register/admin")
    AuthResponse registerAdmin(@Valid @RequestBody RegisterRequest request) {
        return authService.register(AccountType.ADMIN, request);
    }

    @PostMapping("/register/principal")
    AuthResponse registerPrincipal(@Valid @RequestBody RegisterRequest request) {
        return authService.register(AccountType.PRINCIPAL, request);
    }

    @PostMapping("/login")
    AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/invitations/accept")
    AuthResponse acceptInvitation(@Valid @RequestBody AcceptInvitationRequest request) {
        return authService.acceptInvitation(request);
    }

    @PostMapping("/refresh")
    AuthResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return authService.refresh(request);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(
            @AuthenticationPrincipal AppUser user,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader,
            @RequestBody(required = false) LogoutRequest request) {
        authService.logout(user, bearerTokenFrom(authorizationHeader), request);
    }

    @PostMapping("/verify-email")
    MessageResponse verifyEmail(@Valid @RequestBody TokenRequest request) {
        authService.verifyEmail(request);
        return new MessageResponse("Email verified");
    }

    @PostMapping("/verify-email/resend")
    MessageResponse resendVerificationEmail(@AuthenticationPrincipal AppUser user) {
        authService.resendVerificationEmail(user);
        return new MessageResponse("Verification email sent");
    }

    @PostMapping("/password/forgot")
    @ResponseStatus(HttpStatus.ACCEPTED)
    MessageResponse forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.requestPasswordReset(request);
        return new MessageResponse("If the email exists, a password reset link has been sent");
    }

    @PostMapping("/password/reset")
    MessageResponse resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return new MessageResponse("Password reset complete");
    }

    @GetMapping("/me")
    UserResponse me(@AuthenticationPrincipal AppUser user) {
        return new UserResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                user.getAccountType(),
                user.isActive(),
                user.isEmailVerified());
    }

    private String bearerTokenFrom(String authorizationHeader) {
        return authorizationHeader.substring("Bearer ".length());
    }
}
