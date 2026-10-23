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

## M2 security follow-up — page-link authorization

Page-link creation requires update permission on the source page and read permission on the target page. Link removal now applies the same source-update and target-read checks before mutating persisted state, and verifies that both endpoints still exist within the supplied tenant. This prevents a stale or malformed link from being removed without validating its target scope and prevents callers from using link removal to act on a target they cannot read.

The application-level regression test proves that a denied target read leaves the link intact, while an authorized removal succeeds. This is unit-level coverage only; it does not replace database integration tests, concrete host access-policy tests, or full-reactor CI.

## Verification status

This repository change was authored remotely; no Maven build or tests were executed in this session. Run the Workspace application tests and full reactor before treating the milestone as verified.
