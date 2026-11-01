# Revenue and Spend Management — M1.4 Budget Tenant Isolation

- Status: tenant scope added to Budget domain/application/SPI and in-memory adapters; verification pending.
- Date: 2026-11-01.
- Branch: `finance/revenue-spend-m1-followup`.

## 1. Audit finding

The Budget reactor contains two related but distinct models:

1. The operational `Budget` aggregate already carries `tenantId`, but `BudgetStorePort` and `BudgetService` previously looked up budgets by budget ID alone.
2. The planning model (`PlanningBudget`) and `BudgetCommitment` previously carried no tenant identity. Their repository contracts used ID-only reads, and actual totals were keyed without tenant identity.

The module currently provides in-memory adapters only. No PostgreSQL persistence adapter or migration was found in the Budget reactor inspected for this milestone. This change therefore establishes the tenant-aware contract and in-memory behavior; it does not claim production persistence readiness.

The operational and planning models have different fields and lifecycle responsibilities. This milestone does not merge them or assume they are duplicate aggregates. Their long-term ownership and integration need a separate domain review.

## 2. Implemented contract changes

### Planning budget

- `PlanningBudget` now requires and exposes a nonblank tenant ID.
- `BudgetRepository.find` requires both tenant ID and budget ID.
- `BudgetLifecycleService.create/require` and the `BudgetingService` facade propagate tenant scope.
- In-memory planning budget storage uses a composite (tenant, budget ID) key.

### Commitments

- `BudgetCommitment` now requires and exposes tenant ID.
- `CommitmentRepository.find/openFor` require tenant ID.
- `CommitmentService.create/consume/release` require tenant ID and validate it before repository access.
- Commitment creation checks budget availability in the same tenant.
- In-memory commitment storage uses a composite (tenant, commitment ID) key; open commitment queries filter by tenant as well as budget/account/period.

### Actuals and control

- `ActualRepository.add/totalFor` require tenant ID.
- `ActualService`, `BudgetControlEngine` and the `BudgetingService` facade propagate tenant scope.
- In-memory actual totals are keyed by tenant, budget, account and period.

### Operational budget

- `BudgetStorePort.find` now requires tenant ID and budget ID.
- `BudgetService.find/checkAvailability/calculateVariancePercentage` require tenant scope.
- The in-memory operational budget store uses a composite (tenant, budget ID) key.

The unscoped read APIs were intentionally not retained as convenience overloads: an ID-only lookup is not an acceptable cross-tenant application contract.

## 3. Tests added or updated

- Planning budgets with the same budget ID can exist independently for two tenants.
- Commitments with the same commitment ID can exist independently for two tenants.
- A tenant cannot consume a commitment owned by another tenant.
- Actuals and commitment consumption in one tenant do not change another tenant's available balance.
- Operational budget lookups with identical IDs return only the requested tenant's budget.
- Blank tenant IDs are rejected at application/domain entry points.

Tests have been updated to use the tenant-scoped contracts. They have not yet been executed in this environment.

## 4. Security and production limitations

Tenant-scoped repository methods are necessary but not sufficient for authorization.

- The runtime must derive tenant ID from authenticated, authorized request context; it must not trust an arbitrary caller-supplied tenant string.
- Actor identity, role/policy checks, audit evidence and approval authority are not introduced by this change.
- The in-memory adapters are suitable for tests/local composition, not durable production use.
- Before a PostgreSQL adapter is added, its schema must include tenant ownership, tenant-qualified uniqueness/foreign keys and tenant predicates on every read/update/delete.
- Migration/backfill must define how existing planning budgets and commitments acquire tenant ownership. Do not infer tenant ownership from IDs or silently assign a default tenant.
- Cross-capability Spend integration remains blocked until the persistent adapter, authorization boundary, concurrency/transaction semantics and migration are implemented and tested.

## 5. Compatibility

The tenant-scoped method and constructor changes are source-breaking for direct callers of the affected Budget APIs. This is deliberate: retaining ID-only read methods would preserve an unsafe contract. All in-reactor tests inspected for these contracts were updated. Downstream composition modules and other Maven reactors still require compile verification.

## 6. Verification checklist

- [x] Read repository `AGENTS.md`.
- [x] Audit both operational and planning Budget models.
- [x] Scope planning budget and commitment domain ownership by tenant.
- [x] Scope Budget, Commitment and Actual repository read/write contracts.
- [x] Scope application services and compatibility facade.
- [x] Update in-memory adapters with composite tenant keys.
- [x] Add tenant-isolation tests for matching IDs and cross-tenant balance independence.
- [ ] Run `mvn -f budget/pom.xml test`.
- [ ] Compile downstream Maven reactors and update any direct callers.
- [ ] Design PostgreSQL persistence, tenant-safe uniqueness/foreign keys and migration/backfill.
- [ ] Add authenticated tenant/actor context and authorization checks.
- [ ] Add persistence tests for tenant isolation, concurrency, rollback and idempotency.
- [ ] Only then expose Budget as a Spend integration contract.
