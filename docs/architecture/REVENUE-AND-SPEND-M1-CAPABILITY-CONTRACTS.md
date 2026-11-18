# Revenue and Spend Management — M1 Capability Contracts

- Status: proposed contract baseline; implementation is incremental.
- Date: 2026-11-01.
- Scope: revenue operations and expenditure/spend operations across Numou.

## 1. Decision

Revenue and Spend should be first-class business capabilities, but Numou must not create duplicate invoice, receivable, payable, purchasing, budget, payment, wallet, or ledger systems.

M1 establishes ownership and integration contracts. It does not introduce replacement aggregates or persistence.

**Revenue Management** is a proposed coordination capability for source registration/classification and earning evidence only where those invariants are not already owned by Billing, Receivables, Subscription, or a domain module.

**Spend Management** is a proposed capability for internal spend requests, expense claims, approval policy, and authorization. It does not replace Purchasing, Payables, Budget, Payment, or Accounting.

A standalone Revenue Maven module is not approved by this milestone. Billing already contains Invoice and RevenueRecognition models; extraction or a new aggregate requires evidence of distinct invariants and multiple real consumers.

## 2. Existing ownership verified on main

| Capability | Existing responsibility |
|---|---|
| Billing (`finance/billing/core/numou-billing-domain`) | Billing agreements, schedules, invoices, pricing/dunning integration; includes `RevenueRecognition` |
| Receivables (`finance/accounting/numou-accounting-receivables`) | Customer invoices, payments, posting events and repository ports |
| Payables (`finance/accounting/numou-accounting-payables`) | Vendor invoices, posting events, matching and payment scheduling |
| Budget (`budget/budget-domain`) | Budget versions, lines, commitments and controls |
| Purchasing (`supply/purchasing/numou-purchasing-domain`) | Requisitions, purchase orders, receipts and procurement events |
| Accounting | Ledger, accounting policy and posting |
| Payment | Payment execution and provider integration |
| Wallet | Wallet balances, holds and movements |
| Subscription | Subscription lifecycle and billing source |

This is a code-level starting inventory, not a completeness claim for all adapters and consumers.

## 3. Ownership boundaries

### Revenue Management may own
- Stable revenue-source references and source classification.
- Earning evidence references only when no source domain already owns the earning fact.
- Orchestration from source evidence to Billing, Receivables and Accounting.
- Revenue operational read models and reconciliation references.
- Policy selection references, not accounting-policy implementation.

### Revenue Management must not own
- Invoices, billing schedules, pricing or dunning (Billing).
- Receivable balances, invoice posting, payment allocation or collections (Receivables).
- Subscription lifecycle (Subscription).
- Sales, bookings, shipments, fares, milestones, donations or contract aggregates owned by domain modules.
- Journals, chart of accounts, period close or financial statements (Accounting).
- Payment execution or wallet movements.

**Revenue design gate:** demonstrate at least two real consumers with the same lifecycle/invariants and reconcile Billing's existing `RevenueRecognition` implementation before creating a Revenue aggregate or Maven module. If no distinct invariant remains, keep Revenue as composition/read-model functionality.

### Spend Management may own
- Internal spend-request and expense-claim lifecycle.
- Purpose, requester, cost-centre/project/department references.
- Approval routing decisions and evidence.
- Policy-based authorization to proceed.
- Spend classification and source references.
- Orchestration references to Budget, Purchasing, AP and Payment.

### Spend Management must not own
- Budget versions, balances, commitment consumption or release (Budget).
- Requisitions, POs, supplier contracts, goods receipts or sourcing (Purchasing).
- Supplier invoice verification, AP balance or payment schedule (Payables).
- Payment provider state or wallet balance (Payment/Wallet).
- Accounting journals or statutory expense recognition (Accounting).

A spend request is not a PO. Approval is not a budget commitment. A commitment is not an invoice. An invoice is not a payment. An expense claim is not an accounting journal.

## 4. Contract requirements

Cross-capability contracts use stable identifiers and immutable snapshots. They must not expose another capability's mutable aggregate or persistence entity.

Side-effecting commands carry tenant ID, command/idempotency ID with documented scope, actor ID/type, correlation and causation IDs where applicable, source capability/reference, and effective/occurred-at semantics where relevant. The receiving application boundary performs authorization; caller-supplied tenant/actor IDs are context, not proof of authorization.

Use the existing `tech.kayys.numou.foundation.domain.valueobject.Money` where its currency and precision semantics fit. Do not introduce another Money type. Amount basis (gross/net/tax inclusive/exclusive) must be explicit.

### Revenue command vocabulary
- `RegisterRevenueSource`
- `RecordRevenueEvidence`
- `RequestBilling`
- `RequestRevenuePosting`
- `ReverseRevenueEvidence`

Candidate events: `RevenueSourceRegistered`, `RevenueEvidenceRecorded`, `BillingRequested`, `RevenuePostingRequested`, `RevenueEvidenceReversed`.

These do not replace Billing, AR or Accounting events. M2 must map them to existing events or justify a new integration event.

### Spend command vocabulary
- `SubmitSpendRequest`
- `ApproveSpendRequest` / `RejectSpendRequest`
- `CancelSpendRequest`
- `RequestBudgetCommitment`
- `RequestPurchase`
- `LinkSupplierInvoice`
- `RequestSettlement`

Candidate events: `SpendRequestSubmitted`, `SpendRequestApproved`, `SpendRequestRejected`, `SpendRequestCancelled`, `BudgetCommitmentRequested`, `PurchaseRequested`, `SupplierInvoiceLinked`, `SettlementRequested`.

An approval event does not imply Budget accepted a commitment; each receiving capability validates and owns its own state.

## 5. Separate lifecycles

| Lifecycle | Authority |
|---|---|
| Spend request / claim | Spend Management |
| Budget commitment | Budget |
| Requisition / PO / receipt | Purchasing |
| Supplier invoice | Payables |
| Customer invoice / collection | Billing / Receivables |
| Revenue recognition | Accounting policy and existing Billing model; reconcile ownership before extraction |
| Payment execution | Payment |
| Wallet movement | Wallet |
| Journal | Accounting |

Do not collapse these into a universal Transaction or Expense status. Cross-capability orchestration may correlate them, but must not write their states directly.

## 6. Reliability, policy and jurisdiction

Use public application ports in-process and versioned API/event contracts across process boundaries. Use outbox/inbox or equivalent durable delivery where required. Assume at-least-once delivery; consumers deduplicate by event ID and local idempotency scope. Model retries, terminal failure, compensation and reconciliation explicitly. Reversals are compensating facts linked to originals; posted history is not silently edited.

Neutral contracts must not hardcode one tax, recognition, approval or spending policy. Extensions may cover Indonesian tax/accounting, government budget rules, nonprofit fund restrictions, cooperative rules and Sharia contracts (Murabahah, Ijarah, Istisna, Syirkah). Store policy identity/version; the appropriate policy owner evaluates it. Sharia contract obligations and profit allocation remain with the relevant contract/domain capability.

## 7. M1 acceptance and M2 gate

- [x] Existing Billing, AR, AP, Budget, Purchasing, Accounting, Payment and Wallet ownership inspected.
- [x] Revenue and Spend ownership/non-goals documented.
- [x] Command/event vocabulary and context requirements documented.
- [ ] Review Billing `RevenueRecognition` behavior and tests before any extraction.
- [ ] Trace at least two revenue and two spend consumers to real source events and application ports.
- [ ] Trace Budget commitment and Purchasing requisition/PO integration; identify missing adapters.
- [ ] Verify tenant/actor authorization and idempotency at receiving application boundaries.
- [ ] Add architecture tests forbidding cross-capability persistence/entity dependencies.
- [ ] Approve concrete Java artifact/package placement before production APIs.

M2 should implement vertical slices: (1) Spend request lifecycle after checking for existing expense/request aggregates; (2) revenue source/evidence integration after reconciling Billing recognition; (3) Budget/Purchasing application-port adapters; (4) AP/AR/Billing/Accounting integration; (5) persistence, outbox/inbox, tenant isolation and replay tests.

Do not create placeholder Maven modules, empty interfaces, speculative event catalogs, duplicate Money/Invoice/Transaction models, or a universal financial transaction aggregate.

## 8. References

- [AGENTS.md](../../AGENTS.md)
- [ADR-003](ADR-003-reusable-capability-modules.md)
- [Domain capability matrix](DOMAIN-CAPABILITY-MATRIX.md)
- [Accounting ownership](../../finance/accounting/README.md)
- [Finance aggregator](../../finance/pom.xml)
- [Billing domain](../../finance/billing/core/numou-billing-domain)
- [Receivables](../../finance/accounting/numou-accounting-receivables)
- [Payables](../../finance/accounting/numou-accounting-payables)
- [Budget](../../budget)
- [Purchasing](../../supply/purchasing/numou-purchasing-domain)
