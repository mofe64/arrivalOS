package com.arrivalos.trip;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.arrivalos.domain.model.AccountType;
import com.arrivalos.domain.model.AppUser;
import com.arrivalos.domain.model.Trip;
import com.arrivalos.domain.model.TripPrincipal;
import com.arrivalos.domain.repository.AppUserRepository;
import com.arrivalos.domain.repository.TripPrincipalRepository;
import com.arrivalos.domain.repository.WatcherRepository;
import com.arrivalos.trip.dto.AdminPrincipalSummaryResponse;
import com.arrivalos.trip.dto.AdminPrincipalTripResponse;
import com.arrivalos.trip.dto.ConciergeResponse;
import com.arrivalos.trip.dto.WatcherResponse;

@RestController
@RequestMapping("/api/admin/principals")
public class AdminPrincipalController {

    private final AppUserRepository appUserRepository;
    private final TripPrincipalRepository tripPrincipalRepository;
    private final WatcherRepository watcherRepository;

    public AdminPrincipalController(
            AppUserRepository appUserRepository,
            TripPrincipalRepository tripPrincipalRepository,
            WatcherRepository watcherRepository) {
        this.appUserRepository = appUserRepository;
        this.tripPrincipalRepository = tripPrincipalRepository;
        this.watcherRepository = watcherRepository;
    }

    @GetMapping
    @Transactional(readOnly = true)
    List<AdminPrincipalSummaryResponse> listPrincipals(@AuthenticationPrincipal AppUser user) {
        requireAdmin(user);
        return appUserRepository.findByAccountTypeOrderByFullNameAsc(AccountType.PRINCIPAL).stream()
                .map(principal -> AdminPrincipalSummaryResponse.from(principal, tripsFor(principal)))
                .toList();
    }

    private List<AdminPrincipalTripResponse> tripsFor(AppUser principal) {
        return tripPrincipalRepository.findByUserAccountOrderByCreatedAtDesc(principal).stream()
                .map(TripPrincipal::getTrip)
                .map(this::tripResponse)
                .toList();
    }

    private AdminPrincipalTripResponse tripResponse(Trip trip) {
        ConciergeResponse concierge = trip.getAssignedConcierge() == null
                ? null
                : ConciergeResponse.from(trip.getAssignedConcierge());
        List<WatcherResponse> watchers = watcherRepository.findByTripOrderByCreatedAtAsc(trip).stream()
                .map(WatcherResponse::from)
                .toList();
        return AdminPrincipalTripResponse.from(trip, concierge, watchers);
    }

    private void requireAdmin(AppUser user) {
        if (user == null || user.getAccountType() != AccountType.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        }
    }
}
