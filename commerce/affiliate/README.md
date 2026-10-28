# Affiliate & Partner Capability

This is a reusable, deployment-neutral affiliate capability, not a mandate to run a separate service.

## M2 — Core setup foundation

- Tenant-scoped affiliate program, partner, program membership, and referral-link lifecycles.
- Normalized referral codes and explicit tenant/actor/idempotency/correlation command context.
- Repository contracts require tenant-scoped lookups; concrete adapters must enforce tenant scope and uniqueness atomically.

## M3 — Attribution foundation

- Immutable, versioned attribution policies with first-touch and last-touch strategies.
- Deterministic selection from eligible touchpoints, bounded by a policy window and conversion timestamp.
- Immutable decisions record source correlation and policy version, and distinguish attributed from unattributed outcomes.
- Application orchestration checks for an existing decision, requires an authoritative qualified conversion, resolves the effective policy, loads a bounded touchpoint set, and uses atomic `saveOrGet` to converge concurrent replays.
- Source adapters must provide a verified qualifying conversion; client redirects and untrusted status payloads are not evidence.
- Decision repository contract specifies an atomic unique key and exact-replay behavior.

## Not yet production-complete

- Concrete persistence adapters and transaction isolation for uniqueness/concurrency.
- Durable ingestion/outbox, backfill and reconciliation jobs.
- Commission calculation, liability accounting, reversals, disputes, payout and provider execution.
- HTTP endpoints and partner/operator UI.
- Identity/tenant authorization adapter implementation, retention policy, and deployment-specific observability.

Those pieces must follow existing reactor/runtime conventions and have integration tests before they are considered production-ready. Do not expose raw personal identifiers as subject keys; use a tenant-scoped opaque/pseudonymous key under a documented privacy/retention policy.
