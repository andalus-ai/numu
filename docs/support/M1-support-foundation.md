# Support foundation milestone (M1)

**Status:** In progress; this document records scope and acceptance criteria, not completion.

## Goal

Harden the existing Support capability before adding more channels, automation, or AI. Preserve the current ticket domain and public API where possible. Do not create a parallel helpdesk model.

## Changes in these slices

- Provide an explicit `customerVisibleComments()` projection that excludes internal agent notes.
- Keep `comments()` as the complete domain history and document that it must not be serialized directly to a customer-facing response.
- Validate the comment event timestamp before mutating the aggregate, so a rejected command cannot leave an in-memory ticket partially changed.
- Route public ticket creation through `CreatePortalTicketHandler`; tenant and requester identity are derived from the authenticated actor context rather than client-controlled request fields.
- Require authenticated actor context and `TicketAccessPolicy.canComment` before adding comments.
- Require the command author to match the authenticated actor's user ID, reject internal notes from customer/partner actors, and verify the loaded ticket belongs to the actor's tenant before mutation.
- Return a non-specific “Ticket not found” failure for inaccessible tickets to reduce identifier-based information disclosure.

## M1 acceptance checklist

- [x] Customer-visible comment projection is explicit and tested at the domain level.
- [x] Invalid comment timestamp fails before changing comments or raising a domain event.
- [x] Public ticket creation no longer accepts caller-supplied tenant or requester identity.
- [x] Comment writes check actor identity, comment permission, internal-note role, and ticket tenant.
- [ ] Add application-level tests for allowed public comments, rejected cross-tenant writes, author spoofing, and customer attempts to create internal notes.
- [ ] Every customer-facing ticket read model / endpoint uses a safe projection and never serializes the complete internal comment history.
- [ ] Application handlers enforce tenant and actor authorization for status, priority, and assignment operations; identifier possession is not authorization.
- [ ] Public replies and internal notes have separate notification / outbound-delivery rules.
- [ ] Ticket lifecycle transitions, resolution and reopening behavior have regression coverage.
- [ ] Queue assignment, SLA policy and business-calendar semantics are agreed before implementing timers.
- [ ] Persistence tests verify tenant isolation, optimistic concurrency, transaction rollback and idempotent retries where applicable.
- [ ] Support reactor and dependent reactors compile and tests pass in CI.

## Security note

A domain projection is a defense-in-depth contract, not a substitute for API authorization or DTO mapping. Customer-facing handlers must explicitly map only approved fields and use `customerVisibleComments()`; they must not return a serialized `Ticket` aggregate.

The public create endpoint is a customer-portal boundary. It derives tenant and requester from the authenticated actor. The generic `CreateTicketCommand` remains an application-level command for trusted, separately authorized workflows and must not be bound directly to a customer-facing HTTP request.

The comment handler now requires actor and policy dependencies. Its callers and CDI wiring must supply a correctly authenticated `SupportActorProvider` and a tenant-aware `TicketAccessPolicy`; this change is not a substitute for verifying those implementations and all other command handlers.

## Deliberately deferred

This slice does not claim to deliver an agent inbox, SLA engine, queue management, omnichannel delivery, knowledge publishing workflow, feedback triage, or AI features. Those require separate implementation and verification within M1 or later milestones.
