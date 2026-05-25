package com.arrivalos.trip;

import java.time.Instant;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.arrivalos.auth.TokenHasher;
import com.arrivalos.domain.model.ActorType;
import com.arrivalos.domain.model.ConciergeTripAccess;
import com.arrivalos.domain.repository.ConciergeTripAccessRepository;
import com.arrivalos.notification.NotificationService;
import com.arrivalos.trip.dto.ConciergeTripViewResponse;
import com.arrivalos.trip.dto.ConciergeTripTransitionRequest;
import com.arrivalos.trip.dto.TripTransitionResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/concierge/trips")
public class ConciergeTripController {

    private final ConciergeTripAccessRepository conciergeTripAccessRepository;
    private final TokenHasher tokenHasher;
    private final TripTransitionService tripTransitionService;
    private final NotificationService notificationService;
    private final TripManagementService tripManagementService;

    public ConciergeTripController(
            ConciergeTripAccessRepository conciergeTripAccessRepository,
            TokenHasher tokenHasher,
            TripTransitionService tripTransitionService,
            NotificationService notificationService,
            TripManagementService tripManagementService) {
        this.conciergeTripAccessRepository = conciergeTripAccessRepository;
        this.tokenHasher = tokenHasher;
        this.tripTransitionService = tripTransitionService;
        this.notificationService = notificationService;
        this.tripManagementService = tripManagementService;
    }

    @GetMapping("/{tripId}")
    ConciergeTripViewResponse tripView(
            @PathVariable UUID tripId,
            @RequestParam String accessToken) {
        ConciergeTripAccess access = validateAccess(tripId, accessToken);
        return tripManagementService.conciergeTripView(access.getTrip());
    }

    @PostMapping("/{tripId}/timeline-events")
    TripTransitionResponse createTimelineEvent(
            @PathVariable UUID tripId,
            @Valid @RequestBody ConciergeTripTransitionRequest request) {
        ConciergeTripAccess access = validateAccess(tripId, request.accessToken());

        TripTransitionResult result = tripTransitionService.transition(new TripTransitionCommand(
                tripId,
                request.eventType(),
                ActorType.CONCIERGE,
                access.getConcierge().getId(),
                request.checkpointName(),
                request.note(),
                request.idempotencyKey(),
                request.occurredAt(),
                request.offlineCreatedAt()));

        notificationService.notifyTimelineEvent(result);
        return TripTransitionResponse.from(result);
    }

    private ConciergeTripAccess validateAccess(UUID tripId, String rawToken) {
        ConciergeTripAccess access = conciergeTripAccessRepository.findByTokenHash(tokenHasher.hash(rawToken))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Invalid concierge trip access"));
        if (access.isRevoked()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Concierge trip access revoked");
        }
        if (!access.getTrip().getId().equals(tripId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Concierge trip access does not match trip");
        }
        if (access.getExpiresAt().isBefore(Instant.now()) || access.getExpiresAt().equals(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Concierge trip access expired");
        }
        if (access.getTrip().getAssignedConcierge() == null
                || !access.getTrip().getAssignedConcierge().getId().equals(access.getConcierge().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Concierge is not assigned to trip");
        }
        if (!access.getConcierge().isActive()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Concierge is inactive");
        }
        return access;
    }
}
