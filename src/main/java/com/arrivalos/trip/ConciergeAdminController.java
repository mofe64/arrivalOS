package com.arrivalos.trip;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.arrivalos.auth.SecureTokenGenerator;
import com.arrivalos.auth.TokenHasher;
import com.arrivalos.domain.model.AccountType;
import com.arrivalos.domain.model.AppUser;
import com.arrivalos.domain.model.Concierge;
import com.arrivalos.domain.model.ConciergeTripAccess;
import com.arrivalos.domain.model.Trip;
import com.arrivalos.domain.repository.ConciergeRepository;
import com.arrivalos.domain.repository.ConciergeTripAccessRepository;
import com.arrivalos.domain.repository.TripRepository;
import com.arrivalos.trip.dto.AssignConciergeRequest;
import com.arrivalos.trip.dto.ConciergeAccessLinkResponse;
import com.arrivalos.trip.dto.ConciergeResponse;
import com.arrivalos.trip.dto.CreateConciergeAccessLinkRequest;
import com.arrivalos.trip.dto.CreateConciergeRequest;
import com.arrivalos.trip.dto.TripConciergeAssignmentResponse;
import com.arrivalos.trip.dto.UpdateConciergeRequest;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin")
public class ConciergeAdminController {

    private final ConciergeRepository conciergeRepository;
    private final TripRepository tripRepository;
    private final ConciergeTripAccessRepository conciergeTripAccessRepository;
    private final SecureTokenGenerator secureTokenGenerator;
    private final TokenHasher tokenHasher;
    private final String appBaseUrl;

    public ConciergeAdminController(
            ConciergeRepository conciergeRepository,
            TripRepository tripRepository,
            ConciergeTripAccessRepository conciergeTripAccessRepository,
            SecureTokenGenerator secureTokenGenerator,
            TokenHasher tokenHasher,
            @Value("${arrivalos.app.base-url:http://localhost:3000}") String appBaseUrl) {
        this.conciergeRepository = conciergeRepository;
        this.tripRepository = tripRepository;
        this.conciergeTripAccessRepository = conciergeTripAccessRepository;
        this.secureTokenGenerator = secureTokenGenerator;
        this.tokenHasher = tokenHasher;
        this.appBaseUrl = stripTrailingSlash(appBaseUrl);
    }

    @PostMapping("/concierges")
    ConciergeResponse createConcierge(
            @AuthenticationPrincipal AppUser user,
            @Valid @RequestBody CreateConciergeRequest request) {
        requireAdmin(user);
        if (conciergeRepository.existsByPublicId(request.publicId().trim())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Concierge public ID already exists");
        }

        Concierge concierge = new Concierge(
                request.fullName().trim(),
                request.phone().trim(),
                request.publicId().trim());
        concierge.setPhotoUrl(trimToNull(request.photoUrl()));
        return ConciergeResponse.from(conciergeRepository.save(concierge));
    }

    @GetMapping("/concierges")
    List<ConciergeResponse> listConcierges(@AuthenticationPrincipal AppUser user) {
        requireAdmin(user);
        return conciergeRepository.findAllByOrderByFullNameAsc().stream()
                .map(ConciergeResponse::from)
                .toList();
    }

    @GetMapping("/concierges/{conciergeId}")
    ConciergeResponse conciergeDetail(
            @AuthenticationPrincipal AppUser user,
            @PathVariable UUID conciergeId) {
        requireAdmin(user);
        return ConciergeResponse.from(conciergeRepository.findById(conciergeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Concierge not found")));
    }

    @PatchMapping("/concierges/{conciergeId}")
    ConciergeResponse updateConcierge(
            @AuthenticationPrincipal AppUser user,
            @PathVariable UUID conciergeId,
            @Valid @RequestBody UpdateConciergeRequest request) {
        requireAdmin(user);
        Concierge concierge = conciergeRepository.findById(conciergeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Concierge not found"));
        if (request.fullName() != null) {
            if (!org.springframework.util.StringUtils.hasText(request.fullName())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Concierge full name is required");
            }
            concierge.setFullName(request.fullName().trim());
        }
        if (request.phone() != null) {
            if (!org.springframework.util.StringUtils.hasText(request.phone())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Concierge phone is required");
            }
            concierge.setPhone(request.phone().trim());
        }
        if (request.photoUrl() != null) {
            concierge.setPhotoUrl(trimToNull(request.photoUrl()));
        }
        if (request.active() != null) {
            concierge.setActive(request.active());
        }
        return ConciergeResponse.from(conciergeRepository.save(concierge));
    }

    @PostMapping("/trips/{tripId}/concierge-assignment")
    TripConciergeAssignmentResponse assignConcierge(
            @AuthenticationPrincipal AppUser user,
            @PathVariable UUID tripId,
            @Valid @RequestBody AssignConciergeRequest request) {
        requireAdmin(user);
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found"));
        Concierge concierge = conciergeRepository.findById(request.conciergeId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Concierge not found"));
        if (!concierge.isActive()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Concierge is inactive");
        }

        trip.setAssignedConcierge(concierge);
        Trip savedTrip = tripRepository.save(trip);
        return new TripConciergeAssignmentResponse(savedTrip.getId(), ConciergeResponse.from(concierge));
    }

    @PostMapping("/trips/{tripId}/concierge-access-links")
    ConciergeAccessLinkResponse createConciergeAccessLink(
            @AuthenticationPrincipal AppUser user,
            @PathVariable UUID tripId,
            @Valid @RequestBody CreateConciergeAccessLinkRequest request) {
        requireAdmin(user);
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found"));
        Concierge concierge = conciergeRepository.findById(request.conciergeId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Concierge not found"));
        if (trip.getAssignedConcierge() == null
                || !trip.getAssignedConcierge().getId().equals(concierge.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Concierge is not assigned to trip");
        }

        String rawToken = secureTokenGenerator.generate();
        conciergeTripAccessRepository.save(new ConciergeTripAccess(
                trip,
                concierge,
                tokenHasher.hash(rawToken),
                request.expiresAt()));

        return new ConciergeAccessLinkResponse(
                trip.getId(),
                concierge.getId(),
                rawToken,
                conciergeUpdateUrl(trip.getId(), rawToken),
                request.expiresAt());
    }

    private void requireAdmin(AppUser user) {
        if (user == null || user.getAccountType() != AccountType.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        }
    }

    private String conciergeUpdateUrl(UUID tripId, String rawToken) {
        return appBaseUrl + "/concierge/trips/" + tripId + "?token=" + rawToken;
    }

    private String stripTrailingSlash(String value) {
        if (value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
