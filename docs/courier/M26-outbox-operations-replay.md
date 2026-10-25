# M26 — Outbox Operations, Health, and Replay

## Operational health

`CourierOutboxOperations.health(actorReference, destination)` returns grouped status counts plus ready, leased, oldest-pending, and last-state-change information. Access requires a deployment-provided `CourierOutboxOperationsAuthorizer`; no permissive default exists. A REST/admin API can wrap this service later using the deployment's authenticated principal and established authorization policy.

## Dead-letter replay

`replayDeadLetter` only permits `DEAD -> PENDING`. It takes a tenant ID, event ID, destination, authenticated actor reference, and mandatory reason. It locks the publication row, resets delivery attempts for a fresh retry budget, clears the old lease/error, records replay metadata, and inserts an append-only audit record in the same transaction. Replaying a missing or non-DEAD event is rejected. The audit records the previous attempt count so operational history is retained even though the active retry counter resets.

## Security and tenancy

The service does not trust a caller-supplied actor by itself: the configured authorizer must validate the actor against the deployment's authenticated identity/policy system. Every replay targets the composite tenant/event/destination key. Do not expose these methods directly over HTTP without authentication, authorization, rate limiting, and audit-log monitoring.

## Validation status

Contract tests are included but have not been executed in this session. Before production rollout, run the migration chain and PostgreSQL integration tests, and verify unauthorized access, tenant isolation, concurrent replay requests, audit immutability, metrics correctness, and retry-after-replay behavior.
