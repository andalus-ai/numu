# Numou Omnichannel — Milestone 16: Audited Dead-Letter Operations

## Scope

M16 adds provider-neutral operator contracts and a PostgreSQL implementation for inspecting and explicitly re-queuing dead-lettered inbound events.

- Inspection is tenant-scoped and supports optional provider, sanitized error-code, age (updatedBefore), minimum-attempt and bounded-limit filters.
- Summaries exclude raw webhook payloads and secrets.
- Replay requires an authenticated actor identity and host-supplied authorization policy through DeadLetterReplayAuthorizer.
- A replay only changes an existing DEAD_LETTER event to RETRYABLE_FAILURE; it does not mint a new external event identity or reset attempt counts.
- The state transition and audit insert commit in one PostgreSQL transaction. Concurrent replay requests cannot both queue the same row because the update predicates on state = 'DEAD_LETTER'.
- Replay is recorded with tenant/provider/account/event identity, actor, reason, attempt count and timestamp.

## Integration

Apply migration V4__audit_dead_letter_replay.sql after V1–V3. Construct PostgresDeadLetterOperations with the application's DataSource, and wire DeadLetterReplayService to the application's authorization implementation and a UTC clock. Bind actor identity to the authenticated principal; never accept it from an untrusted request body without authentication.

## Reliability and safety limits

Re-queuing does not promise exactly-once downstream effects. It retains the original event identity and therefore the stable M14 idempotency key. The downstream application use case must persist/check that key atomically with its business effect (or provide an equivalent domain-native idempotency mechanism). Operator authorization must be enforced by the host application's RBAC policy; this library intentionally does not invent a role system.

This milestone provides a persistence/service boundary, not an HTTP endpoint or admin UI. Expose it through the host application's authenticated operator API. Audit retention, tamper-evident external export, alerting, and replay rate limits remain deployment responsibilities.

## Validation status

The code and migration have been added to the milestone branch. Maven compilation and PostgreSQL integration tests have not been run in this session; validate the full reactor and migration against a disposable PostgreSQL instance before production rollout.
