# Milestone 29 — Transactional schedule-change outbox

## Goal

Make schedule-change outcomes durably publishable without a dual-write gap between the PostgreSQL plan mutation and event capture.

## Changes

- Adds the append-oriented `schedule_change_outbox` table with a unique execution/event key, JSONB payload, retry scheduling metadata, lease fields, and pending/expired-claim indexes.
- Writes `ScheduleChangeApplied` or `ScheduleChangeConflicted` in the same database transaction as the terminal execution receipt and, for applied changes, the plan/schedule mutation.
- Uses a uniqueness constraint and `ON CONFLICT DO NOTHING` as a final duplicate guard. An execution replay returns its existing receipt without creating another outbox row.
- Adds integration assertions for applied event capture, conflict event capture, replay idempotency, and rollback with no receipt/outbox residue.

## Delivery boundary

This milestone captures events durably; it does not yet deliver them to brokers or mark them published. A later dispatcher should claim rows using short transactions and leases, publish outside the transaction, then mark success using the claim token. Delivery is at-least-once; consumers must deduplicate by stable outbox/event ID. The dispatcher must not hold a database transaction open during network I/O.

## Operational notes

- Outbox rows intentionally reference execution receipts and plans with `ON DELETE RESTRICT`; retention/purge must be explicit and must not remove unpublished events.
- Failure reasons are escaped before constructing JSON. Prefer a typed JSON serializer if the adapter already adopts one.
- Integration tests are authored but require PostgreSQL-backed test execution and the full migration chain before deployment.
