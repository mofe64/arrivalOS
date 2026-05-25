package com.arrivalos.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import com.arrivalos.domain.model.AccountType;
import com.arrivalos.domain.model.ActorType;
import com.arrivalos.domain.model.AppUser;
import com.arrivalos.domain.model.CheckpointStatus;
import com.arrivalos.domain.model.Concierge;
import com.arrivalos.domain.model.ConciergeTripAccess;
import com.arrivalos.domain.model.NotificationAttempt;
import com.arrivalos.domain.model.NotificationChannel;
import com.arrivalos.domain.model.NotificationStatus;
import com.arrivalos.domain.model.RecipientType;
import com.arrivalos.domain.model.TimelineEvent;
import com.arrivalos.domain.model.TimelineEventType;
import com.arrivalos.domain.model.Trip;
import com.arrivalos.domain.model.TripCheckpoint;
import com.arrivalos.domain.model.TripPrincipal;
import com.arrivalos.domain.model.TripStatus;
import com.arrivalos.domain.model.Watcher;

@ActiveProfiles("test")
@DataJpaTest
class ArrivalOsRepositoryTests {

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private ConciergeRepository conciergeRepository;

    @Autowired
    private ConciergeTripAccessRepository conciergeTripAccessRepository;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private TripPrincipalRepository tripPrincipalRepository;

    @Autowired
    private WatcherRepository watcherRepository;

    @Autowired
    private TripCheckpointRepository tripCheckpointRepository;

    @Autowired
    private TimelineEventRepository timelineEventRepository;

    @Autowired
    private NotificationAttemptRepository notificationAttemptRepository;

    @Test
    void savesAndLoadsCoreArrivalGraph() {
        AppUser principalUser = appUserRepository.save(new AppUser(
                "Mr. Adekunle",
                "adekunle@example.com",
                "{noop}password",
                AccountType.PRINCIPAL));

        Concierge concierge = conciergeRepository.save(
                new Concierge("Tunde Bello", "+2347000000000", "GBJ-TUNDE"));

        Trip trip = new Trip("LH568", "MMIA");
        trip.setArrivalTerminal("Arrivals Hall B");
        trip.setAssignedConcierge(concierge);
        trip.setStatus(TripStatus.FLIGHT_LANDED);
        trip = tripRepository.save(trip);

        TripPrincipal firstPrincipal = new TripPrincipal(trip, "Mr. Adekunle", 1);
        firstPrincipal.setUserAccount(principalUser);
        firstPrincipal.setPhone("+2347011111111");
        firstPrincipal.setPrimaryContact(true);
        tripPrincipalRepository.save(firstPrincipal);
        tripPrincipalRepository.save(new TripPrincipal(trip, "Mrs. Adekunle", 2));

        ConciergeTripAccess conciergeAccess = new ConciergeTripAccess(
                trip,
                concierge,
                "hashed-concierge-trip-token",
                Instant.now().plusSeconds(86_400));
        conciergeTripAccessRepository.save(conciergeAccess);

        Watcher watcher = watcherRepository.save(new Watcher(
                trip,
                "Ada Okafor",
                "ada@example.com",
                "+447700900000",
                NotificationChannel.WHATSAPP));

        TripCheckpoint immigration = new TripCheckpoint(trip, "Immigration", 3);
        immigration.setStatus(CheckpointStatus.ACTIVE);
        tripCheckpointRepository.save(immigration);

        TimelineEvent event = new TimelineEvent(trip, TimelineEventType.FLIGHT_LANDED, ActorType.SYSTEM);
        event.setIdempotencyKey("flight-landed-LH568-20260520");
        timelineEventRepository.save(event);

        NotificationAttempt notification = new NotificationAttempt(
                trip,
                RecipientType.WATCHER,
                NotificationChannel.WHATSAPP,
                "KAPSO");
        notification.setRecipientId(watcher.getId());
        notification.setStatus(NotificationStatus.SENT);
        notificationAttemptRepository.save(notification);

        assertThat(appUserRepository.existsByEmailIgnoreCase("ADekunle@example.com")).isTrue();
        assertThat(conciergeRepository.findByActiveTrueOrderByFullNameAsc()).hasSize(1);
        assertThat(conciergeRepository.existsByPublicId("GBJ-TUNDE")).isTrue();
        assertThat(conciergeTripAccessRepository.findByTokenHash("hashed-concierge-trip-token"))
                .hasValueSatisfying(access -> {
                    assertThat(access.getConcierge().getId()).isEqualTo(concierge.getId());
                    assertThat(access.isRevoked()).isFalse();
                });
        assertThat(tripRepository.findByStatusInOrderByUpdatedAtDesc(List.of(TripStatus.FLIGHT_LANDED)))
                .extracting(Trip::getFlightNumber)
                .containsExactly("LH568");
        assertThat(tripPrincipalRepository.findByTripOrderBySequenceNumberAsc(trip))
                .extracting(TripPrincipal::getFullName)
                .containsExactly("Mr. Adekunle", "Mrs. Adekunle");
        assertThat(tripPrincipalRepository.findFirstByTripAndPrimaryContactTrue(trip))
                .hasValueSatisfying(primary ->
                        assertThat(primary.getFullName()).isEqualTo("Mr. Adekunle"));
        assertThat(tripPrincipalRepository.findByUserAccountOrderByCreatedAtDesc(principalUser))
                .extracting(TripPrincipal::getFullName)
                .containsExactly("Mr. Adekunle");
        assertThat(watcherRepository.findByTripOrderByCreatedAtAsc(trip))
                .extracting(Watcher::getFullName)
                .containsExactly("Ada Okafor");
        assertThat(watcherRepository.findByTripAndEmailIgnoreCase(trip, "ADA@example.com")).isPresent();
        assertThat(tripCheckpointRepository.findByTripOrderBySequenceNumberAsc(trip))
                .extracting(TripCheckpoint::getName)
                .containsExactly("Immigration");
        assertThat(timelineEventRepository.findByTripAndIdempotencyKey(trip, "flight-landed-LH568-20260520"))
                .hasValueSatisfying(savedEvent ->
                        assertThat(savedEvent.getEventType()).isEqualTo(TimelineEventType.FLIGHT_LANDED));
        assertThat(notificationAttemptRepository.findByStatusOrderByCreatedAtAsc(NotificationStatus.SENT))
                .extracting(NotificationAttempt::getProvider)
                .containsExactly("KAPSO");
    }
}
