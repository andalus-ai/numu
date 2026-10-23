# Neutral Planning — Milestone 16: Snapshot Governance and Variance

## Scope

Milestone 16 adds optional governance and comparison capabilities for immutable WBS progress snapshots. It is generic project-control functionality and does not assume construction-specific cost or quantity semantics.

## Snapshot review

Approval/rejection is recorded as a separate append-only decision event with tenant, snapshot, actor, outcome, reason and timestamp. The snapshot itself is never changed. A later decision may supersede the current decision while the full history remains available; consumers should use the latest decision for current status and retain the complete history for audit.

The application boundary verifies that the target snapshot exists in the same tenant before appending the decision. Authentication, authorization, actor identity and any separation-of-duties policy remain responsibilities of the calling application.

## Variance

The comparison use case accepts two snapshot IDs and a trusted tenant ID. It rejects missing/cross-tenant snapshots, mismatched WBS scope, identical snapshots and a current reporting date earlier than the baseline. Variance is expressed as percentage points: current completion minus baseline completion, not relative percent change.

## Persistence

Flyway V9 adds a normalized decision event table, a tenant-scoped foreign key to the immutable snapshot, a history index, and a database trigger preventing updates/deletions. The existing snapshot history remains unchanged.

## Verification

Domain and application tests should be run in CI before merge. Changes are authored; no passing test result is claimed.
