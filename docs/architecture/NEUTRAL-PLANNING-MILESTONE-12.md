# Neutral Planning — Milestone 12: Tenant-Scoped WBS Persistence

## Objective

Persist reusable work-breakdown structures without coupling the planning core to a construction module or a specific API/runtime.

## Included

- `WorkBreakdownRepository` application port with tenant-explicit create and lookup operations.
- PostgreSQL adapter that writes the structure header and all nodes in one transaction.
- Composite tenant/structure keys for both header and nodes.
- Foreign keys for structure ownership and parent membership.
- Database-level uniqueness for sibling codes and positions, plus immutable-snapshot triggers.
- A Flyway migration and architecture boundary documentation.

## Consistency model

A saved WBS is an immutable snapshot. Changes should create a new structure ID rather than mutate an existing structure in place. This keeps prior references reproducible and avoids silent historical changes. A future revisioning workflow can explicitly model lineage and approved replacement.

The adapter creates the header and nodes in a single transaction. Any node insert failure rolls back the header and all prior inserts. Lookup always requires both tenant ID and structure ID; callers must still derive tenant identity from trusted authentication and apply authorization in the host application.

## Deliberate boundaries

No construction-only fields, BoQ references, currencies, or quantities are stored in the WBS. Scheduling and domain-specific modules may reference stable WBS node IDs through their own integration boundaries.

## Validation

Tests have not been run in this remote GitHub editing session. Run the planning Maven reactor and PostgreSQL/Flyway integration tests, including a rollback test and a cross-tenant lookup test, before marking ready.
