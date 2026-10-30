# Organization and Party Identity Convergence

- **Status:** Architecture decision; implementation migration is gated on repository and data inventory.
- **Date:** 2026-10-30
- **Scope:** Reconcile the top-level `organization/` capability with the Organization profile introduced in Ecosystem.
- **Related:** `core/framework/ecosystem/ecosystem-domain/.../domain/organization`; `organization/organization-domain`; `organization/organization-application`; `organization/organization-spi`; `organization/organization-adapter`.

## 1. Observed repository state

The repository contains two separate organization models.

### A. Top-level Organization capability

The `organization/` bounded context already contains:

- `Organization` aggregate keyed by `OrganizationId`.
- Mandatory `TenantId`.
- Name, legal name, registration number, and lifecycle status.
- `OrganizationUnit` and `OrganizationMembership` domain models.
- Application commands for organization creation, organization-unit creation, and member invitation.
- SPI repository ports and in-memory adapters.

This capability is structurally closer to the intended reusable organization-management boundary because it already owns organizational units and membership.

### B. Ecosystem Organization profile

The Ecosystem domain now contains an `Organization` aggregate keyed by canonical `PartyId`, with legal/display names, namespaced organization classification, optional tenant association, and profile lifecycle. The Ecosystem application layer has a registration handler that asks a `PartyKindLookupPort` to validate that the referenced Party exists and has kind `ORGANIZATION`.

### C. Identity facts and limitations

- Ecosystem exposes `PartyId`, `PartyKind`, and `PartyRef` vocabulary.
- The inspected Party foundation does not yet provide a complete Party aggregate/repository/persistence adapter contract.
- The top-level Organization aggregate currently uses a distinct `OrganizationId` and requires a tenant.
- A UUID-shaped `OrganizationId` and UUID-shaped `PartyId` are not proof of the same identity.
- The source tree does not establish a complete persisted mapping, data migration history, or verified uniqueness rules between these models.

## 2. Decision

1. **Do not create a third Organization model or another Organization persistence adapter.**
2. **Keep the top-level `organization/` capability as the owner of organizational structure and management**: organization profile lifecycle, units, membership, and future hierarchy/governance capabilities.
3. **Keep Ecosystem Party as the intended canonical cross-domain identity owner.** An organization identity should ultimately be a Party of kind `ORGANIZATION`.
4. **Treat the current Ecosystem Organization aggregate as an integration/profile slice, not a second organizational-structure aggregate.** It must not grow units, memberships, branches, governance, or corporate hierarchy.
5. **Do not silently equate `OrganizationId` and `PartyId`.** Introduce an explicit, validated association only after Party persistence and identity ownership are implemented and verified.
6. **Do not force tenant ownership into Party identity.** Tenant ownership/participation is a separate relationship. The current top-level Organization's mandatory TenantId must be reviewed before supporting external organizations or corporate groups spanning tenants.
7. **Do not perform destructive identifier replacement or database backfill** until all call sites, persisted columns, events, API contracts, and data are inventoried.

## 3. Target responsibility map

| Concern | Owning capability |
|---|---|
| Canonical Party identity and Party kind | Ecosystem / Party |
| Organization profile and organizational lifecycle | Organization |
| Organization units, departments, teams, and internal hierarchy | Organization |
| Membership and organization-scoped roles | Organization |
| Legal entity registrations and jurisdictional identifiers | Organization, through jurisdiction-aware registration records |
| Corporate ownership/control and group relationships | Organization, with explicit legal relationship semantics |
| Tenant membership, tenancy, and isolation | Tenancy |
| Login accounts and authentication principals | Identity |
| Contact points and addresses | Ecosystem Party/contact foundation |

An Organization profile references a Party identity; it does not create or own a parallel Party. An Organization may be associated with a tenant for operational ownership, but a tenant is not itself the organization.

## 4. Migration plan

### Phase A — Inventory and contracts (current)

- [ ] Inventory every `OrganizationId`, `PartyId`, `OrganizationRef`, and `LegalEntityRef` producer and consumer.
- [ ] Inventory all persistence schemas, migrations, event payloads, APIs, fixtures, and seed data that store these identifiers.
- [ ] Establish whether any current OrganizationId values are already backed by Party records; do not infer this from equal UUID values.
- [ ] Document tenant scoping and whether an organization may be external, belong to multiple tenants, or participate in a corporate group.
- [ ] Identify all consumers that depend on the top-level Organization module versus the Ecosystem Organization profile.

### Phase B — Canonical Party persistence

- [ ] Complete or verify Party aggregate, repository, persistence, uniqueness, tenant authorization, and migration contracts in Ecosystem.
- [ ] Add a Party lookup contract that can validate existence, kind, and authorization in local and distributed deployments.
- [ ] Add explicit Organization-to-Party association semantics and tests.
- [ ] Define uniqueness and lifecycle consistency rules across the association.

### Phase C — Organization capability convergence

- [ ] Evolve the top-level Organization aggregate and public contracts to carry a validated Party reference.
- [ ] Preserve OrganizationId as an internal/business aggregate identifier only if there is a demonstrated need; otherwise plan a versioned migration to PartyId after compatibility analysis.
- [ ] Make tenant association semantics explicit and support external organizations without inventing tenant ownership.
- [ ] Keep OrganizationUnit and OrganizationMembership attached to the Organization aggregate identity.
- [ ] Consolidate organization creation and profile registration into one application workflow with an explicit UnitOfWork/outbox strategy.
- [ ] Retire the duplicate Ecosystem Organization aggregate only after consumers and stored data are migrated and contract tests pass.

### Phase D — Production adapters and consumer migration

- [ ] Implement production persistence adapters in the owning Organization capability using the repository's established persistence technology and migration conventions.
- [ ] Add transactional uniqueness constraints for Party association, tenant-scoped codes, and other verified invariants.
- [ ] Migrate CRM, Workforce, Finance, Procurement, POS, Courier/Cargo, and other consumers through stable public contracts.
- [ ] Provide versioned events/API compatibility, idempotent backfill, reconciliation reports, and rollback procedures.
- [ ] Remove temporary adapters only after telemetry and compatibility tests show no remaining callers.

## 5. Non-goals

- No UUID-to-UUID implicit conversion.
- No new parallel Party or Organization identity root.
- No new in-memory-only adapter presented as production persistence.
- No automatic merge of records by name, email, registration number, or UUID.
- No jurisdiction-specific registration fields hard-coded into the universal Organization identity.

## 6. Acceptance criteria

- [ ] One authoritative organizational-structure aggregate and repository boundary.
- [ ] One canonical cross-domain Party identity with validated organization kind.
- [ ] Explicit, persisted, audited Party-to-Organization association.
- [ ] Tenant isolation and external-organization semantics tested.
- [ ] Organization units and memberships remain stable through identity migration.
- [ ] All API/event/persistence consumers inventoried and migrated compatibly.
- [ ] Relevant Maven reactors, adapter integration tests, migration tests, and architecture tests pass.
- [ ] No unverified identity backfill or implicit UUID aliasing remains.
