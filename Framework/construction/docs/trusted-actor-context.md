# Trusted actor identity for BOQ revision commands

## Change

`CaptureBoqRevisionCommand`, `SubmitBoqRevisionForReviewCommand`, and `ApproveBoqRevisionCommand` no longer carry `actorId`. Each handler now requires `BoqRevisionActorContext` and resolves the actor from it at execution time. This prevents a caller from selecting another user's UUID by changing a request-body field.

## Required host integration

Provide a `BoqRevisionActorContext` implementation backed by the authenticated principal/security identity in the request or job execution context. It must:

- fail closed when there is no authenticated principal;
- derive the stable actor UUID from a trusted principal claim or identity mapping;
- never trust actor IDs or role claims supplied in command payloads;
- be scoped correctly for asynchronous execution so identity cannot leak between requests;
- ensure the resulting actor is the same identity evaluated by `BoqRevisionAuthorization`.

Do not register an allow-all or fixed-user implementation in production. A fixed actor provider is suitable only for isolated tests.

## Tenant context remains a separate requirement

The current `BoqRevisionWorkflow` SPI does not accept a tenant ID directly. The hosting integration must still bind tenant identity from trusted server-side context and ensure that the transaction adapter receives that same tenant. Do not infer tenant from the BOQ UUID alone or add tenant ID back to a client-controlled command.

## Compatibility note

This is a source-breaking change for code constructing the three command records or creating the handlers. Update call sites and dependency-injection wiring in the full Numou checkout. The supplied source bundle has no Maven/Gradle build descriptor and omits some foundation types, so compilation cannot be established from this bundle alone.

## Verification

Run `scripts/check-boq-authorization-contract.sh` for static assertions. Then add host-project tests that authenticate as actor A while submitting a payload formerly claiming actor B, and assert that workflow authorization/audit metadata use actor A. Also test unauthenticated calls fail before any BOQ read or write.
