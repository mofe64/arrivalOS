package com.arrivalos.notification;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
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
import com.arrivalos.email.EmailTemplateRenderer;
import com.arrivalos.trip.TripTransitionResult;

@Service
public class EmailTripNotificationService implements NotificationService {

    private static final String PROVIDER = "EMAIL";

    private final EmailSender emailSender;
    private final EmailTemplateRenderer templateRenderer;
    private final NotificationAttemptRepository notificationAttemptRepository;
    private final TripPrincipalRepository tripPrincipalRepository;
    private final WatcherRepository watcherRepository;
    private final String appBaseUrl;
    private final String fromAddress;

    public EmailTripNotificationService(
            EmailSender emailSender,
            EmailTemplateRenderer templateRenderer,
            NotificationAttemptRepository notificationAttemptRepository,
            TripPrincipalRepository tripPrincipalRepository,
            WatcherRepository watcherRepository,
            @Value("${arrivalos.app.base-url}") String appBaseUrl,
            @Value("${arrivalos.email.from}") String fromAddress) {
        this.emailSender = emailSender;
        this.templateRenderer = templateRenderer;
        this.notificationAttemptRepository = notificationAttemptRepository;
        this.tripPrincipalRepository = tripPrincipalRepository;
        this.watcherRepository = watcherRepository;
        this.appBaseUrl = stripTrailingSlash(appBaseUrl);
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
                    "Trip watcher added",
                    "You have been added as a watcher for " + trip.getFlightNumber() + ".",
                    null,
                    null);
        }
    }

    @Override
    @Transactional
    public void notifyWatcherAdded(Trip trip, Watcher watcher) {
        sendWatcherEmail(
                trip,
                watcher,
                "ArrivalOS watcher access added",
                "Watcher access added",
                "You have been added as a watcher for " + trip.getFlightNumber() + ".",
                null,
                null);
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
                sendWatcherEmail(
                        trip,
                        watcher,
                        subjectFor(eventType),
                        headingFor(eventType),
                        bodyFor(trip, event),
                        statusLabel(eventType),
                        event);
            }
        }
        if (notifiesPrincipals(eventType)) {
            for (TripPrincipal principal : tripPrincipalRepository.findByTripOrderBySequenceNumberAsc(trip)) {
                sendPrincipalEmail(
                        trip,
                        principal,
                        subjectFor(eventType),
                        headingFor(eventType),
                        bodyFor(trip, event),
                        statusLabel(eventType),
                        event);
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

    private void sendWatcherEmail(
            Trip trip,
            Watcher watcher,
            String subject,
            String heading,
            String body,
            String statusLabel,
            TimelineEvent event) {
        sendEmail(
                trip,
                RecipientType.WATCHER,
                watcher.getId(),
                watcher.getEmail(),
                subject,
                heading,
                body,
                statusLabel,
                event);
    }

    private void sendPrincipalEmail(
            Trip trip,
            TripPrincipal principal,
            String subject,
            String heading,
            String body,
            String statusLabel,
            TimelineEvent event) {
        String email = Optional.ofNullable(principal.getUserAccount())
                .map(user -> user.getEmail())
                .orElse(null);
        sendEmail(
                trip,
                RecipientType.PRINCIPAL,
                principal.getId(),
                email,
                subject,
                heading,
                body,
                statusLabel,
                event);
    }

    private void sendEmail(
            Trip trip,
            RecipientType recipientType,
            java.util.UUID recipientId,
            String recipientEmail,
            String subject,
            String heading,
            String body,
            String statusLabel,
            TimelineEvent event) {
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
            String html = templateRenderer.render("email-templates/trip-update.html", Map.of(
                    "title", subject,
                    "heading", heading,
                    "intro", body,
                    "flightNumber", trip.getFlightNumber(),
                    "arrivalAirport", trip.getArrivalAirport(),
                    "statusLabel", defaultText(statusLabel, "Trip update"),
                    "checkpoint", event != null ? defaultText(event.getCheckpointName(), "Not applicable") : "Not applicable",
                    "note", event != null ? defaultText(event.getNote(), "No operational note supplied.") : "No operational note supplied.",
                    "meetingPoint", defaultText(trip.getMeetingPoint(), "Not set"),
                    "tripUrl", appBaseUrl));
            emailSender.send(new EmailMessage(
                    recipientEmail.trim(),
                    fromAddress,
                    subject,
                    html,
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

    private String headingFor(TimelineEventType eventType) {
        return switch (eventType) {
            case CONCIERGE_IN_POSITION -> "Your concierge is in position";
            case FLIGHT_LANDED -> "Flight landed";
            case CLIENT_MET -> "Client met";
            case CHECKPOINT_STARTED -> "Checkpoint started";
            case CHECKPOINT_COMPLETED -> "Checkpoint completed";
            case HANDOVER_COMPLETED -> "Handover completed";
            case TRIP_COMPLETED -> "Trip completed";
            default -> "Trip update";
        };
    }

    private String statusLabel(TimelineEventType eventType) {
        return switch (eventType) {
            case CONCIERGE_IN_POSITION -> "Concierge in position";
            case FLIGHT_LANDED -> "Flight landed";
            case CLIENT_MET -> "Client met";
            case CHECKPOINT_STARTED -> "Checkpoint started";
            case CHECKPOINT_COMPLETED -> "Checkpoint completed";
            case HANDOVER_COMPLETED -> "Handover completed";
            case TRIP_COMPLETED -> "Trip completed";
            case TRIP_CANCELLED -> "Trip cancelled";
            default -> "Trip update";
        };
    }

    private String bodyFor(Trip trip, TimelineEvent event) {
        List<String> lines = new java.util.ArrayList<>();
        lines.add("Trip " + trip.getFlightNumber() + " is now " + statusLabel(event.getEventType()) + ".");
        if (hasText(event.getCheckpointName())) {
            lines.add("Checkpoint: " + event.getCheckpointName());
        }
        if (hasText(event.getNote())) {
            lines.add("Note: " + event.getNote());
        }
        return String.join("\n", lines);
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

    private String defaultText(String value, String fallback) {
        return hasText(value) ? value.trim() : fallback;
    }

    private String stripTrailingSlash(String value) {
        if (value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }
}
