# Workspace UI integration contract

**Status:** Implementation boundary for the first integrated workspace web client  
**Scope:** Notion-style workspace UI consuming the existing workspace capability  
**Related contracts:** [ADR-003](../docs/architecture/ADR-003-reusable-capability-modules.md), [REST adapter](workspace-rest-adapter/README.md)

## Current repository boundary

The workspace capability currently provides domain, application, JDBC persistence, and an optional Quarkus REST adapter. The repository does not currently expose an established workspace frontend package or frontend build convention. Do not embed a UI framework in the domain/application modules or add an isolated mock UI that bypasses the real API.

The first web client should be a separately buildable presentation application, composed with a host runtime that supplies authenticated identity, authorization policy, persistence, and the REST resources. The client is not an authentication authority and must not choose a tenant or actor by sending IDs in request bodies.

## Integration principles

1. **Consume public HTTP contracts only.** The browser calls the versioned `/api/v1` endpoints documented in the REST adapter README. It must not access JDBC tables, persistence entities, application implementation classes, or internal repository APIs.
2. **Authentication belongs to the host.** The host runtime establishes authenticated tenant and actor context and configures `WorkspaceApi.IdentityProvider`. The client may use the host's supported session or OIDC flow, but must not invent a default tenant or trust tenant/actor IDs from local storage, query parameters, or editable form fields.
3. **Treat authorization failures as normal states.** A resource identifier is not proof of access. The UI must handle forbidden/not-found responses without leaking whether an inaccessible resource exists.
4. **Make writes revision-aware.** Page updates use `expectedRevision`; restores use `expectedCurrentRevision`; schema changes use `expectedSchemaVersion`; record and saved-view updates use their documented revision guards. On conflict, preserve the user's draft and offer reload/compare/retry rather than silently overwriting newer content.
5. **Keep server state authoritative.** Autosave may debounce edits, but it must serialize or otherwise coordinate writes for a page, retain the last acknowledged revision, expose save status, and never report a save as successful before the server confirms it.
6. **Use real capabilities, not fake local data.** Navigation, pages, templates, search, backlinks, databases, table views, and board views must be driven by REST responses. Demo fixtures belong only in explicit tests/story fixtures.
7. **Remain independently deployable.** The client build must be optional and must not become a dependency of Java domain, application, persistence, or REST artifacts. A standalone, modular-monolith, or distributed host can serve the same built assets and API contract.
8. **Make API origin configurable.** Support same-origin deployment by default and an explicitly configured API base URL for development or separate deployments. Do not hardcode production hosts or credentials.

## First usable UI slice

The initial end-to-end slice should be narrow enough to verify against a real host runtime:

- Workspace/space navigation and root/child page listing.
- Create a workspace, space, and page through the existing endpoints.
- Read and edit page title and typed blocks, with explicit loading, empty, validation, permission, network, and conflict states.
- Revision-aware save status and safe conflict recovery.
- Page history and restore, creating a new revision rather than mutating history.
- Search pages and navigate outgoing links/backlinks.
- Template listing and create-page-from-template.
- A database entry point that can display records and saved table/board views through the existing structured-database endpoints.

The client should not claim full collaborative editing until presence, concurrent editing/merge semantics, comments/mentions, and notification contracts exist.

## Security and reliability acceptance criteria

- No tenant or actor identity is accepted from client request DTOs.
- Every request uses the host's authenticated context; no token or session secret is bundled into static assets.
- UI navigation does not imply authorization; the server rechecks access on every operation.
- Revision conflicts preserve unsaved user edits and provide a deterministic recovery path.
- Autosave handles duplicate submission and network retry without silently creating duplicate pages or losing edits.
- Search, backlinks, templates, and database views display empty and access-denied states without exposing inaccessible records.
- API failures are rendered as actionable, non-sensitive messages; server exception details are not surfaced.
- Tests cover API client serialization, error mapping, revision conflicts, navigation state, and at least one browser-level create/read/update workflow against a composed host.
- CI builds/tests the frontend independently and runs a contract/integration check against the workspace REST adapter. Java capability CI must remain independent of frontend tooling.

## Delivery sequence

1. Verify the host runtime and trusted identity integration used by the target deployment.
2. Select the frontend framework based on repository/runtime constraints and operational ownership; record that choice before introducing dependencies.
3. Implement the first page/navigation/editor vertical slice against the actual REST contract.
4. Add history, templates, search/backlinks, then database table/board views.
5. Add collaboration and file UX only after the corresponding server contracts and access policy are ready.

This document defines the integration boundary and acceptance criteria; it does not claim that a browser UI, host runtime, or end-to-end browser tests already exist.

## Page comment anchors

Page comment threads may optionally include an `anchorBlockId` when created. The ID must identify a block in the target page at creation time; tenant and page scope are validated by the application and JDBC adapter. The anchor is a stable block UUID, not a block position, so reordering does not move the discussion to another block.

The database deliberately does not add a foreign key to the current block table: removing a block must not delete or make its discussion history unreadable. Clients should render a thread whose anchor no longer exists in an orphaned/removed-block discussion area while retaining its original anchor ID. Omitting `anchorBlockId` continues to create a page-level thread.