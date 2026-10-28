# Numou Workspace Web Client

An optional browser client for the workspace REST capability. It is separate from Java domain/application/persistence artifacts and consumes the documented `/api/v1` contract.

## First vertical slice

- Create a workspace, a space, and a page through the real API.
- Browse root pages in the selected space and open a page.
- Edit page title and paragraph text with an optimistic `expectedRevision` guard.
- Inspect page history and restore a prior revision as a new revision.
- Search pages and create pages from templates.
- Show safe network, authorization/not-found, server, and revision-conflict errors.

This is not full Notion parity. Rich block editing, collaboration, database UI, page-link navigation, uploads, and complete sign-in/session integration remain follow-up work.

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

The REST adapter does not currently expose list-workspaces/list-spaces endpoints, so the first slice keeps the created workspace and space in memory for this browser session. A later backend milestone should add authorized navigation read models before reload and multi-workspace navigation are considered complete.

Page editing currently presents paragraph blocks as plain text. It is not a full structured block editor. Concurrent writes use the server revision guard; on conflict, the client preserves the local draft and does not overwrite automatically.
