# Revenue and Spend Management — M1.2 Integration Contract Findings

- Status: source-level contract review; implementation gates identified.
- Date: 2026-11-01.
- Branch: `finance/revenue-spend-m1-contract-architecture`.

## 1. POS revenue-source contract

The POS source is promising but its current `pos.sale.completed` event is **not yet a sufficient revenue fact**.

Verified:
- `SaleCommandService.complete` loads the sale using both tenant and sale ID, checks the open shift and optimistic version, persists the completed sale, then appends `SaleLifecycleEvents.completed`.
- The event envelope includes event ID, tenant ID, aggregate type/ID, event type, schema version and occurred-at timestamp.
- POS event schema version is 1.
- `PosOutboxWriter` explicitly requires implementations to join the active business transaction.
- The PostgreSQL outbox table stores tenant, aggregate identity, event identity, event type, schema version, occurrence time and JSON payload.
- POS has an outbox dispatcher, delivery store, leases and consumer inbox modules in the repository tree.

Critical limitation:
- `pos.sale.completed` payload currently includes sale ID, branch/register/shift, channel, currency, line count and completion time only.
- It does not include line snapshots, subtotal, discounts, tax, gross/net amount, payment status or settlement reference.
- `Sale.subtotal()` calculates unrounded line subtotal only; its own documentation says tax and discount policy are separate.
- POS sale completion explicitly does not execute payment or post accounting.

Decision:
- Do not derive recognized revenue from `lineCount`, sale completion, or subtotal alone.
- A future revenue consumer needs either a versioned immutable financial snapshot event with explicit amount basis and tax/discount treatment, or a tenant-scoped authoritative source read plus a stable sale revision/version.
- Payment settlement must remain a separate fact. Sale completion does not mean cash/card settlement succeeded.
- Voids/reversals need an explicit compensating event and linkage to the original source event; the existing `pos.sale.voided` only applies to an open sale and is not a completed-sale refund/reversal contract.

Transaction caveat:
- The service invokes sale persistence and outbox append sequentially. Both PostgreSQL adapters use `@Transactional`, and the writer documents active-transaction enlistment. However, the inspected service class itself has no transaction annotation, and the repository adapter obtains JDBC connections independently. The composition/runtime transaction boundary and datasource enlistment must be verified before claiming atomicity. The contract comments alone are not proof of a shared transaction in every deployment.

## 2. Hospitality revenue-source contract

Verified:
- `BookingHoldService` creates a tenant/customer-scoped booking hold from a quote.
- It uses a tenant/customer/idempotency-key lookup and delegates race-safe reservation to `AtomicStayHoldPort`.
- `StayBooking` owns HELD, CONFIRMED, CANCELLED and EXPIRED states.
- `StayBookingLifecyclePort` exposes tenant-scoped confirm/cancel and bounded expiry.
- The PostgreSQL adapter uses explicit transactions for atomic multi-night holds and locks/claims rows for concurrency.
- There is no completed-stay/check-out/fulfilled-service lifecycle in the inspected booking aggregate or lifecycle port.

Decision:
- A held or confirmed accommodation booking is a future performance obligation, not automatically earned revenue.
- Revenue integration should wait for a distinct stay-fulfilled/check-out fact and must separately correlate payment/deposit/settlement.
- Do not add a second booking lifecycle to Revenue.

## 3. Budget, AR and AP tenancy finding

### Budget
- `BudgetCommitment` has no tenant identifier.
- `CommitmentRepository` methods accept commitment ID and budget ID but no tenant context.
- The inspected in-memory repository keys commitments by `CommitmentId` alone.
- `CommitmentService` has no actor or tenant argument.

This is not safe to expose as a multi-tenant Spend contract without proving that IDs are globally unguessable and all composition/authorization boundaries enforce tenant ownership. The cleaner long-term contract is tenant-scoped repository operations and a tenant-aware budget/commitment ownership model, introduced compatibly and with migration/tests.

### Receivables
- The inspected `InvoiceRepository` methods accept only invoice ID.
- The inspected AR migration creates `ar_receivable_invoice` with a primary key on `id`, but no `tenant_id` column or tenant-qualified key.
- The inspected `InvoiceService` has no tenant/actor context.

The current inspected AR persistence schema is not tenant-scoped. Do not wire it directly into a multi-tenant Revenue capability until a tenant-scoping migration, repository contract and adapter tests exist.

### Payables
- The inspected `VendorInvoiceRepository` methods accept only invoice ID.
- The inspected AP service methods accept domain IDs/accounts but no tenant/actor context.

The AP schema/adapter implementation still needs full inspection. Treat AP as not yet approved for cross-tenant orchestration until its tenant isolation and authorization are demonstrated.

## 4. M1.2 implementation decision

Do not begin a Revenue consumer against the current POS event payload. Do not begin Spend-to-Budget or Revenue-to-AR orchestration against the inspected unscoped contracts.

Recommended sequence:

1. **POS M-next:** define a versioned financial snapshot contract for completed sales. Explicitly distinguish subtotal, discount, tax, gross/net, currency, rounding, and payment/settlement references. Keep settlement facts separate. Add tests for schema compatibility, source revision identity, void/refund compensation, and transaction atomicity in the actual composition root.
2. **Budget M-next:** design a tenant-safe budget ownership/repository/application contract. Determine whether the existing budget module is single-tenant by composition or intended multi-tenant; encode that explicitly instead of assuming.
3. **AR M-next:** add tenant scope to invoice persistence and repository/application operations with migration/backfill strategy and cross-tenant negative tests.
4. **AP M-next:** inspect all AP persistence and posting adapters, then document/implement tenant and authorization guarantees.
5. **Hospitality M-next:** define a domain-owned stay fulfillment/check-out fact before Revenue consumes hospitality.
6. Only after these prerequisites, implement the Revenue inbox/projection and Spend claim lifecycle.

## 5. Acceptance checklist

- [x] Inspect POS event schema and sale subtotal semantics.
- [x] Inspect POS transactional outbox writer and service composition boundary at source level.
- [x] Inspect Hospitality hold, booking and lifecycle contracts.
- [x] Inspect Budget commitment model/repository and in-memory adapter.
- [x] Inspect AR repository contract and migration schema.
- [x] Inspect AP repository contract and application service.
- [ ] Verify POS JTA/XA transaction behavior in runtime composition and integration tests.
- [ ] Design and test POS financial snapshot event schema.
- [ ] Define tenant-safe Budget contract and migration.
- [ ] Implement tenant-scoped AR persistence with backfill and negative tests.
- [ ] Complete AP adapter/schema tenant audit.
- [ ] Define Hospitality fulfillment/check-out event.
- [ ] Only then approve Revenue and Spend production integration.

