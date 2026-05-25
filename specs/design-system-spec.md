# ArrivalOS Design System Spec

Version: 0.1  
Status: Draft  
Last updated: 2026-05-20

## 1. Objective

This spec defines the visual and interaction system for ArrivalOS.

ArrivalOS is not a marketing surface. It is a live airport concierge interface used by principals, watchers, concierges, and Gbèjà ops during high-trust arrival workflows.

The design system must make the product feel:

- Calm
- Premium
- Operationally precise
- Trustworthy under pressure

## 2. Design Context

### 2.1 Users

ArrivalOS has four user groups:

| User | Context | Design Need |
| --- | --- | --- |
| Principal | Has just landed, may be tired, offline, or unfamiliar with the airport. | Clear identity proof and meeting instructions. |
| Watcher | Remote spouse, EA, sponsor, host, or family member. | Passive visibility without needing to call anyone. |
| Concierge | Field operator moving through airport checkpoints. | Fast mobile controls with offline resilience. |
| Ops | Internal Gbèjà team monitoring many active arrivals. | Dense but calm dashboard with delay visibility. |

### 2.2 Product Emotion

The product should reduce anxiety.

ArrivalOS should not feel like a consumer delivery tracker. It should feel like a premium command layer for protected airport arrivals.

## 3. Brand Tokens

### 3.1 Color

| Token | Hex | Use |
| --- | --- | --- |
| Primary Navy | `#0A1F4D` | Primary surfaces, watcher headers, trip-in-progress bands, PDF covers. |
| Active Gold | `#D4AF37` | Active checkpoint indicator, verified proof accents, rare brand emphasis. |
| Action Blue | `#1E6FE0` | Buttons, links, next actions, selected controls, focus rings. |
| Deep Navy | `#061633` | High-contrast dark background variant. |
| Ivory | `#F7F1E6` | Text on navy surfaces. |
| Mist | `#A8B3C7` | Secondary text on navy surfaces. |
| Surface | `#F8F7F3` | Light page background. |
| Ink | `#10203F` | Primary text on light surfaces. |
| Line | `#D8DDE8` | Borders, dividers, timeline rails. |
| Success | `#1F8A5B` | Completed status only. |
| Warning | `#B7791F` | Delay status only. |
| Danger | `#B42318` | Real escalation only. |

### 3.2 Color Rules

- Use navy for confidence and authority.
- Use blue for interaction.
- Use gold sparingly. Gold should mean active, verified, or official.
- Do not use gold as a general button color.
- Do not use red for normal delays. Red is reserved for urgent escalation.
- Do not communicate status by color alone.

## 4. Typography

### 4.1 Font Families

| Role | Font |
| --- | --- |
| Principal names | Cormorant Garamond |
| Section headers | Cormorant Garamond |
| PDF/report headings | Cormorant Garamond |
| Body text | Inter or Calibri |
| Labels | Inter or Calibri |
| Dashboard data | Inter or Calibri |

### 4.2 Type Rules

- Use serif typography for authority and identity.
- Use sans-serif typography for operational clarity.
- Keep mobile screens compact and readable.
- Avoid oversized marketing-style hero text inside application views.
- Avoid negative letter spacing.
- Labels may use uppercase sparingly, but long operational text should use sentence case.

## 5. Core Interface Patterns

### 5.1 Timeline First

Every ArrivalOS screen should answer:

1. What is happening now?
2. When was it last updated?
3. Who confirmed it?
4. What happens next?

The timeline is the core information architecture.

### 5.2 One Primary Action

Concierge screens should expose one dominant action at a time.

Examples:

- Mark In Position
- Mark Client Met
- Start Immigration
- Complete Immigration
- Complete Handover

Secondary actions:

- Add Note
- Report Delay
- Call Ops

Secondary actions must not visually compete with the main workflow button.

### 5.3 Status Bands

Use a navy status band at the top of principal and watcher views when a trip is active.

The band should show:

- Current state
- Principal name
- Last updated time
- Concierge identity where relevant

### 5.4 Active Checkpoint Indicator

Use gold only for the active checkpoint.

Completed checkpoints should use muted success treatment. Upcoming checkpoints should stay neutral.

The active checkpoint must also have a text label such as:

```text
Current: Immigration
```

Do not rely on the gold color alone.

### 5.5 Shared Artefact Footer

Every shared or exported artefact should include:

```text
Gbèjà Global Security
We don't sleep, so you can
```

Where a sigil or brand mark exists, place it near the footer or document cover.

## 6. Role-Specific Design Rules

### 6.1 Principal View

Design goal:

Identity certainty.

Must prioritize:

- Concierge photo
- Concierge name
- Meeting point
- One-tap call

Avoid:

- Internal notes
- Ops language
- Dense checkpoint tables
- Too many controls

### 6.2 Watcher View

Design goal:

Anxiety reduction.

Must prioritize:

- Current trip status
- Timeline feed
- Last updated time
- Concierge identity
- Delay explanation when needed

Avoid:

- Operational controls
- Ambiguous statuses like "pending"
- Over-notification language

### 6.3 Concierge View

Design goal:

Fast field operation.

Must prioritize:

- Current trip
- Principal identity
- Current checkpoint
- One primary next action
- Offline sync state

Avoid:

- Long forms during live flow
- Multi-step modals for common actions
- Small touch targets

### 6.4 Ops Dashboard

Design goal:

Calm monitoring.

Must prioritize:

- Active trips
- Current state
- Last update time
- Delay flags
- Concierge assignment

Avoid:

- Decorative charts in MVP
- Marketing-style cards
- Map-first layout unless the map directly supports an operational decision

## 7. Components

### 7.1 Primary Button

Use Action Blue `#1E6FE0`.

Rules:

- One primary button per screen region.
- Label with direct verbs.
- Minimum height: 44px on mobile.
- Include loading and disabled states.

### 7.2 Timeline Item

Each timeline item should include:

- Event label
- Timestamp
- Actor or source
- Optional note

Example:

```text
Client met
14:51 · Confirmed by Tunde Bello
```

### 7.3 Delay Notice

Use warning color, not danger.

Copy should be calm:

```text
Slight delay at Immigration. Gbèjà ops is aware.
```

Avoid:

```text
Problem detected
Emergency delay
Something went wrong
```

### 7.4 Offline Banner

Concierge-only.

Copy:

```text
Offline mode. Updates will sync when connection returns.
```

The offline banner should be visible but not alarming.

## 8. Motion And Interaction

- Use motion to clarify state changes, not decorate.
- Keep transitions short and subtle.
- Respect `prefers-reduced-motion`.
- Avoid bouncing, glowing, pulsing, or urgent animation unless there is a true emergency state.
- Optimistically update concierge actions, then sync in the background.

## 9. Content Rules

### 9.1 Voice

Use clear, direct, calm language.

Preferred:

```text
Your concierge is in position.
Mr. Adekunle has met his concierge.
Handover completed at 15:18.
```

Avoid:

```text
Your journey experience has entered the next stage.
We are delighted to inform you of a successful linkage.
Status changed.
```

### 9.2 Status Copy

Status copy should be specific.

Use:

```text
Flight landed
At Immigration
Exited terminal
Handover complete
```

Avoid:

```text
In progress
Pending
Processing
Updated
```

Generic states may exist internally, but the UI should translate them into user-readable labels.

## 10. Accessibility Requirements

- Target WCAG 2.1 AA.
- Use visible focus states with Action Blue.
- Do not rely on color alone for checkpoint or delay status.
- Support reduced motion.
- Use readable tap targets on mobile.
- Keep watcher and principal pages usable on poor mobile connections.

## 11. Anti-Patterns

Do not build ArrivalOS as:

- A generic Uber-style clone.
- A decorative luxury landing page.
- A map-first app before the timeline is proven.
- A dashboard full of charts before there is useful data.
- A chat-first workflow where WhatsApp freeform messages mutate trip state.
- A dark glowing cyber-security interface.
- A beige luxury brochure UI.

## 12. Engineering Notes

- Store design tokens in one place when the frontend is created.
- Keep shared tokens aligned between web UI and PDF receipts.
- Use semantic component names such as `TripStatusBand`, `CheckpointTimeline`, `ConciergeIdentityCard`, and `OfflineSyncBanner`.
- Keep Kapso/WhatsApp message templates visually and verbally aligned with the web UI.
- Treat exported receipts as official artefacts, not screenshots.

## 13. Open Questions

- Confirm final Gbèjà sigil asset path when available.
- Confirm whether Cormorant Garamond will be loaded from Google Fonts, bundled locally, or replaced by Georgia for offline/export contexts.
- Confirm whether Calibri or Inter should be the default sans-serif for web UI.
