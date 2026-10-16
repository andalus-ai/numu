# Numou Syirkah implementation status

## Scope

The initial domain tranche was merged in PR #1. The hardening branch adds the first
application-service and PostgreSQL-adapter layer. This is still a draft implementation,
not approved for live accounting or payment processing.

## Implemented

### Domain

- Immutable effective-dated contract terms and partner terms.
- Exact 100% profit nisbah validation, duplicate party detection, currency checks, and contract-version checks.
- Net result calculation from revenue less direct, operating, and other costs.
- Deterministic largest-remainder allocation using exact integer arithmetic in currency minor units.
- Mudharabah ordinary-loss allocation to capital providers by capital risk.
- Musyarakah ordinary-loss allocation by contributed capital.
- Unsupported Inan, Abdan, and Wujuh loss rules fail closed.
- Calculation fingerprint and policy/version references.

### Application and persistence

- Application port and service for contract creation/activation, evidenced business-result registration,
  calculation persistence, maker-checker approval, and scoped reads.
- PostgreSQL adapter with transaction-scoped contract/result/calculation writes.
- Idempotent calculation persistence keyed by result and calculation hash.
- Distribution obligation rows created from immutable allocations.
- Settlement repository method is idempotency-keyed, locks the obligation row, caps cumulative settlement,
  and refuses settlement unless the calculation is already marked POSTED.
- Audit-event rows for contract, result, calculation, approval, and settlement transitions.
- API skeleton uses `RequestContextHolder` and refuses requests without a verified tenant and actor.
- Tests expanded for large monetary values.

## Known limitations — do not use for live financial transactions yet

1. **Authentication context is not wired end-to-end.** The resource requires `RequestContextHolder.tenantVerified()`
   and an actor, but the repository currently has no confirmed security filter/token integration populating them.
   Requests should fail closed until that integration is completed and tested.
2. **Company authorization is incomplete.** Tenant/company values are included in repository predicates, but a
   verified authorization check that the authenticated actor may operate on each company is still required.
3. **Journal posting is not integrated.** There is no production-safe posting gateway or atomic transaction joining
   the distribution state and the general ledger. The REST API intentionally does not expose settlement. The
   repository settlement method will reject obligations whose calculation is not marked POSTED.
4. **Version lifecycle is incomplete.** Initial contract creation/activation is implemented; safe amendment,
   supersession, termination, and effective-version activation need a dedicated lifecycle implementation.
5. **No real database test has been run.** PostgreSQL migration behavior, transaction rollback, locking, uniqueness,
   retries, and concurrent settlement need Testcontainers tests.
6. **API hardening remains.** Add bean validation, safe exception-to-HTTP mapping, rate limits, actor/correlation
   audit context, OpenAPI request/response schemas, and authorization tests.
7. **Existing Numou GL has architectural risks.** The current journal handler mutates account balances one by one
   before journal persistence, and uses default tenant/ledger references. Syirkah must not rely on this path for
   live posting until a single transactional, tenant-aware ledger posting boundary exists.
8. Run the Maven reactor and CI; review all results before merge.
9. Qualified Sharia advisers and accounting/legal professionals must approve supported structures, loss policies,
   evidence standards, and jurisdiction-specific rules.

## Accounting safety boundary

A calculated loss allocation is not automatically a partner receivable, expense, or journal entry.
Negligence, misconduct, breach, force majeure, and recoverability require evidence and an independent decision.
An approved calculation is still not a posted journal. A recorded settlement row is not proof that a bank transfer
or cash payment occurred; a payment rail/reconciliation integration is separately required.

## Build and validation

Run from the repository root:

```bash
./mvnw -pl Framework/finance/accounting/accounting-capabilities/numou-accounting-islamic -am test
./mvnw -pl Framework/finance/accounting/numou-accounting-adapter -am test
```

The full Maven reactor and PostgreSQL integration tests have **not** been executed by this branch authoring session.
