package com.arrivalos.trip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.arrivalos.domain.model.AccountType;
import com.arrivalos.domain.model.AppUser;
import com.arrivalos.domain.model.TimelineEvent;
import com.arrivalos.domain.model.TimelineEventType;
import com.arrivalos.domain.model.Trip;
import com.arrivalos.domain.model.TripStatus;
import com.arrivalos.domain.repository.AppUserRepository;
import com.arrivalos.domain.repository.TimelineEventRepository;
import com.arrivalos.domain.repository.TripRepository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class TripTransitionControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private TimelineEventRepository timelineEventRepository;

    @Test
    void adminCanSubmitTripTransitionEvent() throws Exception {
        Trip trip = tripRepository.save(new Trip("LH568", "MMIA"));
        String adminToken = registerAndLogin("Ops Admin", "trip-admin@example.com", AccountType.ADMIN);

        mockMvc.perform(post("/api/admin/trips/{tripId}/timeline-events", trip.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventType": "FLIGHT_APPROACHING",
                                  "idempotencyKey": "flight-approaching-api-test"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tripId").value(trip.getId().toString()))
                .andExpect(jsonPath("$.status").value("FLIGHT_APPROACHING"))
                .andExpect(jsonPath("$.event.id", not(blankOrNullString())))
                .andExpect(jsonPath("$.event.eventType").value("FLIGHT_APPROACHING"))
                .andExpect(jsonPath("$.duplicate").value(false));

        Trip savedTrip = tripRepository.findById(trip.getId()).orElseThrow();
        assertThat(savedTrip.getStatus()).isEqualTo(TripStatus.FLIGHT_APPROACHING);
        assertThat(timelineEventRepository.findByTripOrderByOccurredAtAsc(savedTrip))
                .extracting(TimelineEvent::getEventType)
                .containsExactly(TimelineEventType.FLIGHT_APPROACHING);
    }

    @Test
    void adminCanCancelActiveTrip() throws Exception {
        Trip trip = new Trip("AF149", "MMIA");
        trip.setStatus(TripStatus.PROCESSING);
        trip = tripRepository.save(trip);
        String adminToken = registerAndLogin("Cancel Admin", "trip-cancel-admin@example.com", AccountType.ADMIN);

        mockMvc.perform(post("/api/admin/trips/{tripId}/timeline-events", trip.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventType": "TRIP_CANCELLED",
                                  "note": "Principal no longer travelling.",
                                  "idempotencyKey": "trip-cancelled-api-test"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tripId").value(trip.getId().toString()))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.event.eventType").value("TRIP_CANCELLED"))
                .andExpect(jsonPath("$.event.note").value("Principal no longer travelling."))
                .andExpect(jsonPath("$.duplicate").value(false));

        Trip savedTrip = tripRepository.findById(trip.getId()).orElseThrow();
        assertThat(savedTrip.getStatus()).isEqualTo(TripStatus.CANCELLED);
    }

    @Test
    void adminDuplicateIdempotencyKeyReturnsDuplicateResponseWithoutExtraEvent() throws Exception {
        Trip trip = tripRepository.save(new Trip("BA075", "MMIA"));
        String adminToken = registerAndLogin("Duplicate Admin", "trip-duplicate-admin@example.com", AccountType.ADMIN);
        String body = """
                {
                  "eventType": "FLIGHT_APPROACHING",
                  "idempotencyKey": "duplicate-transition-api-test"
                }
                """;

        mockMvc.perform(post("/api/admin/trips/{tripId}/timeline-events", trip.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicate").value(false));

        mockMvc.perform(post("/api/admin/trips/{tripId}/timeline-events", trip.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicate").value(true));

        Trip savedTrip = tripRepository.findById(trip.getId()).orElseThrow();
        assertThat(timelineEventRepository.findByTripOrderByOccurredAtAsc(savedTrip)).hasSize(1);
    }

    @Test
    void duplicateIdempotencyKeyWithDifferentPayloadReturnsConflict() throws Exception {
        Trip trip = tripRepository.save(new Trip("KQ532", "MMIA"));
        String adminToken = registerAndLogin(
                "Different Payload Admin",
                "trip-different-payload-admin@example.com",
                AccountType.ADMIN);
        String idempotencyKey = "duplicate-key-different-payload-api-test";

        mockMvc.perform(post("/api/admin/trips/{tripId}/timeline-events", trip.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventType": "FLIGHT_APPROACHING",
                                  "idempotencyKey": "%s"
                                }
                                """.formatted(idempotencyKey)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicate").value(false));

        mockMvc.perform(post("/api/admin/trips/{tripId}/timeline-events", trip.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventType": "CONCIERGE_IN_POSITION",
                                  "idempotencyKey": "%s"
                                }
                                """.formatted(idempotencyKey)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_ALREADY_USED_FOR_A_DIFFERENT_TRIP_EVENT"))
                .andExpect(jsonPath("$.message")
                        .value("Idempotency key already used for a different trip event"));

        Trip savedTrip = tripRepository.findById(trip.getId()).orElseThrow();
        assertThat(savedTrip.getStatus()).isEqualTo(TripStatus.FLIGHT_APPROACHING);
        assertThat(timelineEventRepository.findByTripOrderByOccurredAtAsc(savedTrip)).hasSize(1);
    }

    @Test
    void invalidTransitionReturnsConflictAndStandardErrorBody() throws Exception {
        Trip trip = tripRepository.save(new Trip("QR1407", "ABV"));
        String adminToken = registerAndLogin("Conflict Admin", "trip-conflict-admin@example.com", AccountType.ADMIN);

        mockMvc.perform(post("/api/admin/trips/{tripId}/timeline-events", trip.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventType": "CLIENT_MET",
                                  "idempotencyKey": "client-met-too-early-api-test"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_TRIP_STATUS_TRANSITION"))
                .andExpect(jsonPath("$.message").value("Invalid trip status transition"))
                .andExpect(jsonPath("$.path").value("/api/admin/trips/" + trip.getId() + "/timeline-events"));

        Trip savedTrip = tripRepository.findById(trip.getId()).orElseThrow();
        assertThat(savedTrip.getStatus()).isEqualTo(TripStatus.CREATED);
        assertThat(timelineEventRepository.findByTripOrderByOccurredAtAsc(savedTrip)).isEmpty();
    }

    @Test
    void closedTripTransitionReturnsConflictAndStandardErrorBody() throws Exception {
        Trip trip = new Trip("EK783", "MMIA");
        trip.setStatus(TripStatus.COMPLETED);
        trip = tripRepository.save(trip);
        String adminToken = registerAndLogin("Closed Admin", "trip-closed-admin@example.com", AccountType.ADMIN);

        mockMvc.perform(post("/api/admin/trips/{tripId}/timeline-events", trip.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventType": "TRIP_CANCELLED",
                                  "idempotencyKey": "closed-trip-transition-api-test"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TRIP_IS_ALREADY_CLOSED"))
                .andExpect(jsonPath("$.message").value("Trip is already closed"));

        Trip savedTrip = tripRepository.findById(trip.getId()).orElseThrow();
        assertThat(savedTrip.getStatus()).isEqualTo(TripStatus.COMPLETED);
        assertThat(timelineEventRepository.findByTripOrderByOccurredAtAsc(savedTrip)).isEmpty();
    }

    @Test
    void missingTripReturnsNotFoundAndStandardErrorBody() throws Exception {
        UUID missingTripId = UUID.randomUUID();
        String adminToken = registerAndLogin("Missing Admin", "trip-missing-admin@example.com", AccountType.ADMIN);

        mockMvc.perform(post("/api/admin/trips/{tripId}/timeline-events", missingTripId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventType": "FLIGHT_APPROACHING",
                                  "idempotencyKey": "missing-trip-api-test"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRIP_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Trip not found"))
                .andExpect(jsonPath("$.path").value("/api/admin/trips/" + missingTripId + "/timeline-events"));
    }

    @Test
    void unauthenticatedUserCannotSubmitTripTransitionEvent() throws Exception {
        Trip trip = tripRepository.save(new Trip("UA612", "MMIA"));

        mockMvc.perform(post("/api/admin/trips/{tripId}/timeline-events", trip.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventType": "FLIGHT_APPROACHING",
                                  "idempotencyKey": "unauthenticated-api-test"
                                }
                                """))
                .andExpect(status().isUnauthorized());

        Trip savedTrip = tripRepository.findById(trip.getId()).orElseThrow();
        assertThat(savedTrip.getStatus()).isEqualTo(TripStatus.CREATED);
        assertThat(timelineEventRepository.findByTripOrderByOccurredAtAsc(savedTrip)).isEmpty();
    }

    @Test
    void principalCannotSubmitTripTransitionEvent() throws Exception {
        Trip trip = tripRepository.save(new Trip("DL054", "MMIA"));
        String principalToken = registerAndLogin(
                "Principal User",
                "trip-principal@example.com",
                AccountType.PRINCIPAL);

        mockMvc.perform(post("/api/admin/trips/{tripId}/timeline-events", trip.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + principalToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventType": "FLIGHT_APPROACHING",
                                  "idempotencyKey": "principal-not-allowed-api-test"
                                }
                                """))
                .andExpect(status().isForbidden());

        Trip savedTrip = tripRepository.findById(trip.getId()).orElseThrow();
        assertThat(savedTrip.getStatus()).isEqualTo(TripStatus.CREATED);
        assertThat(timelineEventRepository.findByTripOrderByOccurredAtAsc(savedTrip)).isEmpty();
    }

    @Test
    void watcherRegistrationIsNotAvailableForTripMutationUsers() throws Exception {
        Trip trip = tripRepository.save(new Trip("VS411", "MMIA"));

        mockMvc.perform(post("/api/auth/register/watcher")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Watcher User",
                                  "email": "trip-watcher@example.com",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isUnauthorized());

        Trip savedTrip = tripRepository.findById(trip.getId()).orElseThrow();
        assertThat(savedTrip.getStatus()).isEqualTo(TripStatus.CREATED);
        assertThat(timelineEventRepository.findByTripOrderByOccurredAtAsc(savedTrip)).isEmpty();
    }

    @Test
    void missingEventTypeReturnsValidationError() throws Exception {
        Trip trip = tripRepository.save(new Trip("MS876", "MMIA"));
        String adminToken = registerAndLogin("Validation Admin", "trip-validation-admin@example.com", AccountType.ADMIN);

        mockMvc.perform(post("/api/admin/trips/{tripId}/timeline-events", trip.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "idempotencyKey": "missing-event-type-api-test"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("eventType"));
    }

    @Test
    void blankIdempotencyKeyReturnsValidationError() throws Exception {
        Trip trip = tripRepository.save(new Trip("TK625", "ABV"));
        String adminToken = registerAndLogin(
                "Idempotency Validation Admin",
                "trip-idempotency-validation-admin@example.com",
                AccountType.ADMIN);

        mockMvc.perform(post("/api/admin/trips/{tripId}/timeline-events", trip.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventType": "FLIGHT_APPROACHING",
                                  "idempotencyKey": "   "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("idempotencyKey"));
    }

    @Test
    void malformedEventTypeReturnsStandardBadRequest() throws Exception {
        Trip trip = tripRepository.save(new Trip("LH569", "MMIA"));
        String adminToken = registerAndLogin("Malformed Admin", "trip-malformed-admin@example.com", AccountType.ADMIN);

        mockMvc.perform(post("/api/admin/trips/{tripId}/timeline-events", trip.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventType": "NOT_A_REAL_EVENT",
                                  "idempotencyKey": "malformed-event-type-api-test"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"))
                .andExpect(jsonPath("$.message").value("Invalid request body"));
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
