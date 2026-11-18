# Revenue and Spend M1.5 — Accounts Payable tenant-isolation audit

- **Status:** Audit complete; AP is not ready for Spend integration
- **Branch:** `finance/revenue-spend-m1-followup`
- **Audited head:** `ea078370a49c1f3b3ea9972cf4e82f54b358998f`
- **Scope:** AP domain, application service, repository contract/adapter, REST resource, CDI producer, schema migration, tests, and repository tenant-context conventions
- **Implementation:** No AP runtime or schema changes in this milestone. The findings show that a safe tenant-isolation patch requires coordinated changes across the aggregate contract, trusted request context, persistence adapter, and migration strategy.

## Executive decision

**Do not integrate Spend Management with the current AP service.** The AP migration contains tenant/company/ledger/period columns, but the live REST and CDI composition uses an in-memory repository. The AP application contract and aggregate do not carry tenant context, and all repository operations are ID-only.

This is not fixed by adding a tenant field to the request DTO. Tenant identity must come from a verified authenticated context, while company, ledger, and period must be validated as resources the actor may use. The current runtime does not demonstrate those controls.

## Evidence and findings

### P0 — Runtime AP path bypasses the tenant-aware database schema

Observed:
- `V5__accounts_payable.sql` creates `accounting.ap_vendor_invoice` with `tenant_id`, `company_id`, `ledger_id`, and `period_id`.
- No AP PostgreSQL repository implementation was found in the audited accounting adapter tree.
- `AccountingServiceProducer` produces `InMemoryVendorInvoiceRepository` and wires `ApInvoiceService` to it.
- `AccountsPayableResource` injects the same in-memory repository and uses it for `GET /{id}`.
- The resource's register and post endpoints call the in-memory-backed service; they do not use the AP SQL table.

Impact:
- AP data is not durable across process restarts in this composition.
- The tenant-aware schema provides no protection to the active REST path.
- A successful API response is not evidence that a vendor invoice was persisted to PostgreSQL.

Required remediation:
- Introduce an AP-specific PostgreSQL repository adapter behind the AP repository port.
- Wire production REST composition to that adapter; keep the in-memory adapter for tests or explicitly named development profiles only.
- Add PostgreSQL integration tests proving write/read behavior and tenant isolation.

### P0 — AP aggregate and repository contract are not tenant scoped

Observed:
- `VendorInvoice` has invoice ID, vendor ID, vendor reference, currency, lines, and status; it has no tenant/company/ledger/period ownership.
- `VendorInvoiceRepository.find` accepts only `VendorInvoiceId`.
- `VendorInvoiceRepository.save` accepts only the aggregate.
- `ApInvoiceService.post`, `cancel`, and `find` load by invoice ID only.
- `InMemoryVendorInvoiceRepository` keys its map by invoice ID only.

Impact:
- The service cannot prove that the caller belongs to the invoice's tenant.
- A guessed or leaked invoice ID is treated as sufficient lookup authority.
- The in-memory implementation cannot represent two tenants owning the same externally supplied invoice ID independently.

Required remediation:
- Introduce an explicit trusted tenant scope in application operations and require it on every repository read/write.
- Scope in-memory test adapters by tenant and test same-ID cross-tenant isolation.
- Decide whether company, ledger, and period are immutable aggregate ownership dimensions or validated references at the application boundary. Preserve their semantics from the existing SQL schema rather than silently dropping them.

### P0 — REST registration and lookup lack a demonstrated verified tenant boundary

Observed:
- `RegisterVendorInvoiceRequest` contains invoice ID, vendor ID, vendor reference, currency, and lines; it does not express a trusted tenant context.
- `AccountsPayableResource` does not resolve a verified tenant from the authenticated request context or invoke a tenant authorization port.
- The GET and POST methods call ID-only application/repository methods.
- No AP-specific authorization or tenant-scope tests exist in the inspected test set.

Impact:
- A tenant ID supplied by an HTTP client would not be a safe fix.
- AP must fail closed if the host has not established a verified tenant and actor context.

Existing reusable pattern:
- CRM's `TenantScopeProvider` contract explicitly requires a trusted verified runtime context and fails closed when missing.
- `RequestContextTenantScopeProvider` reads `RequestContextHolder` only after `tenantVerified()` is true; it does not trust request payloads or unchecked headers.
- The shared foundation `RequestContextHolder` carries tenant, actor, correlation, and request metadata.

Required remediation:
- Reuse the shared verified request-context pattern through an AP adapter/port; do not introduce another static ThreadLocal or accept tenant IDs from request DTOs.
- Add an AP authorization boundary for company/ledger/period and operation permissions. Tenant scope alone does not prove that an actor may post or cancel an invoice.
- Add tests for absent/unverified tenant, cross-tenant access, unauthorized posting, and authorized same-tenant operations.

### P1 — SQL schema has partial scope indexing but incomplete tenant-qualified relational constraints

Observed:
- `ap_vendor_invoice.id` is the global primary key.
- Scope indexes exist for `(tenant_id, company_id, ledger_id, period_id)`, vendor, and status.
- `ap_vendor_invoice_line.invoice_id` references only `ap_vendor_invoice(id)`; the child row does not carry tenant scope and the foreign key is not tenant-qualified.
- No explicit tenant-scoped uniqueness for `(tenant_id, company_id, ledger_id, period_id, vendor_id, vendor_ref)` was found.

Impact:
- A global invoice ID can still be safe if it is truly globally unique and every query is scoped, but that condition is not enforced by the application contract today.
- Child-row ownership is inherited through a global invoice FK rather than checked through a tenant-qualified relationship.
- Duplicate vendor invoice policy is undefined at the schema level; vendor reference uniqueness may be policy-dependent and should not be invented as a universal constraint.

Required remediation:
- Add tenant-qualified constraints/keys and a child-row FK strategy in an additive migration after confirming existing data and identifier assumptions.
- Define duplicate supplier-invoice policy with company/ledger/vendor context before adding a unique constraint.
- Ensure every select, update, delete, and child-line operation includes the verified tenant scope (and relevant company/ledger scope where required).

### P1 — Posting event does not carry tenant, actor, or accounting scope

Observed:
- `SupplierInvoicePosted` contains invoice/vendor references, accounts, lines, currency, and occurred-at, but no tenant/company/ledger/period, actor, correlation, causation, or idempotency metadata.
- `ApInvoiceService.post` changes aggregate state and saves it; the service does not publish through a durable AP outbox.
- The aggregate exposes an in-memory domain event list. The audited AP service does not demonstrate durable event delivery or event clearing after successful publication.

Impact:
- A downstream posting handler cannot reliably route or authorize the event by tenant and ledger from this event contract alone.
- Retries and process failure between persistence and event publication can lose or duplicate downstream effects.

Required remediation:
- Define a versioned posting event envelope with tenant and accounting scope plus actor/correlation/causation metadata.
- Persist aggregate state and outbox event atomically in the AP PostgreSQL transaction.
- Use idempotent consumers/inbox processing and test retry, rollback, and duplicate delivery.
- Keep payment execution distinct from supplier invoice posting; posting an invoice creates an obligation, not proof of payment.

### P2 — Current AP tests do not verify persistence or tenant boundaries

Observed:
- AP tests use `InMemoryVendorInvoiceRepository`.
- Tests cover register/post/cancel and unknown ID behavior.
- No AP PostgreSQL integration test, migration test, cross-tenant test, authorization test, or transactional outbox test was found in the inspected AP module.

Required remediation:
- Keep unit tests for aggregate transitions.
- Add application tests with a tenant-aware fake repository and authorization port.
- Add PostgreSQL integration tests for tenant-scoped CRUD, child-line isolation, transaction rollback, and uniqueness.
- Run the narrow accounting reactor and its dependent adapter reactor before claiming verification.

## Recommended implementation sequence

1. **M1.6 — AP contract and trusted context:** introduce a verified tenant-scope port, actor/authorization boundary, tenant-aware aggregate/repository contract, and tenant-isolated in-memory tests. Resolve company/ledger/period ownership explicitly.
2. **M1.7 — Durable AP persistence:** implement the PostgreSQL repository and additive migration; add tenant-qualified constraints and data/backfill policy; switch the production CDI producer and REST resource away from in-memory storage.
3. **M1.8 — Atomic posting and event delivery:** persist invoice transitions and a versioned tenant-scoped outbox event in one transaction; add idempotent consumer and failure/retry tests.
4. **M1.9 — Operational readiness:** add audit evidence, permission matrix, metrics, migration rehearsal, and reactor/CI verification.
5. **Only then** allow Spend expense claims to create or reference AP obligations through a public application contract. An employee expense claim remains distinct from a supplier invoice, a budget commitment, a payment, and a journal entry.

## Compatibility and migration constraints

- Do not trust tenant, actor, or authorization claims supplied by request bodies or query parameters.
- Do not silently backfill existing AP rows to an arbitrary tenant. Establish an auditable ownership mapping or stop migration when ownership is ambiguous.
- Do not rewrite the historical V5 migration for already deployed databases; use a forward-only migration and validate the exact deployment history first.
- Avoid source-compatibility overloads that preserve unsafe ID-only repository methods.
- Keep the AP capability deployment-neutral: domain/application contracts remain independent of Quarkus and PostgreSQL; adapters and composition select runtime behavior.

## Verification status

- Source inspection: complete for the files and paths listed in this audit.
- Maven compilation/tests: not run in this environment.
- PostgreSQL migration/integration tests: not run.
- CI: not run.
- No PR opened.
