# Trusted tenant context for BOQ revision operations

## Contract

`BoqRevisionTenantContext` resolves the tenant from authenticated server-side context. The three BOQ revision handlers require both `BoqRevisionActorContext` and `BoqRevisionTenantContext`; neither actor nor tenant is accepted from command records. Empty tenant values fail closed.

`BoqRevisionWorkflow` carries `tenantId` explicitly on capture, submission, and approval. Adapters must apply the same tenant to live BOQ reads and updates, item queries, snapshot storage, revision allocation, idempotency reservations/results, authorization checks, and outbox events.

## Integration requirements

- Bind the context implementation to the host application's verified authentication/tenant resolver. Do not implement it by reading an untrusted request header unless that header is validated against authenticated membership.
- Establish tenant context before invoking a command handler. Missing identity must fail closed.
- Use one canonical tenant identifier format across application and database layers. Do not silently trim, case-fold, or remap tenant IDs in only one adapter.
- Authorization must still verify that the actor is permitted to operate on the requested BOQ within that tenant; context resolution is not resource authorization.
- Keep all database reads and writes on the transaction-bound connection. Tenant context does not itself provide transactionality.
- Do not expose whether a BOQ exists in another tenant; cross-tenant resources should have the same external not-found behavior as unknown resources.

## Compatibility

This is a source-breaking handler/workflow API change. Update host dependency injection and every `BoqRevisionWorkflow` implementation/caller together. This starter does not include the host application's authentication provider, so production context binding and end-to-end tests remain required.

## Static contract check

Run `scripts/check-boq-trusted-context-contract.sh`. It verifies the API wiring only; it does not prove runtime tenant isolation.
