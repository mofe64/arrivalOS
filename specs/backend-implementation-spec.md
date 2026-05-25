# ArrivalOS Backend Implementation Spec

Version: 0.1  
Status: In Progress  
Last updated: 2026-05-25

## 1. Objective

This spec captures the current backend implementation so another agent can continue without rediscovering product decisions or code shape.

ArrivalOS backend is a Spring Boot service for trip timelines, account auth, concierge capability access, notifications, and audit-friendly airport arrival workflows.

## 2. Current Stack

| Area | Decision |
| --- | --- |
| Runtime | Java 21 |
| Framework | Spring Boot 4.0.6 |
| Persistence | Spring Data JPA |
| Migration | Flyway |
| Production database | PostgreSQL |
| Test database | H2 in PostgreSQL compatibility mode |
| Security | Spring Security with custom JWT bearer filter |
| Email | SMTP locally, Resend in production, noop in tests |
| Local email inbox | MailDev |
| Tests | Maven wrapper with Spring Boot endpoint and repository tests |

Run tests from the project root:

```bash
./mvnw test
```

Known non-blocking test warnings:

- Flyway warns that H2 `2.4.240` is newer than the latest verified version.
- Hibernate notes that explicit `H2Dialect` is unnecessary in tests.
- Mockito emits a dynamic agent warning.

## 3. Implemented Backend Foundation

### 3.1 Core Schema

Implemented through Flyway migrations:

- `V1__create_arrivalos_core_tables.sql`

The project has not yet run production migrations, so the current schema is intentionally collapsed into a single initial migration instead of a chain of backfills.

Core tables:

| Table | Purpose |
| --- | --- |
| `app_users` | Login-capable users only: admins and principals. |
| `account_invitations` | Hashed invite tokens for admin-created admin/principal accounts. |
| `concierges` | Operational concierge records. Concierges do not log in. |
| `trips` | Airport arrival workflow container. |
| `trip_principals` | One or more protected people/passengers attached to a trip. |
| `watchers` | Trip-scoped notification recipients. Watchers do not log in. |
| `concierge_trip_access` | Trip-scoped capability tokens for concierge updates. |
| `trip_checkpoints` | Ordered checkpoint list and per-checkpoint status. |
| `timeline_events` | Append-oriented event log for trip activity. |
| `notification_attempts` | Outbound message attempts and provider delivery state. |
| `refresh_tokens` | Server-side refresh token hashes and session state. |
| `email_verification_tokens` | One-time email verification token hashes. |
| `password_reset_tokens` | One-time password reset token hashes. |
| `revoked_access_tokens` | Access token IDs revoked before natural expiry. |

Design note:

`trips` intentionally does not store `principal_name`, `principal_phone`, or `principal_photo_url`. Those fields belong to `trip_principals` because one trip may contain a family, delegation, or executive party.

### 3.2 User And Concierge Model

Login-capable account types:

- `ADMIN`
- `PRINCIPAL`

Watchers are not login users. They are contact records attached to a trip by a principal and are notified through their configured channel.

Concierges are not users. A concierge record has:

- `full_name`
- `phone`
- `photo_url`
- `public_id`
- `active`

Concierge state updates must eventually be authorized through `concierge_trip_access`, not through a reusable password login.

## 4. Implemented Auth Contract

### 4.1 Routes

Current auth routes:

```text
POST /api/auth/register/admin
POST /api/auth/register/principal
POST /api/auth/login
POST /api/auth/invitations/accept
POST /api/auth/refresh
POST /api/auth/logout
POST /api/auth/verify-email
POST /api/auth/verify-email/resend
POST /api/auth/password/forgot
POST /api/auth/password/reset
GET  /api/auth/me
```

There is intentionally no concierge registration or login route.
There is intentionally no watcher registration or login route.

Admin invitation route:

```text
POST /api/admin/invitations
```

Admin/principal visibility and watcher routes:

```text
GET  /api/admin/principals
POST /api/principal/trips/{tripId}/watchers
```

### 4.2 JWT Rules

Access tokens:

- Are JWTs.
- Use `tokenUse=access`.
- Include role/account type information.
- Include a token ID so individual access tokens can be revoked.
- Are rejected if revoked.
- Are rejected if issued before the user's `password_changed_at`.

Refresh tokens:

- Are JWTs.
- Use `tokenUse=refresh`.
- Include role/account type information.
- Are stored server-side as hashes.
- Rotate on refresh.
- Can be revoked on logout.
- Are revoked for a user after password reset.

### 4.3 Email Verification

Registration creates an email verification token and sends a verification email.

Login requires `email_verified=true`.

Admin invitations create an `account_invitations` row and send an invitation email. Accepting an invitation creates a verified `AppUser` with the invited account type and then issues tokens.

Verification tokens are:

- Random opaque tokens externally.
- Stored only as hashes.
- One-time use.
- Expiring.

### 4.4 Password Reset

Forgot-password route always returns an accepted generic response to avoid account enumeration.

Password reset tokens are:

- Random opaque tokens externally.
- Stored only as hashes.
- One-time use.
- Expiring.

Successful password reset:

- Updates the password hash.
- Updates `password_changed_at`.
- Revokes outstanding refresh tokens for that user.

### 4.5 Logout

Logout:

- Requires the current bearer access token.
- Accepts an optional refresh token body.
- Stores the current access token ID in `revoked_access_tokens`.
- Revokes the supplied refresh token when present.
- Returns `204 No Content`.

## 5. Email Implementation

### 5.1 Providers

The email layer is behind `EmailSender`.

Implementations:

| Implementation | Use |
| --- | --- |
| `SmtpEmailSender` | Local/dev SMTP delivery, including MailDev. |
| `ResendEmailSender` | Production delivery through Resend. |
| `NoopEmailSender` | Tests. |

Configuration:

```yaml
arrivalos:
  email:
    provider: smtp
    from: ArrivalOS <no-reply@arrivalos.local>
    resend-api-key: ${RESEND_API_KEY:}
```

Provider values:

- `smtp`
- `resend`
- `noop`

### 5.2 Local MailDev

`docker-compose.yml` includes MailDev.

Ports:

- SMTP: `1025`
- Web inbox: `1080`

### 5.3 Templates

Current templates:

- `src/main/resources/email-templates/email-verification.html`
- `src/main/resources/email-templates/password-reset.html`
- `src/main/resources/email-templates/account-invitation.html`

Templates are rendered by `EmailTemplateRenderer`.

## 6. Standard API Error Contract

All exception responses should use `ApiErrorResponse`.

Shape:

```json
{
  "timestamp": "2026-05-25T12:00:00Z",
  "status": 400,
  "code": "VALIDATION_FAILED",
  "message": "Validation failed",
  "path": "/api/auth/register/principal",
  "requestId": "6d3f2d94-8d95-40a4-90aa-25f31a7e02d6",
  "fieldErrors": [
    {
      "field": "email",
      "message": "must be a well-formed email address"
    }
  ]
}
```

Field meaning:

| Field | Purpose |
| --- | --- |
| `timestamp` | Server-side failure time. |
| `status` | HTTP status code. |
| `code` | Stable machine-readable frontend key. |
| `message` | Display-safe human message. |
| `path` | Request path that failed. |
| `requestId` | Support/debugging correlation ID. |
| `fieldErrors` | Optional form-level validation errors. |

Current caveat:

`ResponseStatusException` codes are currently derived from the reason text. The next hardening step should introduce custom domain exceptions with explicit enum codes so changing a display message cannot break frontend branching.

## 7. Security Boundaries

Current rules that must remain intact:

- Concierges do not get app user accounts.
- Watchers do not get app user accounts.
- Admins and principals are the only login-capable account types.
- Principals must only see/mutate trip-side customer records for trips linked to them.
- Watchers and principals must not mutate operational trip state.
- Concierge update ability must stay trip-scoped and revocable.
- Raw verification, reset, refresh, invite, and concierge capability tokens must not be stored.
- Email verification must remain required before login unless explicitly changed by product.

## 8. Testing State

Current endpoint/repository tests cover:

- App startup.
- Repository/migration compatibility against H2.
- Registration for admin and principal.
- Admin invitation and invite acceptance for admin/principal accounts.
- Watcher registration route blocked.
- Principal-created watcher notification recipients.
- Admin principal list with principals, trips, watchers, and concierge assignment.
- Login.
- Email verification.
- Verification resend.
- Refresh token rotation.
- Logout/revocation behavior.
- Forgot-password and reset-password flows.
- Standard error response fields.

Latest known result:

```text
./mvnw test
Tests run: 84, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## 9. Next Backend PRs

### PR 2: Trip State Transition Service

Objective:

Create a service that owns allowed trip state changes and timeline event creation.

Include:

- Explicit transition table for `TripStatus`.
- Event creation through one service path.
- Idempotency key handling for repeated concierge submissions.
- Tests for valid, invalid, duplicate, and out-of-order transitions.

Reason:

Controllers should not directly mutate trip status. The transition service becomes the system's correctness boundary.

### PR 3: Admin Trip APIs

Objective:

Allow admins to create and manage the operational setup for a trip.

Include:

- Create trip.
- Add multiple trip principals.
- Add watchers.
- Assign concierge.
- Create default checkpoints.
- Issue concierge trip access token.

Reason:

This makes the model usable from an ops workflow and prepares the concierge capability flow.

### PR 4: Concierge Capability APIs

Objective:

Allow assigned concierges to view and update one trip without login.

Include:

- Resolve trip by capability token.
- Validate token hash, expiry, revocation, trip assignment, and trip status.
- Submit timeline events.
- Add notes.
- Revoke capability on trip close/cancel.

Reason:

This implements the product decision that concierges are public operational actors with trip-scoped update ability.

### PR 5: Customer-Safe Principal And Watcher Views

Objective:

Expose limited trip/timeline views to principals and watchers.

Include:

- Principal own trips.
- Watcher assigned trips.
- Customer-safe timeline projection.
- Authorization tests that prevent cross-trip access.

Reason:

This is the first customer-facing visibility layer.

### PR 6: Kapso Notification Service

Objective:

Send WhatsApp updates through Kapso while keeping ArrivalOS as the system of record.

Include:

- Provider interface.
- Kapso implementation.
- Notification attempt persistence.
- Webhook signature verification.
- Delivery status updates.
- Tests with mocked Kapso client.

Reason:

WhatsApp is a key channel, but provider logic must not leak into trip controllers or state services.

## 10. Agent Continuation Notes

Start with PR 2 unless the user redirects.

Do not add concierge login. That contradicts the current product model.

Do not move principal fields back onto `trips`. Multiple principals per trip is intentional.

Do not let WhatsApp inbound messages mutate state in MVP. ArrivalOS timeline events remain the source of truth.

Do not add frontend work until backend trip state and access boundaries are stable.
