# M22 — Scheduled POD projection worker

## Objective

Run the M21 database-local proof-of-delivery tracking projector from the Quarkus runtime, rather than requiring an operator or application caller to invoke it manually.

## Runtime behavior

- A Quarkus scheduled bean invokes the projector every five seconds by default.
- Overlapping executions in the same application instance are skipped.
- Batch size is bounded to 1–500 and defaults to 100.
- Failures are logged while the next scheduled invocation remains available; the projector retains responsibility for transactional rollback, retry time, and outbox acknowledgement.

## Configuration

- `numou.courier.pod-projector.interval`: Quarkus scheduler interval, default `5s`.
- `numou.courier.pod-projector.batch-size`: integer 1–500, default `100`.

Multiple application instances are safe to run because database rows are claimed with `FOR UPDATE SKIP LOCKED` and tracking writes are idempotent. Production deployment should still set an explicit interval, monitor logs and oldest unpublished event age, and ensure database pool capacity can accommodate the worker.

## Lifecycle boundary

This worker projects an immutable `DELIVERED` tracking fact only. It does not update `courier_delivery_tasks` or `courier_shipments`. The current shipment status constraint permits only `DRAFT`, `BOOKED`, and `CANCELLED`; delivery-task records also lack a first-class persisted delivery-attempt identifier that can safely bind `delivery_attempt_reference` to a task. M23 must introduce and populate that binding, then implement guarded task transitions in the same transaction as projection. Guessing the association from a free-form reference would risk completing the wrong task.

## Validation gates

Tests are authored but have not been run in this session. CI must verify Quarkus scheduler dependency resolution, bean discovery, configuration parsing, startup, repeated execution, multi-instance behavior, and PostgreSQL retry/idempotency integration.
