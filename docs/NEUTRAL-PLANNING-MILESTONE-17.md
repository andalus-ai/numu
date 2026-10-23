# Neutral Planning — Milestone 17: Approved Progress Baselines

## Goal
Provide reusable, tenant-scoped baseline management for WBS progress reporting. The capability is neutral to construction, software delivery, operations, and other work domains.

## Included
- Immutable named baselines anchored to a persisted progress snapshot.
- Baseline creation requires the latest recorded snapshot decision to be `APPROVED`; a later rejection supersedes an earlier approval.
- Scope validation ensures the baseline structure and node match the source snapshot.
- Effective date cannot precede the snapshot's reporting date.
- PostgreSQL persistence, tenant-scoped lookup/listing, unique tenant baseline codes, and database-level immutability enforcement.
- Comparison of a later snapshot against the baseline source, expressed as percentage-point variance.

## Semantics and security
- A baseline references immutable evidence rather than duplicating or modifying snapshot contributions.
- Variance is current completion minus baseline completion in percentage points; positive values mean progress above baseline, negative values mean behind baseline.
- Authorization and actor identity must be established by the calling application. The creator ID is recorded but is not a substitute for authorization.
- A baseline can only be created when its source snapshot's latest decision is approval. Decision ordering follows the existing repository's decided-at/id ordering.
- The schema uses tenant-scoped keys and foreign keys. Database triggers prevent baseline mutation.

## Validation
Tests should run for planning domain, application, and PostgreSQL adapter modules. This remote GitHub editing session has not executed Maven or database integration tests.
