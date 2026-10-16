# Numou Syirkah implementation status

## Scope of this branch

This branch adds a first integrated domain tranche to the existing
`numou-accounting-islamic` capability module and a PostgreSQL migration under the
existing accounting adapter. It does not create a parallel accounting engine.

## Implemented in this tranche

- Immutable effective-dated contract version terms with tenant/company ownership.
- Party roles, contributed capital, and agreed profit nisbah.
- Net result calculation using revenue less direct, operating, and other costs.
- Deterministic largest-remainder allocation for currency minor units.
- Mudharabah ordinary loss allocation only to capital providers in proportion to capital.
- Musyarakah ordinary loss allocation in proportion to capital contributions.
- Fail-closed behavior for Inan, Abdan, and Wujuh loss policies until an approved policy is implemented.
- Immutable calculation result with policy/version references and a SHA-256 calculation fingerprint.
- Initial PostgreSQL schema for contract versions, party terms, business results, calculation snapshots,
  distribution obligations, and idempotency keys.
- Domain tests for allocation, loss policy boundaries, break-even, and invalid terms.

## Explicit production blockers

This branch is not yet safe to use for live financial posting. Before release:

1. Wire persistence repositories and transactional application services to the migration.
2. Integrate calculation approval and distribution posting with the existing journal-entry lifecycle.
3. Enforce idempotency and concurrency at the application and database transaction boundaries.
4. Add authenticated tenant/company authorization and audit event integration to all Syirkah commands.
5. Add API request validation, error mapping, OpenAPI contracts, and end-to-end tests.
6. Add PostgreSQL Testcontainers migration and concurrency tests.
7. Run the complete Maven reactor and CI workflow.
8. Obtain review from qualified Sharia advisers and accounting/legal professionals for supported contract types,
   local regulations, and the precise loss treatment.

## Important design boundary

A calculated loss allocation is not automatically a receivable, expense, or journal entry.
Negligence, misconduct, breach, and recoverability must be evidenced and approved separately.
No API in this tranche should post money merely because a calculation exists.

## Build

Run from the repository root:

```bash
./mvnw -pl Framework/finance/accounting/accounting-capabilities/numou-accounting-islamic -am test
```

Then run the full reactor CI before merging.
