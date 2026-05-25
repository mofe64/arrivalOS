package com.arrivalos.notification;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.arrivalos.domain.model.NotificationAttempt;
import com.arrivalos.domain.model.NotificationChannel;
import com.arrivalos.domain.model.NotificationStatus;
import com.arrivalos.domain.model.RecipientType;
import com.arrivalos.domain.model.TimelineEvent;
import com.arrivalos.domain.model.TimelineEventType;
import com.arrivalos.domain.model.Trip;
import com.arrivalos.domain.model.TripPrincipal;
import com.arrivalos.domain.model.Watcher;
import com.arrivalos.domain.repository.NotificationAttemptRepository;
import com.arrivalos.domain.repository.TripPrincipalRepository;
import com.arrivalos.domain.repository.WatcherRepository;
import com.arrivalos.email.EmailMessage;
import com.arrivalos.email.EmailSender;
import com.arrivalos.trip.TripTransitionResult;

@Service
public class EmailTripNotificationService implements NotificationService {

    private static final String PROVIDER = "EMAIL";

    private final EmailSender emailSender;
    private final NotificationAttemptRepository notificationAttemptRepository;
    private final TripPrincipalRepository tripPrincipalRepository;
    private final WatcherRepository watcherRepository;
    private final String fromAddress;

    public EmailTripNotificationService(
            EmailSender emailSender,
            NotificationAttemptRepository notificationAttemptRepository,
            TripPrincipalRepository tripPrincipalRepository,
            WatcherRepository watcherRepository,
            @Value("${arrivalos.email.from}") String fromAddress) {
        this.emailSender = emailSender;
        this.notificationAttemptRepository = notificationAttemptRepository;
        this.tripPrincipalRepository = tripPrincipalRepository;
        this.watcherRepository = watcherRepository;
        this.fromAddress = fromAddress;
    }

    @Override
    @Transactional
    public void notifyTripCreated(Trip trip, Collection<Watcher> watchers) {
        for (Watcher watcher : watchers) {
            sendWatcherEmail(
                    trip,
                    watcher,
                    "ArrivalOS trip created",
                    "You have been added as a watcher for " + trip.getFlightNumber() + ".");
        }
    }

    @Override
    @Transactional
    public void notifyWatcherAdded(Trip trip, Watcher watcher) {
        sendWatcherEmail(
                trip,
                watcher,
                "ArrivalOS watcher access added",
                "You have been added as a watcher for " + trip.getFlightNumber() + ".");
    }

    @Override
    @Transactional
    public void notifyTimelineEvent(TripTransitionResult result) {
        if (result.duplicate()) {
            return;
        }
        TimelineEvent event = result.event();
        Trip trip = result.trip();
        TimelineEventType eventType = event.getEventType();
        if (notifiesWatchers(eventType, event)) {
            for (Watcher watcher : watcherRepository.findByTripOrderByCreatedAtAsc(trip)) {
                sendWatcherEmail(trip, watcher, subjectFor(eventType), bodyFor(trip, event));
            }
        }
        if (notifiesPrincipals(eventType)) {
            for (TripPrincipal principal : tripPrincipalRepository.findByTripOrderBySequenceNumberAsc(trip)) {
                sendPrincipalEmail(trip, principal, subjectFor(eventType), bodyFor(trip, event));
            }
        }
    }

    private boolean notifiesWatchers(TimelineEventType eventType, TimelineEvent event) {
        return switch (eventType) {
            case CONCIERGE_IN_POSITION,
                    FLIGHT_LANDED,
                    CLIENT_MET,
                    HANDOVER_COMPLETED,
                    TRIP_COMPLETED -> true;
            case CHECKPOINT_STARTED, CHECKPOINT_COMPLETED -> hasText(event.getNote());
            default -> false;
        };
    }

    private boolean notifiesPrincipals(TimelineEventType eventType) {
        return switch (eventType) {
            case CONCIERGE_IN_POSITION, HANDOVER_COMPLETED, TRIP_COMPLETED -> true;
            default -> false;
        };
    }

    private void sendWatcherEmail(Trip trip, Watcher watcher, String subject, String body) {
        sendEmail(
                trip,
                RecipientType.WATCHER,
                watcher.getId(),
                watcher.getEmail(),
                subject,
                body);
    }

    private void sendPrincipalEmail(Trip trip, TripPrincipal principal, String subject, String body) {
        String email = Optional.ofNullable(principal.getUserAccount())
                .map(user -> user.getEmail())
                .orElse(null);
        sendEmail(
                trip,
                RecipientType.PRINCIPAL,
                principal.getId(),
                email,
                subject,
                body);
    }

    private void sendEmail(
            Trip trip,
            RecipientType recipientType,
            java.util.UUID recipientId,
            String recipientEmail,
            String subject,
            String body) {
        NotificationAttempt attempt = new NotificationAttempt(
                trip,
                recipientType,
                NotificationChannel.EMAIL,
                PROVIDER);
        attempt.setRecipientId(recipientId);

        if (!hasText(recipientEmail)) {
            attempt.setStatus(NotificationStatus.FAILED);
            attempt.setFailureReason("Recipient has no email address");
            notificationAttemptRepository.save(attempt);
            return;
        }

        try {
            emailSender.send(new EmailMessage(
                    recipientEmail.trim(),
                    fromAddress,
                    subject,
                    htmlBody(body),
                    body));
            attempt.setStatus(NotificationStatus.SENT);
            attempt.setSentAt(Instant.now());
        } catch (RuntimeException exception) {
            attempt.setStatus(NotificationStatus.FAILED);
            attempt.setFailureReason(truncate(exception.getMessage()));
        }
        notificationAttemptRepository.save(attempt);
    }

    private String subjectFor(TimelineEventType eventType) {
        return switch (eventType) {
            case CONCIERGE_IN_POSITION -> "ArrivalOS concierge in position";
            case FLIGHT_LANDED -> "ArrivalOS flight landed";
            case CLIENT_MET -> "ArrivalOS client met";
            case CHECKPOINT_STARTED -> "ArrivalOS checkpoint started";
            case CHECKPOINT_COMPLETED -> "ArrivalOS checkpoint completed";
            case HANDOVER_COMPLETED -> "ArrivalOS handover completed";
            case TRIP_COMPLETED -> "ArrivalOS trip completed";
            default -> "ArrivalOS trip update";
        };
    }

    private String bodyFor(Trip trip, TimelineEvent event) {
        List<String> lines = new java.util.ArrayList<>();
        lines.add("Trip " + trip.getFlightNumber() + " is now " + event.getEventType().name() + ".");
        if (hasText(event.getCheckpointName())) {
            lines.add("Checkpoint: " + event.getCheckpointName());
        }
        if (hasText(event.getNote())) {
            lines.add("Note: " + event.getNote());
        }
        return String.join("\n", lines);
    }

    private String htmlBody(String body) {
        return "<p>" + body.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\n", "<br>") + "</p>";
    }

    private String truncate(String value) {
        if (!hasText(value)) {
            return "Email delivery failed";
        }
        String trimmed = value.trim();
        if (trimmed.length() <= 1000) {
            return trimmed;
        }
        return trimmed.substring(0, 1000);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
