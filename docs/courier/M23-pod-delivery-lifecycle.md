# M23 — Proof-of-delivery delivery-task lifecycle integration

## Objective

Connect the immutable proof-of-delivery (POD) evidence and M21/M22 tracking projection to the persisted delivery-task lifecycle without guessing which task a POD belongs to.

## Persisted attempt identity

Migration V23 adds `courier_delivery_tasks.delivery_attempt_reference`, backfills existing tasks, and enforces tenant-scoped uniqueness. The reference is deterministic: `TASK:<delivery-task-UUID>`. New task inserts persist the same value. POD submissions must use this exact value as `delivery_attempt_reference`; the projector does not match free-form courier references, shipment references, or a "most recent" task.

The reference is derived from the stable task ID and stored in the database so downstream callers can obtain/use a stable, documented correlation key. The caller still must ensure that the POD is submitted for the actual delivery attempt.

## Transactional projection behavior

For each POD outbox row, the projector now:
1. Resolves the shipment within the event's tenant.
2. Finds the delivery task by tenant, shipment ID, and exact attempt reference.
3. Transitions only `OUT_FOR_DELIVERY` or `DELIVERY_ATTEMPTED` to `DELIVERED`, persisting delivered time, recipient label, and evidence reference.
4. Writes the append-only `DELIVERED` tracking event and acknowledges the outbox row in the same transaction.

A replay is accepted only when the already-delivered task has the same evidence reference, recipient, and delivered timestamp, and the tracking idempotency key still describes the same shipment/time fact. A mismatched tenant, shipment, attempt reference, invalid lifecycle state, or conflicting outcome is not silently acknowledged; the row remains eligible for bounded-backoff retry and emits an operational error through the existing worker path.

## Scope boundary

This milestone updates the delivery-task lifecycle and tracking timeline. It deliberately does **not** set `courier_shipments.status = 'DELIVERED'`: the shipment schema currently permits only `DRAFT`, `BOOKED`, and `CANCELLED`, and shipment-level completion needs its own domain transition and policy for multi-parcel shipments.

## Validation and production gates

Source-contract tests cover tenant/attempt scoping, guarded transitions, replay checks, and the stable reference. They are not a substitute for PostgreSQL integration tests. CI should verify migration compatibility, two concurrent projectors, a duplicate POD, a conflicting POD for the same task, unknown attempt reference, tenant isolation, rollback after a failed tracking insert, and Quarkus startup. Tests have not been run in this session.
