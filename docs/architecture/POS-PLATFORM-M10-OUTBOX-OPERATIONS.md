# POS Platform M10 — Outbox Operations

## Operational visibility

The persistence operations port exposes aggregate counts for PENDING, IN_FLIGHT, PUBLISHED, and DEAD rows. Counts are system-level operational totals and must be exposed only to authorized operations tooling; do not present them as tenant-scoped data.

## Dead-letter replay

Replay is an explicit command, not an automatic retry:
- requires tenant ID, event ID, operator identity, and a nonblank reason;
- updates only a DEAD event matching both tenant ID and event ID;
- resets delivery attempts and lease state and makes the event immediately eligible;
- increments replay_count and records last_replayed_at;
- appends an immutable audit row in the same database transaction.

A missing, non-DEAD, or cross-tenant event returns false and creates no audit record. Replays preserve the original event ID and payload, so consumers must remain idempotent.

## Security boundary

This milestone deliberately adds an application use case and persistence port, not an unauthenticated REST endpoint. The caller must derive tenant and actor from the authenticated operational principal, not trust arbitrary request fields. HTTP administration should be added only with the repository's established authorization and tenant-context integration.

## Verification

Unit tests cover input validation and tenant/audit context forwarding. PostgreSQL integration tests are still required to prove transaction rollback, cross-tenant rejection, duplicate replay prevention, and audit append behavior. CI/build has not yet been executed.
