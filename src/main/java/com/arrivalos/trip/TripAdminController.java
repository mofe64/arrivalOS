package com.arrivalos.trip;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.arrivalos.domain.model.AccountType;
import com.arrivalos.domain.model.ActorType;
import com.arrivalos.domain.model.AppUser;
import com.arrivalos.domain.model.NotificationStatus;
import com.arrivalos.domain.model.TimelineEventType;
import com.arrivalos.notification.NotificationService;
import com.arrivalos.trip.dto.AddTripPrincipalRequest;
import com.arrivalos.trip.dto.AdminTripDetailResponse;
import com.arrivalos.trip.dto.AdminTripListItemResponse;
import com.arrivalos.trip.dto.CancelTripRequest;
import com.arrivalos.trip.dto.CreateAdminTripRequest;
import com.arrivalos.trip.dto.CreateWatcherRequest;
import com.arrivalos.trip.dto.NotificationAttemptResponse;
import com.arrivalos.trip.dto.TripTransitionRequest;
import com.arrivalos.trip.dto.TripTransitionResponse;
import com.arrivalos.trip.dto.UpdateTripRequest;
import com.arrivalos.trip.dto.WatcherResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/trips")
public class TripAdminController {

    private final TripTransitionService tripTransitionService;
    private final TripManagementService tripManagementService;
    private final NotificationService notificationService;

    public TripAdminController(
            TripTransitionService tripTransitionService,
            TripManagementService tripManagementService,
            NotificationService notificationService) {
        this.tripTransitionService = tripTransitionService;
        this.tripManagementService = tripManagementService;
        this.notificationService = notificationService;
    }

    @PostMapping
    AdminTripDetailResponse createTrip(
            @AuthenticationPrincipal AppUser user,
            @Valid @RequestBody CreateAdminTripRequest request) {
        requireAdmin(user);
        return tripManagementService.createTrip(user, request);
    }

    @GetMapping
    @Transactional(readOnly = true)
    List<AdminTripListItemResponse> listTrips(@AuthenticationPrincipal AppUser user) {
        requireAdmin(user);
        return tripManagementService.listTrips(false);
    }

    @GetMapping("/active")
    @Transactional(readOnly = true)
    List<AdminTripListItemResponse> listActiveTrips(@AuthenticationPrincipal AppUser user) {
        requireAdmin(user);
        return tripManagementService.listTrips(true);
    }

    @GetMapping("/{tripId}")
    @Transactional(readOnly = true)
    AdminTripDetailResponse tripDetail(
            @AuthenticationPrincipal AppUser user,
            @PathVariable UUID tripId) {
        requireAdmin(user);
        return tripManagementService.adminDetail(tripId);
    }

    @PatchMapping("/{tripId}")
    AdminTripDetailResponse updateTrip(
            @AuthenticationPrincipal AppUser user,
            @PathVariable UUID tripId,
            @Valid @RequestBody UpdateTripRequest request) {
        requireAdmin(user);
        return tripManagementService.updateTrip(tripId, request);
    }

    @PostMapping("/{tripId}/principals")
    AdminTripDetailResponse addPrincipal(
            @AuthenticationPrincipal AppUser user,
            @PathVariable UUID tripId,
            @Valid @RequestBody AddTripPrincipalRequest request) {
        requireAdmin(user);
        return tripManagementService.addPrincipal(tripId, request);
    }

    @PostMapping("/{tripId}/watchers")
    WatcherResponse addWatcher(
            @AuthenticationPrincipal AppUser user,
            @PathVariable UUID tripId,
            @Valid @RequestBody CreateWatcherRequest request) {
        requireAdmin(user);
        return tripManagementService.addAdminWatcher(tripId, request);
    }

    @PostMapping("/{tripId}/cancel")
    TripTransitionResponse cancelTrip(
            @AuthenticationPrincipal AppUser user,
            @PathVariable UUID tripId,
            @Valid @RequestBody CancelTripRequest request) {
        requireAdmin(user);
        TripTransitionResult result = tripTransitionService.transition(new TripTransitionCommand(
                tripId,
                TimelineEventType.TRIP_CANCELLED,
                ActorType.OPS,
                user.getId(),
                null,
                request.note(),
                request.idempotencyKey(),
                null,
                null));
        notificationService.notifyTimelineEvent(result);
        return TripTransitionResponse.from(result);
    }

    @GetMapping("/{tripId}/notification-attempts")
    @Transactional(readOnly = true)
    List<NotificationAttemptResponse> notificationAttempts(
            @AuthenticationPrincipal AppUser user,
            @PathVariable UUID tripId,
            @RequestParam(required = false) NotificationStatus status) {
        requireAdmin(user);
        return tripManagementService.notificationAttempts(tripId, status);
    }

    @PostMapping("/{tripId}/timeline-events")
    TripTransitionResponse createTimelineEvent(
            @AuthenticationPrincipal AppUser user,
            @PathVariable UUID tripId,
            @Valid @RequestBody TripTransitionRequest request) {
        requireAdmin(user);

        TripTransitionResult result = tripTransitionService.transition(new TripTransitionCommand(
                tripId,
                request.eventType(),
                ActorType.OPS,
                user.getId(),
                request.checkpointName(),
                request.note(),
                request.idempotencyKey(),
                request.occurredAt(),
                request.offlineCreatedAt()));

        notificationService.notifyTimelineEvent(result);
        return TripTransitionResponse.from(result);
    }

    private void requireAdmin(AppUser user) {
        if (user == null || user.getAccountType() != AccountType.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        }
    }
}
