# Milestone 27 — PostgreSQL atomic schedule-change execution

This milestone implements the M26 execution-store contract in the PostgreSQL adapter.

## Transaction protocol
1. Find an existing receipt by execution ID for idempotent replay.
2. Lock the plan row, then validate the explicit tenant/structure binding in schedule_plan_scope.
3. If the stored revision differs from the approved base revision, persist a terminal CONFLICT receipt without changing the schedule.
4. Otherwise compare-and-set the plan revision, replace working activities and dependencies, and persist the APPLIED receipt in one transaction.
5. Commit the plan and receipt together; SQL/FK failures roll back both.

A SHA-256 fingerprint includes the request, approval/impact evidence, initiator, target metadata, activities, dependencies, and baselines. Reuse of an execution ID with a different fingerprint is rejected.

## Tenant boundary
The adapter fails closed unless a matching (plan_id, tenant_id, structure_id) row exists in schedule_plan_scope. Provision this mapping from the authoritative tenant/structure onboarding path; ownership is never inferred from request data. Existing plans require explicit bindings before this operation can run.

## Limits and verification
- Target revision must equal base revision + 1.
- Working schedule rows are updated; append-only baselines are not rewritten.
- A retry after an uncertain client outcome can recover the committed receipt by execution ID.
- Maven and GitHub Actions were not executed in this remote editing workflow. Do not treat compilation or integration tests as verified until CI reports success.
