# Hospitality Booking — Milestone Implementation Plan

**Target:** `andalus-ai/numu`  
**Branch:** `feat/hospitality-booking-foundation`  
**Status:** Architecture and acceptance plan; implementation and test verification remain pending.

## Product scope

Build customer-side and provider-side booking workflows for staycations, hotels, villas, resorts, guesthouses, and homestays. Support listing discovery, availability, quote/checkout, booking lifecycle, payment, cancellations/refunds, host operations, commissions, and payouts. Treat consumer UI/API composition separately from capability domain rules.

## Repository-first rule

Before implementing artifacts, inspect `AGENTS.md`, ADR-003, the domain capability matrix, relevant reactor POMs, public application ports, persistence adapters, tests, and existing payment/finance implementations. The repository has multiple Maven reactor roots; do not assume a root-level Maven build. Reuse existing contracts when they cover the invariant. Do not create duplicate reservation, payment, ledger, identity, or document ownership.

## Proposed capability boundary

Hospitality owns property/listing/unit semantics, accommodation inventory mapping, stay-specific pricing restrictions and policy snapshots, guest/stay details, and host operations. Generic reservation owns inventory holds and reservation lifecycle where its public contract is suitable. Commerce coordinates checkout and price/order integration. Payment owns payment-provider interactions. Finance owns commission accounting and provider settlement/payout records. Identity/Tenancy owns access control; Document owns media; notification and reviews are reused where present.

Actual Maven artifact/module names must follow the inspected repository layout and capability conventions rather than being invented in advance.

## Milestones

### M1 — Domain contract and repository fit

- Map current reservation, commerce, finance/payment, identity/tenancy, document, notification, and review contracts.
- Define hospitality aggregates, identifiers, status transitions, public application ports, events, tenant/actor context, and idempotency rules.
- Define listing/unit model, property policies, date/time conventions, quote snapshot, and integration mapping.
- Add ADR/ownership-matrix updates only after the real boundary is confirmed.

**Acceptance:** no duplicate owner for booking/payment/ledger; dependency graph remains acyclic and public-contract based; domain has no framework/vendor dependencies; architecture tests cover boundaries.

### M2 — Provider onboarding and listing management

- Provider/manager authorization and listing draft, submit, approve/publish, suspend, archive lifecycle.
- Property, unit/room inventory mapping, capacity, amenities, media references, house rules, check-in/out details, accessibility and location.
- Draft validation and publication readiness; audit listing changes.
- Searchable public listing projection with tenant-safe provider management APIs.

**Acceptance:** unauthorized cross-tenant reads/writes fail; unpublished/suspended listings cannot be booked; media uses Document contracts; property and unit validation is covered by unit tests.

### M3 — Availability, rate plans, and quotes

- Date-based inventory and concurrency-safe availability/hold integration.
- Per-night/date rates, occupancy/extra guest charges, minimum stay, blackout dates, booking windows, fees, discounts, taxes through explicit policies, currency, and rounding rules.
- Quote snapshot with expiry and policy version; clear repricing rules.
- Prevent overlapping confirmed/held reservations; handle hold expiry and retries.

**Acceptance:** concurrency tests prove no overselling; timezone, leap-day, exclusive checkout, same-day and multi-night boundaries are tested; monetary calculations are deterministic and currency-aware.

### M4 — Customer booking and checkout

- Search/filter/sort and listing detail contracts.
- Quote, guest details, create booking/hold, checkout, booking history/detail, cancellation request, and change request.
- Booking lifecycle including pending hold, awaiting payment, confirmed, rejected/expired, cancelled, completed, and disputed states as appropriate to existing contracts.
- Guest communications and booking confirmation through existing notification ports.

**Acceptance:** duplicate submissions are idempotent; payment failure/timeout releases or expires inventory correctly; confirmation is not issued from an unverified browser redirect; authorization protects guest PII.

### M5 — Payment, refunds, commissions, and payouts

- Integrate existing Payment/Commerce/Finance public contracts; do not create a new ledger or save raw payment credentials.
- Payment intent/authorization/capture and verified webhook handling; idempotency keys and event replay protection.
- Configurable platform commission and provider terms, with an auditable booking-level financial breakdown.
- Cancellation policy evaluation, partial/full refund workflow, failed refund retry, chargeback/dispute references, reconciliation, provider payable balances and payout status.
- Define whether capture occurs at booking confirmation, check-in, or another configurable policy; do not hard-code a universal policy.

**Acceptance:** duplicate/out-of-order callbacks do not double-confirm or double-charge; refunds and payouts cannot exceed eligible amounts; ledger and booking status reconcile; secrets are configuration-managed; failure and reconciliation paths are tested.

### M6 — Provider operations and guest stay

- Provider reservation inbox, accept/reject rules where instant-booking is disabled, guest messaging references, arrival/departure status, check-in/out checklist, incident/support handoff, no-show, and extension requests.
- Respect role-based access and minimize exposure of guest details.
- Provider dashboard metrics reconcile to booking and finance records.

**Acceptance:** only authorized host/staff can manage assigned properties; stay transitions are audited and idempotent; extensions revalidate inventory and reprice explicitly.

### M7 — Reviews, reliability, security, and release readiness

- Verified-stay reviews and moderation through existing review capability where available.
- Outbox/inbox or equivalent durable event delivery for required cross-capability side effects.
- Contract, domain, persistence, adapter, authorization, concurrency, replay, migration, and architecture tests.
- Observability for expired holds, failed payment/refund, reconciliation mismatch, payout failure, and stuck booking state.
- API and event compatibility docs, configuration examples, operational runbook, migration plan, and staged rollout.

**Acceptance:** narrow reactor tests pass, broader dependent reactors and architecture checks run where practical, CI outcomes are reported honestly, and unresolved environment failures are listed separately from product failures.

## API/workflow surface (illustrative; finalize against repository conventions)

Customer: search listings, fetch details, get availability/quote, create checkout/booking, fetch own bookings, cancel/request changes, view payment/refund state, submit a verified-stay review.

Provider: create/update listing, submit/publish listing, manage units/rates/calendar, view/manage reservations, manage check-in/out, view financial breakdown, request/configure payout destination through a secure payment-provider contract, view payout/reconciliation status.

Admin/operations: listing moderation, policy configuration, dispute/support references, reconciliation and audit views. Administrative endpoints must have explicit privileged authorization.

## Security and operational requirements

- Tenant isolation and object-level authorization on every provider/customer command.
- No payment-card data storage; verify webhook signatures and provider event identity.
- Idempotency for booking creation, payment operations, refunds, event consumers, and payouts.
- Explicit state machines, transactional boundaries, compensation, timeouts, retry limits, and operator-visible terminal failures.
- Audit actor, tenant, correlation, policy version, and financial state transitions.
- Avoid logging credentials, payment secrets, full sensitive guest details, or private check-in information.
- Configuration for currency, timezone, taxes, fees, commission, payment providers, cancellation policies, and booking confirmation model.

## Delivery strategy

Use one coherent milestone PR for the first vertical slice, then subsequent milestone PRs only when each is reviewable and independently verifiable. Do not split into a PR per class/module. Keep unrelated existing failures separate. Do not merge without explicit authorization.

## Current verification status

This document defines target behavior only. No accommodation implementation, Maven build, automated test, or production deployment is claimed by this plan.
