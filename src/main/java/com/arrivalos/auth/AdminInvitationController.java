package com.arrivalos.auth;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.arrivalos.auth.dto.CreateInvitationRequest;
import com.arrivalos.auth.dto.InvitationResponse;
import com.arrivalos.domain.model.AppUser;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/invitations")
public class AdminInvitationController {

    private final AuthService authService;

    public AdminInvitationController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping
    InvitationResponse createInvitation(
            @AuthenticationPrincipal AppUser user,
            @Valid @RequestBody CreateInvitationRequest request) {
        return authService.createInvitation(user, request);
    }
}
