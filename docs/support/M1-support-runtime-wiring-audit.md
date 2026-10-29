# Support runtime wiring audit

**Status:** Audit finding; production wiring is not complete.

**Scope reviewed:** the Support module tree on `main`, including `support-api`, `support-application`, and `support-domain`. This is a source-tree audit, not a successful runtime boot or test result.

## Findings

1. **The REST resource has an unresolved composition requirement.** `TicketResource` constructor-injects `CreatePortalTicketHandler`, but the Support modules do not currently declare a CDI bean or producer for that handler. The application handlers are ordinary final classes constructed through constructors.
2. **Authenticated actor resolution is only a port.** `SupportActorProvider.currentActor()` is declared, but no Support implementation is present in the reviewed module tree. The production implementation must derive tenant, actor type, user ID, and participant ID from the application's verified authentication/session context. It must not trust request-body fields or arbitrary headers as identity.
3. **Ticket persistence is only a port.** `TicketRepository` is declared, but the Support modules do not include a persistence adapter or schema/migration module. The implementation must consistently scope reads and writes by tenant, preserve aggregate invariants, and support the transaction/event publication semantics used by handlers.
4. **The access policy is not a runtime binding.** `RepositoryTicketAccessPolicy` is a plain application class. It is not CDI-discovered by annotation and cannot be used by injection without an explicit composition decision. It must receive the real tenant-safe repository and be paired with the authenticated actor provider.
5. **Portal read is not exposed by the REST resource.** `GetPortalTicketHandler` has handler-level authorization checks, but `TicketResource` currently exposes only `POST /api/v1/support/tickets`; it does not expose the portal ticket read route.
6. **The module boundary does not yet provide a complete runnable adapter.** The Support reactor currently declares domain, application, and API modules only. No Support infrastructure/persistence module or explicit composition module is declared in `support/pom.xml`.

## Required implementation sequence

- [ ] Identify and reuse the canonical Numou authentication/tenant context; document the exact mapping into `ActorContext`.
- [ ] Add a concrete `SupportActorProvider` adapter that obtains identity from trusted request/security context and fails closed when tenant or actor identity is missing or inconsistent.
- [ ] Add the Support persistence adapter and migration(s), following existing Numou persistence conventions rather than introducing a parallel database pattern.
- [ ] Add an explicit composition module or CDI producers for handlers and their dependencies. Keep CDI/runtime dependencies out of the application module unless the repository's architecture specifically requires otherwise.
- [ ] Bind `RepositoryTicketAccessPolicy` to the production repository and authenticated actor provider. Ensure the repository itself is tenant-safe; policy checks must not be the only isolation boundary.
- [ ] Expose portal ticket reads through a DTO-only REST endpoint, preserving the non-disclosing inaccessible/missing-ticket behavior.
- [ ] Add CDI boot tests that prove the resource and all required dependencies resolve; add endpoint tests for anonymous access, requester ownership, partner role matching, staff access, cross-tenant denial, and internal-note non-disclosure.
- [ ] Add persistence integration tests for tenant isolation, transaction rollback, concurrency, and event publication behavior.
- [ ] Run the Support reactor and relevant parent/dependent builds; review CI results before declaring the milestone complete.

## Acceptance criteria

- The Support API starts with no unsatisfied CDI dependencies.
- No customer-controlled field can set tenant, requester, or actor identity.
- A missing/invalid authenticated context is rejected, not silently mapped to a default tenant or actor.
- Every ticket lookup and mutation has tenant isolation at the persistence boundary and authorization at the application boundary.
- Portal responses use explicit response DTOs and never serialize the domain aggregate directly.
- The production wiring and persistence behavior are covered by automated tests and verified CI.

## Non-goals for this audit

This document does not claim to implement authentication, persistence, migrations, CDI wiring, or the portal read endpoint. Those changes depend on locating and reusing the actual Numou runtime and persistence conventions. No placeholder identity provider or in-memory repository should be shipped as a production substitute.
