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

Successful create operations return `201 Created` with a resource location. Revision conflicts preserve the application API's `409` response. Application validation and access errors are mapped to HTTP responses without exposing server-side exception details.

## Authentication and tenant isolation

The adapter intentionally does **not** accept tenant or actor IDs in request bodies. A host runtime must provide a CDI-managed `WorkspaceApi` configured with an `IdentityProvider` that obtains tenant and actor identifiers from trusted, authenticated request context. The resource is not a standalone deployable server and does not invent an authentication strategy or a default tenant. A host must also compose the workspace services, persistence adapters, and authorization policy.

## Composition

Add `numou-workspace-rest-adapter` only to runtimes that need HTTP access. Consumers that use workspace in-process may depend only on `numou-workspace-application`; the domain module has no HTTP dependency.
