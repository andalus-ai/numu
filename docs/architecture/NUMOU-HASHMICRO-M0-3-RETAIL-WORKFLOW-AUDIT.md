# M0.3 — Retail reference workflow audit (initial source evidence)

- **Status:** Partial source-structure audit; end-to-end workflow trace remains open
- **Date:** 2026-11-18
- **Repository:** `andalus-ai/numu`
- **Branch:** `numou/m0-3-retail-workflow-audit`
- **Reference scenario:** SME / multi-branch retail — receive stock, sell at POS, collect payment, update inventory, account for sale and reconcile settlement.
- **Purpose:** Establish verified integration boundaries before proposing new retail/ERP implementation.

> This document records only facts supported by fetched repository files and public HashMicro product pages. Maven dependency declarations prove compile-time dependency intent, not runtime invocation, transactionality, test coverage or production readiness.

## 1. Executive finding

Numou already has distinct Commerce/POS, Supply/Inventory, Finance/Payment and Finance/Accounting capability areas. The POS adapter declares a dependency on the framework-neutral payment API and the foundation adapter. Inventory has separate domain, application, SPI and adapter artifacts. Accounting has separate domain, application and adapter artifacts.

This is a useful starting architecture. However, the fetched POMs do not establish a complete POS-to-inventory-to-accounting-to-bank-settlement workflow. The immediate gap is therefore **workflow evidence and contract tracing**, not a justified request to create replacement modules.

The most important source-level question is how a completed POS sale causes stock movement, accounting recognition, tender settlement and reconciliation, including retries and returns. Those edges must be traced through application ports, events, persistence adapters and tests before a code change is proposed.

## 2. External competitive evidence — HashMicro Retail POS

Official HashMicro pages currently advertise the following retail workflow capabilities:

- Omnichannel order management, centralized inventory, online/offline store integration, buy-online-pickup-in-store and returns.
- B2B / wholesale ordering with customer-specific pricing, bulk orders and approval flows.
- Consignment tracking and profit-sharing calculations.
- Cash, card, QRIS and digital-wallet tender acceptance; EDC integration; shift cash declarations; cashbox tracking and end-of-day reports.
- Bank settlement and automated reconciliation for card, QRIS and transfer receipts.
- Coretax/DJP e-Faktur generation.
- POS integration with inventory, accounting, procurement, HRM, CRM and e-commerce.

Sources (accessed 2026-11-18):
- https://www.hashmicro.com/id/pos
- https://www.hashmicro.com/id/hash-retail-software
- https://www.hashmicro.com/id/inventaris
- https://www.hashmicro.com/id/sistem-akuntansi

These are publicly advertised capabilities. This audit has not independently tested HashMicro's product or verified implementation depth.

## 3. Numou repository evidence fetched from `main`

### 3.1 Commerce and POS

**`commerce/pom.xml`**
- Commerce aggregator declares offering, configuration, pricing, promotion, affiliate, subscription, POS, kiosk, omnichannel and marketplace modules.

**`commerce/pos/pom.xml`**
- POS is a distinct Maven aggregator.
- It declares `numou-pos-domain`, `numou-pos-application` and `numou-pos-adapter`.

**`commerce/pos/numou-pos-domain/pom.xml`**
- Domain module depends on `numou-foundation`.
- It includes JUnit and ArchUnit test dependencies.
- A comment explicitly guards POS as a separate bounded context and warns against reusing the bare canonical Product aggregate name.

**`commerce/pos/numou-pos-application/pom.xml`**
- Application module depends on POS domain and `numou-foundation-application`.
- POM alone does not show use-case coverage or downstream integration behavior.

**`commerce/pos/numou-pos-adapter/pom.xml`**
- Adapter depends on POS application, `numou-payment-api` and `numou-foundation-adapter`.
- It declares Quarkus REST, CDI, scheduler, Micrometer, PostgreSQL JDBC and Flyway dependencies.
- Test dependencies include Quarkus JUnit 5 and Testcontainers PostgreSQL.
- The dependency on payment API is evidence of an intended payment contract boundary; it does not prove successful tender execution, idempotency or settlement reconciliation.
- The adapter POM does not directly declare Inventory or Accounting artifacts. This is not proof that integration is absent: integration may be implemented via events, other composition modules or external adapters. Trace the actual source before concluding.

### 3.2 Inventory

**`supply/pom.xml`**
- Supply aggregator separates inventory, purchasing, fulfillment and warehouse.
- Its description assigns stock ownership to Inventory, supplier commitments to Purchasing and demand execution/shipment handoff to Fulfillment.

**`supply/inventory/pom.xml`**
- Inventory is split into domain, application, SPI and adapter modules.

**`supply/inventory/inventory-domain/pom.xml`**
- Domain module declares JUnit test dependency.

**`supply/inventory/inventory-application/pom.xml`**
- Application depends on Inventory domain and SPI.

**`supply/inventory/inventory-spi/pom.xml`**
- SPI depends on Inventory domain.

**`supply/inventory/inventory-adapter/pom.xml`**
- Adapter depends on Inventory domain and SPI.
- It declares PostgreSQL JDBC and Flyway, plus JUnit and Testcontainers PostgreSQL test dependencies.
- This establishes a persistence adapter boundary, not the correctness of stock ledger invariants, reservations, valuation or POS integration.

### 3.3 Payment

**`finance/payment/pom.xml`**
- Payment aggregator declares API, gateway, method, core, PostgreSQL adapter, merchant, gRPC, REST and processor modules.
- The POM comments identify `numou-payment-fnb` and `numou-payment-util` as parked legacy modules because they reference APIs not present in the workspace. This is an explicit migration boundary to preserve.

**`finance/payment/numou-payment-api/pom.xml`**
- Describes a framework-neutral consumer contract for payment execution and refunds.
- This is the preferred POS integration seam to inspect first.

**`finance/payment/numou-payment-core/pom.xml`**
- Core currently declares Quarkus ORM/Panache, PostgreSQL, REST server/client and other framework dependencies in addition to API, gateway and method modules.
- This is a review target for the architecture audit: determine whether framework/persistence concerns are intentionally owned by this module or should be behind the existing adapter boundary. Do not refactor based on the POM alone.

### 3.4 Accounting

**`finance/accounting/pom.xml`**
- Accounting aggregator includes capabilities, domain, application, payables, receivables, consolidation, treasury, governance, adapter and local integrations.

**`finance/accounting/numou-accounting-application/pom.xml`**
- Application depends on accounting domain, payables, receivables, consolidation, treasury, document, Islamic accounting, master-data, multitenancy, reporting, workflow, tax, cost, currency, compliance and foundation application artifacts.
- This is broad compile-time composition. The audit must verify which use cases and posting contracts POS actually consumes.

**`finance/accounting/numou-accounting-adapter/pom.xml`**
- Adapter depends on accounting application and selected accounting capability modules, plus foundation adapter.
- It declares REST/CDI, PostgreSQL, Flyway, validation, OpenAPI and Testcontainers-related dependencies.

## 4. Retail workflow evidence matrix

| Workflow step | Candidate owner | Evidence fetched | Current audit conclusion |
|---|---|---|---|
| Product/price lookup | Commerce offering/configuration/pricing + POS | Commerce aggregator declares these modules; POS domain guards separate POS context | Ownership exists at module level; API and snapshot semantics not traced |
| Goods receipt | Purchasing + Inventory/Warehouse | Supply aggregator declares distinct owners | Receipt-to-stock behavior not traced |
| POS cart and checkout | POS domain/application/adapter | Three-layer POS Maven structure verified | Use cases, state machine and persistence not traced |
| Tender execution | Payment API and payment implementation | POS adapter depends on payment API; payment API describes execution/refund contract | Contract boundary intended; invocation/replay not traced |
| Stock decrement/reservation | Inventory | Inventory domain/application/SPI/adapter structure verified | No evidence yet for sale reservation/commit or concurrent oversell handling |
| Revenue/tax/COGS posting | Accounting | Accounting domain/application/adapter and cost/tax/reporting modules declared | No evidence yet for POS posting contract or balanced journal behavior |
| Shift close/cash declaration | POS / payment / finance | Not established by fetched POMs | Open audit question |
| Bank/EDC/QRIS settlement reconciliation | Payment / treasury | Payment and accounting treasury modules exist | Open audit question; no settlement-to-bank evidence fetched |
| Returns/refunds | POS + Payment + Inventory + Accounting | Payment API advertises framework-neutral refund contract | Return orchestration and compensating ledger/stock effects not traced |
| Customer receipt/profile | POS + Customer/CRM | CRM exists in repository baseline; no direct POS integration evidence fetched | Open audit question |
| Offline operation and retry | POS adapter/runtime | Scheduler and metrics dependencies declared | Not evidence of offline support or safe retry semantics |

## 5. Critical contract and invariant questions

The following questions must be answered from source code and tests before implementation:

1. What is the canonical POS sale identifier and idempotency key across checkout retries?
2. Does checkout reserve stock before tender, commit stock after payment, or use another explicit state machine?
3. How are partial payment, split tender, failed tender, void and payment timeout represented?
4. How are payment provider callbacks deduplicated and correlated to a POS sale?
5. Does a sale create an immutable accounting posting request/event with a stable source reference?
6. How are revenue, tax, discounts, payment fees, cash/card/QRIS clearing and COGS represented?
7. How are inventory cost layers/valuation and COGS synchronized with accounting?
8. How do returns restore stock and reverse revenue/tax/COGS without mutating the original posted journal?
9. How does shift close reconcile expected cash, declared cash, EDC/QRIS totals, fees and settlement batches?
10. Are tenant, legal entity, branch, warehouse, terminal, cashier and business date propagated and authorized consistently?
11. What is the transactional outbox/inbox boundary for each cross-capability handoff?
12. Which tests prove duplicate delivery, process crash, timeout, late callback, concurrent checkout and reconciliation mismatch behavior?

## 6. Competitive gaps to prioritize for Numou retail

The following are **audit targets**, not claims that Numou lacks these features:

- **P0 — Sale-to-ledger integrity:** stable sale reference, idempotent posting, balanced journals and traceable reversal.
- **P0 — Stock correctness:** reservation/commit, concurrent checkout protection, branch/warehouse ownership and stock movement trace.
- **P0 — Tender lifecycle:** split tender, failed/late payment, refunds, provider callback deduplication and settlement state.
- **P0 — Tenant and branch isolation:** company, branch, terminal, cashier, warehouse and legal-entity context.
- **P1 — Shift and cash control:** opening float, cash in/out, cashier declaration, variance approval and end-of-day close.
- **P1 — Omnichannel:** centralized ATP/stock, order source, fulfillment location, pickup, returns and channel idempotency.
- **P1 — Procurement replenishment:** reorder policy, purchase suggestion, approval and receipt-to-stock.
- **P1 — Loyalty and customer:** customer profile, points, membership, vouchers and consent-aware history.
- **P1 — Reconciliation:** bank/EDC/QRIS settlement import, fee/MDR handling, matching and exception workflow.
- **P2 — Consignment and B2B:** partner stock ownership, settlement/profit share, customer-specific price lists and credit approval.
- **P2 — Indonesia localization:** tax invoice integration and effective-dated tax rules, subject to current regulatory verification.
- **P2 — Store operations:** offline-first checkout, device health, printer/barcode integration and resilient synchronization.

## 7. Recommended next source-tracing order

1. POS domain/application/adapter: enumerate commands, queries, aggregates, repositories, REST resources, migrations and tests.
2. Payment API/core/PostgreSQL adapter: trace execute/refund contracts, attempt lifecycle, callback deduplication, outbox and settlement.
3. Inventory domain/application/adapter: trace stock ledger, reservations, movements, valuation, concurrency and migrations.
4. Accounting domain/application: trace journal creation/posting, source references, reversals, period controls and cost/tax integration.
5. POS composition and event contracts: prove the end-to-end handoff without cross-module persistence writes.
6. Run the documented Maven reactor commands and exact relevant GitHub Actions jobs; capture run URLs and reports. POM declarations are not CI evidence.

## 8. Proposed acceptance criteria for the retail reference scenario

The scenario should not be called complete until automated tests demonstrate:

- A successful sale updates the intended branch/warehouse stock exactly once.
- A retried checkout does not create a duplicate sale, payment or journal.
- A failed or timed-out tender can be safely retried or reconciled without overselling.
- A successful payment is traceable to a stable sale and settlement reference.
- Revenue, tax, discounts, payment clearing/fees and COGS are posted according to configured accounting policy.
- Refund/return creates correct compensating payment, inventory and accounting effects.
- Shift close identifies cash and electronic tender variances with auditable resolution.
- Tenant, company, branch, cashier and warehouse isolation is tested.
- Cross-capability events survive crashes and can be replayed safely.
- The relevant database migrations and integration tests pass in CI.

## 9. Limitations and status

- This pass fetched Maven POMs, not Java/Kotlin source files, SQL migrations, REST contracts or test implementations.
- No Maven build or test command was run.
- No current GitHub Actions run was inspected.
- No HashMicro demo or private documentation was accessed.
- No production-readiness score is assigned.
- No implementation defect is asserted solely from absent direct POM dependencies.

**M0.3 status: Partial.** The retail capability topology and high-value contract questions are now documented. The next pass must trace source code and tests before proposing or changing implementation.


## 10. M0.3a — POS checkout source trace (2026-11-18)

This section extends the initial POM-only audit using the current default-branch source files fetched during M0.3a. It traces the checkout application flow and its direct payment/persistence seams; it does not claim the entire retail workflow is complete.

### 10.1 Verified checkout flow

Source files inspected:

- `commerce/pos/numou-pos-application/src/main/java/tech/kayys/numou/pos/application/CheckoutOrchestrationService.java`
- `commerce/pos/numou-pos-application/src/main/java/tech/kayys/numou/pos/application/CheckoutRecoveryService.java`
- `commerce/pos/numou-pos-application/src/main/java/tech/kayys/numou/pos/domain/CheckoutSession.java`
- `commerce/pos/numou-pos-application/src/main/java/tech/kayys/numou/pos/application/port/out/CheckoutInventoryPort.java`
- `commerce/pos/numou-pos-application/src/main/java/tech/kayys/numou/pos/application/port/out/CheckoutPaymentPort.java`
- `commerce/pos/numou-pos-application/src/main/java/tech/kayys/numou/pos/application/port/out/CheckoutRepository.java`
- `commerce/pos/numou-pos-adapter/src/main/java/tech/kayys/numou/pos/adapter/payment/PaymentCapabilityCheckoutAdapter.java`
- `commerce/pos/numou-pos-adapter/src/main/java/tech/kayys/numou/pos/adapter/persistence/PostgresCheckoutRepository.java`
- `commerce/pos/numou-pos-adapter/src/main/resources/db/migration/V6__pos_checkout_sessions.sql`
- `commerce/pos/numou-pos-application/src/test/java/tech/kayys/numou/pos/application/CheckoutOrchestrationServiceTest.java`
- `commerce/pos/numou-pos-adapter/src/test/java/tech/kayys/numou/pos/adapter/persistence/PostgresCheckoutRepositoryTest.java`

Observed state path:

1. A new checkout validates that the tenant identifier is a UUID, loads the sale in tenant scope, requires an open sale with lines, and obtains the payable amount through the pricing port.
2. `STOCK_PENDING` calls inventory reserve with deterministic key `pos:<checkoutId>:reserve`, then persists the reservation reference.
3. `PAYMENT_PENDING` without a payment ID calls Payment `createOrGet` using deterministic key `pos:<checkoutId>:payment`, then validates tenant, amount and currency before attaching the payment ID.
4. Reconciliation treats pending/unknown payment outcomes as non-terminal. It commits inventory only after Payment reports `SUCCEEDED`; terminal failed/cancelled/expired outcomes release the reservation.
5. A successful payment is first persisted as `PAID`, then inventory is committed, then checkout is marked `COMPLETED`. This ordering supports retry after a crash between payment confirmation, stock commit and terminal checkout persistence, provided the inventory adapter honors the port's documented idempotency contract.
6. Recovery is tenant-scoped and bounded to 1–500 rows. The PostgreSQL query selects pending, unknown, paid and compensation-required states.

The domain state and migration enforce positive amount, currency, timestamps, required reservation references, required payment IDs for payment-bearing states, and tenant+checkout primary key. The repository performs tenant-scoped reads and compare-and-set updates by expected version. Tests cover the service's main success/pending/unknown/failure flows, retry idempotency at the fake-port level, tenant-scoped persistence reads and stale-version rejection. These are source-level test assertions only; this audit session has not run them.

### 10.2 Concrete findings and risks

| ID | Finding | Evidence | Required action |
|---|---|---|---|
| POS-A1 | No accounting-posting port, accounting event publication or outbox handoff is visible in `CheckoutOrchestrationService`. The service marks checkout completed after payment success and stock commit. | Checkout orchestration source and its declared ports | Trace sale completion/event infrastructure elsewhere. If no existing reliable handoff exists, add a transactional POS completion outbox and an accounting consumer using the existing accounting contracts; do not write accounting tables from POS. |
| POS-A2 | `COMPENSATION_REQUIRED` is selected by `findRecoverable`, and is allowed by the DB status check, but `reconcile` has no explicit branch for it. Its current path proceeds to payment lookup and then can attempt transitions that the domain rejects. | CheckoutRecoveryService, PostgresCheckoutRepository, CheckoutOrchestrationService, CheckoutSession | Define compensation state semantics and an explicit idempotent recovery/terminal path, or remove the state from the query/schema only after proving it is unused. |
| POS-A3 | Payment adapter maps `PARTIALLY_REFUNDED` and `REFUNDED` to `UNKNOWN`. | PaymentCapabilityCheckoutAdapter | Preserve refund outcomes as explicit statuses or route refund lifecycle to a separate refund/reversal workflow; do not conflate a known refund with an unknown provider outcome. |
| POS-A4 | Concurrent first attempts for the same tenant+checkout ID can race between `find` and `create`; the database primary key protects uniqueness, but the losing caller currently receives a storage exception rather than loading the winner's session. | CheckoutOrchestrationService and V6 migration | Add insert-or-load semantics for identical checkout identity and reject a conflicting sale ID; test concurrent same-key starts against PostgreSQL. |
| POS-A5 | Checkout service tests use fake inventory/payment/repository ports. The PostgreSQL tests verify persistence and optimistic conflicts, but the fetched tests do not prove the whole orchestration against real PostgreSQL plus real capability adapters. | Both test files | Add an integration test for crash/retry boundaries and adapter idempotency once the concrete inventory composition adapter is identified. |
| POS-A6 | Payment lookup validates tenant, amount and currency but does not compare the payment's caller reference to the checkout reference. | CheckoutOrchestrationService and PaymentObligation contract | Verify the payment implementation guarantees idempotency-key/request binding; consider carrying and validating callerReference in the POS snapshot as defense in depth. |

### 10.3 Payment contract evidence

The framework-neutral `PaymentApplicationPort` explicitly documents tenant authorization, durable idempotency, concurrency-safe state transitions and the rule that timeout is not proof of failure. Its `find` operation is tenant-scoped, and `requestRefund` returns a refund-operation state distinct from the payment obligation. This supports retaining Payment as the capability owner and keeping POS on its public API.

### 10.4 Implementation decision

Do not replace the existing checkout saga. The next implementation milestone should be a narrow reliability increment around the findings above, starting with:
1. Make checkout recovery exhaustive for every persisted non-terminal state, especially `COMPENSATION_REQUIRED`.
2. Make concurrent checkout creation return the winning durable session for an identical request.
3. Preserve payment refund states without mapping them to provider uncertainty.
4. Trace the existing POS sale completion and accounting integration before adding an outbox or new event contract.
5. Add real PostgreSQL concurrency/recovery tests and run the POS reactor plus focused CI jobs.

No implementation code was changed in M0.3a because the correct compensation and accounting contracts must first be aligned with the existing inventory/payment/accounting capability owners.
