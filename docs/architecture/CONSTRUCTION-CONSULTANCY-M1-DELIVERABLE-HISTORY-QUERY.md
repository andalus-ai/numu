# Consultancy deliverable history query

## Boundary

The application query accepts a trusted tenant/actor context, deliverable identifier and the organization the actor is acting for. The organization identifier is not treated as proof of access: it must match the persisted client or consultant organization on the deliverable and pass the authorization port.

## Behavior

- Resolve the deliverable using tenant-scoped persistence before checking the acting organization.
- Return the same not-found outcome for a missing deliverable and an organization unrelated to that deliverable, avoiding a resource-existence disclosure.
- Require the new VIEW_DELIVERABLE_HISTORY permission for the authenticated actor and the persisted party organization.
- Return history in database-generated append order from the immutable PostgreSQL history table.
- Keep history access in the application boundary; no HTTP endpoint or runtime composition is added here.

## Operational notes

The query uses the existing transaction-bound consultancy operations contract so the PostgreSQL adapter can keep tenant scoping explicit. Deployments must map authorization denial and not-found to stable API errors and ensure the history read is included in audit/monitoring policy where required.

This query does not yet attach signed files. Evidence references should be added through the public document capability contract in a separate milestone; do not store binary documents or private document persistence identifiers in this history model.
