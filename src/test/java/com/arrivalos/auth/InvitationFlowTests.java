package com.arrivalos.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.arrivalos.domain.model.AccountType;
import com.arrivalos.domain.model.AppUser;
import com.arrivalos.domain.model.Concierge;
import com.arrivalos.domain.model.NotificationChannel;
import com.arrivalos.domain.model.Trip;
import com.arrivalos.domain.model.TripPrincipal;
import com.arrivalos.domain.repository.AppUserRepository;
import com.arrivalos.domain.repository.ConciergeRepository;
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
class InvitationFlowTests {

    private static final Pattern TOKEN_QUERY_PATTERN = Pattern.compile("token=([^\"&]+)");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RecordingEmailSender recordingEmailSender;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private TripPrincipalRepository tripPrincipalRepository;

    @Autowired
    private WatcherRepository watcherRepository;

    @Autowired
    private ConciergeRepository conciergeRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void resetEmailOutbox() {
        recordingEmailSender.clear();
    }

    @Test
    void adminCanInviteAdminAndPrincipalAndInviteesCanLoginOnlyAfterAcceptingInvite() throws Exception {
        String adminToken = adminToken("invite-owner@example.com");

        invite(adminToken, "Ops Two", "ops-two@example.com", "+2347000000101", "ADMIN")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("ops-two@example.com"))
                .andExpect(jsonPath("$.accountType").value("ADMIN"))
                .andExpect(jsonPath("$.accepted").value(false));
        String adminInviteToken = latestTokenFromEmail("Accept your ArrivalOS invite");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("ops-two@example.com", "password123")))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(acceptInviteJson(adminInviteToken, "password123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", not(blankOrNullString())))
                .andExpect(jsonPath("$.refreshToken", not(blankOrNullString())))
                .andExpect(jsonPath("$.user.email").value("ops-two@example.com"))
                .andExpect(jsonPath("$.user.accountType").value("ADMIN"))
                .andExpect(jsonPath("$.user.emailVerified").value(true));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("ops-two@example.com", "password123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.accountType").value("ADMIN"));

        invite(adminToken, "Principal One", "principal-invite@example.com", "+2347000000102", "PRINCIPAL")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountType").value("PRINCIPAL"));
        String principalInviteToken = latestTokenFromEmail("Accept your ArrivalOS invite");

        mockMvc.perform(post("/api/auth/invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(acceptInviteJson(principalInviteToken, "password123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value("principal-invite@example.com"))
                .andExpect(jsonPath("$.user.accountType").value("PRINCIPAL"));
    }

    @Test
    void nonAdminCannotInviteUsersAndWatcherRegistrationIsGone() throws Exception {
        String adminToken = adminToken("principal-inviter@example.com");
        invite(adminToken, "Principal", "principal-no-invite@example.com", null, "PRINCIPAL")
                .andExpect(status().isOk());
        String principalInviteToken = latestTokenFromEmail("Accept your ArrivalOS invite");
        MvcResult principalAccept = mockMvc.perform(post("/api/auth/invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(acceptInviteJson(principalInviteToken, "password123")))
                .andExpect(status().isOk())
                .andReturn();
        String principalToken = objectMapper.readTree(principalAccept.getResponse().getContentAsString())
                .get("accessToken")
                .asString();

        invite(principalToken, "Blocked Admin", "blocked-admin@example.com", null, "ADMIN")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ADMIN_ACCESS_REQUIRED"));

        mockMvc.perform(post("/api/auth/register/watcher")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Watcher User",
                                  "email": "watcher-login@example.com",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void principalCanAddTripWatcherAsNotificationRecipientWithoutCreatingLoginAccount() throws Exception {
        String adminToken = adminToken("watcher-admin@example.com");
        String principalToken = acceptedInviteToken(
                adminToken,
                "Watcher Principal",
                "watcher-principal@example.com",
                AccountType.PRINCIPAL);
        AppUser principal = appUserRepository.findByEmailIgnoreCase("watcher-principal@example.com").orElseThrow();
        Trip trip = tripRepository.save(new Trip("BA075", "MMIA"));
        TripPrincipal tripPrincipal = new TripPrincipal(trip, "Watcher Principal", 1);
        tripPrincipal.setPrimaryContact(true);
        tripPrincipal.setUserAccount(principal);
        tripPrincipalRepository.save(tripPrincipal);

        mockMvc.perform(post("/api/principal/trips/{tripId}/watchers", trip.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + principalToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Ada Okafor",
                                  "email": "ada@example.com",
                                  "phone": "+447700900000",
                                  "notificationChannel": "EMAIL"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Ada Okafor"))
                .andExpect(jsonPath("$.email").value("ada@example.com"))
                .andExpect(jsonPath("$.phone").value("+447700900000"))
                .andExpect(jsonPath("$.notificationChannel").value("EMAIL"));

        assertThat(appUserRepository.findByEmailIgnoreCase("ada@example.com")).isEmpty();
        assertThat(watcherRepository.findByTripOrderByCreatedAtAsc(trip)).hasSize(1);
    }

    @Test
    void adminCanListPrincipalsWithTripsWatchersAndConcierges() throws Exception {
        String adminToken = adminToken("visibility-admin@example.com");
        acceptedInviteToken(adminToken, "Visible Principal", "visible-principal@example.com", AccountType.PRINCIPAL);
        AppUser principal = appUserRepository.findByEmailIgnoreCase("visible-principal@example.com").orElseThrow();
        Concierge concierge = conciergeRepository.save(new Concierge("Tunde Bello", "+2347000000000", "GBJ-VISIBLE"));
        concierge.setPhotoUrl("https://example.com/tunde.jpg");
        conciergeRepository.save(concierge);
        Trip trip = new Trip("LH568", "MMIA");
        trip.setArrivalTerminal("D");
        trip.setScheduledArrivalAt(Instant.parse("2030-05-25T12:00:00Z"));
        trip.setAssignedConcierge(concierge);
        trip = tripRepository.save(trip);
        TripPrincipal tripPrincipal = new TripPrincipal(trip, "Visible Principal", 1);
        tripPrincipal.setPrimaryContact(true);
        tripPrincipal.setUserAccount(principal);
        tripPrincipalRepository.save(tripPrincipal);

        mockMvc.perform(post("/api/principal/trips/{tripId}/watchers", trip.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + acceptedInviteToken(
                                adminToken,
                                "Watcher Owner",
                                "watcher-owner@example.com",
                                AccountType.PRINCIPAL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Family Watcher",
                                  "email": "family@example.com",
                                  "phone": "+2347000000999",
                                  "notificationChannel": "EMAIL"
                                }
                                """))
                .andExpect(status().isForbidden());

        watcherRepository.save(new com.arrivalos.domain.model.Watcher(
                trip,
                "Family Watcher",
                "family@example.com",
                "+2347000000999",
                NotificationChannel.EMAIL));

        mockMvc.perform(get("/api/admin/principals")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.email == 'visible-principal@example.com')].trips[0].flightNumber")
                        .value("LH568"))
                .andExpect(jsonPath("$[?(@.email == 'visible-principal@example.com')].trips[0].watchers[0].fullName")
                        .value("Family Watcher"))
                .andExpect(jsonPath("$[?(@.email == 'visible-principal@example.com')].trips[0].watchers[0].email")
                        .value("family@example.com"))
                .andExpect(jsonPath("$[?(@.email == 'visible-principal@example.com')].trips[0].concierge.fullName")
                        .value("Tunde Bello"));
    }

    private org.springframework.test.web.servlet.ResultActions invite(
            String token,
            String fullName,
            String email,
            String phone,
            String accountType) throws Exception {
        return mockMvc.perform(post("/api/admin/invitations")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new InvitePayload(fullName, email, phone, accountType))));
    }

    private String adminToken(String email) throws Exception {
        AppUser admin = new AppUser("Seed Admin", email, passwordEncoder.encode("password123"), AccountType.ADMIN);
        admin.setEmailVerified(true);
        admin.setEmailVerifiedAt(Instant.now());
        appUserRepository.save(admin);

        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email, "password123")))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(login.getResponse().getContentAsString()).get("accessToken").asString();
    }

    private String acceptedInviteToken(
            String adminToken,
            String fullName,
            String email,
            AccountType accountType) throws Exception {
        invite(adminToken, fullName, email, null, accountType.name()).andExpect(status().isOk());
        String inviteToken = latestTokenFromEmail("Accept your ArrivalOS invite");
        MvcResult accept = mockMvc.perform(post("/api/auth/invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(acceptInviteJson(inviteToken, "password123")))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(accept.getResponse().getContentAsString()).get("accessToken").asString();
    }

    private String latestTokenFromEmail(String subject) {
        String html = recordingEmailSender.latestWithSubject(subject)
                .orElseThrow(() -> new AssertionError("Expected email with subject " + subject))
                .htmlBody();
        Matcher matcher = TOKEN_QUERY_PATTERN.matcher(html);
        if (!matcher.find()) {
            throw new AssertionError("Expected token query parameter in email body");
        }
        return URLDecoder.decode(matcher.group(1), StandardCharsets.UTF_8);
    }

    private String acceptInviteJson(String token, String password) throws Exception {
        return objectMapper.writeValueAsString(new AcceptInvitePayload(token, password));
    }

    private String loginJson(String email, String password) throws Exception {
        return objectMapper.writeValueAsString(new LoginPayload(email, password));
    }

    private record InvitePayload(String fullName, String email, String phone, String accountType) {
    }

    private record AcceptInvitePayload(String token, String password) {
    }

    private record LoginPayload(String email, String password) {
    }
}
