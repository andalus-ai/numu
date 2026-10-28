# Transport M9 — Bounded checkout reconciliation

## Problem

M8 exposes a safe manual refresh operation, but payment status can change asynchronously after the passenger leaves the checkout page. Without a scheduled reconciler, a checkout can remain in `PAYMENT_PENDING` or `PAYMENT_ACTION_REQUIRED` until a client or operator requests refresh. Repeated polling also needs a bounded query and a retry cursor so one failing provider record cannot monopolize every batch.

## Implementation

- `TransportCheckoutRepository.findStaleNonTerminal(updatedBefore, limit)` is a public Transport SPI query. It returns an oldest-first bounded batch and excludes terminal states.
- The PostgreSQL adapter uses parameterized SQL and the V3 partial index on `updated_at, tenant_id, checkout_id` for nonterminal states.
- `TransportCheckoutService.reconcileDue(minimumAge, batchSize)` reuses the same reconciliation path as the API. It does not accept payment outcomes from the scheduler, and Payment remains authoritative.
- Successful inspections advance `updated_at`, even when the provider status remains pending. Failed inspections attempt a compare-and-set timestamp touch to move the retry cursor; failures do not change the payment or booking result.
- `TransportCheckoutReconciliationScheduler` is an optional Quarkus adapter enabled only with `numou.transport.checkout.enabled=true`. It uses bounded batches, skips overlapping local executions, and records run/attempt/failure/duration metrics.
- The scheduler reports per-item failures and continues processing the rest of the batch. Cross-replica duplicate reads are acceptable; settlement commands remain idempotent and repository updates use optimistic versions.

## Configuration

- `numou.transport.checkout.reconciliation.interval`: scheduler interval; default `30s`.
- `numou.transport.checkout.reconciliation.batch-size`: max rows per tick; default `100`, valid range 1–1000.
- `numou.transport.checkout.reconciliation.minimum-age-seconds`: minimum age before polling again; default `60`, valid range 1–86400.

The feature stays disabled by default. Enabling checkout still requires the host to provide the trusted fare quote and public Payment application providers. Missing providers must fail composition rather than silently skip a payment or create a fake quote.

## Observability

Micrometer metrics:

- `numou.transport.checkout.reconciliation.runs`
- `numou.transport.checkout.reconciliation.attempted`
- `numou.transport.checkout.reconciliation.failed`
- `numou.transport.checkout.reconciliation.duration`

The runtime logs nonzero failed-item counts. Operators should alert on sustained failures and aged `RECONCILIATION_REQUIRED` checkouts; M9 does not automate refunds or decide whether a customer should be compensated.

## Verification

Added application coverage for stale pending checkout refresh and retry-cursor advancement, plus PostgreSQL coverage for the bounded query and terminal-state filtering. The PostgreSQL test executes the V2 and V3 SQL migrations.

CI and the actual Maven reactor still need to run against this branch before this milestone can be considered verified. Docker-dependent PostgreSQL tests may be skipped in environments without Docker.
