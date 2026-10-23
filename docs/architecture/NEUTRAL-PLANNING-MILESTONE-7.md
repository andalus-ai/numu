# Neutral Planning — Milestone 7: Audited Quantity Corrections

## Purpose

Quantity measurements are evidence, not mutable state. If a measurement is wrong, the system must retain the original record and record the correction as a separate auditable fact. This milestone introduces that correction link without changing the neutral planning model into a construction-only BoQ workflow.

## Delivered

- `QuantityMeasurementCorrection` captures tenant, exact quantity-definition revision, original measurement, replacement measurement, actor, timestamp, reason, and idempotency key.
- `QuantityCorrectionRepository` exposes tenant-explicit append and lookup operations.
- `RecordQuantityMeasurementCorrection` verifies that both measurements exist under the supplied tenant and exact definition revision before it delegates to persistence.
- `JdbcQuantityCorrectionRepository` provides PostgreSQL persistence, tenant-qualified queries, semantic idempotency replay checks, and audit-history lookup.
- Migration `V4__quantity_measurement_corrections.sql` adds composite foreign keys tying both measurements to the same tenant and definition, a distinct-measurement constraint, idempotency uniqueness, and immutable-row trigger.
- Domain, application, and PostgreSQL integration tests cover invalid references, replay, tenant/definition scope, and retention of original evidence.

## Correction semantics

A correction does not update or delete the original measurement. The replacement is a separate measurement row. The correction event links the two and records why the replacement should be considered. Consumers should read correction history explicitly when determining the currently accepted value; this milestone does not silently change the existing `findLatestMeasurementAsOf` contract to infer a current value from corrections.

This separation avoids conflating chronology ("what was recorded") with interpretation ("which record is accepted after review"). A future accepted-value projection should define how multiple corrections, corrections-of-corrections, rejected corrections, and review/approval status are resolved.

## Persistence integrity

- Both original and replacement measurement references use composite foreign keys containing tenant and definition IDs.
- The database rejects cross-tenant or cross-definition correction links.
- Correction records are append-only at the database layer.
- A repeated tenant/idempotency key with identical correction payload is a no-op; different payload reuse is rejected.
- Corrections require an explicit reason and actor.

## Remaining production work

- Authorization policy and trusted tenant context must be enforced at service/API boundaries; a tenant ID in a domain record is not proof of authorization.
- Correction review/approval and accepted-value projection are not implemented here.
- The use case currently validates existence before append; database constraints remain the final integrity boundary.
- Run the Maven reactor and PostgreSQL/Flyway integration tests in CI. Tests were authored but were not executed in this remote editing session.
