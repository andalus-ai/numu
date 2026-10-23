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

A JDBC/PostgreSQL persistence adapter is included in the follow-up persistence increment. It uses tenant-scoped queries, database uniqueness/FK constraints, and a transaction for page + current blocks + immutable revision + revision blocks. The host must configure the `DataSource` and migration runner. The standalone adapter deliberately does not supply an authentication provider or infer authorization from tenant IDs.
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

Unit tests cover template block identity/position normalization, invalid template data, and typed block metadata. These tests have been authored but have not been executed in this remote-editing session. Before merge, verify root Maven reactor registration, run all workspace module tests, apply migrations V1–V4 to a clean PostgreSQL database, and add or run persistence integration tests for template round trips, normalized-name conflicts, tenant isolation, and rollback on block insert failure.

## M2 increment — tenant-scoped full-text search

The application exposes a search repository port scoped to a tenant and workspace, with an optional space filter. The JDBC adapter uses PostgreSQL full-text search over page titles and block text, with GIN indexes introduced by migration V4. The application service validates query length and result limits, checks workspace/space read access, and filters each candidate through page-level read authorization before returning results. Search hits are projections rather than full page payloads.

The search contract intentionally does not expose cross-workspace search, semantic/AI search, snippets, or a frontend editor. Search integration tests against PostgreSQL are still required to verify query behavior and index usage.

## M2 increment — transport-neutral content API

The base `WorkspaceApi` remains stable for workspace, space, page, and revision operations. `WorkspaceContentApi` adds a separate transport-neutral boundary for template creation/listing, page creation from a template, page search, outgoing links, backlinks, and link removal. This keeps newer capabilities available to HTTP, RPC, or in-process hosts without making the application module depend on a web framework.

Both API boundaries accept an identity provider supplied by the host and derive tenant/actor IDs from that trusted context; request records do not contain caller-controlled identity fields. The content API returns bounded projections rather than persistence objects, and maps invalid requests, denied access, and revision conflicts to the same API exception contract as the base API. Host adapters still need to map these exceptions to their transport's response format and provide real authentication, access-policy, audit, and database adapters.

This is an application/API boundary, not a concrete HTTP server. Route registration, authentication middleware, rate limiting, OpenAPI generation, and HTTP-level integration tests remain host-adapter responsibilities.

## PostgreSQL integration verification

The JDBC adapter includes opt-in integration tests that apply workspace migrations V1–V4 to an isolated temporary schema and exercise template persistence, normalized-name uniqueness, metadata round trips, tenant-scoped reads, page revisions, links/backlinks, and full-text search. They are discovered by Maven Surefire but skip when no disposable test database is configured.

Configure `NUMOU_WORKSPACE_TEST_JDBC_URL` with a PostgreSQL test database URL. Optionally set `NUMOU_WORKSPACE_TEST_DB_USER` and `NUMOU_WORKSPACE_TEST_DB_PASSWORD`. The database role must be allowed to create and drop schemas. Each test creates a uniquely named schema and drops it afterward; use a dedicated disposable database, never a production database. Run with `mvn -pl workspace/workspace-persistence-jdbc -am test` from the repository root. The PostgreSQL driver is test-scoped and does not become a runtime dependency of Workspace consumers.

These tests do not replace full-reactor CI or dedicated performance checks for search indexes. Verify the test profile in the project's actual CI environment before treating database compatibility as established.

## Atomic audited mutations — reliability increment

The application defines an optional `AuditedMutationRepository` capability for adapters that can persist a resource mutation and its audit row in the same database transaction. The JDBC adapter implements this for workspace creation, space creation, page creation/update (including current blocks and immutable revision snapshots), template creation (including template blocks), and page-link creation/removal. If the audit insert fails, the resource mutation rolls back; stale optimistic page updates do not emit audit rows.

Application services retain compatibility with simple/in-memory adapters by using the original repository and audit ports when the combined capability is absent. Such adapters do **not** provide atomic resource/audit consistency and should be used only where that limitation is acceptable. Production adapters should implement `AuditedMutationRepository` and test audit-write failure rollback. Audit delivery outside the database transaction is not a substitute for this contract.

The PostgreSQL integration suite includes a failure-injection test that violates the audit action length constraint and verifies both the page row and revision history remain absent. This test is opt-in and must be run against a disposable PostgreSQL database using the documented `NUMOU_WORKSPACE_TEST_JDBC_URL` configuration. No Maven or PostgreSQL test execution was performed during this remote-editing session.

### Atomic-write verification follow-up

The PostgreSQL integration suite now covers failure injection for workspace, space, page-template, page create/update, and page-link create/delete operations. Each case checks that a failed audit insert leaves the resource state unchanged and does not create an audit row. An additional optimistic-concurrency case verifies that a stale page update does not write a new revision or audit event.

These tests are opt-in and were added as regression coverage; they are not evidence of a passing build until executed against a disposable PostgreSQL instance. Constraint failures and stale compare-and-set outcomes have different contracts: storage constraint exceptions fail the operation, while a stale revision returns `false` without an audit event.

## M2 security follow-up — page-link authorization

Page-link creation requires update permission on the source page and read permission on the target page. Link removal now applies the same source-update and target-read checks before mutating persisted state, and verifies that both endpoints still exist within the supplied tenant. This prevents a stale or malformed link from being removed without validating its target scope and prevents callers from using link removal to act on a target they cannot read.

The application-level regression test proves that a denied target read leaves the link intact, while an authorized removal succeeds. This is unit-level coverage only; it does not replace database integration tests, concrete host access-policy tests, or full-reactor CI.


## M2 security regression coverage — tenant scope and visibility

Application-level tests now exercise the authorization boundaries for search and page links:
- Search passes the exact tenant/workspace/space scope to its repository, clamps oversized limits, filters unreadable page hits, and does not invoke the search repository when workspace authorization or space-scope validation fails.
- Search rejects blank, oversized, and invalid-limit requests before repository access.
- Page-link lookup and deletion are tenant-scoped; denied target visibility prevents link removal.
- Outgoing-link listing fails when a linked target is unreadable, and backlinks omit source pages the actor cannot read.

These tests validate application policy orchestration with test doubles. They do not prove that every JDBC query or host access policy is correct; the PostgreSQL integration suite and full Maven reactor still need to run in CI.
