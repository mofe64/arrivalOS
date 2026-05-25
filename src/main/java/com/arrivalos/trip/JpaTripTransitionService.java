package com.arrivalos.trip;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.arrivalos.domain.model.CheckpointStatus;
import com.arrivalos.domain.model.TimelineEvent;
import com.arrivalos.domain.model.TimelineEventType;
import com.arrivalos.domain.model.Trip;
import com.arrivalos.domain.model.TripCheckpoint;
import com.arrivalos.domain.model.TripStatus;
import com.arrivalos.domain.repository.ConciergeTripAccessRepository;
import com.arrivalos.domain.repository.TimelineEventRepository;
import com.arrivalos.domain.repository.TripCheckpointRepository;
import com.arrivalos.domain.repository.TripRepository;

@Service
public class JpaTripTransitionService implements TripTransitionService {

    private static final Map<TripStatus, Map<TimelineEventType, TripStatus>> TRANSITIONS = transitions();

    private final TripRepository tripRepository;
    private final TimelineEventRepository timelineEventRepository;
    private final ConciergeTripAccessRepository conciergeTripAccessRepository;
    private final TripCheckpointRepository tripCheckpointRepository;

    public JpaTripTransitionService(
            TripRepository tripRepository,
            TimelineEventRepository timelineEventRepository,
            ConciergeTripAccessRepository conciergeTripAccessRepository,
            TripCheckpointRepository tripCheckpointRepository) {
        this.tripRepository = tripRepository;
        this.timelineEventRepository = timelineEventRepository;
        this.conciergeTripAccessRepository = conciergeTripAccessRepository;
        this.tripCheckpointRepository = tripCheckpointRepository;
    }

    @Override
    @Transactional
    public TripTransitionResult transition(TripTransitionCommand command) {
        Trip trip = tripRepository.findById(command.tripId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found"));

        String idempotencyKey = requireIdempotencyKey(command);
        TimelineEvent duplicateEvent = timelineEventRepository.findByTripAndIdempotencyKey(trip, idempotencyKey)
                .orElse(null);
        if (duplicateEvent != null) {
            ensureSamePayload(duplicateEvent, command);
            return new TripTransitionResult(trip, duplicateEvent, true);
        }

        TripStatus nextStatus = nextStatus(trip.getStatus(), command.eventType());
        TimelineEvent event = new TimelineEvent(trip, command.eventType(), command.actorType());
        event.setActorId(command.actorId());
        event.setCheckpointName(trimToNull(command.checkpointName()));
        event.setNote(trimToNull(command.note()));
        event.setIdempotencyKey(idempotencyKey);
        event.setOccurredAt(command.occurredAt());
        event.setOfflineCreatedAt(command.offlineCreatedAt());

        trip.setStatus(nextStatus);
        updateCheckpointState(trip, command);
        Trip savedTrip = tripRepository.save(trip);
        TimelineEvent savedEvent = timelineEventRepository.save(event);
        revokeConciergeAccessIfClosed(savedTrip);

        return new TripTransitionResult(savedTrip, savedEvent, false);
    }

    private void revokeConciergeAccessIfClosed(Trip trip) {
        if (trip.getStatus() == TripStatus.COMPLETED || trip.getStatus() == TripStatus.CANCELLED) {
            conciergeTripAccessRepository.revokeUnrevokedByTrip(trip, Instant.now());
        }
    }

    private void updateCheckpointState(Trip trip, TripTransitionCommand command) {
        if (command.eventType() != TimelineEventType.CHECKPOINT_STARTED
                && command.eventType() != TimelineEventType.CHECKPOINT_COMPLETED) {
            return;
        }
        String checkpointName = trimToNull(command.checkpointName());
        if (checkpointName == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Checkpoint name is required");
        }
        TripCheckpoint checkpoint = tripCheckpointRepository.findByTripAndNameIgnoreCase(trip, checkpointName)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Checkpoint not found for trip"));
        Instant occurredAt = command.occurredAt() == null ? Instant.now() : command.occurredAt();
        if (command.eventType() == TimelineEventType.CHECKPOINT_STARTED) {
            ensureCanStartCheckpoint(trip, checkpoint);
            checkpoint.setStatus(CheckpointStatus.ACTIVE);
            checkpoint.setStartedAt(occurredAt);
        } else {
            ensureCanCompleteCheckpoint(checkpoint);
            checkpoint.setStatus(CheckpointStatus.COMPLETED);
            checkpoint.setCompletedAt(occurredAt);
        }
        tripCheckpointRepository.save(checkpoint);
    }

    private void ensureCanStartCheckpoint(Trip trip, TripCheckpoint checkpoint) {
        if (checkpoint.getStatus() == CheckpointStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Checkpoint is already completed");
        }
        if (checkpoint.getStatus() == CheckpointStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Checkpoint is already active");
        }

        for (TripCheckpoint existing : tripCheckpointRepository.findByTripOrderBySequenceNumberAsc(trip)) {
            if (existing.getStatus() != CheckpointStatus.ACTIVE) {
                continue;
            }
            if (existing.getCompletedAt() != null) {
                existing.setStatus(CheckpointStatus.COMPLETED);
                tripCheckpointRepository.save(existing);
                continue;
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Another checkpoint is already active");
        }
    }

    private void ensureCanCompleteCheckpoint(TripCheckpoint checkpoint) {
        if (checkpoint.getStatus() != CheckpointStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Checkpoint must be active before completion");
        }
    }

    private TripStatus nextStatus(TripStatus currentStatus, TimelineEventType eventType) {
        if (eventType == TimelineEventType.TRIP_CREATED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "TRIP_CREATED cannot be submitted as an operational transition");
        }
        if (currentStatus == TripStatus.COMPLETED || currentStatus == TripStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Trip is already closed");
        }

        TripStatus nextStatus = TRANSITIONS
                .getOrDefault(currentStatus, Map.of())
                .get(eventType);
        if (nextStatus == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Invalid trip status transition");
        }
        return nextStatus;
    }

    private String requireIdempotencyKey(TripTransitionCommand command) {
        if (!StringUtils.hasText(command.idempotencyKey())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency key is required");
        }
        return command.idempotencyKey().trim();
    }

    private void ensureSamePayload(TimelineEvent existingEvent, TripTransitionCommand command) {
        if (!Objects.equals(existingEvent.getEventType(), command.eventType())
                || !Objects.equals(existingEvent.getActorType(), command.actorType())
                || !Objects.equals(existingEvent.getActorId(), command.actorId())
                || !Objects.equals(existingEvent.getCheckpointName(), trimToNull(command.checkpointName()))
                || !Objects.equals(existingEvent.getNote(), trimToNull(command.note()))) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Idempotency key already used for a different trip event");
        }
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private static Map<TripStatus, Map<TimelineEventType, TripStatus>> transitions() {
        EnumMap<TripStatus, Map<TimelineEventType, TripStatus>> transitions = new EnumMap<>(TripStatus.class);
        transitions.put(TripStatus.CREATED, transitionMap(
                Map.entry(TimelineEventType.FLIGHT_APPROACHING, TripStatus.FLIGHT_APPROACHING),
                Map.entry(TimelineEventType.CONCIERGE_IN_POSITION, TripStatus.CONCIERGE_IN_POSITION),
                Map.entry(TimelineEventType.TRIP_CANCELLED, TripStatus.CANCELLED)));
        transitions.put(TripStatus.FLIGHT_APPROACHING, transitionMap(
                Map.entry(TimelineEventType.CONCIERGE_IN_POSITION, TripStatus.CONCIERGE_IN_POSITION),
                Map.entry(TimelineEventType.TRIP_CANCELLED, TripStatus.CANCELLED)));
        transitions.put(TripStatus.CONCIERGE_IN_POSITION, transitionMap(
                Map.entry(TimelineEventType.FLIGHT_LANDED, TripStatus.FLIGHT_LANDED),
                Map.entry(TimelineEventType.TRIP_CANCELLED, TripStatus.CANCELLED)));
        transitions.put(TripStatus.FLIGHT_LANDED, transitionMap(
                Map.entry(TimelineEventType.CLIENT_MET, TripStatus.CLIENT_MET),
                Map.entry(TimelineEventType.TRIP_CANCELLED, TripStatus.CANCELLED)));
        transitions.put(TripStatus.CLIENT_MET, transitionMap(
                Map.entry(TimelineEventType.CHECKPOINT_STARTED, TripStatus.PROCESSING),
                Map.entry(TimelineEventType.TRIP_CANCELLED, TripStatus.CANCELLED)));
        transitions.put(TripStatus.PROCESSING, transitionMap(
                Map.entry(TimelineEventType.CHECKPOINT_STARTED, TripStatus.PROCESSING),
                Map.entry(TimelineEventType.CHECKPOINT_COMPLETED, TripStatus.PROCESSING),
                Map.entry(TimelineEventType.TERMINAL_EXITED, TripStatus.TERMINAL_EXITED),
                Map.entry(TimelineEventType.TRIP_CANCELLED, TripStatus.CANCELLED)));
        transitions.put(TripStatus.TERMINAL_EXITED, transitionMap(
                Map.entry(TimelineEventType.HANDOVER_COMPLETED, TripStatus.HANDOVER_COMPLETED),
                Map.entry(TimelineEventType.TRIP_CANCELLED, TripStatus.CANCELLED)));
        transitions.put(TripStatus.HANDOVER_COMPLETED, transitionMap(
                Map.entry(TimelineEventType.TRIP_COMPLETED, TripStatus.COMPLETED),
                Map.entry(TimelineEventType.TRIP_CANCELLED, TripStatus.CANCELLED)));
        return transitions;
    }

    @SafeVarargs
    private static Map<TimelineEventType, TripStatus> transitionMap(
            Entry<TimelineEventType, TripStatus>... entries) {
        EnumMap<TimelineEventType, TripStatus> map = new EnumMap<>(TimelineEventType.class);
        for (Entry<TimelineEventType, TripStatus> entry : entries) {
            map.put(entry.getKey(), entry.getValue());
        }
        return Map.copyOf(map);
    }
}
