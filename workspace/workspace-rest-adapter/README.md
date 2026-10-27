# Workspace REST adapter

This is an optional HTTP transport for the workspace application capability. The workspace domain and application modules remain usable without Quarkus or HTTP.

## Routes

Base path: `/api/v1`

| Method | Path | Purpose |
| --- | --- | --- |
| POST | `/workspaces` | Create a workspace |
| POST | `/workspaces/{workspaceId}/spaces` | Create a space |
| POST | `/workspaces/{workspaceId}/spaces/{spaceId}/pages` | Create a page with typed blocks |
| GET | `/pages/{pageId}` | Read a page |
| PUT | `/pages/{pageId}` | Update a page using `expectedRevision` |
| GET | `/pages/{pageId}/history` | Read immutable revision history |
| POST | `/pages/{pageId}/history/restore` | Restore a historical snapshot as a new revision, guarded by `expectedCurrentRevision` |
| GET | `/spaces/{spaceId}/pages?parentPageId={id}` | List direct children; omit `parentPageId` to list root pages |
| POST | `/workspaces/{workspaceId}/templates` | Create a reusable page template |
| GET | `/workspaces/{workspaceId}/templates` | List templates available in a workspace |
| POST | `/templates/{templateId}/pages` | Create a new page from a template |
| GET | `/workspaces/{workspaceId}/search?q={query}&spaceId={id}&limit={n}` | Search readable pages; `spaceId` is optional and limit is 1–100 (default 20) |
| POST | `/pages/{sourcePageId}/links` | Link a source page to a readable target page |
| GET | `/pages/{pageId}/links` | List outgoing page links |
| GET | `/pages/{pageId}/backlinks` | List readable pages linking to this page |
| DELETE | `/links/{linkId}` | Remove a page link |

Successful create operations return `201 Created` with a resource location; link deletion returns `204 No Content`. Revision conflicts preserve the application API's `409` response. Application validation and access errors are mapped to HTTP responses without exposing server-side exception details. Search and link visibility rules are enforced by the application services, not by transport filters.

## Authentication and tenant isolation

The adapter intentionally does **not** accept tenant or actor IDs in request bodies. A host runtime must provide CDI-managed `WorkspaceApi` and `WorkspaceContentApi` instances configured with an `IdentityProvider` that obtains tenant and actor identifiers from trusted, authenticated request context. The resources are not a standalone deployable server and do not invent an authentication strategy or a default tenant. A host must also compose the workspace services, persistence adapters, and authorization policy.

## Composition

Add `numou-workspace-rest-adapter` only to runtimes that need HTTP access. Consumers that use workspace in-process may depend only on `numou-workspace-application`; the domain module has no HTTP dependency. The content endpoints reuse the existing transport-neutral template, search, and page-link use cases rather than introducing a second implementation of those rules.
