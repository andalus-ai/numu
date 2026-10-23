# Neutral Planning — Milestone 6: Tenant-Scoped Scope Quantities

## Intent

Provide a reusable quantity foundation connected to opaque scope/WBS references without making the planning core depend on construction BoQ, a particular WBS implementation, procurement, or software delivery.

## Delivered in this milestone

- `ScopeQuantityDefinition`: immutable definition tied to an explicit tenant and an opaque `(scopeType, scopeId)` reference. Definitions have an explicit positive revision, unit code, and planned quantity.
- `QuantityMeasurement`: immutable, dated actual-quantity evidence tied to the exact definition revision.
- `ScopeQuantityRepository`: tenant-explicit application port for creating definitions, looking them up by tenant, appending measurements, and reading dated history.
- `CreateScopeQuantityDefinition`: enforces sequential revisions for a scope/code in the supplied tenant. The database uniqueness constraint remains the final concurrency guard.
- `RecordQuantityMeasurement`: checks that the referenced definition exists inside the measurement's tenant before recording it.
- PostgreSQL adapter with tenant-qualified reads and tenant-qualified foreign keys.
- Migration `V3__tenant_scope_quantities.sql` with immutable-row triggers, history indexes, non-negative numeric constraints, unique scope revisions, and tenant-scoped idempotency.
- Domain tests for normalization, quantity bounds, precision, revision, and dated measurement invariants.

## Data and integrity model

- Tenant identity is part of every quantity primary key and every query predicate.
- A measurement's composite foreign key (`tenant_id, definition_id`) prevents a measurement from referencing a definition belonging to another tenant.
- Definition revisions are append-only. A change to scope, planned amount, unit, or description creates a new definition ID and next revision; historical measurements continue to reference the revision they measured.
- Measurement writes are append-only. Replaying the same tenant/idempotency key with the same business payload is a no-op; reusing it with a different payload is rejected.
- Quantity values use `NUMERIC(20,6)` in PostgreSQL and must be non-negative. Units are neutral codes; conversion policies are intentionally outside this foundation.

## Security boundary

Explicit tenant IDs and tenant-scoped persistence are necessary isolation controls, but they are **not authentication or authorization**. HTTP/RPC endpoints must derive the tenant from trusted identity/authorization context rather than accept an arbitrary tenant ID from an untrusted request. Row-level security can be added as defense in depth where the deployment's connection/session model supports it.

## Intentionally excluded

- Currency, rate cards, tax, markup, valuation, claims, retention, payment certification, and construction-specific BoQ rules.
- Automatic unit conversion: converting quantities requires explicit dimensional compatibility and rounding policy.
- Corrections that overwrite history. A future correction workflow should append a superseding/correction record with actor, reason, and reference while retaining the original evidence.
- Cross-aggregate transactions between schedule edits, quantity definitions, and quantity measurements.

## Validation status

Domain tests are included but were not executed in this remote editing session. The Maven reactor and PostgreSQL/Flyway integration tests must run in CI before the branch is marked production-ready. Review the migration against the repository's configured Flyway locations and existing database constraints before deployment.
