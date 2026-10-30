# Workspace REST adapter

This is an optional HTTP transport for the workspace application capability. The workspace domain and application modules remain usable without Quarkus or HTTP.

## Routes

Base path: `/api/v1`

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/workspaces` | List workspaces readable by the authenticated actor in the current tenant, ordered by creation time and ID |
| POST | `/workspaces` | Create a workspace |
| GET | `/workspaces/{workspaceId}/spaces` | List spaces in a readable workspace, ordered by creation time and ID |
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

Navigation discovery is tenant-scoped in the repository and resource-authorized in the application service. The workspace list omits resources the actor cannot read; listing spaces first verifies access to the parent workspace. The adapter intentionally does **not** accept tenant or actor IDs in request bodies. A host runtime must provide CDI-managed `WorkspaceApi` and `WorkspaceContentApi` instances configured with an `IdentityProvider` that obtains tenant and actor identifiers from trusted, authenticated request context. The resources are not a standalone deployable server and do not invent an authentication strategy or a default tenant. A host must also compose the workspace services, persistence adapters, and authorization policy.

## Composition

Add `numou-workspace-rest-adapter` only to runtimes that need HTTP access. Consumers that use workspace in-process may depend only on `numou-workspace-application`; the domain module has no HTTP dependency. The content endpoints reuse the existing transport-neutral template, search, and page-link use cases rather than introducing a second implementation of those rules.

## Structured database endpoints

The same optional adapter also exposes the existing structured-database use cases:

| Method | Path | Purpose |
| --- | --- | --- |
| POST | `/workspaces/{workspaceId}/databases` | Create a workspace database |
| POST | `/databases/{databaseId}/tables` | Create a table and typed properties |
| PUT | `/databases/{databaseId}/tables/{tableId}/schema` | Apply compatible schema changes using `expectedSchemaVersion` |
| POST | `/databases/{databaseId}/tables/{tableId}/records` | Create a record |
| PUT | `/records/{recordId}` | Update a record using `expectedRevision` |
| GET | `/databases/{databaseId}/tables/{tableId}/records?limit={n}&offset={n}` | Page through records |
| POST | `/databases/{databaseId}/tables/{tableId}/records/query` | Query records with exact-match filters and bounded pagination |
| POST | `/databases/{databaseId}/tables/{tableId}/views` | Create a personal saved view |
| GET | `/databases/{databaseId}/tables/{tableId}/views` | List the authenticated actor's saved views |
| PUT | `/databases/{databaseId}/views/{viewId}` | Update a saved view using `expectedRevision` |
| GET | `/databases/{databaseId}/views/{viewId}/records?limit={n}&offset={n}` | Execute saved filters and sorts |
| GET | `/databases/{databaseId}/views/{viewId}/board?limit={n}&offset={n}` | Query a saved board view with configured grouping |
| GET | `/databases/{databaseId}/views/{viewId}/validation` | Diagnose saved-view/schema drift |
| DELETE | `/databases/{databaseId}/views/{viewId}` | Delete the actor's personal view |

Record query limits are bounded to 1–200, with default 50 for table and saved-view queries and 100 for board queries. The application service remains authoritative for record and table authorization, schema validation, revision checks, and atomic audited writes. Schema mutation is deliberately limited to the existing safe-evolution service contract; destructive changes still require the explicit migration workflow rather than bypassing it through HTTP.
