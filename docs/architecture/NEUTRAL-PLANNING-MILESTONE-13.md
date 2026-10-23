# Neutral Planning — Milestone 13: WBS-to-Schedule Integration

## Objective

Associate schedule activities with stable nodes in an immutable neutral WBS without coupling the WBS to schedule internals or a particular delivery domain.

## Included

- Immutable `WorkBreakdownActivityLink` domain value.
- Tenant-explicit application repository port and JDBC adapter.
- A use case that validates the supplied activity belongs to the supplied schedule plan, resolves the WBS under the supplied tenant, and verifies the node exists before saving a link.
- PostgreSQL migration with WBS node and schedule activity foreign keys, uniqueness per structure/plan/activity, and lookup indexes.
- Use-case tests for valid links, unknown activities, and tenant-scoped WBS lookup.

## Semantics

An activity can be linked to one node per WBS structure. It may be linked to different WBS snapshots when an application explicitly models multiple structures. Link rows are not silently reassigned; a changed mapping should be represented as a new structure/link lifecycle or a future explicit revision command.

The database FK verifies the schedule activity exists, while the application use case verifies that it belongs to the supplied in-memory plan. The host must load that plan through an authorization-aware boundary and derive the tenant ID from trusted authentication. The current schedule repository does not itself provide tenant-scoped lookup, so this use case intentionally does not load plans by unscoped ID.

## Rollup boundary

This milestone establishes a stable mapping layer. A future milestone can aggregate progress by WBS node using explicit leaf selection and approved weights. Parent and descendant progress must not be combined in the same rollup.

## Validation

Tests are authored but have not been executed in this remote editing session. Run the planning Maven reactor and PostgreSQL/Flyway integration suite before marking ready.
