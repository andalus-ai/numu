# Support runtime wiring audit

**Status:** Partially implemented; production composition and persistence remain incomplete.

**Scope reviewed:** the Support module tree on `main`, including `support-api`, `support-application`, and `support-adapter`. This is a source-tree audit, not a successful runtime boot or test result.

## Implemented capabilities

1. **Authenticated actor provider exists.** `DefaultSupportActorProvider` uses Identity's current principal, Security's verified tenant/principal context, active tenant membership, scoped permissions, and tenant-scoped issuer/subject-to-Party association. It does not trust request-body identity or JWT groups.
2. **Party-to-Participant resolution exists.** `DefaultSupportPartyParticipantResolver` reads tenant-scoped Ecosystem Party–Participant associations and maps only CUSTOMER/PARTNER roles. Missing, future-effective, unsupported, or ambiguous mappings fail closed.
3. **Portal ticket read handler exists.** `GetPortalTicketHandler` checks actor access and tenant equality before returning a role-filtered `TicketResponse`.
4. **Portal read route is being exposed by this milestone.** `GET /api/v1/support/tickets/{ticketId}` delegates to the authorized handler and returns the DTO, never the Ticket aggregate.
5. **Portal ticket creation supports both requester roles in this milestone.** The handler derives CUSTOMER/PARTNER requester role and ParticipantId from the verified actor context; it rejects staff actors and missing Participant identity.

## Remaining runtime blockers

1. **Handler composition is unresolved.** `TicketResource` constructor-injects application handlers, but the Support application handlers are ordinary classes and no production CDI producer/composition root for all handler dependencies has been confirmed. Adding a route does not by itself make the resource bootable.
2. **Ticket persistence is still only a port.** `TicketRepository` is declared, but the Support modules do not include a production persistence adapter or schema/migration module. The implementation must consistently scope reads and writes by tenant, preserve aggregate invariants, and support transaction/event publication semantics.
3. **The access policy needs production binding.** `RepositoryTicketAccessPolicy` is a plain application class. It must receive the production tenant-safe repository and be paired with the authenticated actor provider. The repository itself must enforce tenant isolation; policy checks must not be the only isolation boundary.
4. **The module boundary lacks a runnable persistence/composition adapter.** The Support reactor currently declares domain, application, API, and security/Ecosystem adapter modules, but no Support ticket persistence module or explicit application composition module.
5. **Runtime and endpoint verification is outstanding.** Add CDI boot tests proving the resource and all dependencies resolve; endpoint tests for anonymous access, requester ownership, partner role matching, staff access, cross-tenant denial, invalid IDs, and internal-note non-disclosure.

## Required implementation sequence

- [x] Identify and reuse the canonical Numou authentication/tenant context; document the exact mapping into `ActorContext`.
- [x] Add a concrete authenticated `SupportActorProvider` adapter that obtains identity from trusted request/security context and fails closed when tenant or actor identity is missing or inconsistent.
- [x] Add a concrete tenant-scoped Party-to-Participant resolver using Ecosystem's authoritative relationship records.
- [ ] Add the Support persistence adapter and migration(s), following existing Numou persistence conventions rather than introducing a parallel database pattern.
- [ ] Add an explicit composition module or CDI producers for handlers and their dependencies. Keep CDI/runtime dependencies out of the application module unless the repository's architecture specifically requires otherwise.
- [ ] Bind `RepositoryTicketAccessPolicy` to the production repository and authenticated actor provider. Ensure the repository itself is tenant-safe.
- [x] Expose portal ticket reads through a DTO-only REST endpoint, preserving the non-disclosing inaccessible/missing-ticket behavior.
- [ ] Add CDI boot and endpoint tests for the complete production path.
- [ ] Add persistence integration tests for tenant isolation, transaction rollback, concurrency, and event publication behavior.
- [ ] Run the Support reactor and relevant parent/dependent builds; review CI results before declaring the milestone complete.

## Acceptance criteria

- The Support API starts with no unsatisfied CDI dependencies.
- No customer-controlled field can set tenant, requester, or actor identity.
- A missing/invalid authenticated context is rejected, not silently mapped to a default tenant or actor.
- Every ticket lookup and mutation has tenant isolation at the persistence boundary and authorization at the application boundary.
- Portal responses use explicit response DTOs and never serialize the domain aggregate directly.
- Customer and partner portal actors can create and read only their own tenant/requester tickets.
- The production wiring and persistence behavior are covered by automated tests and verified CI.

## Non-goals for this audit

This document does not claim that Support ticket persistence, schema migrations, CDI composition, or a successful runtime boot have been implemented. No placeholder identity provider or in-memory repository should be shipped as a production substitute.
