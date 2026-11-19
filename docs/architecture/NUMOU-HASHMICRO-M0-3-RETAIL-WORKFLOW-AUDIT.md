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


## 11. M0.3b — Inventory and sale-completion handoff trace (2026-11-18)

### 11.1 Inventory already provides the required primitives

Additional source files inspected:

- `supply/inventory/inventory-application/src/main/java/tech/kayys/numou/inventory/application/InventoryService.java`
- `supply/inventory/inventory-application/src/main/java/tech/kayys/numou/inventory/application/TenantScopedInventoryService.java`
- `supply/inventory/inventory-spi/src/main/java/tech/kayys/numou/inventory/spi/InventoryRepository.java`
- `supply/inventory/inventory-adapter/src/main/java/tech/kayys/numou/inventory/adapter/postgres/PostgresInventoryRepository.java`
- `supply/inventory/inventory-adapter/src/main/java/tech/kayys/numou/inventory/adapter/postgres/PostgresTenantInventoryRepositoryProvider.java`
- `supply/inventory/inventory-adapter/src/main/resources/db/migration/V1__inventory_ledger_and_reservations.sql`
- `supply/inventory/inventory-adapter/src/test/java/tech/kayys/numou/inventory/adapter/postgres/PostgresInventoryRepositoryTest.java`

Verified capabilities:
- Tenant-explicit application facade scopes the repository provider and tenant-scopes idempotency keys.
- PostgreSQL repository binds each instance to one tenant and includes tenant_id in queries.
- Reservation creation accepts a durable command key, checks the payload fingerprint, serializes stock operations with stock-row locks, checks available stock, and stores a command receipt in the same transaction.
- Reservation command lookup can recover the original reservation after a lost response.
- Idempotent reserved issue atomically inserts the stock movement, changes the reservation from ACTIVE to COMMITTED and writes the command receipt.
- Reservation release/expiry uses version checks; release-if-active returns an already RELEASED or EXPIRED reservation unchanged.
- PostgreSQL tests cover same-key replay/payload conflict and a two-worker reservation race that must allow only one 6-unit reservation against 10 units.

**Boundary:** Inventory has durable primitives for the POS adapter to implement reserve/commit/release. The POS-specific implementation of CheckoutInventoryPort was not located in the inspected source set, so the actual mapping from POS reservationReference to Inventory ReservationId, and the use of stable command keys for commit, remain unverified. Do not add another stock reservation subsystem.

### 11.2 POS transactional outbox and sale lifecycle events already exist

Additional source files inspected:

- `commerce/pos/numou-pos-application/src/main/java/tech/kayys/numou/pos/application/SaleCommandService.java`
- `commerce/pos/numou-pos-application/src/main/java/tech/kayys/numou/pos/application/event/SaleLifecycleEvents.java`
- `commerce/pos/numou-pos-application/src/main/java/tech/kayys/numou/pos/application/port/out/PosOutboxWriter.java`
- `commerce/pos/numou-pos-application/src/main/java/tech/kayys/numou/pos/application/port/out/PosOutboxEvent.java`
- `commerce/pos/numou-pos-adapter/src/main/java/tech/kayys/numou/pos/adapter/persistence/PostgresPosOutboxWriter.java`
- `commerce/pos/numou-pos-adapter/src/main/resources/db/migration/V2__pos_transactional_outbox.sql`
- `docs/architecture/POS-PLATFORM-M4-TRANSACTIONAL-OUTBOX.md`

The outbox is not merely a proposed future component: a PostgreSQL writer, schema, application port and integration tests exist. SaleCommandService.open, complete and void append versioned sale lifecycle events through that port in the same command path as their sale mutation. SaleLifecycleEvents.completed emits event type `pos.sale.completed`. The outbox contract is at-least-once delivery; the current M4 documentation says a dispatcher/broker publisher is not included.

**Correction to POS-A1:** the POS outbox and sale-completed event producer do exist. The gap is that CheckoutOrchestrationService does not invoke the sale completion command or append a checkout completion event itself, and no accounting consumer/dispatcher was established by this source trace. The remaining task is to connect the existing sale/checkout lifecycle correctly and trace downstream consumers, not to create a second outbox.

### 11.3 Critical checkout-to-sale lifecycle gap

The checkout orchestration service receives only a read-only CheckoutSalePort (find by tenant and sale ID). It validates the sale is OPEN, but after successful payment and inventory commit it marks only CheckoutSession COMPLETED. It does not update the Sale aggregate to COMPLETED and does not call SaleCommandService.complete.

The separate SaleCommandService.complete does the correct sale transition and appends pos.sale.completed via the existing transactional outbox, but no invocation from CheckoutOrchestrationService was found.

This creates a dangerous split-brain lifecycle: a checkout can be COMPLETED while its source Sale remains OPEN. A later checkout with a different checkout ID can pass the sale's OPEN check and create another payment/reservation attempt against the same sale. The sale completion command also requires an expected sale version and open-shift authorization, which the checkout orchestration currently does not carry.

**POS-A7 — P0:** checkout completion and sale completion are not coordinated. Define one application-level completion contract that atomically or durably coordinates the payment-success/stock-commit result with sale state transition and the existing sale-completed outbox event. If cross-capability atomicity is impossible, model explicit intermediate states and recovery; never mark checkout complete while leaving the sale eligible for a second checkout. Add tests proving a completed sale cannot enter a second checkout, including crash/retry between stock commit and sale completion.

### 11.4 Updated implementation priorities

1. **P0 — Sale/checkout lifecycle convergence (POS-A7):** make sale completion idempotent and part of checkout recovery; preserve the existing SaleCommandService and outbox event contract.
2. **P0 — Compensation recovery (POS-A2):** make every persisted non-terminal checkout state recoverable and test the compensation-required path.
3. **P0 — Inventory adapter mapping:** prove CheckoutInventoryPort maps reservation to Inventory ReservationId and commit to idempotent issueReserved; prove release uses tenant-scoped release-if-active.
4. **P1 — Concurrent checkout create:** insert-or-load the winner for identical tenant/checkout/sale requests; reject sale mismatch.
5. **P1 — Refund status fidelity:** represent known refunded/partially refunded states separately from UNKNOWN.
6. **P1 — Accounting consumer/dispatcher:** inspect existing outbox dispatch infrastructure and accounting source-reference contracts before wiring the sale-completed event to journal posting. Do not infer that a sale-completed event alone is an accounting journal.

No source implementation was changed in M0.3b. The findings now identify the exact lifecycle seam that must be fixed without duplicating the existing inventory, payment, sale or outbox capabilities.


## 11. M0.3b — Inventory adapter and sale-to-accounting route trace (2026-11-18)

This pass follows the M0.3a next-step order: verify the concrete inventory adapter/idempotency boundary and trace the existing sale-completion event toward Accounting. The source tree and the files listed below were inspected on `main`. This is a source-level trace, not a Maven or runtime verification.

### 11.1 Inventory reservation contract and adapter gap

Inspected:
- `commerce/pos/numou-pos-application/src/main/java/tech/kayys/numou/pos/application/port/out/CheckoutInventoryPort.java`
- `commerce/pos/numou-pos-application/src/main/java/tech/kayys/numou/pos/application/CheckoutOrchestrationService.java`
- POS source tree under `commerce/pos/numou-pos-adapter`
- Inventory source tree under `supply/inventory`

Verified:
- POS defines a tenant-scoped `CheckoutInventoryPort` with `reserve`, `commit` and `release`; its contract explicitly requires durable idempotency.
- Checkout uses stable operation keys for reserve, commit and release.
- No concrete implementation of `CheckoutInventoryPort` was found in the POS adapter source tree. The inspected POS adapter POM does not declare Inventory artifacts.
- Inventory has separate domain/application/SPI/adapter boundaries and PostgreSQL persistence, but the inspected POS source does not establish a concrete bridge.

Conclusion: the port contract exists, but concrete runtime composition and idempotency are not proven. Do not claim stock reservation/commit is operational end-to-end until a host composition or adapter is located and tested.

Required next action:
1. Locate the host composition module that supplies `CheckoutInventoryPort`, if one exists outside the POS subtree.
2. Verify reserve idempotency by tenant + checkout operation key and that replay returns the same reservation reference.
3. Verify commit/release are safe terminal operations for the same reservation and tenant mismatch is rejected.
4. If no implementation exists, add a dedicated POS-to-Inventory application adapter using public Inventory contracts; never let POS write Inventory tables directly.
5. Add PostgreSQL-backed tests for duplicate reserve, commit replay, release replay, commit-vs-release races, tenant isolation and process retry.

### 11.2 Sale completion event and Accounting route

Inspected:
- `commerce/pos/numou-pos-application/src/main/java/tech/kayys/numou/pos/application/SaleCommandService.java`
- `commerce/pos/numou-pos-application/src/main/java/tech/kayys/numou/pos/application/event/SaleLifecycleEvents.java`
- `commerce/pos/numou-pos-application/src/main/java/tech/kayys/numou/pos/application/PosEventConsumer.java`
- `commerce/pos/numou-pos-application/src/main/java/tech/kayys/numou/pos/application/PosOutboxDispatcher.java`
- `commerce/pos/numou-pos-adapter/src/main/java/tech/kayys/numou/pos/adapter/persistence/PostgresPosOutboxWriter.java`
- `commerce/pos/numou-pos-adapter/src/main/java/tech/kayys/numou/pos/adapter/persistence/PostgresPosEventInbox.java`
- `commerce/pos/numou-pos-adapter/src/main/resources/db/migration/V2__pos_transactional_outbox.sql`

Verified:
- `SaleCommandService.complete` persists the completed sale and appends `pos.sale.completed` through `SaleLifecycleEvents`.
- The event is versioned (schema version 1), tenant-scoped, and carries sale, branch, register, shift, channel, currency, line count and completion timestamp.
- `PostgresPosOutboxWriter` persists the event envelope; the migration provides durable delivery state, attempts, availability, leases and publication timestamps.
- `PosOutboxDispatcher` claims bounded batches and publishes with retry/dead-letter handling.
- `PosEventConsumer` and `PostgresPosEventInbox` provide tenant + stable consumer name + event ID deduplication. The inbox marker and handler are intended to share a transaction.

Not proven:
- No concrete Accounting consumer for `pos.sale.completed` was found in the inspected POS module.
- No source evidence establishes posting instruction, account mapping, tax/discount/fee treatment, COGS valuation, legal-entity/business-date context or journal idempotency for POS sales.
- The generic inbox does not prove an Accounting handler is registered or that it posts a balanced journal.
- Checkout completion and sale completion are distinct paths; the checkout saga itself does not append an accounting posting request.

Conclusion: POS already has a durable sale lifecycle outbox and generic consumer inbox. Identify an existing Accounting integration/host consumer and posting contract before adding anything. Do not add a second POS accounting outbox or parallel posting engine before that search is complete.

### 11.3 M0.3b findings

| ID | Finding | Status | Next action |
|---|---|---|---|
| POS-A7 | `CheckoutInventoryPort` has a durable-idempotency contract, but no concrete adapter was found in the POS adapter subtree. | Open — runtime composition not proven | Locate host binding; otherwise implement a public-contract adapter and integration tests. |
| POS-A8 | Sale completion emits `pos.sale.completed` through the POS transactional outbox. | Verified at source level | Trace registered consumers and deployment composition. |
| POS-A9 | No Accounting consumer/posting mapping for the POS completion event was established in inspected source. | Open — not proof of global absence | Search host integrations and Accounting local-integration modules; verify journal source reference and idempotency. |
| POS-A10 | POS has a generic tenant-scoped inbox deduplication mechanism suitable for a logical consumer. | Verified at source level | Verify the actual Accounting consumer uses it in the same transaction as journal creation/outbox append. |

### 11.4 Decision and next implementation gate

Keep the existing POS sale outbox and Payment API boundary. Do not introduce replacement modules or direct cross-capability database writes.

Before implementing a POS-to-Inventory adapter or Accounting consumer, complete one focused composition trace:
- Search host/runtime composition modules for the `CheckoutInventoryPort` binding.
- Search Commerce/Finance local-integration modules for `pos.sale.completed` handling.
- Confirm Accounting's supported application command/handler for posting an externally sourced sale.
- Verify tenant, legal entity, branch, business date, currency and source-reference semantics.
- Establish whether the existing POS inbox/outbox can carry the handoff without a new schema.

Acceptance evidence for the next code milestone:
- A concrete, registered Inventory adapter with PostgreSQL tests for reserve/commit/release idempotency.
- An Accounting consumer that uses the existing posting API, stable source reference and idempotent event handling.
- End-to-end tests proving one completed sale produces one stock consumption and one balanced journal despite duplicate delivery and crash/retry.
- Focused POS, Inventory and Accounting Maven reactor tests plus CI run links.

**M0.3b status: source trace completed; runtime composition and Accounting posting route remain unverified. No production implementation or build/test pass is claimed in this pass.**


## 12. M0.3c — POS runtime delivery and Accounting posting contract trace (2026-11-20)

This pass inspects the POS outbox runtime, POS event inbox, Accounting local integration composition and journal posting application API. It also revisits the completion boundary to identify the minimum safe implementation contract. Source inspection only; no Maven build or test was run.

### 12.1 Correction: POS outbox dispatcher is implemented

The earlier M0.3b note that the M4 outbox documentation described the dispatcher as not included is stale relative to the current source. The current POS adapter contains:

- `PosOutboxDispatchJob`: Quarkus scheduled trigger, enabled by `numou.pos.outbox.enabled`, with skip-overlap behavior.
- `PosOutboxDispatcherProducer`: constructs the application dispatcher from configured lease, retry backoff, batch size and max-attempt values.
- `PostgresPosOutboxDeliveryStore`: PostgreSQL `FOR UPDATE SKIP LOCKED` claims, lease tokens/fencing, retry rescheduling and published marking.
- `V3__pos_outbox_delivery_leases.sql`: lease token and expiry columns plus due/expired-lease indexes.
- `V4__pos_consumer_inbox.sql`: tenant + logical consumer + event ID primary key.
- `PostgresPosEventInbox`: executes the inbox marker and consumer handler in the same transactional method.
- `PosOutboxOperations`: delivery status counts and audited dead-letter replay.

The dispatcher is bounded, publishes at least once, retries with capped exponential backoff, and moves exhausted deliveries to DEAD. The database lease token prevents an expired worker from marking a row published after another worker has reclaimed it.

**Correction to M0.3b:** the POS outbox dispatcher and generic inbox are present. The unresolved point is not the absence of dispatch infrastructure; it is the concrete publisher binding and the downstream consumer registration.

### 12.2 Runtime publisher and consumer composition

Inspected the complete `commerce/pos/numou-pos-adapter` source tree and the application ports.

- `PosOutboxDispatcherProducer` injects `PosEventPublisher` and `PosOutboxDeliveryStore`.
- The POS adapter subtree contains the dispatcher producer and delivery store but no concrete `PosEventPublisher` implementation.
- `PosEventConsumer` is a generic application helper; `PostgresPosEventInbox` supplies durable deduplication.
- No concrete Accounting handler for `pos.sale.completed` was found in the inspected POS adapter or Accounting local-integration source.
- GitHub's code-search endpoint returned incomplete results for exact-symbol searches, so this is not a proof that no host-level publisher/consumer exists anywhere in every deployment module.

The deployment must provide a real `PosEventPublisher` when `numou.pos.outbox.enabled=true`. That binding should be explicit in the host composition, and it must preserve event ID, tenant, schema version, ordering/correlation metadata and at-least-once semantics. The consumer must use a stable logical consumer name and the POS inbox (or a compatible shared inbox) in the same transaction as the durable Accounting effect.

### 12.3 Accounting posting capability exists, but POS posting policy does not appear in the inspected route

Inspected:
- `finance/accounting/numou-accounting-application/.../api/command/PostJournalEntryCommand.java`
- `finance/accounting/numou-accounting-application/.../handler/PostJournalEntryHandler.java`
- `finance/accounting/numou-accounting-application/.../spi/AccountingCommandService.java`
- `finance/accounting/numou-accounting-application/.../posting/PostingInstruction.java`
- `finance/accounting/numou-accounting-application/.../posting/PostingLine.java`
- `finance/accounting/numou-accounting-application/.../posting/ar/CustomerInvoicePostedRule.java`
- `finance/accounting/numou-accounting-application/.../posting/ar/CustomerPaymentReceivedRule.java`
- `finance/accounting/numou-accounting-application/.../posting/ap/SupplierInvoicePostedRule.java`
- `finance/accounting/numou-accounting-local-integrations/.../AccountingLocalIntegrationsProducer.java`

Verified:
- Accounting exposes a journal-posting command with explicit tenant and ledger scope and fields for source type/source ID.
- The handler validates journal/account/fiscal-period constraints, persists the journal, and appends Accounting domain events to its own outbox.
- Posting instructions enforce balanced debit and credit totals.
- Existing inspected posting rules cover AR invoice, AR customer receipt and AP supplier invoice.
- The inspected local integration producer only composes in-memory Inventory, Budget and Project Finance services. Its class documentation explicitly limits it to local development, demonstrations and isolated tests; it is not the POS-to-Accounting production bridge.

Not established:
- No POS sale revenue/tax/discount/fee/COGS posting rule or handler was found in the inspected Accounting application source tree.
- No registered consumer maps `pos.sale.completed` to `PostJournalEntryCommand`.
- No evidence proves durable deduplication of an externally sourced POS journal by tenant + sale/event source reference.
- The existence of a generic journal command does not define retail accounting policy or chart-of-accounts mapping.

### 12.4 POS completion event is not a sufficient accounting document

The current schema-version-1 `pos.sale.completed` payload contains sale ID, branch ID, register ID, shift ID, channel, currency, line count and completion timestamp. It does not contain:
- authoritative gross/net amount or tax breakdown;
- discount, promotion or rounding allocations;
- tender allocations (cash/card/QRIS/wallet), payment transaction IDs or fees;
- immutable sale-line snapshots and revenue account/tax category mapping;
- inventory cost valuation / COGS amounts;
- legal entity, accounting ledger, accounting date or effective tax-policy reference.

Therefore an Accounting consumer must **not** infer or fabricate a journal from this event. The current event is a lifecycle notification, not a complete financial posting document.

**POS-A11 — P0:** introduce an immutable, versioned financial completion snapshot at the correct POS application boundary (or a dedicated posting-request contract) that references the sale and checkout, contains the authoritative pricing/tax/tender/valuation results, and is appended transactionally to the existing POS outbox. The Accounting consumer must validate the snapshot, resolve tenant/legal-entity/ledger/account mappings, create a balanced journal and record durable source idempotency. Keep the current lightweight `pos.sale.completed` event backward compatible.

### 12.5 Minimum safe checkout-to-sale completion contract

Further inspection of `CheckoutOrchestrationService`, `CheckoutSalePort`, `SaleCommandService`, `SaleCommandContext`, `SaleRepository` and `CheckoutSession` establishes why a direct call to the existing sale command is not a safe one-line fix:

- Checkout currently has a read-only `CheckoutSalePort`; it receives a `Sale` snapshot but not its repository version.
- `SaleCommandService.complete` requires a trusted `SaleCommandContext` (tenant, actor, correlation ID) and expected sale version.
- `CheckoutOrchestrationService.start/reconcile` receives tenant, checkout ID and sale ID, but no authenticated actor/correlation context and no sale-completion port.
- `CheckoutSession` does not persist the actor/correlation context needed to recover the completion command after a process restart.
- The checkout flow commits Inventory and marks the checkout COMPLETED, but does not transition the Sale aggregate.

The safe fix needs a dedicated application-level completion contract that captures trusted initiation metadata and a stable operation key, retrieves/checks the versioned Sale state, and calls the existing idempotent sale lifecycle command. The recovery worker must be able to complete the same transition without trusting a new caller's actor identity. Preserve the actor who initiated checkout and the original correlation ID as durable audit metadata. Do not let an untrusted caller supply actor or tenant identity.

**POS-A12 — P0:** checkout completion and Sale completion must converge under one durable, replayable command. Add a persisted completion intent/operation record or extend the checkout state with the required trusted audit context and sale version. The transition must prevent a second checkout against a sale whose payment/stock effects have already succeeded. Define crash recovery for the boundary between stock commit, Sale completion and checkout terminal state.

### 12.6 Remaining recovery and payment findings confirmed

- `COMPENSATION_REQUIRED` remains in the database recoverable query and enum, but the inspected domain has no transition method into that state and `reconcile` has no explicit compensation handler. It can be loaded from persistence without a valid recovery action.
- `PaymentCapabilityCheckoutAdapter` still maps known `PARTIALLY_REFUNDED` and `REFUNDED` states to `UNKNOWN`. Refunds are known financial outcomes and require an explicit reversal/return workflow rather than provider-uncertainty semantics.
- The checkout's initial `find` then `create` is not an atomic insert-or-load operation; PostgreSQL uniqueness prevents duplicate rows but the losing concurrent caller receives a storage failure rather than the winner's session.

### 12.7 M0.3c findings and implementation order

| ID | Finding | Priority | Next action |
|---|---|---|---|
| POS-A11 | Current sale-completed event lacks financial data required for safe revenue/tax/tender/COGS posting. | P0 | Add versioned immutable financial completion snapshot to existing outbox; define Accounting mapping and source idempotency. |
| POS-A12 | Checkout can complete without Sale completion; checkout lacks trusted actor/correlation and versioned Sale completion contract. | P0 | Persist trusted initiation/audit context and use a replayable versioned Sale completion command. |
| POS-A13 | Outbox dispatch exists, but concrete `PosEventPublisher` binding is not present in the inspected POS adapter subtree. | P1 | Verify host binding in each supported runtime profile; add a composition test with the real publisher. |
| POS-A14 | No POS-specific Accounting consumer/posting rule was established in inspected Accounting local-integration/application source. | P0 | Implement consumer using existing journal command and balanced posting primitives only after the financial snapshot and account policy are defined. |
| POS-A15 | `COMPENSATION_REQUIRED` is persisted/recoverable but has no explicit domain transition/recovery behavior. | P0 | Define compensation state machine and idempotent inventory/payment recovery. |
| POS-A16 | Known refunded payment statuses are collapsed into UNKNOWN. | P1 | Preserve refund state and model refund/reversal as a distinct lifecycle. |
| POS-A17 | Concurrent first checkout creation is not insert-or-load. | P1 | Implement atomic insert-or-load with sale ID payload conflict protection and PostgreSQL race tests. |

### 12.8 Decision

Do not add a second POS outbox, a second event inbox, a second Inventory ledger or a replacement Accounting engine. The next code milestone should be split into two safe increments:

1. **M0.3d — Checkout/Sale lifecycle convergence:** trusted durable actor/correlation metadata, versioned Sale completion command, explicit compensation state machine, atomic insert-or-load, and PostgreSQL crash/concurrency tests.
2. **M0.3e — Financial posting contract:** authoritative financial snapshot, host publisher binding, Accounting consumer, tenant/ledger/account mapping, durable source idempotency, balanced journal tests and refund/return reversal policy.

Do not create accounting postings from the current lifecycle-only event. No source implementation was changed in M0.3c because the current contract lacks the trusted context and financial facts needed to make a safe cross-capability change. No Maven build or test result is claimed.
