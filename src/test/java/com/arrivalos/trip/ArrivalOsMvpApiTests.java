package com.arrivalos.trip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.arrivalos.auth.TokenHasher;
import com.arrivalos.domain.model.AccountType;
import com.arrivalos.domain.model.AppUser;
import com.arrivalos.domain.model.Concierge;
import com.arrivalos.domain.model.ConciergeTripAccess;
import com.arrivalos.domain.model.NotificationStatus;
import com.arrivalos.domain.model.TimelineEventType;
import com.arrivalos.domain.model.Trip;
import com.arrivalos.domain.model.TripPrincipal;
import com.arrivalos.domain.model.TripStatus;
import com.arrivalos.domain.model.Watcher;
import com.arrivalos.domain.repository.AppUserRepository;
import com.arrivalos.domain.repository.ConciergeRepository;
import com.arrivalos.domain.repository.ConciergeTripAccessRepository;
import com.arrivalos.domain.repository.NotificationAttemptRepository;
import com.arrivalos.domain.repository.TimelineEventRepository;
import com.arrivalos.domain.repository.TripCheckpointRepository;
import com.arrivalos.domain.repository.TripPrincipalRepository;
import com.arrivalos.domain.repository.TripRepository;
import com.arrivalos.domain.repository.WatcherRepository;
import com.arrivalos.email.RecordingEmailConfiguration;
import com.arrivalos.email.RecordingEmailSender;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Import(RecordingEmailConfiguration.class)
class ArrivalOsMvpApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private TripPrincipalRepository tripPrincipalRepository;

    @Autowired
    private TripCheckpointRepository tripCheckpointRepository;

    @Autowired
    private TimelineEventRepository timelineEventRepository;

    @Autowired
    private NotificationAttemptRepository notificationAttemptRepository;

    @Autowired
    private WatcherRepository watcherRepository;

    @Autowired
    private ConciergeRepository conciergeRepository;

    @Autowired
    private ConciergeTripAccessRepository conciergeTripAccessRepository;

    @Autowired
    private RecordingEmailSender recordingEmailSender;

    @Autowired
    private TokenHasher tokenHasher;

    @BeforeEach
    void resetOutbox() {
        recordingEmailSender.clear();
    }

    @Test
    void adminCreatesTripWithDefaultCheckpointsPrincipalsWatchersAndInitialAuditEvent() throws Exception {
        String adminToken = createVerifiedUserAndLogin("Admin Create", "mvp-create-admin@example.com", AccountType.ADMIN);
        AppUser principal = createVerifiedUser(
                "Linked Principal",
                "mvp-linked-principal@example.com",
                AccountType.PRINCIPAL);
        Concierge concierge = conciergeRepository.save(new Concierge("Tunde Bello", "+2347000000000", "GBJ-MVP-CREATE"));

        MvcResult result = mockMvc.perform(post("/api/admin/trips")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "flightNumber": "BA075",
                                  "arrivalAirport": "MMIA",
                                  "arrivalTerminal": "D",
                                  "meetingPoint": "Meet at the Gbèjà desk beside arrivals exit D.",
                                  "scheduledArrivalAt": "2030-05-25T12:00:00Z",
                                  "principals": [
                                    {
                                      "userAccountId": "%s",
                                      "primaryContact": true
                                    },
                                    {
                                      "fullName": "Second Principal",
                                      "phone": "+2347000000999"
                                    }
                                  ],
                                  "watchers": [
                                    {
                                      "fullName": "Ada Okafor",
                                      "email": "mvp-watcher@example.com",
                                      "phone": "+447700900000"
                                    }
                                  ],
                                  "assignedConciergeId": "%s"
                                }
                                """.formatted(principal.getId(), concierge.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flightNumber").value("BA075"))
                .andExpect(jsonPath("$.meetingPoint").value("Meet at the Gbèjà desk beside arrivals exit D."))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.principals", hasSize(2)))
                .andExpect(jsonPath("$.watchers[0].notificationChannel").value("EMAIL"))
                .andExpect(jsonPath("$.concierge.fullName").value("Tunde Bello"))
                .andExpect(jsonPath("$.checkpoints", hasSize(7)))
                .andExpect(jsonPath("$.checkpoints[0].name").value("Port Health"))
                .andExpect(jsonPath("$.checkpoints[6].name").value("Quarantine"))
                .andExpect(jsonPath("$.timelineEvents[0].eventType").value("TRIP_CREATED"))
                .andReturn();

        UUID tripId = UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
        Trip trip = tripRepository.findById(tripId).orElseThrow();
        assertThat(tripPrincipalRepository.findByTripOrderBySequenceNumberAsc(trip)).hasSize(2);
        assertThat(tripCheckpointRepository.findByTripOrderBySequenceNumberAsc(trip)).hasSize(7);
        assertThat(timelineEventRepository.findByTripOrderByOccurredAtAsc(trip))
                .extracting(event -> event.getEventType())
                .containsExactly(TimelineEventType.TRIP_CREATED);
        assertThat(notificationAttemptRepository.findByTripOrderByCreatedAtAsc(trip))
                .extracting(attempt -> attempt.getStatus())
                .containsExactly(NotificationStatus.SENT);

        mockMvc.perform(patch("/api/admin/trips/{tripId}", tripId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "meetingPoint": "Updated meeting point at the private protocol desk."
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meetingPoint").value("Updated meeting point at the private protocol desk."));
    }

    @Test
    void adminTripListAndDetailExposeCurrentTimelineStateAndBlockNonAdminDetailAccess() throws Exception {
        String adminToken = createVerifiedUserAndLogin("Admin List", "mvp-list-admin@example.com", AccountType.ADMIN);
        String principalToken = createVerifiedUserAndLogin("Principal Blocked", "mvp-list-principal@example.com", AccountType.PRINCIPAL);
        Trip trip = tripRepository.save(new Trip("LH568", "MMIA"));

        mockMvc.perform(post("/api/admin/trips/{tripId}/timeline-events", trip.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventType": "FLIGHT_APPROACHING",
                                  "idempotencyKey": "mvp-list-flight-approaching"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/trips/active")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '%s')].status".formatted(trip.getId())).value("FLIGHT_APPROACHING"))
                .andExpect(jsonPath("$[?(@.id == '%s')].lastTimelineEvent.eventType".formatted(trip.getId()))
                        .value("FLIGHT_APPROACHING"));

        mockMvc.perform(get("/api/admin/trips/{tripId}", trip.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + principalToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ADMIN_ACCESS_REQUIRED"));
    }

    @Test
    void principalTripsAreIsolatedAndTimelineProjectionIsCustomerSafe() throws Exception {
        String principalOneToken = createVerifiedUserAndLogin(
                "Principal One",
                "mvp-principal-one@example.com",
                AccountType.PRINCIPAL);
        AppUser principalOne = appUserRepository.findByEmailIgnoreCase("mvp-principal-one@example.com").orElseThrow();
        String principalTwoToken = createVerifiedUserAndLogin(
                "Principal Two",
                "mvp-principal-two@example.com",
                AccountType.PRINCIPAL);
        AppUser principalTwo = appUserRepository.findByEmailIgnoreCase("mvp-principal-two@example.com").orElseThrow();

        Trip ownTrip = createLinkedTrip("QR1407", principalOne);
        ownTrip.setMeetingPoint("Meet at protocol desk C.");
        ownTrip = tripRepository.save(ownTrip);
        Trip otherTrip = createLinkedTrip("EK783", principalTwo);
        timelineEventRepository.save(new com.arrivalos.domain.model.TimelineEvent(
                ownTrip,
                TimelineEventType.TRIP_CREATED,
                com.arrivalos.domain.model.ActorType.OPS));
        timelineEventRepository.save(new com.arrivalos.domain.model.TimelineEvent(
                ownTrip,
                TimelineEventType.CONCIERGE_IN_POSITION,
                com.arrivalos.domain.model.ActorType.CONCIERGE));

        mockMvc.perform(get("/api/principal/trips")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + principalOneToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(ownTrip.getId().toString()))
                .andExpect(jsonPath("$[0].flightNumber").value("QR1407"));

        mockMvc.perform(get("/api/principal/trips/{tripId}", otherTrip.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + principalOneToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PRINCIPAL_IS_NOT_ASSIGNED_TO_TRIP"));

        mockMvc.perform(get("/api/principal/trips/{tripId}", ownTrip.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + principalOneToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meetingPoint").value("Meet at protocol desk C."));

        mockMvc.perform(get("/api/principal/trips/{tripId}/timeline", ownTrip.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + principalOneToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].eventType").value("CONCIERGE_IN_POSITION"));
    }

    @Test
    void conciergeReadEndpointValidatesCapabilityTokenAndReturnsSafeTripWorkflowData() throws Exception {
        AppUser principal = createVerifiedUser(
                "Concierge Principal",
                "mvp-concierge-principal@example.com",
                AccountType.PRINCIPAL);
        Concierge concierge = conciergeRepository.save(new Concierge(
                "Field Concierge",
                "+2347000000777",
                "GBJ-MVP-READ"));
        Trip trip = createLinkedTrip("VS411", principal);
        trip.setMeetingPoint("Meet at arrivals pillar A4.");
        trip.setArrivalTerminal("A");
        trip.setAssignedConcierge(concierge);
        trip = tripRepository.save(trip);
        tripCheckpointRepository.save(new com.arrivalos.domain.model.TripCheckpoint(trip, "Immigration", 1));
        watcherRepository.save(new Watcher(
                trip,
                "Remote Watcher",
                "mvp-concierge-watcher@example.com",
                null,
                com.arrivalos.domain.model.NotificationChannel.EMAIL));
        String rawToken = "concierge-read-token";
        conciergeTripAccessRepository.save(new ConciergeTripAccess(
                trip,
                concierge,
                tokenHasher.hash(rawToken),
                Instant.parse("2030-05-25T12:00:00Z")));

        mockMvc.perform(get("/api/concierge/trips/{tripId}", trip.getId())
                        .queryParam("accessToken", rawToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flightNumber").value("VS411"))
                .andExpect(jsonPath("$.meetingPoint").value("Meet at arrivals pillar A4."))
                .andExpect(jsonPath("$.concierge.fullName").value("Field Concierge"))
                .andExpect(jsonPath("$.principals[0].fullName").value("Concierge Principal"))
                .andExpect(jsonPath("$.watcherCount").value(1))
                .andExpect(jsonPath("$.nextAllowedAction.eventType").value("CONCIERGE_IN_POSITION"))
                .andExpect(jsonPath("$.notificationAttempts").doesNotExist());
    }

    @Test
    void conciergeReadEndpointRejectsExpiredRevokedAndWrongTripTokens() throws Exception {
        Concierge concierge = conciergeRepository.save(new Concierge(
                "Blocked Concierge",
                "+2347000000666",
                "GBJ-MVP-BLOCK"));
        AppUser principal = createVerifiedUser(
                "Blocked Principal",
                "mvp-blocked-principal@example.com",
                AccountType.PRINCIPAL);
        Trip trip = createLinkedTrip("AF149", principal);
        trip.setAssignedConcierge(concierge);
        trip = tripRepository.save(trip);
        Trip otherTrip = tripRepository.save(new Trip("EK783", "MMIA"));
        otherTrip.setAssignedConcierge(concierge);
        otherTrip = tripRepository.save(otherTrip);

        String expiredToken = "expired-read-token";
        conciergeTripAccessRepository.save(new ConciergeTripAccess(
                trip,
                concierge,
                tokenHasher.hash(expiredToken),
                Instant.parse("2020-05-25T12:00:00Z")));

        String revokedToken = "revoked-read-token";
        ConciergeTripAccess revoked = conciergeTripAccessRepository.save(new ConciergeTripAccess(
                trip,
                concierge,
                tokenHasher.hash(revokedToken),
                Instant.parse("2030-05-25T12:00:00Z")));
        revoked.setRevokedAt(Instant.parse("2026-05-25T12:00:00Z"));
        conciergeTripAccessRepository.save(revoked);

        String wrongTripToken = "wrong-trip-read-token";
        conciergeTripAccessRepository.save(new ConciergeTripAccess(
                otherTrip,
                concierge,
                tokenHasher.hash(wrongTripToken),
                Instant.parse("2030-05-25T12:00:00Z")));

        mockMvc.perform(get("/api/concierge/trips/{tripId}", trip.getId())
                        .queryParam("accessToken", expiredToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CONCIERGE_TRIP_ACCESS_EXPIRED"));

        mockMvc.perform(get("/api/concierge/trips/{tripId}", trip.getId())
                        .queryParam("accessToken", revokedToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CONCIERGE_TRIP_ACCESS_REVOKED"));

        mockMvc.perform(get("/api/concierge/trips/{tripId}", trip.getId())
                        .queryParam("accessToken", wrongTripToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CONCIERGE_TRIP_ACCESS_DOES_NOT_MATCH_TRIP"));
    }

    @Test
    void watcherMutationRulesAllowAdminsAndOwningPrincipalsOnly() throws Exception {
        String adminToken = createVerifiedUserAndLogin("Watcher Admin", "mvp-watcher-admin@example.com", AccountType.ADMIN);
        AppUser owner = createVerifiedUser("Watcher Owner", "mvp-watcher-owner@example.com", AccountType.PRINCIPAL);
        String ownerToken = login("mvp-watcher-owner@example.com");
        String outsiderToken = createVerifiedUserAndLogin("Watcher Outsider", "mvp-watcher-outsider@example.com", AccountType.PRINCIPAL);
        Trip trip = createLinkedTrip("AF149", owner);

        mockMvc.perform(post("/api/admin/trips/{tripId}/watchers", trip.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Admin Watcher",
                                  "email": "mvp-admin-watcher@example.com"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notificationChannel").value("EMAIL"));

        mockMvc.perform(post("/api/principal/trips/{tripId}/watchers", trip.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Owner Watcher",
                                  "email": "mvp-owner-watcher@example.com"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notificationChannel").value("EMAIL"));

        mockMvc.perform(post("/api/principal/trips/{tripId}/watchers", trip.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + outsiderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Blocked Watcher",
                                  "email": "mvp-blocked-watcher@example.com"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PRINCIPAL_IS_NOT_ASSIGNED_TO_TRIP"));
    }

    @Test
    void transitionEmailsPersistAttemptsAndFailuresDoNotRollbackState() throws Exception {
        String adminToken = createVerifiedUserAndLogin("Notify Admin", "mvp-notify-admin@example.com", AccountType.ADMIN);
        AppUser principal = createVerifiedUser("Notify Principal", "mvp-notify-principal@example.com", AccountType.PRINCIPAL);
        Trip trip = createLinkedTrip("KQ532", principal);
        watcherRepository.save(new Watcher(
                trip,
                "Notify Watcher",
                "mvp-notify-watcher@example.com",
                null,
                com.arrivalos.domain.model.NotificationChannel.EMAIL));

        mockMvc.perform(post("/api/admin/trips/{tripId}/timeline-events", trip.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventType": "CONCIERGE_IN_POSITION",
                                  "idempotencyKey": "mvp-notify-concierge-position"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONCIERGE_IN_POSITION"));

        assertThat(notificationAttemptRepository.findByTripOrderByCreatedAtAsc(trip))
                .extracting(attempt -> attempt.getStatus())
                .containsExactly(NotificationStatus.SENT, NotificationStatus.SENT);
        String updateHtml = recordingEmailSender.latestWithSubject("ArrivalOS concierge in position")
                .orElseThrow()
                .htmlBody();
        assertThat(updateHtml)
                .contains("<!doctype html>")
                .contains("Verified arrival timeline update")
                .contains("KQ532")
                .contains("Concierge in position")
                .doesNotStartWith("<p>");

        recordingEmailSender.failNext(new RuntimeException("smtp unavailable"));
        mockMvc.perform(post("/api/admin/trips/{tripId}/timeline-events", trip.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventType": "FLIGHT_LANDED",
                                  "idempotencyKey": "mvp-notify-flight-landed-fails"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FLIGHT_LANDED"));

        Trip savedTrip = tripRepository.findById(trip.getId()).orElseThrow();
        assertThat(savedTrip.getStatus()).isEqualTo(TripStatus.FLIGHT_LANDED);
        assertThat(notificationAttemptRepository.findByTripOrderByCreatedAtAsc(trip))
                .extracting(attempt -> attempt.getStatus())
                .containsExactly(NotificationStatus.SENT, NotificationStatus.SENT, NotificationStatus.FAILED);

        mockMvc.perform(get("/api/admin/trips/active")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '%s')].emailFailedNotificationCount".formatted(trip.getId()))
                        .value(1));

        mockMvc.perform(get("/api/admin/trips/{tripId}/notification-attempts", trip.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .queryParam("status", "FAILED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].status").value("FAILED"))
                .andExpect(jsonPath("$[0].provider").value("EMAIL"));
    }

    @Test
    void completionAndCancellationRevokeConciergeAccessLinks() throws Exception {
        String adminToken = createVerifiedUserAndLogin("Revoke Admin", "mvp-revoke-admin@example.com", AccountType.ADMIN);
        Concierge concierge = conciergeRepository.save(new Concierge("Revoked Concierge", "+2347000000888", "GBJ-MVP-REVOKE"));
        Trip trip = new Trip("TK625", "MMIA");
        trip.setStatus(TripStatus.HANDOVER_COMPLETED);
        trip.setAssignedConcierge(concierge);
        trip = tripRepository.save(trip);
        ConciergeTripAccess access = conciergeTripAccessRepository.save(new ConciergeTripAccess(
                trip,
                concierge,
                "mvp-revoke-token-" + trip.getId(),
                Instant.parse("2030-05-25T12:00:00Z")));

        mockMvc.perform(post("/api/admin/trips/{tripId}/timeline-events", trip.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventType": "TRIP_COMPLETED",
                                  "idempotencyKey": "mvp-complete-revokes"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        assertThat(conciergeTripAccessRepository.findById(access.getId()).orElseThrow().getRevokedAt()).isNotNull();
    }

    @Test
    void kapsoWebhookRoutesAreNotImplementedInEmailOnlyVersion() throws Exception {
        String adminToken = createVerifiedUserAndLogin(
                "No Kapso Admin",
                "mvp-no-kapso-admin@example.com",
                AccountType.ADMIN);

        mockMvc.perform(post("/api/kapso/webhooks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    private Trip createLinkedTrip(String flightNumber, AppUser principalUser) {
        Trip trip = tripRepository.save(new Trip(flightNumber, "MMIA"));
        TripPrincipal principal = new TripPrincipal(trip, principalUser.getFullName(), 1);
        principal.setUserAccount(principalUser);
        principal.setPrimaryContact(true);
        tripPrincipalRepository.save(principal);
        return trip;
    }

    private AppUser createVerifiedUser(String fullName, String email, AccountType accountType) {
        AppUser user = new AppUser(fullName, email, "{noop}password123", accountType);
        user.setEmailVerified(true);
        user.setEmailVerifiedAt(Instant.now());
        return appUserRepository.save(user);
    }

    private String createVerifiedUserAndLogin(String fullName, String email, AccountType accountType) throws Exception {
        createVerifiedUser(fullName, email, accountType);
        return login(email);
    }

    private String login(String email) throws Exception {
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "password123"
                                }
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", not(blankOrNullString())))
                .andReturn();
        JsonNode body = objectMapper.readTree(login.getResponse().getContentAsString());
        return body.get("accessToken").asText();
    }
}
