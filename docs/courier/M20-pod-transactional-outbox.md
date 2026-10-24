# M20 — Transactional proof-of-delivery event outbox

## Objective

Ensure a newly accepted proof-of-delivery record and the event that announces it commit in the same database transaction. This closes the classic dual-write gap where evidence is stored but a process crash prevents the tracking event from being queued.

## Included

- V22 creates a tenant-scoped outbox with event payload, retry timing, attempt count, lease fields, and publication timestamp.
- The POD JDBC adapter writes `courier.proof-of-delivery.recorded.v1` in the same transaction as the new POD row.
- Idempotent replay and conflicting idempotency keys do not enqueue a second event.
- Documentation defines the delivery contract and the requirements for a future publisher/consumer.

## Delivery semantics

The outbox guarantees atomic persistence, not exactly-once delivery. A publisher must claim rows with a short lease (typically `FOR UPDATE SKIP LOCKED`), publish using `tenant_id + id` as the stable event ID, and mark success only after broker acknowledgement. Lease expiry and retry are expected; consumers must be idempotent. Publisher implementation, broker-specific delivery, and shipment-state transition are deliberately not implied by this schema-only relay boundary.

## Security and operations

Tenant identity must be taken from authenticated context. Do not expose arbitrary outbox payloads to tenant-facing callers. Restrict direct table writes and keep the outbox payload free of recipient PII and evidence URLs; the event contains references only. Monitor oldest unpublished event age, retries, lease expiry, and dead-letter/manual recovery.

## Validation gates

Run the Courier Maven tests and apply V22 to PostgreSQL in CI. Add database-backed tests for rollback atomicity, replay creates no duplicate event, claim/lease races, retry after publisher crash, and tenant isolation before enabling a publisher in production.
