# Outbox lease fencing acceptance test

Run `scripts/postgres-outbox-lease-fencing-test.sh` directly, or include it in `scripts/run-phase1-validation.sh --postgres-only`. The script creates a disposable PostgreSQL 16 container and applies the starter migrations; it does not connect to an existing database.

## Scenario

1. A first worker claims an event with lease token A.
2. The lease expires and a recovery worker reclaims the event with token B.
3. Worker A attempts to acknowledge, reschedule as failed, and renew the lease using token A.
4. Each stale mutation must affect zero rows, leaving worker B's lease and attempt count intact.
5. Worker B acknowledges with token B, and the event transitions to `PUBLISHED`.

## Acceptance criteria

- A stale lease token cannot acknowledge a newer claim.
- A stale worker cannot overwrite retry state or extend another worker's lease.
- The current lease holder can acknowledge successfully.
- The attempt count reflects two claims and no stale mutation changes ownership.

## Scope limitation

This is a SQL-protocol test of the starter schema and predicates. It does not invoke or validate the production Syirkah relay adapter, broker, or actual application migrations. Confirm that every production ack/fail/renew statement predicates on tenant, event ID, expected status, and the current lease token (or equivalent fencing generation) before relying on this behavior.
