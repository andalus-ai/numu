# Customer M1 — Repository implementation and security audit

- **Status:** Audit evidence recorded; implementation and runtime verification remain open
- **Base:** `main` at `65904047be7357b43dda83d3537e326242bcf1c6`
- **Scope:** Customer capability M1 from [Customer Capability Contract](customer-capability-contract.md)
- **Related:** [CRM tenant-isolation contract](../../crm/docs/tenant-isolation-m1-contract.md), [Party/Contact/Address contract](contact-address-capability-contract.md), [ADR-003](ADR-003-reusable-capability-modules.md)

This document records source-level observations. It does not claim that production runtime wiring, every migration, or every consumer has been verified.

## 1. Executive result

The repository already contains the key identity primitives and a tenant-scoped CRM Customer implementation. The next step is **not** to create another Party model or immediately move CRM's Customer aggregate.

The current boundary is incomplete:

1. Ecosystem owns canonical Party identity and exposes `PartyId`, `PartyKind`, `PartyRef`, `PartyRepository`, and `PartyLookupPort`.
2. CRM owns a separate `Customer` aggregate keyed by `CustomerId`, but it has no explicit Party reference.
3. CRM Customer still duplicates generic identity/contact/address data and embeds contact/address collections.
4. CRM's Customer repository resolves tenant scope from `TenantScopeProvider` and scopes its inspected reads, counts, existence checks, deletes, and updates.
5. The CRM tenant migration line has reached V17 on the audited base. Legacy tenant ownership remains a separately governed reconciliation process; no ownership should be inferred from Party IDs, actor IDs, email, phone, or organization names.

**M1 conclusion:** source-level ownership and persistence boundaries are sufficiently understood to design the next contract increment, but Party-to-Customer association, compatibility, and production tenant/security verification are not complete.

## 2. Confirmed source inventory

### 2.1 Ecosystem Party

Observed under `core/framework/ecosystem`:

- Domain aggregate: `ecosystem-domain/.../domain/model/Party.java`.
- Stable identity vocabulary: `PartyId`, `PartyKind`, `PartyStatus`, and `PartyRef`.
- Application ports: `PartyRepository` and `PartyLookupPort`.
- JDBC adapter: `ecosystem-persistence-jdbc/.../JdbcPartyRepository.java`.
- Migration V2 creates `ecosystem_party` with a global Party primary key, kind, lifecycle status, timestamps, and version.
- Migration V1 separately stores tenant-scoped external identity associations.

The Party aggregate intentionally owns identity kind and lifecycle, not names, contact details, customer lifecycle, or organization structure. The Party table is cross-tenant by design; tenant membership/association must remain a separate business relationship.

### 2.2 CRM Customer

Observed under `crm/numou-crm-domain`, `crm/numou-crm-application`, and `crm/numou-crm-adapter`:

- Domain aggregate: `domain/model/Customer.java`, keyed by `CustomerId`.
- Repository port: `domain/repository/CustomerRepository.java`.
- Application handlers: create, update, get, search, and add-contact handlers.
- Persistence: `CustomerEntity` and `CustomerRepositoryImpl`.
- Customer table: `crm_customers`, with nullable `tenant_id` for unresolved legacy ownership.
- Contacts and addresses are JPA element collections stored in `crm_customer_contacts` and `crm_customer_addresses`, joined through the owning customer row.

The aggregate currently stores customer number, company/person names, email, phone, free-text address fields, industry, website, tax ID, currency, payment terms, credit limit, account status, notes, contacts, addresses, and an active flag. It has no explicit `PartyId`.

The create handler constructs the CRM aggregate directly from request command fields. It does not currently establish a canonical Party association.

### 2.3 CRM tenant enforcement

The inspected `CustomerRepositoryImpl`:

- obtains tenant identity from `TenantScopeProvider.requireTenantScope()`;
- assigns the verified tenant to new persistence entities;
- scopes update lookup by both customer ID and tenant ID;
- scopes find-by-ID, existence, delete, email/customer-number lookup, company/industry search, active listing, and industry count by tenant;
- does not expose an unscoped customer-by-ID repository method in the inspected interface.

This is positive source-level evidence, not proof that every standalone or embedded runtime installs the trusted provider correctly. The runtime composition and real adapter tests must still verify fail-closed behavior.

### 2.4 Migration and reconciliation

The audited CRM migration directory contains V1–V17. Relevant milestones include:

- V8–V14: nullable tenant ownership across the CRM aggregates, including referrals.
- V15: composite tenant-local relationship constraints.
- V16: evidence-backed ownership proposals and append-only proposal audit.
- V17: transactional application of an individually approved ownership proposal, with related-tenant validation.

V16/V17 do not silently infer ownership. They require evidence, explicit review, and a separate execution step. This policy must be preserved.

## 3. Ownership map

| Data / behavior | Owner | M1 rule |
|---|---|---|
| Canonical person or organization identity | Ecosystem Party | Reference by `PartyId`; never recreate in CRM |
| Party kind and global identity lifecycle | Ecosystem Party | Do not conflate with customer lifecycle |
| Generic email/phone endpoints and verification | Ecosystem ContactPoint | Customer should reference Party/contact associations, not own a second verification model |
| Postal/physical addresses | Ecosystem Address | Customer must not become the canonical address store |
| Customer role in a tenant/business context | Customer capability (currently CRM implementation) | Explicit tenant-scoped association to Party |
| Customer-specific profile, number, classification, lifecycle | Customer capability | Keep distinct from Party identity |
| Sales lead, opportunity, campaign and pipeline | CRM | CRM consumes Customer/Party contracts |
| Credit limit, payment terms, currency/credit policy | Finance or a specifically defined customer commercial-account contract | Do not treat as universal identity fields |
| Orders, invoices, bookings and shipment snapshots | Owning transaction capability | Preserve immutable transaction-time evidence |

The CRM `Account` aggregate and the CRM `Customer` aggregate also need a field/relationship-level semantic review before any consolidation. Similar names are not evidence that they represent the same business concept.

## 4. M1 security findings and open verification

### Source-level strengths

- Customer repository methods inspected are tenant-scoped.
- The trusted tenant-scope provider is mandatory in the repository adapter.
- CRM tenant ownership is nullable for legacy records rather than assigned to an invented default.
- V15 database constraints reinforce tenant-local relationships.
- V16/V17 provide an evidence-backed, audited, transactional reconciliation path.

### Gaps / verification still required

1. Verify standalone and embedded CRM compositions install a trusted tenant provider and reject missing/ambiguous context.
2. Verify all Customer REST endpoints, exports, nested contact/address operations, and asynchronous jobs use the same tenant and authorization boundary.
3. Verify cross-tenant direct-ID probes return a non-disclosing not-found result at the API boundary.
4. Verify PostgreSQL adapter tests exercise two tenants for Customer read, update, delete, list/search/count, nested collections, and concurrent requests.
5. Inventory CRM outbox, audit, and asynchronous tables against the preflight script and deployed schemas. The current preflight includes child collection tables that do not have their own tenant column; confirm they remain strictly parent-owned and cannot be accessed independently before interpreting their missing `tenant_id` as a defect.
6. Confirm the V16/V17 operational executor is callable only by a trusted deployment role whose identity is derived from verified operations authentication. The SQL function itself does not authenticate the actor string.
7. Review uniqueness semantics for customer number, email, tax registration, and external identifiers. Each must be explicitly global or tenant-local; do not change existing global constraints without collision inventory.

No production runtime or integration test was executed as part of this source audit.

## 5. M1 boundary decision

Do not move `Customer` out of CRM in this milestone. Do not add a second Party aggregate, duplicate contact/address tables, or an implicit UUID conversion.

The next implementation increment should introduce a Customer-owned, tenant-scoped Party association through a narrow public contract:

- A stable `CustomerId` remains the customer-role identifier.
- A stable `PartyId` identifies the canonical person or organization.
- A customer registration command must carry or obtain an explicit Party reference; a trusted application port validates Party existence, kind, and authorization.
- The Party identity itself remains cross-tenant. The Customer-to-Party association is tenant-scoped.
- A Party reference is not authorization; customer access remains tenant- and permission-scoped.
- Do not auto-create or auto-merge Parties based on email, phone, name, or tax ID.
- Do not introduce a cross-database foreign key if Ecosystem and CRM are independently persisted/deployed. Use the Party lookup port for modular-monolith composition and a versioned remote contract when distributed.
- Do not make Party registration and Customer registration appear atomic unless a real shared transaction or an explicit idempotent workflow/outbox contract guarantees it.
- Keep the existing CRM API and CustomerId behavior compatible while adding the association as an additive capability.

The CRM domain reactor already declares a dependency on `numou-ecosystem-domain`. The CRM application reactor should depend on the Ecosystem public application contract only if its POM and composition are updated intentionally; it must not depend on Ecosystem persistence internals.

## 6. Next acceptance criteria

- [ ] Verify the actual Party persistence adapter and runtime registration in the ecosystem reactor.
- [ ] Define a typed Customer-to-Party association value and lifecycle rules.
- [ ] Define a public Party lookup/validation port usable by Customer application code.
- [ ] Define tenant policy for the Customer-to-Party association independently from global Party identity.
- [ ] Add two-tenant tests for valid Party references, wrong-kind references, unknown references, and unauthorized references.
- [ ] Add duplicate-registration/idempotency behavior without matching by contact data.
- [ ] Preserve existing CRM Customer APIs and data; no automatic backfill.
- [ ] Document the Account-versus-Customer distinction and ownership of commercial terms.
- [ ] Verify standalone and embedded composition, then run the CRM and Ecosystem reactor tests.
- [ ] Record exact CI results; do not mark production readiness while broader CI failures or runtime prerequisites remain unresolved.

## 7. Explicit non-goals

- No flag-day extraction of CRM Customer.
- No destructive migration or automatic Party creation/backfill.
- No implicit `CustomerId` to `PartyId` conversion.
- No duplicate contact/address/consent/verification implementation.
- No change to V16/V17 legacy ownership governance.
- No claim of production readiness based solely on source inspection.
