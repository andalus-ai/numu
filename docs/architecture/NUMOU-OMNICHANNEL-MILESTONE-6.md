# Numou Omnichannel — M6: Durable PostgreSQL Inbox

## Scope

M6 introduces an optional PostgreSQL/JDBC adapter for the core `InboundEventInbox` port. The core remains persistence- and framework-neutral. The adapter is not required by standalone domain modules.

## Behavior

- Uses a composite primary key over tenant, provider, provider account and external event ID.
- Uses PostgreSQL `INSERT ... ON CONFLICT DO NOTHING` as the atomic claim operation.
- Returns `ACCEPTED` only when this call inserted and committed the row.
- Returns `DUPLICATE` when the same scoped event key already exists with the same SHA-256 digest.
- Returns `PAYLOAD_CONFLICT` when a key is reused with a different digest; it never overwrites the original digest.
- Persists only validated metadata and the digest, not the raw webhook body.
- Includes a state column and an index for future retry/worker scans; this milestone does not yet implement state transitions, attempt scheduling, raw-payload storage or dispatch.

## Integration

Apply `V1__create_omnichannel_inbound_event.sql` with the deployment's existing migration runner. Supply a PostgreSQL-backed `DataSource`. The adapter requires auto-commit connections so that a successful claim is committed before downstream processing begins. A deployment must not dispatch business effects until the claim returns `ACCEPTED` and provider authentication plus tenant/account authorization have already succeeded.

## Operational and security notes

- Restrict database access to the service role; tenant scope is part of the unique key, not a substitute for authorization.
- Do not put credentials or raw payloads in metadata or logs.
- Retain conflict events for investigation; alerting/quarantine policy is an application concern.
- Use the existing domain application ports for business effects. This adapter does not mutate orders, inventory, payments or fulfillment.
- Do not claim end-to-end exactly-once processing: the inbox prevents duplicate claims, while downstream side effects still need idempotency and/or an outbox strategy.

## Validation status and follow-up

The source and migration are added, but Maven tests and PostgreSQL integration/concurrency tests have not been run in this environment. Before production use, add database-backed tests for concurrent same-key claims, digest conflicts, transaction/connection behavior, schema migration, and the supported PostgreSQL versions.

M7 should add durable attempt tracking, bounded retry scheduling, atomic lifecycle transitions and audited dead-letter replay, with an explicit worker/dispatch boundary.
