# Neutral Planning — Milestone 15: Historical WBS Progress Snapshots

## Scope

Milestone 15 adds immutable, tenant-scoped storage for WBS progress calculations. It builds on weighted leaf-level calculation without adding construction-specific concepts to the planning core.

## Snapshot evidence

Each snapshot stores tenant, WBS structure and target node, schedule plan and caller-supplied plan revision, reporting date, record timestamp, weighting-policy version, calculated percentage, total weight, included activity count, and each included activity's actual percentage and explicit weight.

The domain object verifies that detail rows reproduce the stored total weight and rounded weighted completion. A mismatch between the result and recorded inputs is rejected before persistence.

## Persistence contract

The application port is tenant-scoped for reads and appends snapshots atomically with activity inputs. PostgreSQL stores a header and normalized detail rows, uses foreign keys to preserve WBS/plan/activity references, indexes history queries, and rejects updates/deletes at the database layer.

History queries are bounded to tenant, structure, node and date range and ordered newest reporting date first. The caller remains responsible for authorization and supplying the trusted tenant ID and plan revision. The snapshot records the supplied revision; it does not claim to reconstruct a schedule revision unless the caller's plan repository provides it.

## Constraints

- Weights are explicit and comparable; no cost, quantity or effort inference is made.
- Snapshot identity is independent of reporting date, allowing multiple recalculations to be retained without overwriting evidence.
- This milestone does not infer authorization from tenant IDs and does not change the existing plan-loading security boundary.

## Verification

Changes are authored, but CI/build results must be checked after the PR is opened. No test pass is claimed here.
