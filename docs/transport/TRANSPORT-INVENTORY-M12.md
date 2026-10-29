# Transport M12 — Tenant-scoped operator reconciliation queue

## Goal

Give authorized support and operations staff a safe way to discover aged checkout/payment/refund cases that require investigation, without turning a queue read into a payment mutation or exposing other tenants' records.

## API

When `numou.transport.checkout.enabled=true`:

- `GET /v1/transport/checkouts/reconciliation?olderThanSeconds=60&limit=50`
- `olderThanSeconds` defaults to 60 and must be between 0 and 31,536,000.
- `limit` defaults to 50 and must be between 1 and 100.
- The endpoint derives tenant and actor only from the verified host security context.
- The endpoint requires active tenant membership and an explicit `READ` policy decision for `transport.booking.reconciliation`, with the authenticated tenant as resource ID.
- The response includes checkout/departure/hold/payment/refund correlation, lifecycle/refund statuses, amount/currency, refund reason, and last update time. It intentionally excludes payment action URIs and performs no refresh, refund, or inventory mutation.

## Query behavior

- Only the caller's tenant is queried.
- The queue includes aged `RECONCILIATION_REQUIRED`, `REFUND_REQUESTED`, `REFUND_PENDING`, and `REFUND_FAILED` cases.
- Results are oldest-first and bounded. A tenant/status/update-time partial index supports the query.
- The repository API defaults to fail closed if an adapter has not implemented this operator query, rather than returning an empty queue that could falsely imply no unresolved cases.

## Verification added

- PostgreSQL integration coverage for tenant isolation, age cutoff, invalid limit, and unresolved-state filtering.
- Application coverage for bounded, tenant-scoped, read-only queue behavior.
- Authorization coverage for the dedicated reconciliation resource permission.

## Operational notes

This queue is not a substitute for metrics and alerts. Continue alerting on aged reconciliation and refund failures. Operator actions such as retrying a failed refund, recording a resolution, or closing a case should be separate audited commands with explicit authorization and idempotency; M12 adds read-only discovery only.

Before production, run the Transport reactor and host-level integration tests using the real identity, tenant-membership, authorization, and Payment providers. PostgreSQL tests require Docker/Testcontainers.
