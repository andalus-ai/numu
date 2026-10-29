# PersonRef and PartyId Identity Mapping Decision

- **Status:** Proposed compatibility contract; implementation is gated on repository and data audit.
- **Scope:** Mapping Workforce's existing `PersonRef` to Ecosystem's `PartyId`.
- **Related:** [Person and Workforce Capability Contract](person-workforce-attribute-contract.md), [M0 Implementation Audit](person-workforce-m0-implementation-audit.md).

## 1. Problem and observed evidence

The repository currently has two strongly typed UUID references with overlapping person-identity language:

- `foundation.domain.ref.PersonRef` is documented as a universal cross-domain reference to a person.
- `ecosystem.domain.party.PartyId` is documented as the canonical identifier for a Party, while `PartyKind.PERSON` identifies individual people.
- `PartyRef` wraps a `PartyId` and a `PartyKind`.
- Workforce's `Worker` aggregate stores a `PersonRef`, not a `PartyId`.

The inspected source tree establishes these value types and their usage in Workforce. It does **not**, by itself, establish a complete persisted Party aggregate, repository, migration history, or a verified one-to-one mapping between existing PersonRef values and Party records. Equal UUID representations are not proof that two identifiers refer to the same persisted identity.

## 2. Decision

1. **Target canonical identity:** Ecosystem Party is the intended owner of cross-domain identity. A person is represented as a Party whose kind is `PERSON`; organization identities use `ORGANIZATION`.
2. **No implicit UUID aliasing:** Do not add convenience conversions that silently translate `PersonRef` to `PartyId` (or vice versa) merely because both wrap UUIDs.
3. **Compatibility during migration:** Existing Workforce contracts may continue to use `PersonRef` until callers, persisted data, and repository adapters have been reconciled. New cross-capability contracts should use the canonical Party contract when that contract is implemented and available to the consumer.
4. **Translate explicitly where needed:** If a migration period requires both types, introduce a named adapter/translation port at the capability boundary. It must resolve and validate identity existence and person kind; it must not manufacture a Party or assume identity from a UUID alone.
5. **Do not duplicate identity:** Do not create a separate Person identity root inside Workforce, CRM, logistics, marketplace, or other consumers. Domain capabilities own their role and engagement records, not the canonical person's identity.
6. **Authentication remains separate:** A login/User account is not automatically a Party or a Person. Any association must be explicit, authorized, and auditable.

## 3. Required repository audit before code migration

The implementation milestone must establish the following facts from actual code and data:

- Whether Ecosystem has a persisted Party aggregate, repository port, production adapter, tenant model, migrations, and unique constraints; if not, identify and implement that owning capability before migrating consumers.
- All call sites and producers of `PersonRef`, including Workforce, CRM/Customer, marketplace, courier, identity, workspace, and event contracts.
- Whether PersonRef values are generated independently, imported from an existing Party source, or stored as external references.
- Existing database columns, event payloads, APIs, serialized documents, and fixtures that contain person UUIDs.
- Whether Party identity is globally unique or tenant-scoped, and how access to cross-tenant identity is authorized.
- Merge, duplicate resolution, deletion/deactivation, retention, and audit behavior for identity records.
- Whether a person may have multiple worker engagements in one tenant or across employers. Do not use a global unique constraint on person identity to prohibit valid engagements.

The source-tree audit alone is not sufficient to authorize a production data migration.

## 4. Migration strategy

### Phase A — Inventory and compatibility

1. Map every consumer and persistence location of both identifier types.
2. Define an explicit compatibility matrix for each API, event, and repository contract.
3. Add contract tests for valid mappings, unknown identities, wrong party kinds, tenant boundaries, and legacy values.
4. Keep the current identifiers and existing stored values unchanged while the inventory is incomplete.

### Phase B — Establish canonical Party persistence

1. Complete or verify the Party aggregate, repository, persistence adapter, migrations, and tenant/authorization enforcement in Ecosystem.
2. Add a supported lookup/translation contract for consumers that need to resolve a person Party.
3. Ensure cross-capability consumers depend on public contracts, not Ecosystem persistence entities or tables.

### Phase C — Backfill and dual-read only when justified

1. Prepare an idempotent, observable backfill only after proving how legacy PersonRef values map to canonical Party records.
2. Quarantine unmatched and ambiguous records for explicit reconciliation; do not guess matches from names, email addresses, phone numbers, or document numbers.
3. If dual-read or dual-write is temporarily needed, define ownership, consistency rules, retry behavior, and a removal date.
4. Never overwrite or merge identities silently. Record migration provenance and provide a reconciliation report.

### Phase D — Consumer migration and retirement

1. Migrate one capability at a time to the canonical Party contract.
2. Preserve compatibility for public APIs/events through a versioned transition.
3. Remove legacy PersonRef storage only after consumers, historical data, event replay, and integrations are verified.
4. Retire the translation adapter only after telemetry and contract tests show that no supported caller depends on it.

## 5. Acceptance criteria

- [ ] The actual Party aggregate, repository, persistence, tenant policy, and migrations are documented and verified.
- [ ] All PersonRef and PartyId producers, consumers, and persisted uses are inventoried.
- [ ] The identity mapping is explicit, validated, and testable; no implicit UUID cast is introduced.
- [ ] Unknown, ambiguous, wrong-kind, and unauthorized cross-tenant references fail safely.
- [ ] Existing data is preserved and migration is idempotent, auditable, and reversible or recoverable.
- [ ] Workforce engagement uniqueness is defined independently of canonical person identity.
- [ ] API/event compatibility and rollback procedures are documented.
- [ ] Relevant Maven reactors and integration tests pass before the migration is marked production-ready.

## 6. Out of scope

This decision does not implement a new Person aggregate, Party persistence adapter, identity merge service, custom-attribute engine, or database migration. Those changes require evidence from the repository and data audit above. This document records the safe contract and gates the implementation; it is not proof that canonical Party persistence is already complete.
