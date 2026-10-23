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

## Production gates still required\n\nA JDBC/PostgreSQL persistence adapter is included in the follow-up persistence increment. It uses tenant-scoped queries, database uniqueness/FK constraints, and a transaction for page + current blocks + immutable revision + revision blocks. The host must configure the `DataSource` and migration runner. The standalone adapter deliberately does not supply an authentication provider or infer authorization from tenant IDs.
- Register workspace aggregator in the root Maven reactor.
- Add persistence adapters and migrations with tenant constraints, uniqueness, optimistic locking, and transactional page/revision/audit writes.
- Add transport/API endpoints and derive identity from authenticated context.
- Add concrete access-policy and file-storage adapters.
- Run full Maven reactor and PostgreSQL integration suite. Remote repository editing does not execute builds or tests.


## M2 increment — page links and backlinks

The optional page-link capability stores directed links between workspace pages. Backlinks are queried from persisted incoming links; they are not inferred from page text and do not require a rich-text editor. Link creation requires update permission on the source and read permission on the target. Outgoing-link listing and backlink listing require read access to the requested page, and backlink results are filtered through source-page read permissions to avoid disclosing inaccessible pages. A PostgreSQL migration enforces tenant-scoped foreign keys, unique directed links, and a no-self-link constraint.

This increment does not yet parse inline links, provide a UI/editor, or add full-text search. Those are later M2/M3 concerns.


## M2 increment — reusable page templates

Page templates are tenant-owned blueprints scoped to a workspace. The application service checks workspace-level access before creating or listing templates, and page creation from a template also goes through the normal page creation use case so space permissions, attachment authorization, initial revision creation, and normal page invariants remain in force. Instantiation gives every block a fresh ID and normalizes block positions; it does not share mutable page identity with the template.

The application boundary exposes a `PageTemplateRepository` port, with a JDBC adapter and PostgreSQL migration for tenant-scoped template and block storage. The database enforces normalized-name uniqueness within a workspace and cascades template-block deletion. Template records and blocks are inserted in one JDBC transaction. The adapter and migration still require execution against PostgreSQL and full reactor verification before this capability is considered production-ready.

## M2 increment — richer content block contracts

The generic block vocabulary now includes table, callout, toggle, and checklist-item primitives in addition to paragraphs, headings, lists, quotes, code, files, dividers, and opaque domain references. Callout tones are restricted to a small supported set, and checklist checked-state metadata accepts only boolean text values. Table cell/column serialization and rendering remain editor-adapter responsibilities; the domain does not assume a particular frontend or JSON library. Attributes remain inert metadata and must never be evaluated as code.

## Current verification status

Unit tests cover template block identity/position normalization, invalid template data, and typed block metadata. These tests have been authored but have not been executed in this remote-editing session. Before merge, verify root Maven reactor registration, run all workspace module tests, apply migrations V1–V3 to a clean PostgreSQL database, and add or run persistence integration tests for template round trips, normalized-name conflicts, tenant isolation, and rollback on block insert failure.