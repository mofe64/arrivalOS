# ArrivalOS Specs

This directory holds product, architecture, and implementation specs for ArrivalOS.

The purpose of this folder is to keep decisions explicit before development begins. Specs should describe what is being built, why it matters, what is intentionally out of scope, and what tradeoffs were accepted.

## Current Specs

| Spec | Purpose |
| --- | --- |
| [arrivalos-mvp-spec.md](./arrivalos-mvp-spec.md) | Defines the lean MVP scope, roles, state machine, screens, data model, and Kapso-powered WhatsApp messaging approach. |
| [backend-implementation-spec.md](./backend-implementation-spec.md) | Captures the current Spring Boot backend implementation, auth lifecycle, error contract, local infrastructure, and next backend PRs. |
| [design-system-spec.md](./design-system-spec.md) | Defines ArrivalOS brand tokens, typography, UI patterns, role-specific design rules, and accessibility requirements. |

## Spec Rules

- Keep specs focused on decisions, not vague product ambition.
- Mark deferred features clearly so they do not silently return to MVP scope.
- Treat ArrivalOS as a trip timeline system first.
- Treat WhatsApp as a communication channel, not the system of record.
- Keep implementation specs current after each backend PR so agents can resume without rediscovering decisions.
- Prefer small, buildable phases over large all-in-one releases.
