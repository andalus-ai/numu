# Numou Workspace Web Client

An optional browser client for the workspace REST capability. It is separate from Java domain/application/persistence artifacts and consumes the documented `/api/v1` contract.

## First vertical slice

- List the authenticated actor's accessible workspaces and spaces from the server.
- Restore the last selected workspace and space after a browser reload.
- Create a workspace, a space, and a page through the real API.
- Browse root pages in the selected space and open a page.
- Edit typed page blocks: paragraphs, two heading levels, bulleted/numbered items, quotes, code, callouts, and dividers.\n- Add, convert, reorder, and remove blocks while preserving existing block IDs and extension attributes.\n- Save the whole document with an optimistic `expectedRevision` guard; the server remains authoritative for revision history.
- Inspect page history and restore a prior revision as a new revision.
- Search pages and create pages from templates.
- Show safe network, authorization/not-found, server, and revision-conflict errors.

This is not full Notion parity. Nested blocks, rich inline formatting, drag-and-drop, collaboration, database UI, page-link navigation, uploads, and complete sign-in/session integration remain follow-up work.

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
- For a separate API origin, add a `meta` tag named `numou-api-base` to `index.html` and configure credentialed CORS/session policy at the host. Never embed secrets in static assets.
- Use HTTPS in deployments and a restrictive Content Security Policy.

## Known limits

The client restores the last selected workspace and space from tenant-authorized server listings. It currently selects the first accessible workspace/space when the saved selection is no longer available; a full multi-workspace switcher remains follow-up work.

The editor uses a typed, ordered block array and retains unknown block types and extension attributes when saving. It is not yet a full Notion-style editor: nested blocks, rich inline marks, embeds, drag-and-drop, and autosave are not included. Concurrent writes use the server revision guard; on conflict, the client preserves the local draft and does not overwrite automatically.
