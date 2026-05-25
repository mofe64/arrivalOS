package com.arrivalos.auth;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.arrivalos.email.RecordingEmailConfiguration;
import com.arrivalos.email.RecordingEmailSender;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Import(RecordingEmailConfiguration.class)
class AuthControllerTests {

    private static final Pattern TOKEN_QUERY_PATTERN = Pattern.compile("token=([^\"&]+)");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RecordingEmailSender recordingEmailSender;

    @BeforeEach
    void resetEmailOutbox() {
        recordingEmailSender.clear();
    }

    @Test
    void registersAdminAndUsesTokenForMeEndpoint() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register/admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("Gbèjà Admin", "admin@example.com", "+2347000000010", "password123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken", not(blankOrNullString())))
                .andExpect(jsonPath("$.refreshToken", not(blankOrNullString())))
                .andExpect(jsonPath("$.accessTokenExpiresAt", not(blankOrNullString())))
                .andExpect(jsonPath("$.refreshTokenExpiresAt", not(blankOrNullString())))
                .andExpect(jsonPath("$.user.email").value("admin@example.com"))
                .andExpect(jsonPath("$.user.accountType").value("ADMIN"))
                .andExpect(jsonPath("$.user.emailVerified").value(false))
                .andReturn();

        org.assertj.core.api.Assertions.assertThat(recordingEmailSender.messages()).hasSize(1);
        String token = accessTokenFrom(result);
        assertJwtIncludesRole(token, "ADMIN");

        mockMvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("admin@example.com"))
                .andExpect(jsonPath("$.accountType").value("ADMIN"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.emailVerified").value(false));
    }

    @Test
    void registersPrincipal() throws Exception {
        mockMvc.perform(post("/api/auth/register/principal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("Mr. Adekunle", "principal@example.com", "+2347000000020", "password123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", not(blankOrNullString())))
                .andExpect(jsonPath("$.refreshToken", not(blankOrNullString())))
                .andExpect(jsonPath("$.user.accountType").value("PRINCIPAL"))
                .andExpect(jsonPath("$.user.email").value("principal@example.com"));
    }

    @Test
    void rejectsWatcherRegistrationBecauseWatchersAreNotificationRecipientsOnly() throws Exception {
        mockMvc.perform(post("/api/auth/register/watcher")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("Ada Okafor", "watcher@example.com", "+447700900000", "password123")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logsInRegisteredUserWithCaseInsensitiveEmail() throws Exception {
        mockMvc.perform(post("/api/auth/register/principal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("Case Test", "case-login@example.com", null, "password123")))
                .andExpect(status().isOk());

        verifyLatestEmail();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "CASE-LOGIN@example.com",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", not(blankOrNullString())))
                .andExpect(jsonPath("$.refreshToken", not(blankOrNullString())))
                .andExpect(jsonPath("$.user.email").value("case-login@example.com"))
                .andExpect(jsonPath("$.user.accountType").value("PRINCIPAL"));
    }

    @Test
    void rejectsLoginUntilEmailIsVerified() throws Exception {
        mockMvc.perform(post("/api/auth/register/principal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("Unverified User", "unverified@example.com", null, "password123")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "unverified@example.com",
                                  "password": "password123"
                                }
                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_VERIFICATION_REQUIRED"))
                .andExpect(jsonPath("$.message").value("Email verification required"))
                .andExpect(jsonPath("$.path").value("/api/auth/login"))
                .andExpect(jsonPath("$.requestId", not(blankOrNullString())));
    }

    @Test
    void refreshesTokenPairAndRejectsRefreshTokenReuse() throws Exception {
        MvcResult registration = mockMvc.perform(post("/api/auth/register/principal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("Refresh User", "refresh@example.com", null, "password123")))
                .andExpect(status().isOk())
                .andReturn();

        String refreshToken = refreshTokenFrom(registration);
        MvcResult refresh = mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshPayload(refreshToken))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", not(blankOrNullString())))
                .andExpect(jsonPath("$.refreshToken", not(blankOrNullString())))
                .andExpect(jsonPath("$.user.accountType").value("PRINCIPAL"))
                .andReturn();

        assertJwtIncludesRole(accessTokenFrom(refresh), "PRINCIPAL");

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RefreshPayload(refreshToken))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"))
                .andExpect(jsonPath("$.message").value("Invalid refresh token"));
    }

    @Test
    void verifiesEmailAndRejectsTokenReuse() throws Exception {
        mockMvc.perform(post("/api/auth/register/principal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("Verify User", "verify@example.com", null, "password123")))
                .andExpect(status().isOk());

        String verificationToken = latestTokenFromEmail("Verify your ArrivalOS email");

        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TokenPayload(verificationToken))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Email verified"));

        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new TokenPayload(verificationToken))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_VERIFICATION_TOKEN"))
                .andExpect(jsonPath("$.message").value("Invalid verification token"));
    }

    @Test
    void logoutRevokesAccessTokenAndRefreshToken() throws Exception {
        MvcResult registration = mockMvc.perform(post("/api/auth/register/admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("Logout User", "logout@example.com", null, "password123")))
                .andExpect(status().isOk())
                .andReturn();
        String accessToken = accessTokenFrom(registration);
        String refreshToken = refreshTokenFrom(registration);

        mockMvc.perform(post("/api/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshPayload(refreshToken))))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RefreshPayload(refreshToken))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"))
                .andExpect(jsonPath("$.message").value("Invalid refresh token"));
    }

    @Test
    void passwordResetChangesPasswordAndRevokesRefreshTokens() throws Exception {
        MvcResult registration = mockMvc.perform(post("/api/auth/register/principal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("Reset User", "reset@example.com", null, "oldPassword123")))
                .andExpect(status().isOk())
                .andReturn();
        String oldRefreshToken = refreshTokenFrom(registration);

        verifyLatestEmail();

        mockMvc.perform(post("/api/auth/password/forgot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "reset@example.com"
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value("If the email exists, a password reset link has been sent"));

        String resetToken = latestTokenFromEmail("Reset your ArrivalOS password");
        mockMvc.perform(post("/api/auth/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ResetPasswordPayload(resetToken, "newPassword123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password reset complete"));

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RefreshPayload(oldRefreshToken))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"))
                .andExpect(jsonPath("$.message").value("Invalid refresh token"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "reset@example.com",
                                  "password": "newPassword123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value("reset@example.com"));
    }

    @Test
    void forgotPasswordDoesNotRevealUnknownEmail() throws Exception {
        mockMvc.perform(post("/api/auth/password/forgot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "missing@example.com"
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value("If the email exists, a password reset link has been sent"));

        org.assertj.core.api.Assertions.assertThat(recordingEmailSender.messages()).isEmpty();
    }

    @Test
    void rejectsDuplicateRegistrationEmail() throws Exception {
        String body = registerJson("Duplicate User", "duplicate@example.com", null, "password123");

        mockMvc.perform(post("/api/auth/register/admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/register/principal")
                        .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_IS_ALREADY_REGISTERED"))
                .andExpect(jsonPath("$.message").value("Email is already registered"));
    }

    @Test
    void rejectsInvalidLogin() throws Exception {
        mockMvc.perform(post("/api/auth/register/admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("Login User", "bad-login@example.com", null, "password123")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "bad-login@example.com",
                                  "password": "wrong-password"
                                }
                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_EMAIL_OR_PASSWORD"))
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void rejectsInvalidRegistrationPayload() throws Exception {
        mockMvc.perform(post("/api/auth/register/principal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "",
                                  "email": "not-an-email",
                                  "password": "short"
                                }
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("email"))
                .andExpect(jsonPath("$.fieldErrors[1].field").value("fullName"))
                .andExpect(jsonPath("$.fieldErrors[2].field").value("password"));
    }

    @Test
    void rejectsMeEndpointWithoutBearerToken() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsInvalidBearerToken() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-valid-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void doesNotExposeConciergeRegistrationRoute() throws Exception {
        mockMvc.perform(post("/api/auth/register/concierge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("Tunde Bello", "tunde@example.com", "+2347000000000", "password123")))
                .andExpect(status().isUnauthorized());
    }

    private String registerJson(String fullName, String email, String phone, String password) throws Exception {
        return objectMapper.writeValueAsString(new RegisterPayload(fullName, email, phone, password));
    }

    private String accessTokenFrom(MvcResult result) throws Exception {
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("accessToken").asText();
    }

    private String refreshTokenFrom(MvcResult result) throws Exception {
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("refreshToken").asText();
    }

    private void assertJwtIncludesRole(String jwt, String expectedRole) throws Exception {
        String[] parts = jwt.split("\\.");
        JsonNode payload = objectMapper.readTree(Base64.getUrlDecoder().decode(parts[1]));
        org.assertj.core.api.Assertions.assertThat(payload.get("role").asText()).isEqualTo(expectedRole);
        org.assertj.core.api.Assertions.assertThat(payload.get("accountType").asText()).isEqualTo(expectedRole);
        org.assertj.core.api.Assertions.assertThat(payload.get("tokenUse").asText()).isEqualTo("access");
    }

    private void verifyLatestEmail() throws Exception {
        String verificationToken = latestTokenFromEmail("Verify your ArrivalOS email");
        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TokenPayload(verificationToken))))
                .andExpect(status().isOk());
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

    private record RegisterPayload(String fullName, String email, String phone, String password) {
    }

    private record RefreshPayload(String refreshToken) {
    }

    private record TokenPayload(String token) {
    }

    private record ResetPasswordPayload(String token, String newPassword) {
    }
}
