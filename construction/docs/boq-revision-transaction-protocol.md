# BOQ revision persistence protocol (PostgreSQL starter)

## Scope and assumptions

This slice adds a persistence port and a PostgreSQL schema for immutable revision headers, line snapshots, and an outbox. The source dump does not establish the deployed physical BOQ table name, database adapter, or transaction API, so this deliberately does not pretend to be a drop-in production adapter.

## Required transaction boundaries

### Capture revision

1. Begin a database transaction.
2. Lock the BOQ row (or use an equivalent compare-and-set) and verify the expected current revision.
3. Load the current BOQ items from the authoritative item repository.
4. Calculate each line using the explicit `calculation_policy_version`; persist the header and every item snapshot.
5. Commit. A uniqueness conflict on `(boq_id, revision_number)` means the command lost a race and must return a conflict, not silently overwrite.

### Submit or approve

1. Begin a transaction and lock/read the revision header.
2. Check its current status and actor authorization.
3. Update using a status predicate, e.g. `... WHERE boq_id = ? AND revision_number = ? AND status = ?`; require exactly one affected row.
4. Update the mutable BOQ aggregate in the same transaction if the current domain lifecycle requires it.
5. Insert a stable event ID into `construction_outbox_event` in the same transaction.
6. Commit. Only the outbox worker publishes after commit; consumers should be idempotent by `event_id`.

A handler must not implement these steps as independent repository writes without a shared transaction boundary.

## Important implementation details

- The revision header primary key prevents duplicate revision numbers for one BOQ.
- Item rows reference the snapshot header, not live `BoqItem` rows, preserving historical values.
- Persist the rate currency code and calculation policy version so old totals remain explainable if policy changes later.
- `NUMERIC(24, 8)` is a starter precision, not a business-approved universal precision. Confirm supported quantity/rate precision and currency rounding rules before deployment.
- The migration omits a foreign key to the mutable BOQ table because its physical table name was not verifiable from the source dump. Add it once the real schema is confirmed.
- Use the project's existing Flyway naming/version conventions when integrating; this standalone version name may need adjustment to avoid a collision.
- Authorization must be checked by the application/service layer; an actor UUID in a command is audit data, not proof of permission.
- Consider adding tenant ID to keys and indexes if BOQs are tenant-scoped in the deployed schema.

## Concurrency tests required before release

1. Two concurrent captures for the same BOQ revision: exactly one succeeds.
2. Two concurrent approvals: exactly one status compare-and-set succeeds.
3. A transaction failing after header insert leaves no partial line rows.
4. An outbox insert failure rolls back the status transition.
5. Retried publication does not duplicate downstream effects.
6. Snapshot reads remain unchanged after live BOQ items are edited.

## 6. Outbox relay delivery contract (follow-up slice)

- Claim a small batch atomically using a short database transaction and a lease token per row. In PostgreSQL, use `FOR UPDATE SKIP LOCKED` and set `status='PUBLISHING'`, `worker_id`, `lease_token`, and `locked_until` before committing.
- Deliver to the broker outside the database transaction. Do not hold a database transaction open across network I/O.
- Acknowledge only when `event_id` and the current `lease_token` match. This prevents a worker whose lease expired from acknowledging a row already reclaimed by another worker.
- On failure, store a bounded error message, increment attempts, and set `available_at` using `OutboxRetryPolicy`. Move to `FAILED` (terminal) after the configured attempt limit. A scheduled operator replay should be explicit and audited.
- A crash after broker delivery but before database acknowledgement can duplicate delivery. Consumers must deduplicate by `event_id`; the platform does not claim exactly-once delivery.
- Reclaim expired `PUBLISHING` leases. Do not permanently strand rows when a worker dies.

## 7. Command idempotency contract

For mutating HTTP/API commands, scope the idempotency key to the authenticated tenant and operation. Store a stable SHA-256 fingerprint of canonicalized request content (excluding transport-only fields). A repeated key + same fingerprint returns the stored result; the same key + different fingerprint is a conflict. The reservation, business mutation, outbox insert, and completed response should commit atomically. Never treat an actor ID supplied in the body as proof of authorization. Define retention and cleanup based on API retry windows before enabling expiry.

The new SPI contracts and migration are still adapter-facing scaffolding. The SQL adapter must implement these invariants and the migration must be checked against the actual deployed schema before rollout.
