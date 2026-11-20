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


## 12. M0.3c — Runtime composition and Accounting idempotency contract trace (2026-11-20)

This pass traces the current default-branch source after M0.3b. It intentionally does not add an Inventory adapter or an Accounting consumer until the existing host composition and durable command semantics are established.

### 12.1 POS runtime composition

Inspected the complete tracked POS Java source paths and the POS adapter POM.

Verified:
- The POS adapter has CDI composition for the Payment checkout adapter, POS sale repository adapter, and POS outbox dispatcher.
- The POS adapter POM declares POS application, Payment API and Foundation adapter dependencies.
- No Inventory API/SPI/application dependency is declared in the POS adapter POM.
- No implementation of `CheckoutInventoryPort` appears in the tracked POS adapter Java source paths.
- The generic POS event consumer is an application service around `PosEventInbox`; it is not itself a CDI registration of a business consumer.
- The outbox dispatcher composes a configured `PosEventPublisher`; the publisher is a provider-neutral port and does not register Accounting handlers.

Conclusion: the POS adapter has proven Payment and outbox composition seams, but **the Inventory checkout adapter and broker/Accounting consumer registration remain unproven**. The absence is bounded to the inspected POS subtree and tracked source tree, not asserted as proof that no external deployment may provide the binding.

### 12.2 Accounting local integration and posting API

Inspected:
- `finance/accounting/numou-accounting-local-integrations/.../AccountingLocalIntegrationsProducer.java`
- `finance/accounting/numou-accounting-application/.../PostJournalEntryCommand.java`
- `finance/accounting/numou-accounting-application/.../PostJournalEntryHandler.java`
- `finance/accounting/numou-accounting-application/.../JournalEntryRepository.java`
- `finance/accounting/numou-accounting-application/.../PostingInstruction.java`
- `finance/accounting/numou-accounting-application/.../IdempotencyGuard.java`
- `finance/accounting/numou-accounting-domain/.../BridgeIdempotencyRegistry.java`
- `finance/accounting/numou-accounting-adapter/.../V7__processed_command.sql`

Verified:
- `AccountingLocalIntegrationsProducer` explicitly documents itself as local-development/demo/test composition and produces Inventory, Budget and Project services backed by in-memory adapters. It is not a production POS-to-Inventory bridge.
- `PostJournalEntryCommand` carries explicit tenant and ledger scope, and includes `sourceType` and `sourceId`.
- `PostJournalEntryHandler` validates the journal, applies account movements, saves the journal and writes accounting outbox events. In the inspected handler, `sourceType` and `sourceId` are not used for lookup, uniqueness enforcement or duplicate-source rejection.
- `JournalEntryRepository` exposes save and lookup methods by journal ID/tenant/ledger, but no source-type/source-ID lookup contract.
- `PostingInstruction` independently validates balanced debit and credit totals, but this value object is not itself evidence that the POS event has a configured mapping or reaches the journal command handler.
- Accounting has a generic `processed_command` table migration and an in-memory `IdempotencyGuard`. The inspected `PostJournalEntryHandler` does not use either of these to protect a source event from replay.
- `BridgeIdempotencyRegistry` and its inspected in-memory implementation cover bridge dispatch bookkeeping, not proof of durable journal posting idempotency for POS sale events.

Conclusion: Accounting has useful primitives, but **the current inspected journal posting path does not demonstrate durable idempotency by source event or source business reference**. A repeated POS event could therefore be dangerous if a future consumer simply invokes this handler repeatedly without a durable transactional guard. This is a verified integration gap in the inspected path, not a claim that every Accounting entry point is non-idempotent.

### 12.3 M0.3c findings

| ID | Finding | Status | Required next action |
|---|---|---|---|
| POS-A11 | No concrete `CheckoutInventoryPort` implementation was found in POS tracked adapter sources; POS POM does not depend on Inventory contracts. | Open — no registered production binding established | Implement a dedicated adapter against public Inventory application/SPI contracts, or document the external host binding and prove it with a composition test. |
| POS-A12 | POS publisher port and generic inbox exist, but no Accounting event consumer registration was found in inspected composition. | Open — deployment registration not established | Add a consumer in an explicit composition/integration module and test broker delivery through the real inbox. |
| POS-A13 | Accounting command includes `sourceType`/`sourceId`, but inspected journal handler ignores them and does not consult the generic processed-command table or in-memory guard. | Verified in inspected handler | Add durable tenant + ledger + source-type + source-id uniqueness/claim semantics at the Accounting persistence boundary; store/replay the result and commit claim, journal, account movements and outbox atomically. |
| POS-A14 | Accounting's generic `processed_command` migration exists, but the inspected handler does not demonstrate its use. | Verified source separation | Reuse only after verifying schema scope, atomicity, retention and replay-result semantics; do not add a parallel idempotency table by default. |
| POS-A15 | Local Accounting composition uses in-memory Inventory adapters and is explicitly non-production. | Verified | Do not reuse this producer as production POS composition. |

### 12.4 Implementation decision

M0.3c completes the source/composition audit gate. The next implementation milestone is **M0.4 — Retail Integration Reliability**, split into two independently testable slices:

1. **M0.4a — Accounting source idempotency:** define the stable external source identity (tenant, ledger, source type, source ID), inspect and reuse the existing processed-command infrastructure where safe, add a durable unique constraint/claim protocol, and guarantee that account movements, journal save, processed result and accounting outbox are one transaction. Duplicate requests must return the original journal/result without applying account movements again. Conflicting payloads for the same source identity must fail explicitly rather than silently returning a different result.
2. **M0.4b — POS-to-Inventory adapter and consumer composition:** bind `CheckoutInventoryPort` through Inventory's public contract; bind the POS outbox publisher to the selected transport; register a stable Accounting consumer name using the existing inbox contract; verify tenant and event identity propagation and the transactional relationship between inbox claim and journal posting.

Do not combine the two slices into a new monolithic retail service. POS owns sale lifecycle, Inventory owns stock ledger/reservations, Payment owns tender/refund lifecycle, and Accounting owns journals and source idempotency.

### 12.5 M0.4 acceptance gates

- Concurrent duplicate accounting commands with the same tenant/ledger/source identity produce exactly one journal and one set of account balance changes.
- A retry after a process crash returns the previously committed result.
- Same source identity with a different canonical payload is rejected and audited.
- Journal, account updates, processed-command result and accounting outbox are committed atomically.
- Duplicate `pos.sale.completed` delivery is harmless through the registered stable consumer.
- Inventory reserve/commit/release replay returns the original result and cannot cross tenant boundaries.
- Integration tests run with PostgreSQL; no in-memory fake is accepted as sole proof of the durable path.
- Focused POS, Inventory and Accounting Maven reactor builds/tests pass in CI.

**M0.3c status: source and composition trace completed. M0.4 implementation is now sufficiently scoped; no code change or passing test is claimed in this audit milestone.**

## 13. M0.4a — Accounting posting durability gate (2026-11-20)

This is a source-level architecture gate before implementing source-event idempotency. The objective is to ensure one externally sourced retail sale can create one durable journal without double-applying account balances or emitting duplicate Accounting events.

### 13.1 Runtime persistence findings

Inspected on the current `main` source tree:

- `finance/accounting/numou-accounting-application/src/main/java/tech/kayys/numou/accounting/application/handler/PostJournalEntryHandler.java`
- `finance/accounting/numou-accounting-application/src/main/java/tech/kayys/numou/accounting/application/port/JournalEntryRepository.java`
- `finance/accounting/numou-accounting-application/src/main/java/tech/kayys/numou/accounting/application/port/AccountRepository.java`
- `finance/accounting/numou-accounting-adapter/src/main/java/tech/kayys/numou/accounting/interfaces/repository/InMemoryJournalEntryRepository.java`
- `finance/accounting/numou-accounting-adapter/src/main/java/tech/kayys/numou/accounting/interfaces/repository/InMemoryOutboxRepository.java`
- `finance/accounting/numou-accounting-adapter/src/main/java/tech/kayys/numou/accounting/interfaces/repository/PostgresAccountRepository.java`
- `finance/accounting/numou-accounting-adapter/src/main/java/tech/kayys/numou/accounting/interfaces/producer/AccountingServiceProducer.java`
- `finance/accounting/numou-accounting-adapter/src/main/resources/db/migration/V2__journal_entry_tables.sql`
- `finance/accounting/numou-accounting-adapter/src/main/resources/db/migration/V3__transactional_outbox.sql`
- `finance/accounting/numou-accounting-adapter/src/main/resources/db/migration/V7__processed_command.sql`
- `finance/accounting/numou-accounting-adapter/src/main/resources/db/migration/V14__general_ledger_tenant_ledger_scope.sql`
- `finance/accounting/numou-accounting-application/src/test/java/tech/kayys/numou/accounting/application/CqrsAndOutboxTest.java`

Verified observations:

1. `PostJournalEntryHandler` loads each account, mutates its in-memory aggregate, saves each account, saves the journal, and then saves emitted outbox events as separate repository operations. The handler does not establish a transaction around these operations.
2. The inspected adapter contains `InMemoryJournalEntryRepository` and `InMemoryOutboxRepository`; the recursive Accounting adapter source tree contains no PostgreSQL implementation of `JournalEntryRepository` or `OutboxRepository`.
3. `AccountingServiceProducer` injects those repository ports into the command handler. The current CDI bean implementations found for journal and outbox persistence are in-memory.
4. `PostgresAccountRepository.save` opens and closes its own JDBC connection for each call. A sequence of account saves cannot be assumed to share a transaction with journal persistence or outbox writes.
5. The `TransactionPort` in the Accounting application is a payment-transaction lookup/reconciliation port. It is not a database unit-of-work or transaction manager.
6. V2/V14 define durable journal and journal-line tables, and V3 defines an outbox table. Schema existence does not prove that the command handler writes through PostgreSQL adapters.
7. V7 defines `processed_command(request_id, command_type, processed_at, result_token)`. In the inspected posting handler and repository interfaces, no durable claim/read/update integration with this table was established. Its presence alone does not provide posting idempotency.
8. `PostJournalEntryCommand` carries `sourceType` and `sourceId`, but the inspected handler does not use them to look up an existing source posting, reject a changed payload, or return a previously committed journal.
9. `CqrsAndOutboxTest` uses in-memory account, journal and outbox test doubles. It proves the tested command path's expected behavior with those doubles, not atomic PostgreSQL commit/rollback or concurrent source-event replay.

### 13.2 Why source idempotency must not be implemented in isolation

A unique source key or in-memory guard would not close the correctness gap while account balances, journal rows, processed-command receipt and outbox events are persisted independently. A crash between these writes can leave partial accounting state; a concurrent duplicate can race before the original source claim is durably visible.

Therefore M0.4a is gated on a single persistence transaction that owns all state changed by journal posting. The transaction must include:
- source identity claim and canonical request fingerprint;
- account balance/movement writes;
- journal header and lines;
- durable processed-command result (or an explicitly selected replacement with a migration strategy);
- Accounting outbox event rows.

The uniqueness scope for an external posting is tenant + ledger + normalized source type + source ID. Replaying the same identity and same canonical payload must return the original journal/result. Reusing the identity with a different payload must fail with a stable idempotency-conflict outcome. A duplicate race must not apply balances or outbox events twice.

### 13.3 Correct implementation sequence

1. Add a shared Accounting persistence transaction boundary (or equivalent JDBC unit-of-work) that all participating PostgreSQL repositories can join; do not open independent connections per repository operation inside the posting command.
2. Implement PostgreSQL journal-entry and outbox repositories and wire them into the production CDI composition. Ensure journal header/lines and outbox writes use the same connection/transaction.
3. Refactor the account posting write path so balance updates are concurrency-safe and use the same unit-of-work; prefer ledger movement rows as the authoritative audit trail if already supported by the domain/schema, and avoid blind read-modify-write lost updates.
4. Define and migrate the durable source-posting receipt with a unique tenant/ledger/source identity and canonical payload fingerprint. Reuse V7 only if its request identity and result semantics can be made safe without breaking existing consumers; otherwise introduce a deliberate versioned migration.
5. Integrate claim/replay/conflict behavior into `PostJournalEntryHandler` and ensure the result and outbox are committed atomically.
6. Add PostgreSQL integration tests for duplicate replay, payload conflict, concurrent duplicate requests, tenant/ledger isolation, rollback after injected failure at each write boundary, and outbox exactly-once persistence. Run the Accounting Maven reactor and capture CI evidence.

### 13.4 M0.4a status and decision

**Status: implementation blocked by missing durable composition and transaction boundary; source audit complete for the inspected posting path.**

No Java implementation is claimed in this pass. The correct next code increment is the shared JDBC transaction/unit-of-work plus durable journal/outbox composition, followed by source-event idempotency inside that transaction. POS-to-Inventory and POS-to-Accounting runtime composition remain separate downstream milestones; do not wire `pos.sale.completed` to the current in-memory journal posting path and call it production-ready.

