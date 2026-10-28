# Construction Consultancy M1 — Engagement and Deliverable Domain

- **Status:** Domain-model foundation implemented on the feature branch; persistence, application authorization, API, and runtime integration remain future work.
- **Related plan:** [Indonesian Construction Consultancy and Sharia Istisna' Roadmap](INDONESIA-CONSTRUCTION-CONSULTANCY-SHARIA-ROADMAP.md)
- **Baseline audit:** [M0 audit](INDONESIA-CONSTRUCTION-CONSULTANCY-M0-AUDIT.md)
- **Architecture:** [ADR-003](ADR-003-reusable-capability-modules.md) and [Domain Capability Matrix](DOMAIN-CAPABILITY-MATRIX.md)

## Scope in this milestone

The Construction domain gains a consultancy-specific engagement and deliverable lifecycle without changing neutral Project, Scope, Document, Workflow, or Finance contracts.

### Engagement

- Keeps opaque project/client/consultant organization references; it does not own CRM or identity data.
- Requires distinct client and consultant organizations.
- Supports DRAFT → PROPOSED → ACTIVE → COMPLETED and cancellation with a required reason.
- Tracks a scope revision for proposal changes before activation.
- Rejects silent scope revision after activation. Post-activation changes must be handled by an explicit contract/change-control workflow.

### Deliverable

- Belongs to an engagement and has a stable code and revision number.
- Supports DRAFT → SUBMITTED → ACCEPTED or REJECTED.
- Only the client organization can review the deliverable in the domain model.
- A rejected deliverable must be revised to a new revision and resubmitted before another decision.
- Records the reviewing organization and review comment/rejection reason.

## Security and capability boundaries

The organization-level reviewer check is a domain invariant, not complete authorization. An application layer must authenticate the acting person, establish tenant scope, verify their authority to act for the client organization, and authorize access to the referenced engagement before calling these methods. Possession of an ID must never grant access.

The model intentionally does not copy client profiles, staff qualifications, workflow engines, document bytes, invoices, or ledger state. Future application/adapters should consume those capabilities through public contracts and avoid direct cross-capability persistence access.

## Not claimed by M1

This change is not yet an end-to-end consultancy workflow. It does not add:
- repository/SPI implementations or database migrations;
- tenant-scoped application commands, authenticated actor context, or HTTP endpoints;
- durable outbox/event publication or idempotent persistence commands;
- attachment/document revision linkage;
- proposal fee schedules, timesheets, invoice/receivable integration, or payment behavior;
- Indonesian compliance rules or permit/certification claims;
- an istisna' contract profile or a Sharia compliance claim.

These should be introduced only after the existing shared contracts and runtime composition are traced. In particular, contract fee and signed commercial terms must remain separate from consultancy scope metadata.

## Domain test coverage added

- proposal submission and activation ordering;
- proposal scope revision and revision increment;
- prohibition on silent post-activation scope change;
- completion and cancellation guards;
- distinct client/consultant organizations;
- required cancellation reason;
- client-only deliverable review;
- rejection, revision, resubmission, and acceptance lifecycle.

The repository's applicable Maven reactor must run these tests before this milestone can be considered verified. No build/test result is implied by this document.
