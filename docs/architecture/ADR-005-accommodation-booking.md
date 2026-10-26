# ADR-005: Accommodation booking as a hospitality extension

- **Status:** Proposed
- **Date:** 2026-10-26
- **Scope:** Staycations, hotels, villas, resorts, guesthouses, and homestays
- **Repository:** `andalus-ai/numu`

## Context

Numu should support a customer-to-provider accommodation marketplace without duplicating generic reservation, commerce, payment, finance, identity, document, notification, or review behavior. The repository's `AGENTS.md`, ADR-003, and capability ownership matrix require explicit ownership, neutral domain code, intentional dependencies, tenant isolation, idempotency, and milestone-sized delivery. Existing `reservation`, `commerce`, and `finance` directories must be inspected before adding overlapping capabilities.

## Decision

Introduce a hospitality/accommodation domain extension that owns property-specific concepts and invariants, and composes existing capabilities through their public contracts. Do not create a second generic booking engine or payment ledger.

### Hospitality-owned concepts

- Provider profile and property-management authorization references (identity remains owned by Identity/Tenancy).
- Property listing, property type, address/location reference, descriptions, photos/document references, amenities, accessibility attributes, policies, and publication/moderation state.
- Accommodation unit or inventory class, occupancy limits, room/unit attributes, and sellable inventory mapping.
- Date-specific rates, restrictions, minimum/maximum stay, check-in/out rules, and property-specific fees where no existing pricing contract owns them.
- Hospitality-specific booking details such as guest count, requested unit, check-in/out dates, guest instructions, and stay lifecycle projections.
- Host operations: listing readiness, reservation inbox, guest communication references, check-in/check-out tasks, and operational status.

### Reuse existing capability owners

- Reservation owns generic holds, reservation lifecycle, conflict prevention, and booking change contracts where those contracts satisfy accommodation needs.
- Commerce owns checkout/order orchestration and shared pricing integration where available.
- Payment owns payment authorization/capture/refund integration and webhook lifecycle where available; hospitality must not store card data or infer payment success from a browser redirect.
- Finance owns ledgering, commissions, provider balances, settlement, and payout accounting where available.
- Identity/Tenancy owns identities, roles, membership, tenant boundaries, and authorization.
- Document owns media/document storage and signed access where available.
- Notification and review/rating capabilities should be reused if present; add adapters or domain-specific policies only where necessary.

These are target ownership assignments. Before code is added, inspect current contracts and implementations; adapt the design to actual public APIs rather than assuming every capability is complete.

## Core invariants

1. A property and its units are isolated by tenant/owner authorization; possession of a property or booking ID is not authorization.
2. A listing cannot be booked unless published, bookable for the requested dates, and valid for the requested occupancy.
3. Availability is checked and inventory held atomically or through a concurrency-safe reservation contract. Concurrent requests cannot oversell the same unit.
4. Every quote is an immutable, versioned snapshot of currency, nights, nightly rates, taxes/fees, discounts, deposit, cancellation terms, provider commission, and total. Repricing is explicit.
5. Check-out is exclusive for night-based stays unless a product policy explicitly defines otherwise. Date intervals use a single documented timezone/date convention.
6. Booking, payment, and provider settlement are separate state machines. A payment success redirect is not proof of payment; verified provider events and reconciliation determine payment status.
7. Commands and callbacks that can create side effects are idempotent. Replays do not duplicate bookings, captures, refunds, commissions, or payouts.
8. Payment/refund and reservation transitions are coordinated with explicit pending, failure, retry, expiry, and compensation states; distributed side effects use durable outbox/inbox or equivalent where warranted.
9. Cancellation/refund eligibility is computed from the accepted policy snapshot and auditable facts, not from mutable current listing text.
10. Provider earnings distinguish gross booking value, discounts, taxes, platform commission, payment fees, refunds, chargebacks, reserves, and net payable amount.
11. Sensitive guest data and check-in instructions are disclosed only to authorized parties and at the appropriate booking stage.
12. All time-sensitive operations record tenant, actor, correlation, idempotency, and audit context where applicable.

## Deployment and integration

Keep the domain independent of HTTP, CDI/runtime wiring, persistence frameworks, brokers, payment vendors, and cloud SDKs. Expose public application ports; implement provider-specific gateways as adapters. The capability may be composed in a modular monolith or exposed remotely without duplicating business rules.

## Consequences

- Hospitality-specific models remain distinct from generic commerce products and generic reservations.
- Payment providers, commission policies, cancellation policies, taxes, currencies, and property types are configurable through explicit contracts rather than hard-coded global assumptions.
- The first implementation must verify Maven reactor roots and existing module APIs before selecting artifact names or wiring dependencies.
- Migration, compatibility, authorization, concurrency, and replay behavior are acceptance requirements, not optional future polish.
