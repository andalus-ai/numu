# Outbox consumer idempotency and duplicate-delivery acceptance

Run the SQL protocol test from the `syirkah-phase1` directory:

```bash
bash scripts/postgres-outbox-consumer-idempotency-test.sh
```

It creates a disposable PostgreSQL 16 container, applies the starter migrations, and inserts one tenant-owned outbox event. The harness claims the event, records a simulated broker delivery, deliberately leaves the database acknowledgement undone, expires the lease, and claims the event a second time. It then feeds both deliveries to a test-only consumer transaction.

## Expected acceptance

- The relay claims the same event twice and the attempt count reaches two.
- The simulated delivery log contains two deliveries.
- The consumer inbox has a unique `(tenant_id, event_id)` key and stores one row.
- The business-effect ledger has one row because only a newly accepted inbox key can apply the effect.
- Inbox insertion and the business effect are executed in the same database transaction.

## Important boundaries

This demonstrates the SQL idempotency pattern, not end-to-end exactly-once delivery. The transport remains at-least-once; duplicate deliveries are expected. The consumer must atomically persist its inbox/deduplication key and business effect in the same transactional database. If the effect is an external side effect, use an idempotency key or a separate transactional/outbox protocol at that boundary. This fixture does not invoke a real broker, `PgOutboxRelayStore`, or a production consumer adapter. Align it with the actual Numou event envelope, tenant context, and consumer schema before relying on it.
