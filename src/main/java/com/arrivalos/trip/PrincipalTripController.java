package com.arrivalos.trip;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.arrivalos.domain.model.AppUser;
import com.arrivalos.trip.dto.CreateWatcherRequest;
import com.arrivalos.trip.dto.PrincipalTripDetailResponse;
import com.arrivalos.trip.dto.PrincipalTripSummaryResponse;
import com.arrivalos.trip.dto.TimelineEventResponse;
import com.arrivalos.trip.dto.WatcherResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/principal/trips")
public class PrincipalTripController {

    private final TripManagementService tripManagementService;

    public PrincipalTripController(TripManagementService tripManagementService) {
        this.tripManagementService = tripManagementService;
    }

    @GetMapping
    List<PrincipalTripSummaryResponse> listTrips(@AuthenticationPrincipal AppUser user) {
        return tripManagementService.principalTrips(user);
    }

    @GetMapping("/{tripId}")
    PrincipalTripDetailResponse tripDetail(
            @AuthenticationPrincipal AppUser user,
            @PathVariable UUID tripId) {
        return tripManagementService.principalTrip(user, tripId);
    }

    @GetMapping("/{tripId}/timeline")
    List<TimelineEventResponse> timeline(
            @AuthenticationPrincipal AppUser user,
            @PathVariable UUID tripId) {
        return tripManagementService.principalTimeline(user, tripId);
    }

    @PostMapping("/{tripId}/watchers")
    WatcherResponse createWatcher(
            @AuthenticationPrincipal AppUser user,
            @PathVariable UUID tripId,
            @Valid @RequestBody CreateWatcherRequest request) {
        return tripManagementService.addPrincipalWatcher(user, tripId, request);
    }
}
