# ArrivalOS Backend

Spring Boot backend for the ArrivalOS email-only admin, principal, and concierge MVP.

ArrivalOS is a timeline-first airport concierge visibility and audit system. This backend keeps ArrivalOS as the source of truth for trip state and sends email notifications from timeline events.

WhatsApp, Kapso, WhatsApp webhooks, and inbound WhatsApp commands are intentionally not implemented in this version.

## Stack

- Java 21
- Spring Boot 4
- Spring Security with JWT bearer tokens
- Spring Data JPA
- Flyway
- PostgreSQL for runtime
- H2 for tests
- Email providers: SMTP, Resend, or noop

## Local Run

Start PostgreSQL and MailDev:

```bash
docker compose up -d
```

Run the backend:

```bash
./mvnw spring-boot:run
```

Health check:

```bash
curl http://localhost:8080/api/health
```

## Tests

```bash
./mvnw test
```

Tests use the `test` Spring profile and an in-memory H2 database in PostgreSQL compatibility mode.

## Authentication Model

Login-capable accounts:

- `ADMIN`
- `PRINCIPAL`

Non-login actors:

- Watchers are trip-scoped email notification recipients.
- Concierges are operational records authorized by trip-scoped capability tokens.

Email verification is required before login.

## Required Production Environment

Set these for production:

```bash
ARRIVALOS_JWT_SECRET=replace-with-a-long-random-secret
DATABASE_URL=jdbc:postgresql://host:5432/arrivalos
DATABASE_USERNAME=arrivalos
DATABASE_PASSWORD=replace-me
ARRIVALOS_APP_BASE_URL=https://app.example.com
ARRIVALOS_CORS_ALLOWED_ORIGINS=https://app.example.com
ARRIVALOS_EMAIL_PROVIDER=resend
ARRIVALOS_EMAIL_FROM='ArrivalOS <no-reply@example.com>'
RESEND_API_KEY=replace-me
```

For SMTP instead of Resend:

```bash
ARRIVALOS_EMAIL_PROVIDER=smtp
MAIL_HOST=smtp.example.com
MAIL_PORT=587
MAIL_USERNAME=replace-me
MAIL_PASSWORD=replace-me
MAIL_SMTP_AUTH=true
MAIL_SMTP_STARTTLS=true
```

For local/dev email with MailDev:

```bash
ARRIVALOS_EMAIL_PROVIDER=smtp
MAIL_HOST=localhost
MAIL_PORT=1025
MAIL_SMTP_AUTH=false
MAIL_SMTP_STARTTLS=false
```

For tests only:

```bash
ARRIVALOS_EMAIL_PROVIDER=noop
```

## Core API Areas

Auth:

- `POST /api/auth/register/admin`
- `POST /api/auth/register/principal`
- `POST /api/auth/login`
- `POST /api/auth/refresh`
- `POST /api/auth/logout`
- `GET /api/auth/me`

Admin/ops:

- `POST /api/admin/trips`
- `GET /api/admin/trips`
- `GET /api/admin/trips/active`
- `GET /api/admin/trips/{tripId}`
- `PATCH /api/admin/trips/{tripId}`
- `POST /api/admin/trips/{tripId}/timeline-events`
- `POST /api/admin/trips/{tripId}/principals`
- `POST /api/admin/trips/{tripId}/watchers`
- `POST /api/admin/trips/{tripId}/cancel`
- `GET /api/admin/trips/{tripId}/notification-attempts`
- `GET /api/admin/concierges`

Principal:

- `GET /api/principal/trips`
- `GET /api/principal/trips/{tripId}`
- `GET /api/principal/trips/{tripId}/timeline`
- `POST /api/principal/trips/{tripId}/watchers`

Concierge capability:

- `GET /api/concierge/trips/{tripId}?accessToken=...`
- `POST /api/concierge/trips/{tripId}/timeline-events`

## Notification Behavior

This version sends email only. Notification attempts are persisted with:

- recipient type
- channel
- provider
- status
- failure reason
- timestamps

Email failures are non-blocking for timeline transitions. A failed email records a `FAILED` notification attempt but does not roll back the trip state change or timeline event.
# arrivalOS
