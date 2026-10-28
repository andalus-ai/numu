# Customer Capability Contract

**Status:** Proposed implementation contract; code and runtime behavior remain to be verified  
**Scope:** Reusable customer capability for Numou  
**Related:** [ADR-003 — Reusable capability modules](ADR-003-reusable-capability-modules.md), [Domain capability matrix](DOMAIN-CAPABILITY-MATRIX.md), [CRM M1 issue #897](https://github.com/andalus-ai/numu/issues/897)

## 1. Decision

Customer should be a first-class reusable business capability, not a feature owned exclusively by CRM. A capability is a logical business boundary; it does not automatically require a separate service, database, Maven reactor, or deployment.

Customer builds on the canonical identity supplied by Ecosystem Party. CRM consumes Customer and Party contracts. Other business capabilities may use Customer without depending on CRM.

This is a target contract, not a claim that the repository already implements the complete design.

## 2. Ownership boundaries

| Concern | Authoritative owner | Contract |
|---|---|---|
| Person and organization identity | Ecosystem Party | Canonical party identity and generic party relationships |
| Customer role and customer-account lifecycle | Customer | A party's customer role within a tenant/business context |
| Customer-specific profile and classification | Customer | Customer segment, lifecycle state, customer-specific identifiers and attributes with defined semantics |
| Customer-account relationship | Customer | Account hierarchy/relationship where the business meaning is specifically customer-facing |
| Lead, qualification, opportunity, pipeline, forecast | CRM | Sales prospecting and relationship-workflow semantics |
| CRM activities and campaign execution | CRM / relevant marketing capability | Sales and campaign workflow, not canonical identity |
| Orders, bookings, shipments, work orders, invoices, payments | Owning transaction capability | Own lifecycle and invariants; reference party/customer IDs through public contracts |
| Generic contact-point identity and verification | Existing authoritative contact/identity capability | Do not copy contact ownership into Customer without an explicit ownership decision |
| Consent and communication preferences | Explicitly assigned privacy/consent owner | Do not create duplicate consent records in CRM and Customer; document lawful purpose, scope, provenance and revocation semantics |
| Customer 360 screen and sales insights | Consumer/read-model layer | Compose authorized data from source capabilities; not a second system of record |

A customer is usually a role played by a party, not a second person or organization identity. One party may be a customer, supplier, carrier, partner, employee, or recipient in different contexts. Those roles must not be conflated.

## 3. Public contract requirements

Before extracting or expanding implementation, inspect the existing Party, Customer, and CRM APIs and preserve compatible behavior where safe.

- Use stable opaque Party IDs and Customer IDs; make the relationship explicit rather than relying on names, email addresses, or phone numbers as identity.
- Scope customer records and every customer lookup by trusted tenant/business context where the product's tenancy model requires it. Resolve tenant context from authenticated host security context, never from an untrusted payload field or arbitrary header.
- Treat possession of an ID as a reference, not authorization. Cross-tenant resource access should not reveal whether another tenant's record exists.
- Keep domain models framework-neutral. HTTP, persistence, CDI, broker, and provider details belong in application ports, adapters, and runtime composition.
- Offer in-process application contracts for modular monolith composition and versioned API/event contracts for distributed composition. Both must reuse the same domain rules.
- Cross-capability events/commands should carry tenant, actor/correlation and idempotency context as appropriate. Use durable delivery/outbox/inbox where reliability requires it.
- Do not expose persistence entities, internal repositories, or shared tables as integration contracts.
- Do not create a generic universal profile field bag without validation, provenance, privacy rules, and clear ownership.

Names of Maven artifacts and package structure must follow the actual repository conventions after the owning reactor and existing Party contracts are inspected. Do not create a separate deployable service solely to satisfy the word "module."

## 4. Data and migration policy

1. Inventory existing CRM customer entities, contacts, addresses, identifiers, repositories, migrations, APIs, and all cross-module consumers.
2. Define a field-by-field source-of-truth map. Preserve distinctions between Party identity, customer role/profile, sales account, and transaction snapshots.
3. Establish an explicit mapping between existing CRM customer IDs and canonical Party IDs. Preserve external references and audit history.
4. Detect collisions and ambiguous mappings for operator review. Never auto-merge records across tenants, and never auto-merge solely because email, phone, or organization name matches.
5. Introduce compatibility adapters/read paths before migrating consumers. Avoid a flag-day rewrite.
6. Backfill in an observable, restartable and idempotent manner; report unmatched, duplicate and conflicting records. Reconcile counts and samples before switching reads/writes.
7. Keep transactional snapshots in orders, invoices, bookings and other records where historical legal/commercial evidence requires the values at transaction time. A live customer profile must not rewrite historical documents.
8. Remove duplicate storage only after consumers have migrated, rollback is understood, and reconciliation is accepted.

Migrations must be additive where practical, have explicit rollback/forward-recovery guidance, and be tested against representative existing data. No destructive migration is authorized by this contract.

## 5. Security, privacy and audit

- Enforce tenant scope at application and persistence boundaries; test both read and write paths, including list/search/count/export and nested contacts/addresses.
- Apply authorization to customer data and sensitive fields, including tax identifiers and financial terms.
- Record actor, time, reason and correlation for merge, deduplication, role changes and sensitive profile updates.
- Treat consent as purpose- and channel-specific, traceable to its source, and revocable. Consent is not inferred from an email address or an existing business relationship.
- Minimize personal data in events and logs. Use references and only publish fields required by the consumer.
- Define retention, deletion, correction and legal-hold behavior with the privacy/data-governance owner before implementing merge or erasure flows.

## 6. Acceptance criteria for the first implementation milestone

- [ ] Repository inventory identifies existing Party contracts, CRM Customer APIs, actual Maven reactor roots and all known consumers.
- [ ] Field ownership and ID mapping are documented; duplicate identity ownership is not introduced.
- [ ] Customer public application contract can be consumed without depending on CRM internals.
- [ ] Tenant context is trusted, mandatory where required, and passed through the application boundary; missing/ambiguous context fails closed.
- [ ] Cross-tenant ID probes behave as not-found and do not leak record existence.
- [ ] Tests cover tenant isolation for detail, list/search, nested resources and mutations.
- [ ] Tests cover Party-to-Customer mapping, duplicate/conflict handling, and idempotent migration/backfill.
- [ ] Existing CRM behavior and public contracts remain compatible or have a documented versioned migration.
- [ ] Architecture checks prevent Customer from depending on CRM and prevent consumers from importing private Customer persistence classes.
- [ ] The narrowest relevant Maven reactor and dependent reactors are built/tested; results and environmental failures are reported accurately.
- [ ] Standalone, modular-monolith and distributed composition paths are documented without duplicating domain rules.

## 7. Delivery sequence

**M1 — Inventory and security baseline:** map current contracts and consumers; verify tenant context propagation and fix demonstrated access-control gaps.

**M2 — Customer public contract:** define customer-role/profile application ports, identifiers, authorization rules, and architecture tests using repository conventions.

**M3 — Compatibility and migration:** mapping/backfill, collision reports, compatibility adapters, reconciliation and rollback rehearsal.

**M4 — Consumer migration:** migrate CRM and then other consumers in bounded changes; keep domain transaction ownership local to each capability.

**M5 — Customer lifecycle maturity:** deduplication/merge review workflow, audit/provenance, privacy and consent ownership, search and authorized read models.

**M6 — Deployment and operational readiness:** verify in-process and remote adapters, contract compatibility, observability, failure handling and tenant isolation in each supported composition.

Do not open a milestone PR as complete until its code, tests, migration behavior and CI evidence have been inspected. Keep PRs milestone-sized rather than creating one PR per small edit.
