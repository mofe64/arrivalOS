package com.arrivalos.trip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.stream.Stream;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

import com.arrivalos.domain.model.ActorType;
import com.arrivalos.domain.model.Concierge;
import com.arrivalos.domain.model.ConciergeTripAccess;
import com.arrivalos.domain.model.TimelineEvent;
import com.arrivalos.domain.model.TimelineEventType;
import com.arrivalos.domain.model.Trip;
import com.arrivalos.domain.model.TripCheckpoint;
import com.arrivalos.domain.model.TripStatus;
import com.arrivalos.domain.repository.ConciergeRepository;
import com.arrivalos.domain.repository.ConciergeTripAccessRepository;
import com.arrivalos.domain.repository.TimelineEventRepository;
import com.arrivalos.domain.repository.TripCheckpointRepository;
import com.arrivalos.domain.repository.TripRepository;

@ActiveProfiles("test")
@SpringBootTest
class TripTransitionServiceTests {

    @Autowired
    private TripTransitionService tripTransitionService;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private TimelineEventRepository timelineEventRepository;

    @Autowired
    private TripCheckpointRepository tripCheckpointRepository;

    @Autowired
    private ConciergeRepository conciergeRepository;

    @Autowired
    private ConciergeTripAccessRepository conciergeTripAccessRepository;

    @ParameterizedTest
    @MethodSource("allowedTransitions")
    void allowedTransitionsUpdateTripStatusAndCreateTimelineEvent(
            TripStatus initialStatus,
            TimelineEventType eventType,
            ActorType actorType,
            TripStatus expectedStatus) {
        Trip trip = tripWithStatus(initialStatus);
        if (eventType == TimelineEventType.CHECKPOINT_STARTED
                || eventType == TimelineEventType.CHECKPOINT_COMPLETED) {
            TripCheckpoint checkpoint = new TripCheckpoint(trip, "Immigration", 1);
            if (eventType == TimelineEventType.CHECKPOINT_COMPLETED) {
                checkpoint.setStatus(com.arrivalos.domain.model.CheckpointStatus.ACTIVE);
            }
            tripCheckpointRepository.save(checkpoint);
        }
        UUID actorId = actorType == ActorType.SYSTEM ? null : UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-05-25T09:15:00Z");
        String idempotencyKey = "allowed-" + initialStatus + "-" + eventType + "-" + trip.getId();
        String checkpointName = eventType == TimelineEventType.CHECKPOINT_STARTED
                || eventType == TimelineEventType.CHECKPOINT_COMPLETED
                ? "Immigration"
                : null;

        TripTransitionResult result = tripTransitionService.transition(new TripTransitionCommand(
                trip.getId(),
                eventType,
                actorType,
                actorId,
                checkpointName,
                "Concierge is waiting at arrivals.",
                idempotencyKey,
                occurredAt,
                null));

        assertThat(result.duplicate()).isFalse();
        assertThat(result.trip().getStatus()).isEqualTo(expectedStatus);
        assertThat(result.event().getEventType()).isEqualTo(eventType);
        assertThat(result.event().getActorType()).isEqualTo(actorType);
        assertThat(result.event().getActorId()).isEqualTo(actorId);
        assertThat(result.event().getNote()).isEqualTo("Concierge is waiting at arrivals.");
        assertThat(result.event().getIdempotencyKey()).isEqualTo(idempotencyKey);
        assertThat(result.event().getOccurredAt()).isEqualTo(occurredAt);

        Trip savedTrip = tripRepository.findById(trip.getId()).orElseThrow();
        assertThat(savedTrip.getStatus()).isEqualTo(expectedStatus);
        assertThat(timelineEventRepository.findByTripOrderByOccurredAtAsc(savedTrip))
                .extracting(TimelineEvent::getEventType)
                .containsExactly(eventType);
    }

    @Test
    void duplicateIdempotencyKeyWithSamePayloadReturnsExistingEventWithoutCreatingAnotherTimelineEvent() {
        Trip trip = tripRepository.save(new Trip("BA075", "MMIA"));
        String idempotencyKey = "same-offline-submit-" + trip.getId();
        TripTransitionCommand command = new TripTransitionCommand(
                trip.getId(),
                TimelineEventType.FLIGHT_APPROACHING,
                ActorType.SYSTEM,
                null,
                null,
                null,
                idempotencyKey,
                Instant.parse("2026-05-25T10:00:00Z"),
                null);

        TripTransitionResult first = tripTransitionService.transition(command);
        TripTransitionResult second = tripTransitionService.transition(command);

        assertThat(first.duplicate()).isFalse();
        assertThat(second.duplicate()).isTrue();
        assertThat(second.event().getId()).isEqualTo(first.event().getId());

        Trip savedTrip = tripRepository.findById(trip.getId()).orElseThrow();
        assertThat(savedTrip.getStatus()).isEqualTo(TripStatus.FLIGHT_APPROACHING);
        assertThat(timelineEventRepository.findByTripOrderByOccurredAtAsc(savedTrip)).hasSize(1);
    }

    @Test
    void duplicateIdempotencyKeyWithDifferentPayloadIsRejectedWithoutCreatingAnotherTimelineEvent() {
        Trip trip = tripRepository.save(new Trip("ET900", "LOS"));
        String idempotencyKey = "same-key-different-payload-" + trip.getId();
        tripTransitionService.transition(new TripTransitionCommand(
                trip.getId(),
                TimelineEventType.FLIGHT_APPROACHING,
                ActorType.SYSTEM,
                null,
                null,
                null,
                idempotencyKey,
                Instant.parse("2026-05-25T10:00:00Z"),
                null));

        assertThatThrownBy(() -> tripTransitionService.transition(new TripTransitionCommand(
                trip.getId(),
                TimelineEventType.CONCIERGE_IN_POSITION,
                ActorType.CONCIERGE,
                UUID.randomUUID(),
                null,
                null,
                idempotencyKey,
                Instant.parse("2026-05-25T10:05:00Z"),
                null)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertResponseStatusException(
                        exception,
                        HttpStatus.CONFLICT,
                        "Idempotency key already used for a different trip event"));

        Trip savedTrip = tripRepository.findById(trip.getId()).orElseThrow();
        assertThat(savedTrip.getStatus()).isEqualTo(TripStatus.FLIGHT_APPROACHING);
        assertThat(timelineEventRepository.findByTripOrderByOccurredAtAsc(savedTrip)).hasSize(1);
    }

    @ParameterizedTest
    @MethodSource("invalidTransitions")
    void rejectsInvalidTransitionsAndDoesNotMutateTripOrTimeline(
            TripStatus initialStatus,
            TimelineEventType eventType) {
        Trip trip = tripWithStatus(initialStatus);

        assertThatThrownBy(() -> tripTransitionService.transition(new TripTransitionCommand(
                trip.getId(),
                eventType,
                ActorType.CONCIERGE,
                UUID.randomUUID(),
                null,
                null,
                "invalid-" + initialStatus + "-" + eventType + "-" + trip.getId(),
                Instant.parse("2026-05-25T11:00:00Z"),
                null)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertResponseStatusException(
                        exception,
                        HttpStatus.CONFLICT,
                        "Invalid trip status transition"));

        Trip savedTrip = tripRepository.findById(trip.getId()).orElseThrow();
        assertThat(savedTrip.getStatus()).isEqualTo(initialStatus);
        assertThat(timelineEventRepository.findByTripOrderByOccurredAtAsc(savedTrip)).isEmpty();
    }

    @Test
    void rejectsMissingTrip() {
        UUID missingTripId = UUID.randomUUID();

        assertThatThrownBy(() -> tripTransitionService.transition(new TripTransitionCommand(
                missingTripId,
                TimelineEventType.FLIGHT_APPROACHING,
                ActorType.SYSTEM,
                null,
                null,
                null,
                "missing-trip-" + missingTripId,
                Instant.parse("2026-05-25T11:00:00Z"),
                null)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertResponseStatusException(
                        exception,
                        HttpStatus.NOT_FOUND,
                        "Trip not found"));
    }

    @Test
    void rejectsTripCreatedAsOperationalTransition() {
        Trip trip = tripRepository.save(new Trip("QR1407", "ABV"));

        assertThatThrownBy(() -> tripTransitionService.transition(new TripTransitionCommand(
                trip.getId(),
                TimelineEventType.TRIP_CREATED,
                ActorType.OPS,
                null,
                null,
                null,
                "trip-created-not-allowed-" + trip.getId(),
                Instant.parse("2026-05-25T11:00:00Z"),
                null)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertResponseStatusException(
                        exception,
                        HttpStatus.CONFLICT,
                        "TRIP_CREATED cannot be submitted as an operational transition"));

        Trip savedTrip = tripRepository.findById(trip.getId()).orElseThrow();
        assertThat(savedTrip.getStatus()).isEqualTo(TripStatus.CREATED);
        assertThat(timelineEventRepository.findByTripOrderByOccurredAtAsc(savedTrip)).isEmpty();
    }

    @Test
    void checkpointEventsMoveTripIntoProcessingAndPreserveCheckpointContext() {
        Trip trip = new Trip("VS411", "MMIA");
        trip.setStatus(TripStatus.CLIENT_MET);
        trip = tripRepository.save(trip);
        tripCheckpointRepository.save(new TripCheckpoint(trip, "Immigration", 3));

        TripTransitionResult result = tripTransitionService.transition(new TripTransitionCommand(
                trip.getId(),
                TimelineEventType.CHECKPOINT_STARTED,
                ActorType.CONCIERGE,
                UUID.randomUUID(),
                "Immigration",
                "Principal has entered immigration processing.",
                "checkpoint-started-" + trip.getId(),
                Instant.parse("2026-05-25T12:00:00Z"),
                null));

        assertThat(result.trip().getStatus()).isEqualTo(TripStatus.PROCESSING);
        assertThat(result.event().getCheckpointName()).isEqualTo("Immigration");
        assertThat(result.event().getNote()).isEqualTo("Principal has entered immigration processing.");
    }

    @Test
    void rejectsStartingSecondActiveCheckpoint() {
        Trip trip = new Trip("BA075", "MMIA");
        trip.setStatus(TripStatus.PROCESSING);
        trip = tripRepository.save(trip);
        TripCheckpoint immigration = new TripCheckpoint(trip, "Immigration", 3);
        immigration.setStatus(com.arrivalos.domain.model.CheckpointStatus.ACTIVE);
        tripCheckpointRepository.save(immigration);
        tripCheckpointRepository.save(new TripCheckpoint(trip, "Customs", 5));

        UUID tripId = trip.getId();
        assertThatThrownBy(() -> tripTransitionService.transition(new TripTransitionCommand(
                tripId,
                TimelineEventType.CHECKPOINT_STARTED,
                ActorType.CONCIERGE,
                UUID.randomUUID(),
                "Customs",
                null,
                "second-active-checkpoint-" + tripId,
                Instant.parse("2026-05-25T12:15:00Z"),
                null)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertResponseStatusException(
                        exception,
                        HttpStatus.CONFLICT,
                        "Another checkpoint is already active"));
    }

    @Test
    void rejectsCompletingCheckpointThatIsNotActive() {
        Trip trip = new Trip("LH568", "MMIA");
        trip.setStatus(TripStatus.PROCESSING);
        trip = tripRepository.save(trip);
        tripCheckpointRepository.save(new TripCheckpoint(trip, "Customs", 5));

        UUID tripId = trip.getId();
        assertThatThrownBy(() -> tripTransitionService.transition(new TripTransitionCommand(
                tripId,
                TimelineEventType.CHECKPOINT_COMPLETED,
                ActorType.CONCIERGE,
                UUID.randomUUID(),
                "Customs",
                null,
                "inactive-checkpoint-completion-" + tripId,
                Instant.parse("2026-05-25T12:30:00Z"),
                null)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertResponseStatusException(
                        exception,
                        HttpStatus.CONFLICT,
                        "Checkpoint must be active before completion"));
    }

    @Test
    void preservesOfflineCreatedAtWhenProvided() {
        Trip trip = tripRepository.save(new Trip("KQ532", "MMIA"));
        Instant offlineCreatedAt = Instant.parse("2026-05-25T08:58:00Z");

        TripTransitionResult result = tripTransitionService.transition(new TripTransitionCommand(
                trip.getId(),
                TimelineEventType.FLIGHT_APPROACHING,
                ActorType.SYSTEM,
                null,
                null,
                null,
                "offline-created-at-" + trip.getId(),
                Instant.parse("2026-05-25T09:00:00Z"),
                offlineCreatedAt));

        assertThat(result.event().getOfflineCreatedAt()).isEqualTo(offlineCreatedAt);
    }

    @ParameterizedTest
    @EnumSource(value = TripStatus.class, names = {"COMPLETED", "CANCELLED"})
    void rejectsTransitionsAfterTerminalTripState(TripStatus terminalStatus) {
        Trip trip = tripWithStatus(terminalStatus);

        assertThatThrownBy(() -> tripTransitionService.transition(new TripTransitionCommand(
                trip.getId(),
                TimelineEventType.HANDOVER_COMPLETED,
                ActorType.CONCIERGE,
                UUID.randomUUID(),
                null,
                null,
                "handover-after-close-" + trip.getId(),
                Instant.parse("2026-05-25T13:00:00Z"),
                null)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertResponseStatusException(
                        exception,
                        HttpStatus.CONFLICT,
                        "Trip is already closed"));
    }

    @ParameterizedTest
    @MethodSource("closingTransitions")
    void closingTripRevokesUnrevokedConciergeAccessLinks(
            TripStatus initialStatus,
            TimelineEventType closingEvent,
            TripStatus terminalStatus) {
        Concierge concierge = conciergeRepository.save(new Concierge(
                "Closure Concierge",
                "+2347055555555",
                "GBJ-CLOSE-" + UUID.randomUUID().toString().substring(0, 8)));
        Trip trip = tripWithStatus(initialStatus);
        trip.setAssignedConcierge(concierge);
        trip = tripRepository.save(trip);
        ConciergeTripAccess activeAccess = conciergeTripAccessRepository.save(new ConciergeTripAccess(
                trip,
                concierge,
                "active-close-" + trip.getId(),
                Instant.parse("2030-05-25T12:00:00Z")));
        ConciergeTripAccess alreadyRevokedAccess = conciergeTripAccessRepository.save(new ConciergeTripAccess(
                trip,
                concierge,
                "already-revoked-close-" + trip.getId(),
                Instant.parse("2030-05-25T12:00:00Z")));
        Instant originalRevokedAt = Instant.parse("2026-05-20T12:00:00Z");
        alreadyRevokedAccess.setRevokedAt(originalRevokedAt);
        conciergeTripAccessRepository.save(alreadyRevokedAccess);
        Instant beforeTransition = Instant.now();

        TripTransitionResult result = tripTransitionService.transition(new TripTransitionCommand(
                trip.getId(),
                closingEvent,
                ActorType.OPS,
                UUID.randomUUID(),
                null,
                "Closing trip.",
                "close-revokes-access-" + closingEvent + "-" + trip.getId(),
                Instant.parse("2026-05-25T13:30:00Z"),
                null));

        Instant afterTransition = Instant.now();
        assertThat(result.trip().getStatus()).isEqualTo(terminalStatus);

        ConciergeTripAccess savedActiveAccess = conciergeTripAccessRepository.findById(activeAccess.getId())
                .orElseThrow();
        ConciergeTripAccess savedAlreadyRevokedAccess = conciergeTripAccessRepository.findById(alreadyRevokedAccess.getId())
                .orElseThrow();
        assertThat(savedActiveAccess.getRevokedAt()).isBetween(beforeTransition, afterTransition);
        assertThat(savedAlreadyRevokedAccess.getRevokedAt()).isEqualTo(originalRevokedAt);
    }

    private Trip tripWithStatus(TripStatus status) {
        Trip trip = new Trip("T-" + UUID.randomUUID().toString().substring(0, 8), "MMIA");
        trip.setStatus(status);
        return tripRepository.save(trip);
    }

    private void assertResponseStatusException(Throwable exception, HttpStatus status, String reason) {
        ResponseStatusException responseStatusException = (ResponseStatusException) exception;
        assertThat(responseStatusException.getStatusCode()).isEqualTo(status);
        assertThat(responseStatusException.getReason()).isEqualTo(reason);
    }

    private static Stream<Arguments> allowedTransitions() {
        return Stream.of(
                Arguments.of(
                        TripStatus.CREATED,
                        TimelineEventType.FLIGHT_APPROACHING,
                        ActorType.SYSTEM,
                        TripStatus.FLIGHT_APPROACHING),
                Arguments.of(
                        TripStatus.CREATED,
                        TimelineEventType.CONCIERGE_IN_POSITION,
                        ActorType.CONCIERGE,
                        TripStatus.CONCIERGE_IN_POSITION),
                Arguments.of(
                        TripStatus.FLIGHT_APPROACHING,
                        TimelineEventType.CONCIERGE_IN_POSITION,
                        ActorType.CONCIERGE,
                        TripStatus.CONCIERGE_IN_POSITION),
                Arguments.of(
                        TripStatus.CONCIERGE_IN_POSITION,
                        TimelineEventType.FLIGHT_LANDED,
                        ActorType.SYSTEM,
                        TripStatus.FLIGHT_LANDED),
                Arguments.of(
                        TripStatus.FLIGHT_LANDED,
                        TimelineEventType.CLIENT_MET,
                        ActorType.CONCIERGE,
                        TripStatus.CLIENT_MET),
                Arguments.of(
                        TripStatus.CLIENT_MET,
                        TimelineEventType.CHECKPOINT_STARTED,
                        ActorType.CONCIERGE,
                        TripStatus.PROCESSING),
                Arguments.of(
                        TripStatus.PROCESSING,
                        TimelineEventType.CHECKPOINT_STARTED,
                        ActorType.CONCIERGE,
                        TripStatus.PROCESSING),
                Arguments.of(
                        TripStatus.PROCESSING,
                        TimelineEventType.CHECKPOINT_COMPLETED,
                        ActorType.CONCIERGE,
                        TripStatus.PROCESSING),
                Arguments.of(
                        TripStatus.PROCESSING,
                        TimelineEventType.TERMINAL_EXITED,
                        ActorType.CONCIERGE,
                        TripStatus.TERMINAL_EXITED),
                Arguments.of(
                        TripStatus.TERMINAL_EXITED,
                        TimelineEventType.HANDOVER_COMPLETED,
                        ActorType.CONCIERGE,
                        TripStatus.HANDOVER_COMPLETED),
                Arguments.of(
                        TripStatus.HANDOVER_COMPLETED,
                        TimelineEventType.TRIP_COMPLETED,
                        ActorType.OPS,
                        TripStatus.COMPLETED),
                Arguments.of(
                        TripStatus.CREATED,
                        TimelineEventType.TRIP_CANCELLED,
                        ActorType.OPS,
                        TripStatus.CANCELLED),
                Arguments.of(
                        TripStatus.FLIGHT_APPROACHING,
                        TimelineEventType.TRIP_CANCELLED,
                        ActorType.OPS,
                        TripStatus.CANCELLED),
                Arguments.of(
                        TripStatus.CONCIERGE_IN_POSITION,
                        TimelineEventType.TRIP_CANCELLED,
                        ActorType.OPS,
                        TripStatus.CANCELLED),
                Arguments.of(
                        TripStatus.FLIGHT_LANDED,
                        TimelineEventType.TRIP_CANCELLED,
                        ActorType.OPS,
                        TripStatus.CANCELLED),
                Arguments.of(
                        TripStatus.CLIENT_MET,
                        TimelineEventType.TRIP_CANCELLED,
                        ActorType.OPS,
                        TripStatus.CANCELLED),
                Arguments.of(
                        TripStatus.PROCESSING,
                        TimelineEventType.TRIP_CANCELLED,
                        ActorType.OPS,
                        TripStatus.CANCELLED),
                Arguments.of(
                        TripStatus.TERMINAL_EXITED,
                        TimelineEventType.TRIP_CANCELLED,
                        ActorType.OPS,
                        TripStatus.CANCELLED),
                Arguments.of(
                        TripStatus.HANDOVER_COMPLETED,
                        TimelineEventType.TRIP_CANCELLED,
                        ActorType.OPS,
                        TripStatus.CANCELLED));
    }

    private static Stream<Arguments> invalidTransitions() {
        return Stream.of(
                Arguments.of(TripStatus.CREATED, TimelineEventType.CLIENT_MET),
                Arguments.of(TripStatus.CREATED, TimelineEventType.CHECKPOINT_STARTED),
                Arguments.of(TripStatus.CREATED, TimelineEventType.TRIP_COMPLETED),
                Arguments.of(TripStatus.FLIGHT_APPROACHING, TimelineEventType.CLIENT_MET),
                Arguments.of(TripStatus.CONCIERGE_IN_POSITION, TimelineEventType.CLIENT_MET),
                Arguments.of(TripStatus.FLIGHT_LANDED, TimelineEventType.CHECKPOINT_STARTED),
                Arguments.of(TripStatus.CLIENT_MET, TimelineEventType.TERMINAL_EXITED),
                Arguments.of(TripStatus.PROCESSING, TimelineEventType.HANDOVER_COMPLETED),
                Arguments.of(TripStatus.PROCESSING, TimelineEventType.TRIP_COMPLETED),
                Arguments.of(TripStatus.TERMINAL_EXITED, TimelineEventType.CHECKPOINT_COMPLETED),
                Arguments.of(TripStatus.HANDOVER_COMPLETED, TimelineEventType.TERMINAL_EXITED),
                Arguments.of(TripStatus.HANDOVER_COMPLETED, TimelineEventType.CHECKPOINT_COMPLETED));
    }

    private static Stream<Arguments> closingTransitions() {
        return Stream.of(
                Arguments.of(
                        TripStatus.HANDOVER_COMPLETED,
                        TimelineEventType.TRIP_COMPLETED,
                        TripStatus.COMPLETED),
                Arguments.of(
                        TripStatus.PROCESSING,
                        TimelineEventType.TRIP_CANCELLED,
                        TripStatus.CANCELLED));
    }
}
