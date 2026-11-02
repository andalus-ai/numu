# Revenue and Spend Management — M1.1 Source Trace

- Status: source inspection completed for the paths listed below; integration readiness remains incomplete.
- Date: 2026-11-01.
- Branch: `finance/revenue-spend-m1-contract-architecture`.
- Scope: inspect existing application/domain contracts before adding Revenue or Spend code.

## 1. Findings

### Revenue path

| Source | Observed contract | Consequence |
|---|---|---|
| POS `SaleCommandService` | Sale completion persists the sale and appends `SaleLifecycleEvents.completed` through `PosOutboxWriter`; command execution is tenant/actor scoped and idempotent. | POS already owns the sale lifecycle and durable completion fact. Revenue must consume this fact; it must not create a second sale or mutate POS state. |
| Hospitality `CustomerStayService` | Produces a tenant-scoped `StayQuote` after listing, unit, guest and availability checks. The inspected method is quote-only and explicitly does not create an inventory hold. | A quote is not earned revenue, a booking, a completed stay, or a receivable. Revenue integration must wait for the authoritative booking/fulfilment/payment milestone. |
| Billing `RevenueRecognition` | Existing aggregate stores contract/customer references, total value, schedules, recognized/deferred totals and status. | This is a substantial existing recognition model, so do not add a parallel RevenueRecognition aggregate. Reconcile its behavior and tests before any extraction or reuse contract. |
| Billing `InvoicePort` | Billing application requests invoice generation and invoice detail through a port returning an invoice identifier/details. | Existing Billing integration seam; a new Revenue capability should not generate invoices independently. |
| Receivables `InvoiceService` | Registers, posts, cancels and retrieves `CustomerInvoice` via its repository. | AR owns customer invoice lifecycle/posting; Revenue should reference the resulting invoice/AR identifiers rather than duplicate them. |

### Spend path

| Source | Observed contract | Consequence |
|---|---|---|
| Purchasing `RequisitionService` | Tenant-scoped draft creation, line addition, submit and approve; approval records actor ID. | Purchasing already owns requisitions. Spend may initiate/link a purchase request but must not own a parallel requisition state machine. |
| Budget `CommitmentService` | Creates, consumes and releases `BudgetCommitment`; checks budget control before creating. | Budget owns commitment state and control. Spend approval must not imply commitment acceptance. |
| Payables `ApInvoiceService` | Registers, posts and cancels vendor invoices through the AP repository. | AP owns supplier invoice lifecycle. Spend claims/requests should link to AP records when applicable, not duplicate supplier invoices. |
| Expense search | Repository code search for expense, reimbursement and expense-claim terms returned no matching results. | No existing expense-claim capability was identified by this search; this is a candidate gap, not proof that no expense-related behavior exists anywhere in the repository. |

## 2. Important integration gaps observed

1. The inspected AR `InvoiceService` and AP `ApInvoiceService` methods accept identifiers and domain values but do not expose tenant/actor context in these service signatures. This does not prove their repositories/adapters are globally unscoped; inspect those implementations before exposing them as cross-capability public contracts.
2. The inspected Budget `CommitmentService` signature has no tenant or actor context. Its repository and composition boundary need inspection before it can safely serve multi-tenant Spend orchestration.
3. Purchasing requisition operations explicitly pass tenant IDs, but the inspected service only validates a nonblank actor on approval; authorization must be verified at the application boundary and adapter/composition layer.
4. POS is the strongest inspected source for an event-driven revenue consumer because completion is written through an outbox. Verify the exact event schema, transactional atomicity of sale update plus outbox append, and consumer idempotency before implementing a projection.
5. Hospitality quote is not a revenue trigger. Trace the booking/confirmation/stay-completion and payment lifecycle before mapping any event to revenue evidence.
6. Billing `RevenueRecognition` uses `ZoneId.systemDefault()` in schedule generation and the inspected methods do not show tenant or actor context. Review its full invariants, persistence mapping, tests, rounding/remainder behavior, and deterministic clock/time-zone assumptions before treating it as a production-grade shared contract.

## 3. Proposed first vertical slices

### Spend — start with employee/operational expense claim

Implement only after confirming the repository-wide expense search and domain ownership review. Candidate lifecycle:

`DRAFT → SUBMITTED → APPROVED | REJECTED → SETTLEMENT_REQUESTED → SETTLED`

The capability should own claim evidence, claimant, purpose, cost-centre/project references, policy decision and approval history. It should call Budget through a tenant-safe commitment contract where a commitment is required, then AP/Payment only when the business process actually requires those capabilities. A claim is not automatically a supplier invoice or PO.

### Revenue — begin as an integration consumer, not a new aggregate

Use POS sale completion as the first candidate source fact because the inspected POS service publishes a lifecycle event via outbox. The first slice should prove:
- event schema and stable source reference;
- tenant-safe inbox/idempotency;
- explicit recognition/earning policy decision;
- mapping to existing Billing/AR contracts without writing their persistence;
- reconciliation and reversal behavior.

Do not treat sale completion alone as proof of cash settlement or statutory revenue recognition.

## 4. M1.1 exit criteria

- [x] Inspect POS sale completion and outbox usage.
- [x] Inspect Hospitality quote boundary and confirm it is not an earning/booking fact.
- [x] Inspect Billing recognition model and invoice port.
- [x] Inspect AR/AP invoice application services.
- [x] Inspect Budget commitment and Purchasing requisition application services.
- [x] Search for existing expense/reimbursement/claim implementation; no matching code-search results returned.
- [ ] Inspect full POS event schema and transactional outbox adapter/tests.
- [ ] Inspect Hospitality booking confirmation, completion and settlement lifecycle.
- [ ] Inspect tenant-scoped AR/AP/Budget repository and adapter implementations.
- [ ] Review Billing recognition tests, persistence, rounding and timezone behavior.
- [ ] Confirm public application ports and composition roots for each integration.
- [ ] Add contract tests for tenant isolation, idempotent replay, duplicate/out-of-order events and reversals.

## 5. Decision

No production Java code or new Maven module is added in M1.1. The source trace confirms that Revenue should initially be an event-driven integration/read-model concern, while Spend has a plausible gap around internal expense claims. Before implementing Spend, first close the tenant/authorization and Budget integration contract gaps. This avoids building a new capability on unsafe or ambiguous downstream APIs.
