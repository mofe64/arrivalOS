package com.arrivalos.trip;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.arrivalos.domain.model.AccountType;
import com.arrivalos.domain.model.ActorType;
import com.arrivalos.domain.model.AppUser;
import com.arrivalos.domain.model.CheckpointStatus;
import com.arrivalos.domain.model.Concierge;
import com.arrivalos.domain.model.NotificationChannel;
import com.arrivalos.domain.model.NotificationStatus;
import com.arrivalos.domain.model.TimelineEvent;
import com.arrivalos.domain.model.TimelineEventType;
import com.arrivalos.domain.model.Trip;
import com.arrivalos.domain.model.TripCheckpoint;
import com.arrivalos.domain.model.TripPrincipal;
import com.arrivalos.domain.model.TripStatus;
import com.arrivalos.domain.model.Watcher;
import com.arrivalos.domain.repository.AppUserRepository;
import com.arrivalos.domain.repository.ConciergeRepository;
import com.arrivalos.domain.repository.NotificationAttemptRepository;
import com.arrivalos.domain.repository.TimelineEventRepository;
import com.arrivalos.domain.repository.TripCheckpointRepository;
import com.arrivalos.domain.repository.TripPrincipalRepository;
import com.arrivalos.domain.repository.TripRepository;
import com.arrivalos.domain.repository.WatcherRepository;
import com.arrivalos.notification.NotificationService;
import com.arrivalos.trip.dto.AddTripPrincipalRequest;
import com.arrivalos.trip.dto.AdminTripDetailResponse;
import com.arrivalos.trip.dto.AdminTripListItemResponse;
import com.arrivalos.trip.dto.ConciergeResponse;
import com.arrivalos.trip.dto.ConciergeTripViewResponse;
import com.arrivalos.trip.dto.CreateAdminTripRequest;
import com.arrivalos.trip.dto.CreateWatcherRequest;
import com.arrivalos.trip.dto.NextAllowedActionResponse;
import com.arrivalos.trip.dto.NotificationAttemptResponse;
import com.arrivalos.trip.dto.PrincipalTripDetailResponse;
import com.arrivalos.trip.dto.PrincipalTripSummaryResponse;
import com.arrivalos.trip.dto.TimelineEventResponse;
import com.arrivalos.trip.dto.TripCheckpointResponse;
import com.arrivalos.trip.dto.TripPrincipalResponse;
import com.arrivalos.trip.dto.UpdateTripRequest;
import com.arrivalos.trip.dto.WatcherResponse;
import com.arrivalos.web.error.ApiException;

@Service
public class TripManagementService {

    private static final List<String> DEFAULT_CHECKPOINTS = List.of(
            "Port Health",
            "DSS",
            "Immigration",
            "Baggage Claim",
            "Customs",
            "NDLEA",
            "Quarantine");

    private static final List<TripStatus> ACTIVE_STATUSES = List.of(
            TripStatus.CREATED,
            TripStatus.FLIGHT_APPROACHING,
            TripStatus.CONCIERGE_IN_POSITION,
            TripStatus.FLIGHT_LANDED,
            TripStatus.CLIENT_MET,
            TripStatus.PROCESSING,
            TripStatus.TERMINAL_EXITED,
            TripStatus.HANDOVER_COMPLETED);

    private static final Duration STALE_AFTER = Duration.ofMinutes(30);

    private final TripRepository tripRepository;
    private final AppUserRepository appUserRepository;
    private final ConciergeRepository conciergeRepository;
    private final TripPrincipalRepository tripPrincipalRepository;
    private final WatcherRepository watcherRepository;
    private final TripCheckpointRepository tripCheckpointRepository;
    private final TimelineEventRepository timelineEventRepository;
    private final NotificationAttemptRepository notificationAttemptRepository;
    private final NotificationService notificationService;

    public TripManagementService(
            TripRepository tripRepository,
            AppUserRepository appUserRepository,
            ConciergeRepository conciergeRepository,
            TripPrincipalRepository tripPrincipalRepository,
            WatcherRepository watcherRepository,
            TripCheckpointRepository tripCheckpointRepository,
            TimelineEventRepository timelineEventRepository,
            NotificationAttemptRepository notificationAttemptRepository,
            NotificationService notificationService) {
        this.tripRepository = tripRepository;
        this.appUserRepository = appUserRepository;
        this.conciergeRepository = conciergeRepository;
        this.tripPrincipalRepository = tripPrincipalRepository;
        this.watcherRepository = watcherRepository;
        this.tripCheckpointRepository = tripCheckpointRepository;
        this.timelineEventRepository = timelineEventRepository;
        this.notificationAttemptRepository = notificationAttemptRepository;
        this.notificationService = notificationService;
    }

    @Transactional
    public AdminTripDetailResponse createTrip(AppUser admin, CreateAdminTripRequest request) {
        requireAdmin(admin);

        Trip trip = new Trip(request.flightNumber().trim(), request.arrivalAirport().trim());
        trip.setArrivalTerminal(trimToNull(request.arrivalTerminal()));
        trip.setMeetingPoint(trimToNull(request.meetingPoint()));
        trip.setScheduledArrivalAt(request.scheduledArrivalAt());
        trip.setActualArrivalAt(request.actualArrivalAt());
        trip.setStatus(TripStatus.CREATED);
        if (request.assignedConciergeId() != null) {
            trip.setAssignedConcierge(activeConcierge(request.assignedConciergeId()));
        }
        Trip savedTrip = tripRepository.save(trip);

        List<TripPrincipal> principals = savePrincipals(savedTrip, request.principals());
        List<Watcher> watchers = saveWatchers(savedTrip, request.watchers());
        saveCheckpoints(savedTrip, request.checkpoints());
        TimelineEvent createdEvent = new TimelineEvent(savedTrip, TimelineEventType.TRIP_CREATED, ActorType.OPS);
        createdEvent.setActorId(admin.getId());
        createdEvent.setNote("Trip created");
        timelineEventRepository.save(createdEvent);

        notificationService.notifyTripCreated(savedTrip, watchers);
        return adminDetail(savedTrip);
    }

    @Transactional(readOnly = true)
    public List<AdminTripListItemResponse> listTrips(boolean activeOnly) {
        List<Trip> trips = activeOnly
                ? tripRepository.findByStatusInOrderByUpdatedAtDesc(ACTIVE_STATUSES)
                : tripRepository.findAllByOrderByUpdatedAtDesc();
        return trips.stream()
                .map(this::adminListItem)
                .toList();
    }

    @Transactional(readOnly = true)
    public AdminTripDetailResponse adminDetail(UUID tripId) {
        return adminDetail(findTrip(tripId));
    }

    @Transactional
    public AdminTripDetailResponse updateTrip(UUID tripId, UpdateTripRequest request) {
        Trip trip = findTrip(tripId);
        if (request.flightNumber() != null) {
            requireText(request.flightNumber(), "FLIGHT_NUMBER_REQUIRED", "Flight number is required");
            trip.setFlightNumber(request.flightNumber().trim());
        }
        if (request.arrivalAirport() != null) {
            requireText(request.arrivalAirport(), "ARRIVAL_AIRPORT_REQUIRED", "Arrival airport is required");
            trip.setArrivalAirport(request.arrivalAirport().trim());
        }
        if (request.arrivalTerminal() != null) {
            trip.setArrivalTerminal(trimToNull(request.arrivalTerminal()));
        }
        if (request.meetingPoint() != null) {
            trip.setMeetingPoint(trimToNull(request.meetingPoint()));
        }
        if (request.scheduledArrivalAt() != null) {
            trip.setScheduledArrivalAt(request.scheduledArrivalAt());
        }
        if (request.actualArrivalAt() != null) {
            trip.setActualArrivalAt(request.actualArrivalAt());
        }
        if (request.assignedConciergeId() != null) {
            trip.setAssignedConcierge(activeConcierge(request.assignedConciergeId()));
        }
        return adminDetail(tripRepository.save(trip));
    }

    @Transactional
    public AdminTripDetailResponse addPrincipal(UUID tripId, AddTripPrincipalRequest request) {
        Trip trip = findTrip(tripId);
        TripPrincipal principal = buildPrincipal(
                trip,
                request.userAccountId(),
                request.fullName(),
                request.phone(),
                request.photoUrl(),
                request.primaryContact(),
                nextPrincipalSequence(trip));
        if (principal.isPrimaryContact()) {
            clearPrimaryContact(trip);
        }
        tripPrincipalRepository.save(principal);
        return adminDetail(trip);
    }

    @Transactional
    public WatcherResponse addAdminWatcher(UUID tripId, CreateWatcherRequest request) {
        Trip trip = findTrip(tripId);
        Watcher watcher = createWatcher(trip, request.fullName(), request.email(), request.phone());
        notificationService.notifyWatcherAdded(trip, watcher);
        return WatcherResponse.from(watcher);
    }

    @Transactional
    public WatcherResponse addPrincipalWatcher(AppUser user, UUID tripId, CreateWatcherRequest request) {
        requirePrincipal(user);
        Trip trip = findTrip(tripId);
        ensurePrincipalOwnsTrip(user, trip);
        Watcher watcher = createWatcher(trip, request.fullName(), request.email(), request.phone());
        notificationService.notifyWatcherAdded(trip, watcher);
        return WatcherResponse.from(watcher);
    }

    @Transactional(readOnly = true)
    public List<NotificationAttemptResponse> notificationAttempts(UUID tripId, NotificationStatus status) {
        Trip trip = findTrip(tripId);
        List<com.arrivalos.domain.model.NotificationAttempt> attempts = status == null
                ? notificationAttemptRepository.findByTripOrderByCreatedAtAsc(trip)
                : notificationAttemptRepository.findByTripAndStatusOrderByCreatedAtAsc(trip, status);
        return attempts.stream()
                .map(NotificationAttemptResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PrincipalTripSummaryResponse> principalTrips(AppUser user) {
        requirePrincipal(user);
        return tripPrincipalRepository.findByUserAccountOrderByCreatedAtDesc(user).stream()
                .map(TripPrincipal::getTrip)
                .distinct()
                .sorted(Comparator.comparing(Trip::getUpdatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .reversed())
                .map(this::principalSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public PrincipalTripDetailResponse principalTrip(AppUser user, UUID tripId) {
        requirePrincipal(user);
        Trip trip = findTrip(tripId);
        ensurePrincipalOwnsTrip(user, trip);
        return principalDetail(trip);
    }

    @Transactional(readOnly = true)
    public ConciergeTripViewResponse conciergeTripView(Trip trip) {
        List<TripCheckpointResponse> checkpoints = checkpointResponses(trip);
        TripCheckpointResponse currentCheckpoint = currentCheckpoint(checkpoints);
        return new ConciergeTripViewResponse(
                trip.getId(),
                trip.getFlightNumber(),
                trip.getArrivalAirport(),
                trip.getArrivalTerminal(),
                trip.getMeetingPoint(),
                trip.getScheduledArrivalAt(),
                trip.getActualArrivalAt(),
                trip.getStatus(),
                tripPrincipalRepository.findByTripOrderBySequenceNumberAsc(trip).stream()
                        .map(TripPrincipalResponse::from)
                        .toList(),
                conciergeResponse(trip),
                checkpoints,
                customerSafeTimeline(trip),
                watcherRepository.findByTripOrderByCreatedAtAsc(trip).size(),
                currentCheckpoint,
                nextAllowedAction(trip, checkpoints, currentCheckpoint));
    }

    @Transactional(readOnly = true)
    public List<TimelineEventResponse> principalTimeline(AppUser user, UUID tripId) {
        requirePrincipal(user);
        Trip trip = findTrip(tripId);
        ensurePrincipalOwnsTrip(user, trip);
        return customerSafeTimeline(trip);
    }

    private AdminTripDetailResponse adminDetail(Trip trip) {
        List<TripPrincipalResponse> principals = tripPrincipalRepository.findByTripOrderBySequenceNumberAsc(trip).stream()
                .map(TripPrincipalResponse::from)
                .toList();
        List<WatcherResponse> watchers = watcherRepository.findByTripOrderByCreatedAtAsc(trip).stream()
                .map(WatcherResponse::from)
                .toList();
        List<TripCheckpointResponse> checkpoints = checkpointResponses(trip);
        List<TimelineEventResponse> timelineEvents = timelineEventRepository.findByTripOrderByOccurredAtAsc(trip).stream()
                .map(TimelineEventResponse::from)
                .toList();
        List<NotificationAttemptResponse> attempts = notificationAttemptRepository.findByTripOrderByCreatedAtAsc(trip)
                .stream()
                .map(NotificationAttemptResponse::from)
                .toList();

        return AdminTripDetailResponse.from(
                trip,
                principals,
                watchers,
                conciergeResponse(trip),
                checkpoints,
                timelineEvents,
                attempts,
                lastUpdatedAt(trip),
                currentCheckpoint(checkpoints));
    }

    private AdminTripListItemResponse adminListItem(Trip trip) {
        List<TripPrincipal> principals = tripPrincipalRepository.findByTripOrderBySequenceNumberAsc(trip);
        List<TimelineEvent> events = timelineEventRepository.findByTripOrderByOccurredAtDesc(trip);
        TimelineEvent lastEvent = events.isEmpty() ? null : events.get(0);
        Instant lastUpdatedAt = lastUpdatedAt(trip, lastEvent);

        return new AdminTripListItemResponse(
                trip.getId(),
                trip.getFlightNumber(),
                trip.getArrivalAirport(),
                trip.getArrivalTerminal(),
                trip.getScheduledArrivalAt(),
                trip.getStatus(),
                primaryPrincipal(principals),
                principals.size(),
                conciergeResponse(trip),
                watcherRepository.findByTripOrderByCreatedAtAsc(trip).size(),
                lastEvent == null ? null : TimelineEventResponse.from(lastEvent),
                lastUpdatedAt,
                isStale(trip, lastUpdatedAt),
                notificationAttemptRepository.countByTripAndStatus(trip, NotificationStatus.SENT),
                notificationAttemptRepository.countByTripAndStatus(trip, NotificationStatus.FAILED));
    }

    private PrincipalTripSummaryResponse principalSummary(Trip trip) {
        List<TripCheckpointResponse> checkpoints = checkpointResponses(trip);
        TimelineEvent lastEvent = customerSafeEvents(trip).stream()
                .max(Comparator.comparing(TimelineEvent::getOccurredAt))
                .orElse(null);
        return new PrincipalTripSummaryResponse(
                trip.getId(),
                trip.getFlightNumber(),
                trip.getArrivalAirport(),
                trip.getArrivalTerminal(),
                trip.getScheduledArrivalAt(),
                trip.getActualArrivalAt(),
                trip.getStatus(),
                conciergeResponse(trip),
                trip.getMeetingPoint(),
                lastEvent == null ? null : TimelineEventResponse.from(lastEvent),
                lastUpdatedAt(trip, lastEvent),
                currentCheckpoint(checkpoints));
    }

    private PrincipalTripDetailResponse principalDetail(Trip trip) {
        List<TripCheckpointResponse> checkpoints = checkpointResponses(trip);
        return new PrincipalTripDetailResponse(
                trip.getId(),
                trip.getFlightNumber(),
                trip.getArrivalAirport(),
                trip.getArrivalTerminal(),
                trip.getScheduledArrivalAt(),
                trip.getActualArrivalAt(),
                trip.getStatus(),
                tripPrincipalRepository.findByTripOrderBySequenceNumberAsc(trip).stream()
                        .map(TripPrincipalResponse::from)
                        .toList(),
                conciergeResponse(trip),
                trip.getMeetingPoint(),
                checkpoints,
                customerSafeTimeline(trip),
                lastUpdatedAt(trip),
                currentCheckpoint(checkpoints));
    }

    private List<TimelineEventResponse> customerSafeTimeline(Trip trip) {
        return customerSafeEvents(trip).stream()
                .map(TimelineEventResponse::from)
                .toList();
    }

    private List<TimelineEvent> customerSafeEvents(Trip trip) {
        return timelineEventRepository.findByTripOrderByOccurredAtAsc(trip).stream()
                .filter(event -> event.getEventType() != TimelineEventType.TRIP_CREATED)
                .toList();
    }

    private List<TripPrincipal> savePrincipals(
            Trip trip,
            List<CreateAdminTripRequest.TripPrincipalRequest> requests) {
        List<TripPrincipal> principals = new ArrayList<>();
        for (int index = 0; index < requests.size(); index++) {
            CreateAdminTripRequest.TripPrincipalRequest request = requests.get(index);
            TripPrincipal principal = buildPrincipal(
                    trip,
                    request.userAccountId(),
                    request.fullName(),
                    request.phone(),
                    request.photoUrl(),
                    request.primaryContact(),
                    index + 1);
            if (request.primaryContact() == null && index == 0) {
                principal.setPrimaryContact(true);
            }
            principals.add(principal);
        }
        if (principals.stream().filter(TripPrincipal::isPrimaryContact).count() > 1) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "MULTIPLE_PRIMARY_PRINCIPALS",
                    "Only one trip principal can be primary");
        }
        return tripPrincipalRepository.saveAll(principals);
    }

    private TripPrincipal buildPrincipal(
            Trip trip,
            UUID userAccountId,
            String fullName,
            String phone,
            String photoUrl,
            Boolean primaryContact,
            int sequenceNumber) {
        AppUser account = null;
        if (userAccountId != null) {
            account = appUserRepository.findById(userAccountId)
                    .orElseThrow(() -> new ApiException(
                            HttpStatus.NOT_FOUND,
                            "PRINCIPAL_ACCOUNT_NOT_FOUND",
                            "Principal account not found"));
            if (account.getAccountType() != AccountType.PRINCIPAL) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "USER_ACCOUNT_IS_NOT_PRINCIPAL",
                        "User account is not a principal");
            }
        }
        String resolvedName = firstText(fullName, account == null ? null : account.getFullName());
        if (!StringUtils.hasText(resolvedName)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "PRINCIPAL_NAME_REQUIRED",
                    "Principal full name is required");
        }
        TripPrincipal principal = new TripPrincipal(trip, resolvedName.trim(), sequenceNumber);
        principal.setUserAccount(account);
        principal.setPhone(firstText(phone, account == null ? null : account.getPhone()));
        principal.setPhotoUrl(trimToNull(photoUrl));
        principal.setPrimaryContact(Boolean.TRUE.equals(primaryContact));
        return principal;
    }

    private List<Watcher> saveWatchers(
            Trip trip,
            List<CreateAdminTripRequest.TripWatcherRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }
        List<Watcher> watchers = new ArrayList<>();
        for (CreateAdminTripRequest.TripWatcherRequest request : requests) {
            watchers.add(createWatcher(trip, request.fullName(), request.email(), request.phone()));
        }
        return watchers;
    }

    private Watcher createWatcher(Trip trip, String fullName, String email, String phone) {
        String normalizedEmail = requireEmail(email);
        if (watcherRepository.findByTripAndEmailIgnoreCase(trip, normalizedEmail).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "WATCHER_ALREADY_EXISTS_FOR_TRIP", "Watcher already exists for trip");
        }
        Watcher watcher = new Watcher(
                trip,
                fullName.trim(),
                normalizedEmail,
                trimToNull(phone),
                NotificationChannel.EMAIL);
        return watcherRepository.save(watcher);
    }

    private void saveCheckpoints(Trip trip, List<CreateAdminTripRequest.TripCheckpointRequest> requests) {
        List<String> checkpointNames = requests == null || requests.isEmpty()
                ? DEFAULT_CHECKPOINTS
                : requests.stream().map(CreateAdminTripRequest.TripCheckpointRequest::name).toList();
        List<TripCheckpoint> checkpoints = new ArrayList<>();
        for (int index = 0; index < checkpointNames.size(); index++) {
            checkpoints.add(new TripCheckpoint(trip, checkpointNames.get(index).trim(), index + 1));
        }
        tripCheckpointRepository.saveAll(checkpoints);
    }

    private int nextPrincipalSequence(Trip trip) {
        return tripPrincipalRepository.findByTripOrderBySequenceNumberAsc(trip).stream()
                .mapToInt(TripPrincipal::getSequenceNumber)
                .max()
                .orElse(0) + 1;
    }

    private void clearPrimaryContact(Trip trip) {
        for (TripPrincipal existing : tripPrincipalRepository.findByTripOrderBySequenceNumberAsc(trip)) {
            if (existing.isPrimaryContact()) {
                existing.setPrimaryContact(false);
                tripPrincipalRepository.save(existing);
            }
        }
    }

    private Concierge activeConcierge(UUID conciergeId) {
        Concierge concierge = conciergeRepository.findById(conciergeId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CONCIERGE_NOT_FOUND", "Concierge not found"));
        if (!concierge.isActive()) {
            throw new ApiException(HttpStatus.CONFLICT, "CONCIERGE_IS_INACTIVE", "Concierge is inactive");
        }
        return concierge;
    }

    private Trip findTrip(UUID tripId) {
        return tripRepository.findById(tripId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TRIP_NOT_FOUND", "Trip not found"));
    }

    public void requireAdmin(AppUser user) {
        if (user == null || user.getAccountType() != AccountType.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ADMIN_ACCESS_REQUIRED", "Admin access required");
        }
    }

    private void requirePrincipal(AppUser user) {
        if (user == null || user.getAccountType() != AccountType.PRINCIPAL) {
            throw new ApiException(HttpStatus.FORBIDDEN, "PRINCIPAL_ACCESS_REQUIRED", "Principal access required");
        }
    }

    private void ensurePrincipalOwnsTrip(AppUser user, Trip trip) {
        if (!tripPrincipalRepository.existsByTripAndUserAccount(trip, user)) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "PRINCIPAL_IS_NOT_ASSIGNED_TO_TRIP",
                    "Principal is not assigned to trip");
        }
    }

    private List<TripCheckpointResponse> checkpointResponses(Trip trip) {
        return tripCheckpointRepository.findByTripOrderBySequenceNumberAsc(trip).stream()
                .map(TripCheckpointResponse::from)
                .toList();
    }

    private TripCheckpointResponse currentCheckpoint(List<TripCheckpointResponse> checkpoints) {
        return checkpoints.stream()
                .filter(checkpoint -> checkpoint.status() == CheckpointStatus.ACTIVE)
                .findFirst()
                .orElse(null);
    }

    private TripPrincipalResponse primaryPrincipal(List<TripPrincipal> principals) {
        return principals.stream()
                .filter(TripPrincipal::isPrimaryContact)
                .findFirst()
                .or(() -> principals.stream().findFirst())
                .map(TripPrincipalResponse::from)
                .orElse(null);
    }

    private ConciergeResponse conciergeResponse(Trip trip) {
        return trip.getAssignedConcierge() == null ? null : ConciergeResponse.from(trip.getAssignedConcierge());
    }

    private Instant lastUpdatedAt(Trip trip) {
        TimelineEvent lastEvent = timelineEventRepository.findByTripOrderByOccurredAtDesc(trip).stream()
                .findFirst()
                .orElse(null);
        return lastUpdatedAt(trip, lastEvent);
    }

    private Instant lastUpdatedAt(Trip trip, TimelineEvent lastEvent) {
        if (lastEvent != null && lastEvent.getOccurredAt() != null) {
            return lastEvent.getOccurredAt();
        }
        return trip.getUpdatedAt();
    }

    private boolean isStale(Trip trip, Instant lastUpdatedAt) {
        return ACTIVE_STATUSES.contains(trip.getStatus())
                && lastUpdatedAt != null
                && lastUpdatedAt.isBefore(Instant.now().minus(STALE_AFTER));
    }

    private String requireEmail(String email) {
        if (!StringUtils.hasText(email)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "WATCHER_EMAIL_REQUIRED", "Watcher email is required");
        }
        return email.trim();
    }

    private String firstText(String preferred, String fallback) {
        if (StringUtils.hasText(preferred)) {
            return preferred.trim();
        }
        if (StringUtils.hasText(fallback)) {
            return fallback.trim();
        }
        return null;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private NextAllowedActionResponse nextAllowedAction(
            Trip trip,
            List<TripCheckpointResponse> checkpoints,
            TripCheckpointResponse currentCheckpoint) {
        return switch (trip.getStatus()) {
            case CREATED, FLIGHT_APPROACHING -> new NextAllowedActionResponse(
                    TimelineEventType.CONCIERGE_IN_POSITION,
                    null,
                    "Mark concierge in position");
            case CONCIERGE_IN_POSITION -> new NextAllowedActionResponse(
                    TimelineEventType.FLIGHT_LANDED,
                    null,
                    "Mark flight landed");
            case FLIGHT_LANDED -> new NextAllowedActionResponse(
                    TimelineEventType.CLIENT_MET,
                    null,
                    "Mark client met");
            case CLIENT_MET -> nextCheckpointStart(checkpoints);
            case PROCESSING -> currentCheckpoint == null
                    ? nextProcessingAction(checkpoints)
                    : new NextAllowedActionResponse(
                            TimelineEventType.CHECKPOINT_COMPLETED,
                            currentCheckpoint.name(),
                            "Complete " + currentCheckpoint.name());
            case TERMINAL_EXITED -> new NextAllowedActionResponse(
                    TimelineEventType.HANDOVER_COMPLETED,
                    null,
                    "Complete handover");
            case HANDOVER_COMPLETED -> new NextAllowedActionResponse(
                    TimelineEventType.TRIP_COMPLETED,
                    null,
                    "Close trip");
            case COMPLETED, CANCELLED -> null;
        };
    }

    private NextAllowedActionResponse nextProcessingAction(List<TripCheckpointResponse> checkpoints) {
        NextAllowedActionResponse checkpointStart = nextCheckpointStart(checkpoints);
        if (checkpointStart != null) {
            return checkpointStart;
        }
        return new NextAllowedActionResponse(TimelineEventType.TERMINAL_EXITED, null, "Mark terminal exited");
    }

    private NextAllowedActionResponse nextCheckpointStart(List<TripCheckpointResponse> checkpoints) {
        return checkpoints.stream()
                .filter(checkpoint -> checkpoint.status() == CheckpointStatus.PENDING)
                .findFirst()
                .map(checkpoint -> new NextAllowedActionResponse(
                        TimelineEventType.CHECKPOINT_STARTED,
                        checkpoint.name(),
                        "Start " + checkpoint.name()))
                .orElse(null);
    }

    private void requireText(String value, String code, String message) {
        if (!StringUtils.hasText(value)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, code, message);
        }
    }
}
