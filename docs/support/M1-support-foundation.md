# Support foundation milestone (M1)

**Status:** In progress; this document records scope and acceptance criteria, not completion.

## Goal

Harden the existing Support capability before adding more channels, automation, or AI. Preserve the current ticket domain and public API where possible. Do not create a parallel helpdesk model.

## Changes in these slices

- Provide an explicit `customerVisibleComments()` projection that excludes internal agent notes.
- Restrict internal-comment visibility in response mapping to support staff; customer and partner actors both receive public comments only.
- Keep `comments()` as the complete domain history and document that it must not be serialized directly to a customer-facing response.
- Validate the comment event timestamp before mutating the aggregate, so a rejected command cannot leave an in-memory ticket partially changed.
- Route public ticket creation through `CreatePortalTicketHandler`; tenant and requester identity are derived from the authenticated actor context rather than client-controlled request fields.
- Require authenticated actor context and `TicketAccessPolicy.canComment` before adding comments.
- Require support staff (agent/manager/system), tenant ownership, and resolve authorization before status mutations; require support staff and tenant ownership for priority and assignment mutations.
- Require the command author to match the authenticated actor's user ID, reject internal notes from customer/partner actors, and verify the loaded ticket belongs to the actor's tenant before mutation.
- Return a non-specific “Ticket not found” failure for inaccessible tickets to reduce identifier-based information disclosure.

## M1 acceptance checklist

- [x] Customer-visible comment projection is explicit and tested at the domain level.
- [x] Invalid comment timestamp fails before changing comments or raising a domain event.
- [x] Public ticket creation no longer accepts caller-supplied tenant or requester identity.
- [x] Comment writes check actor identity, comment permission, internal-note role, and ticket tenant.
- [ ] Add application-level tests for allowed public comments, rejected cross-tenant writes, author spoofing, and customer attempts to create internal notes.
- [x] `TicketResponse` only includes internal comments for agent/manager/system actors; customer and partner response projections expose public comments only.
- [x] Expose the portal ticket read route through `GetPortalTicketHandler`, preserving access-policy checks, tenant equality, and DTO-only response mapping. Production CDI composition and persistence remain open; see [the runtime wiring audit](M1-support-runtime-wiring-audit.md).
- [x] Provide a repository-backed default ticket access policy that checks tenant, requester participant, requester role, staff-only resolution, and closed-ticket comment restrictions.
- [x] Add handler-level regression tests proving portal reads stop when policy denies access, reject cross-tenant tickets even when policy allows them, and return a DTO only after both checks pass.
- [x] Status, priority, and assignment handlers require authenticated support staff and enforce tenant ownership; resolving/closing additionally checks `TicketAccessPolicy.canResolve`.
- [ ] Add application-level authorization tests for status, priority, and assignment denial cases; verify production actor-provider and access-policy wiring.
- [ ] Resolve remaining Support runtime composition blockers: tenant-safe ticket persistence/migrations, explicit handler CDI wiring, production policy binding, and CDI/endpoint verification. See [the runtime wiring audit](M1-support-runtime-wiring-audit.md).
- [ ] Public replies and internal notes have separate notification / outbound-delivery rules.
- [ ] Ticket lifecycle transitions, resolution and reopening behavior have regression coverage.
- [ ] Queue assignment, SLA policy and business-calendar semantics are agreed before implementing timers.
- [ ] Persistence tests verify tenant isolation, optimistic concurrency, transaction rollback and idempotent retries where applicable.
- [ ] Support reactor and dependent reactors compile and tests pass in CI.

## Security note

A domain projection is a defense-in-depth contract, not a substitute for API authorization or DTO mapping. Customer-facing handlers must explicitly map only approved fields and use `customerVisibleComments()` or an equivalent role-safe projection; they must not return a serialized `Ticket` aggregate.

The public create endpoint is a customer-portal boundary. It derives tenant and requester from the authenticated actor. The generic `CreateTicketCommand` remains an application-level command for trusted, separately authorized workflows and must not be bound directly to a customer-facing HTTP request.

Comment, status, priority, assignment, and portal-read handlers require actor context and policy dependencies. `RepositoryTicketAccessPolicy` supplies a fail-closed repository-backed default: portal actors must match the ticket requester participant and role, and all actors must match the ticket tenant. It is a plain application class and is not automatically registered as a CDI bean; the application composition root must explicitly wire it with the authenticated `SupportActorProvider` and tenant-aware `TicketRepository`. Verify that wiring before production use, and audit all other command/read handlers. The current Support source tree has no concrete actor-provider or persistence adapter and no explicit handler CDI composition; these are tracked in [the runtime wiring audit](M1-support-runtime-wiring-audit.md).

## Deliberately deferred

This slice does not claim to deliver an agent inbox, SLA engine, queue management, omnichannel delivery, knowledge publishing workflow, feedback triage, or AI features. Those require separate implementation and verification within M1 or later milestones.
