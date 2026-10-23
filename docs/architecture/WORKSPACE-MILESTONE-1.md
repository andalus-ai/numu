# Workspace Milestone 1 — Optional workspace foundation

## Objective
Provide a reusable knowledge and collaboration foundation without making any Numou domain module depend on it. Finance, Syirkah, procurement, supply chain, construction, project management, and software delivery remain independently usable. Integrations are optional adapters, never mandatory domain dependencies.

## Included
- Tenant-owned workspace and space domain records.
- Hierarchical pages scoped to a workspace and space.
- Immutable typed blocks for text, files, and opaque domain references.
- Ports for tenant-scoped persistence, access policy, attachment validation, and audit delivery.
- Immutable revision snapshots and optimistic revision checks.
- Application operations for workspace/space/page creation, page read/update, child listing, and history retrieval.
- Tests for core invariants, revision handling, authorization boundary invocation, and file-reference validation.

## Architecture rules
1. Workspace domain has no dependency on finance, Syirkah, procurement, construction, planning, supply chain, identity providers, HTTP frameworks, or storage adapters.
2. Domain modules must not import workspace packages. Optional adapters may represent domain records as opaque DOMAIN_REFERENCE blocks.
3. Tenant IDs are scope inputs, not proof of authorization. The host derives tenant and actor identity from trusted authentication and implements resource-level access policy.
4. Persistence adapters enforce tenant predicates, unique space slugs per tenant/workspace, parent-page scope constraints, and optimistic revision compare-and-set.
5. PageRepository.saveWithRevision atomically persists current page state and its immutable revision.
6. Attachment references must be checked for same-tenant access; this module does not own binary storage.
7. Audit delivery must be durable or fail the command. Production adapters should persist audit/outbox entries transactionally with state changes.

## Deliberate non-goals
Rich editor UI, concurrent editing/CRDT, custom database schemas and views, comments, notifications, full-text/semantic search, AI, automation, public sharing, and persistence/API adapters are later milestones.

## Production gates still required
- Register workspace aggregator in the root Maven reactor.
- Add persistence adapters and migrations with tenant constraints, uniqueness, optimistic locking, and transactional page/revision/audit writes.
- Add transport/API endpoints and derive identity from authenticated context.
- Add concrete access-policy and file-storage adapters.
- Run full Maven reactor and integration suite. Remote repository editing does not execute builds or tests.
