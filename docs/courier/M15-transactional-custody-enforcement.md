# M15 — Transactional custody enforcement

## Summary

M15 connects custody-sensitive hub scans to tenant-scoped PostgreSQL state. The command now carries explicit container/dispatch-wave identifiers and seal/manifest evidence. The JDBC adapter locks the relevant aggregate row, validates the transition, changes custody state, and writes the scan receipt, tracking fact, and outbox event in one transaction.

## Enforced transitions

- **Seal container:** requires a tenant-owned OPEN container, origin-hub scan, non-empty parcel membership, and nonblank seal/actor references. Updates seal reference, actor, timestamp, and status to SEALED.
- **Dispatch container:** requires a SEALED container at its origin and a tenant-owned DEPARTED dispatch wave with a matching origin/destination route. Persists manifest reference and DISPATCHED state.
- **Receive container:** requires a DISPATCHED container at its destination and an observed seal matching the recorded seal. Persists receiver and RECEIVED state.
- **Load parcel:** requires a tenant-owned LOADING wave at its origin and a parcel row in that wave. Records scan count and discrepancy data transactionally; unexpected parcels are rejected by this acceptance path so no receipt is committed.
- **General HUB_RECEIVED** is only a shipment tracking scan; it does not mutate container custody. Container custody receipt must use CONTAINER_RECEIVED with seal evidence.

The aggregate queries include `tenant_id` and use `FOR UPDATE`. Invalid transitions roll back the receipt, tracking event, outbox row, and aggregate update together. Idempotent replays are resolved before attempting a second transition.

## Persistence and compatibility

Migration V17 adds nullable aggregate references and custody evidence to the receipt ledger, tenant-composite foreign keys, and timeline indexes. Its custody-field CHECK is added `NOT VALID` so historical receipts without new reference fields do not block deployment; PostgreSQL still applies the check to new/updated rows. A separate data-reconciliation rollout can validate the constraint after legacy records are assessed.

## Explicit limitations / production gates

- `EXCEPTION_RECORDED` remains an evidence-only scan. This PR does not authorize override actions; authenticated actor identity, permissions, and an audited override use case must be enforced separately.
- The dispatch wave must already be DEPARTED before a container dispatch scan; this flow does not itself depart a wave.
- The wave parcel scan update currently rejects unexpected parcel acceptance after recording a discrepancy in the same transaction, so rejected unexpected scans are not retained in the wave ledger. A dedicated discrepancy-intake command should persist and audit unexpected scans without falsely accepting them.
- Shipment tracking still stores a null parcel UUID because `parcelReference` is an operational reference and no parcel UUID is carried by the command.
- Testcontainers integration tests were added/extended, but Maven and PostgreSQL execution have not been run in this environment. CI must confirm migration ordering, schema assumptions, compilation, rollback behavior, and concurrency behavior before merge.

## Test coverage added

- Container seal scan transitions OPEN to SEALED and persists seal/actor metadata.
- Empty-container sealing rejects and asserts no scan receipt, tracking event, or outbox row was committed.
- Existing idempotency replay, conflicting fingerprint, tenant isolation, and concurrent duplicate-delivery tests remain in place.

## Rollout

1. Deploy V17 migration before deploying code that emits custody-sensitive scan commands.
2. Update scanner/API clients to provide the mandatory container/wave IDs and seal/manifest evidence for custody scans.
3. Verify Postgres integration tests and query plans.
4. Keep exception override workflows disabled until their authorization/audit implementation is available.
