# Numou Workspace Web Client

An optional browser client for the workspace REST capability. It is separate from Java domain/application/persistence artifacts and consumes the documented `/api/v1` contract.

## First vertical slice

- List the authenticated actor's accessible workspaces and spaces from the server.
- Restore the last selected workspace and space after a browser reload.
- Create a workspace, a space, and a page through the real API.
- Browse root pages in the selected space and open a page.
- Edit page title and typed content blocks (headings, paragraphs, lists, to-dos, quotes, callouts, code, tables, toggles, and dividers) with an optimistic `expectedRevision` guard.
- Add, remove, and reorder blocks while preserving stable block IDs and opaque file/domain-reference blocks.
- Create page-level discussion threads, append replies, and resolve/reopen threads with optimistic revision guards.\n- Show active collaborators on the open page using short-lived, per-tab presence leases; presence pauses while the tab is hidden and expires automatically.
- Type `@handle` in a comment to find active tenant members; the host's Notification integration receives idempotent mention commands.
- Inspect page history and restore a prior revision as a new revision.
- Search pages and create pages from templates.
- Show safe network, authorization/not-found, server, and revision-conflict errors.

This is not full Notion parity. Collaboration, database UI, cross-page linking, uploads, and complete sign-in/session integration remain follow-up work.

## Run locally

Requires Node.js 20 or newer; no package installation is needed.

```sh
cd workspace/web-client
npm test
npm run serve
```

Open `http://127.0.0.1:4173`. The preview server serves static assets only. The API must be reachable at `/api/v1` on the same origin, normally through a reverse proxy or composed host runtime. The preview server does not proxy API requests, authenticate users, configure CORS, or create a production runtime.

## Host security

- Serve files from the host origin or configure a reverse proxy so `/api/v1` reaches the REST adapter.
- The host must establish trusted tenant/actor context and configure the workspace `IdentityProvider`. The client deliberately does not accept tenant/actor IDs and does not store bearer tokens.
- The host enforces authentication and authorization for every request; hiding a button or possessing an ID is not authorization.
- Mention suggestions require a host-provided tenant-scoped `WorkspaceMentionDirectory` and `WorkspaceMentionService`. The host implements `WorkspaceMentionNotificationPort` using the shared Notification capability; Workspace does not depend on Notification or its push providers.
- For a separate API origin, add a `meta` tag named `numou-api-base` to `index.html` and configure credentialed CORS/session policy at the host. Never embed secrets in static assets.
- Use HTTPS in deployments and a restrictive Content Security Policy.

## Known limits

The client restores the last selected workspace and space from tenant-authorized server listings and provides explicit workspace/space selectors. It selects the first accessible workspace/space when the saved selection is no longer available.

The editor supports the block types currently defined by the workspace domain. File attachments and domain-reference blocks remain visible and preserved as opaque records; uploading files or editing domain-owned references belongs to their respective capabilities. Nested block trees, durable cross-service notification outbox delivery, collaborative cursors, push-based presence fan-out, and richer table-cell semantics remain follow-up work. Presence is advisory only and does not lock pages or grant edit access. Concurrent writes use the server revision guard; on conflict, the client preserves the local draft and does not overwrite automatically.
