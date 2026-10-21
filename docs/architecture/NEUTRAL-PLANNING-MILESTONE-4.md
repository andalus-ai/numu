# Neutral Planning Milestone 4 — Actual Progress and Scope References

## Purpose

This milestone introduces a neutral progress-evidence model alongside the editable schedule forecast. It is applicable to construction, software delivery, product launches, procurement, and other project-based work.

## Domain behavior

- Schedule activities can carry a typed `WorkItemReference`; planning treats it as an opaque external identity and does not depend on the scope or construction modules.
- `ActivityProgressObservation` records an immutable, attributed percentage at a business `asOfDate`, including capture time, actor, idempotency key, and optional source reference.
- `ActivityProgressHistory` orders observations by business date and capture time. Replaying the same idempotency key with the same business payload is a no-op; reusing the key for different business data is rejected.
- `ProgressAssessment` computes planned-versus-actual percentage-point variance. Its default planned curve is linear across inclusive calendar dates. It is not earned-value management and does not claim resource/cost weighting.

## Persistence contract

Migration V2 adds a progress-observation table with a foreign key to an activity in the same plan, uniqueness for idempotency keys within a plan, an as-of lookup index, and database-enforced append-only history. Scope/WBS references remain typed external references; cross-module foreign keys are intentionally avoided so planning can be deployed independently.

## Integration boundary and follow-up

This milestone provides the domain model and schema, but does **not** yet add JDBC repository methods for loading/writing progress observations, API endpoints, tenant-aware query scoping, or authorization policy. Wire these before exposing the ledger as a production feature. Tenant isolation must be enforced at the application/repository boundary and represented explicitly in persistence keys in a future tenant-aware migration.

The current schedule adapter replaces working activities during saves. Because the new observation table restricts deletion of referenced activities, the adapter's write strategy must be changed to update retained activity rows in place before progress persistence is enabled. This is a deliberate safety barrier against silently orphaning progress history and must be addressed alongside repository integration.

## Validation

Unit tests cover idempotent replay, conflicting idempotency-key reuse, date-based latest-observation selection, percentage bounds, and schedule variance. Run the planning domain module tests and PostgreSQL adapter integration tests in the repository's Maven reactor; tests have not been executed as part of this change.
