package com.arrivalos.trip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.arrivalos.auth.TokenHasher;
import com.arrivalos.domain.model.AccountType;
import com.arrivalos.domain.model.ActorType;
import com.arrivalos.domain.model.AppUser;
import com.arrivalos.domain.model.Concierge;
import com.arrivalos.domain.model.ConciergeTripAccess;
import com.arrivalos.domain.model.TimelineEvent;
import com.arrivalos.domain.model.TimelineEventType;
import com.arrivalos.domain.model.Trip;
import com.arrivalos.domain.model.TripStatus;
import com.arrivalos.domain.repository.AppUserRepository;
import com.arrivalos.domain.repository.ConciergeRepository;
import com.arrivalos.domain.repository.ConciergeTripAccessRepository;
import com.arrivalos.domain.repository.TimelineEventRepository;
import com.arrivalos.domain.repository.TripRepository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class ConciergeCapabilityFlowTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private ConciergeRepository conciergeRepository;

    @Autowired
    private ConciergeTripAccessRepository conciergeTripAccessRepository;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private TimelineEventRepository timelineEventRepository;

    @Autowired
    private TokenHasher tokenHasher;

    @Test
    void adminCanCreateConciergeAssignTripIssueAccessLinkAndConciergeCanUpdateTrip() throws Exception {
        String adminToken = registerAndLogin("Concierge Admin", "concierge-admin@example.com", AccountType.ADMIN);
        Trip trip = tripRepository.save(new Trip("LH568", "MMIA"));

        MvcResult conciergeResult = mockMvc.perform(post("/api/admin/concierges")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Tunde Bello",
                                  "phone": "+2347000000000",
                                  "photoUrl": "https://example.com/tunde.jpg",
                                  "publicId": "GBJ-TUNDE"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", not(blankOrNullString())))
                .andExpect(jsonPath("$.fullName").value("Tunde Bello"))
                .andExpect(jsonPath("$.phone").value("+2347000000000"))
                .andExpect(jsonPath("$.photoUrl").value("https://example.com/tunde.jpg"))
                .andExpect(jsonPath("$.publicId").value("GBJ-TUNDE"))
                .andExpect(jsonPath("$.active").value(true))
                .andReturn();
        String conciergeId = objectMapper.readTree(conciergeResult.getResponse().getContentAsString())
                .get("id")
                .asString();

        mockMvc.perform(post("/api/admin/trips/{tripId}/concierge-assignment", trip.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "conciergeId": "%s"
                                }
                                """.formatted(conciergeId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tripId").value(trip.getId().toString()))
                .andExpect(jsonPath("$.concierge.id").value(conciergeId));

        MvcResult accessResult = mockMvc.perform(post("/api/admin/trips/{tripId}/concierge-access-links", trip.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "conciergeId": "%s",
                                  "expiresAt": "2030-05-25T12:00:00Z"
                                }
                                """.formatted(conciergeId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tripId").value(trip.getId().toString()))
                .andExpect(jsonPath("$.conciergeId").value(conciergeId))
                .andExpect(jsonPath("$.token", not(blankOrNullString())))
                .andExpect(jsonPath("$.updateUrl", not(blankOrNullString())))
                .andExpect(jsonPath("$.expiresAt").value("2030-05-25T12:00:00Z"))
                .andReturn();
        String rawToken = objectMapper.readTree(accessResult.getResponse().getContentAsString())
                .get("token")
                .asString();
        assertThat(conciergeTripAccessRepository.findByTokenHash(rawToken)).isEmpty();
        assertThat(conciergeTripAccessRepository.findByTokenHash(tokenHasher.hash(rawToken))).isPresent();

        mockMvc.perform(post("/api/concierge/trips/{tripId}/timeline-events", trip.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accessToken": "%s",
                                  "eventType": "CONCIERGE_IN_POSITION",
                                  "note": "Waiting at arrivals.",
                                  "idempotencyKey": "concierge-in-position-flow"
                                }
                                """.formatted(rawToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tripId").value(trip.getId().toString()))
                .andExpect(jsonPath("$.status").value("CONCIERGE_IN_POSITION"))
                .andExpect(jsonPath("$.event.actorType").value("CONCIERGE"))
                .andExpect(jsonPath("$.event.actorId").value(conciergeId))
                .andExpect(jsonPath("$.event.eventType").value("CONCIERGE_IN_POSITION"))
                .andExpect(jsonPath("$.duplicate").value(false));

        Trip savedTrip = tripRepository.findById(trip.getId()).orElseThrow();
        assertThat(savedTrip.getStatus()).isEqualTo(TripStatus.CONCIERGE_IN_POSITION);
        assertThat(timelineEventRepository.findByTripOrderByOccurredAtAsc(savedTrip))
                .extracting(TimelineEvent::getEventType)
                .containsExactly(TimelineEventType.CONCIERGE_IN_POSITION);
    }

    @Test
    void principalCannotCreateConcierge() throws Exception {
        String principalToken = registerAndLogin("Principal User", "concierge-principal@example.com", AccountType.PRINCIPAL);

        mockMvc.perform(post("/api/admin/concierges")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + principalToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Tunde Bello",
                                  "phone": "+2347000000000",
                                  "publicId": "GBJ-WATCHER-BLOCKED"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCannotIssueAccessLinkForUnassignedConcierge() throws Exception {
        String adminToken = registerAndLogin("Unassigned Admin", "unassigned-admin@example.com", AccountType.ADMIN);
        Trip trip = tripRepository.save(new Trip("BA075", "MMIA"));
        Concierge concierge = conciergeRepository.save(new Concierge("Unassigned Concierge", "+2347011111111", "GBJ-UNASSIGNED"));

        mockMvc.perform(post("/api/admin/trips/{tripId}/concierge-access-links", trip.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "conciergeId": "%s",
                                  "expiresAt": "2030-05-25T12:00:00Z"
                                }
                                """.formatted(concierge.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCIERGE_IS_NOT_ASSIGNED_TO_TRIP"))
                .andExpect(jsonPath("$.message").value("Concierge is not assigned to trip"));
    }

    @Test
    void conciergeUpdateRejectsInvalidToken() throws Exception {
        Trip trip = tripRepository.save(new Trip("QR1407", "ABV"));

        mockMvc.perform(post("/api/concierge/trips/{tripId}/timeline-events", trip.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accessToken": "not-a-real-token",
                                  "eventType": "CONCIERGE_IN_POSITION",
                                  "idempotencyKey": "invalid-token-flow"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CONCIERGE_TRIP_ACCESS"))
                .andExpect(jsonPath("$.message").value("Invalid concierge trip access"));
    }

    @Test
    void conciergeUpdateRejectsExpiredToken() throws Exception {
        Trip trip = tripRepository.save(new Trip("VS411", "MMIA"));
        Concierge concierge = conciergeRepository.save(new Concierge("Expired Concierge", "+2347022222222", "GBJ-EXPIRED"));
        trip.setAssignedConcierge(concierge);
        tripRepository.save(trip);
        String rawToken = "expired-token";
        conciergeTripAccessRepository.save(new ConciergeTripAccess(
                trip,
                concierge,
                tokenHasher.hash(rawToken),
                Instant.parse("2020-05-25T12:00:00Z")));

        mockMvc.perform(post("/api/concierge/trips/{tripId}/timeline-events", trip.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accessToken": "%s",
                                  "eventType": "CONCIERGE_IN_POSITION",
                                  "idempotencyKey": "expired-token-flow"
                                }
                                """.formatted(rawToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CONCIERGE_TRIP_ACCESS_EXPIRED"))
                .andExpect(jsonPath("$.message").value("Concierge trip access expired"));
    }

    @Test
    void conciergeUpdateRejectsTokenForDifferentTrip() throws Exception {
        Trip tokenTrip = tripRepository.save(new Trip("AF149", "MMIA"));
        Trip requestedTrip = tripRepository.save(new Trip("EK783", "MMIA"));
        Concierge concierge = conciergeRepository.save(new Concierge("Wrong Trip Concierge", "+2347033333333", "GBJ-WRONG-TRIP"));
        tokenTrip.setAssignedConcierge(concierge);
        requestedTrip.setAssignedConcierge(concierge);
        tripRepository.save(tokenTrip);
        tripRepository.save(requestedTrip);
        String rawToken = "wrong-trip-token";
        conciergeTripAccessRepository.save(new ConciergeTripAccess(
                tokenTrip,
                concierge,
                tokenHasher.hash(rawToken),
                Instant.parse("2030-05-25T12:00:00Z")));

        mockMvc.perform(post("/api/concierge/trips/{tripId}/timeline-events", requestedTrip.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accessToken": "%s",
                                  "eventType": "CONCIERGE_IN_POSITION",
                                  "idempotencyKey": "wrong-trip-token-flow"
                                }
                                """.formatted(rawToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CONCIERGE_TRIP_ACCESS_DOES_NOT_MATCH_TRIP"))
                .andExpect(jsonPath("$.message").value("Concierge trip access does not match trip"));
    }

    @Test
    void conciergeUpdateRejectsRevokedToken() throws Exception {
        Trip trip = tripRepository.save(new Trip("DL054", "MMIA"));
        Concierge concierge = conciergeRepository.save(new Concierge("Revoked Concierge", "+2347044444444", "GBJ-REVOKED"));
        trip.setAssignedConcierge(concierge);
        tripRepository.save(trip);
        String rawToken = "revoked-token";
        ConciergeTripAccess access = conciergeTripAccessRepository.save(new ConciergeTripAccess(
                trip,
                concierge,
                tokenHasher.hash(rawToken),
                Instant.parse("2030-05-25T12:00:00Z")));
        access.setRevokedAt(Instant.parse("2026-05-25T12:00:00Z"));
        conciergeTripAccessRepository.save(access);

        mockMvc.perform(post("/api/concierge/trips/{tripId}/timeline-events", trip.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accessToken": "%s",
                                  "eventType": "CONCIERGE_IN_POSITION",
                                  "idempotencyKey": "revoked-token-flow"
                                }
                                """.formatted(rawToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CONCIERGE_TRIP_ACCESS_REVOKED"))
                .andExpect(jsonPath("$.message").value("Concierge trip access revoked"));
    }

    @Test
    void adminCancellingTripRevokesConciergeAccessLinkAndBlocksFurtherConciergeUpdates() throws Exception {
        String adminToken = registerAndLogin("Closing Admin", "closing-admin@example.com", AccountType.ADMIN);
        Trip trip = tripRepository.save(new Trip("UA612", "MMIA"));
        Concierge concierge = conciergeRepository.save(new Concierge(
                "Closing Concierge",
                "+2347055555555",
                "GBJ-CLOSING"));
        trip.setAssignedConcierge(concierge);
        tripRepository.save(trip);
        String rawToken = "admin-close-token";
        ConciergeTripAccess access = conciergeTripAccessRepository.save(new ConciergeTripAccess(
                trip,
                concierge,
                tokenHasher.hash(rawToken),
                Instant.parse("2030-05-25T12:00:00Z")));

        mockMvc.perform(post("/api/admin/trips/{tripId}/timeline-events", trip.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventType": "TRIP_CANCELLED",
                                  "note": "Principal cancelled the trip.",
                                  "idempotencyKey": "admin-cancel-revokes-link"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.event.eventType").value("TRIP_CANCELLED"));

        ConciergeTripAccess revokedAccess = conciergeTripAccessRepository.findById(access.getId()).orElseThrow();
        assertThat(revokedAccess.getRevokedAt()).isNotNull();

        mockMvc.perform(post("/api/concierge/trips/{tripId}/timeline-events", trip.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accessToken": "%s",
                                  "eventType": "CONCIERGE_IN_POSITION",
                                  "idempotencyKey": "blocked-after-admin-cancel"
                                }
                                """.formatted(rawToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CONCIERGE_TRIP_ACCESS_REVOKED"))
                .andExpect(jsonPath("$.message").value("Concierge trip access revoked"));
    }

    private String registerAndLogin(String fullName, String email, AccountType accountType) throws Exception {
        String registerPath = switch (accountType) {
            case ADMIN -> "/api/auth/register/admin";
            case PRINCIPAL -> "/api/auth/register/principal";
        };

        mockMvc.perform(post(registerPath)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "%s",
                                  "email": "%s",
                                  "password": "password123"
                                }
                                """.formatted(fullName, email)))
                .andExpect(status().isOk());

        AppUser user = appUserRepository.findByEmailIgnoreCase(email).orElseThrow();
        user.setEmailVerified(true);
        appUserRepository.save(user);

        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "password123"
                                }
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(login.getResponse().getContentAsString());
        return body.get("accessToken").asString();
    }
}
