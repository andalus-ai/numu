# Revenue and Spend M1.6 — AP tenant-scoped application contract

- **Status:** Application contract and in-memory isolation implemented; runtime authorization is intentionally fail-closed
- **Branch:** `finance/revenue-spend-m1.6-ap-tenant-contract`
- **Base:** M1.5 audit commit `c237722333f43a9956825cacbfeadb0d3e6752b1`
- **Scope:** AP aggregate ownership, repository contract, application service, trusted request context, authorization port, REST composition, and unit tests

## What changed

### 1. Immutable AP accounting ownership

Added `VendorInvoiceScope` with required:
- tenant ID;
- company ID;
- ledger ID;
- fiscal period ID.

A `VendorInvoice` now requires this scope at construction and exposes it as immutable ownership. No legacy unscoped constructor remains.

Company, ledger, and period are resource scope, not tenant identity. The tenant is resolved separately from the verified request context. The authorization port must verify that the actor is entitled to operate on the selected company/ledger/period.

### 2. Tenant-qualified repository contract

Changed `VendorInvoiceRepository.find` to require both tenant ID and invoice ID. Removed ID-only lookup.

The in-memory adapter uses a composite key of tenant ID + invoice ID. It can represent the same invoice ID in two tenants without collision, and a lookup in a third tenant returns no invoice.

Writes derive the tenant key from the aggregate's immutable scope.

### 3. Trusted request context and authorization contracts

Added:
- `ApRequestContext` — verified tenant and actor identity;
- `ApRequestContextProvider` — trusted runtime context port;
- `ApInvoiceOperation` — explicit REGISTER/READ/POST/CANCEL operation;
- `ApInvoiceAuthorizationPort` — host authorization boundary for actor, accounting scope, and operation.

The Quarkus adapter `RequestContextApProvider` reads the shared `RequestContextHolder`, requires `tenantVerified()`, and fails closed if tenant or actor is absent. It does not read tenant or actor from request DTOs.

The current `DenyByDefaultApInvoiceAuthorizationAdapter` deliberately rejects every operation until a deployment supplies a real identity/policy-backed implementation. This prevents the new contract from being mistaken for complete permission enforcement. AP REST calls will be denied by default until that policy adapter is replaced/configured.

### 4. Application service enforcement

All AP operations now:
1. resolve trusted tenant and actor context;
2. use tenant-qualified repository reads;
3. authorize the requested operation against the invoice's immutable accounting scope;
4. mutate only after authorization succeeds.

Registration receives company/ledger/period as selected resource scope, binds the verified tenant from request context, and authorizes REGISTER before persisting.

The REST resource no longer injects or queries the in-memory repository directly. GET, POST, and registration route through the application service. The registration DTO carries company/ledger/period selection, but no tenant or actor field.

### 5. Tests added/updated

- same invoice ID can exist independently in two tenants;
- third-tenant lookup cannot see either invoice;
- authorization is evaluated before a state transition;
- missing trusted request context fails closed;
- registration/post/cancel lifecycle uses explicit accounting scope;
- domain fixtures now require immutable scope.

## Important limitations and follow-up

- The CDI producer still composes `InMemoryVendorInvoiceRepository`; AP is not durable and process restart loses state.
- The default authorization adapter denies all requests. A real host identity/policy adapter must validate tenant membership, company, ledger, period, and operation permissions before enabling AP.
- The request-context adapter assumes the upstream security filter has populated both verified tenant and actor. The full runtime chain needs an integration test.
- The AP PostgreSQL schema is not yet wired to the runtime. M1.7 must implement the PostgreSQL repository, additive migration, tenant-qualified query predicates/constraints, and backfill policy.
- Domain events still do not carry tenant/accounting scope, actor, correlation, causation, or idempotency metadata. M1.8 owns the versioned event/outbox contract and atomic delivery.
- This is a source-breaking AP contract change: ID-only repository methods and unscoped aggregate construction were intentionally removed. Search and migrate all consumers before merging.
- Company/ledger/period authorization and existence checks are not implemented by the deny-by-default adapter; they must be supplied by the deployment policy adapter.

## Verification

- Source changes committed to the feature branch.
- Maven compilation and tests: **not run** in this environment.
- Quarkus CDI build validation: **not run**.
- PostgreSQL migration/integration tests: **not run**.
- CI: **not run**.
- No PR opened.

## Acceptance criteria for M1.7

1. Implement and wire a PostgreSQL AP repository; no production request path may use in-memory storage.
2. Add tenant-qualified reads, updates, child-line operations, constraints, and tests.
3. Resolve existing AP row ownership through an auditable backfill; fail migration on ambiguous ownership.
4. Replace the deny-by-default authorization adapter with an identity/policy-backed implementation and test tenant/company/ledger/period permissions.
5. Verify request-context propagation and CDI composition in Quarkus tests.
