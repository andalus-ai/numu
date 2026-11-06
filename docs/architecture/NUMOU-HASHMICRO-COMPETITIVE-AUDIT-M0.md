# Numou Competitive Audit — M0 Baseline

- **Status:** Initial evidence-backed baseline; follow-up source tracing required
- **Date:** 2026-11-05
- **Repository:** `andalus-ai/numu`
- **Branch:** `numou/m0-competitive-audit`
- **Purpose:** Establish a truthful Numou baseline for competitive planning against HashMicro and other ERP/business platforms.

> This document distinguishes verified repository evidence, target architecture, and unverified capability maturity. A directory, POM, roadmap, API name, or architecture proposal is not proof of an end-to-end production workflow.

## 1. Executive assessment

Numou already has a documented capability-first architecture and a broad set of business and platform areas. Its strategic opportunity is not to reproduce an ERP module catalog, but to compose reusable capabilities into complete, deployable business applications.

The principal competitive risk is the gap between architectural breadth and customer-ready workflow depth. The next audit must verify whether common business journeys are complete across UI/API, application orchestration, domain invariants, persistence, authorization, reporting, integrations, operational support, and deployment.

**Initial conclusion:** preserve and strengthen the existing capability architecture; do not introduce a parallel ERP core, universal business-object engine, second tenant/party/finance model, or competing application runtime.

## 2. Repository facts verified on 2026-11-05

| Finding | Evidence | Interpretation |
|---|---|---|
| Repository identity and default branch | GitHub repository lookup | `andalus-ai/numu`, default branch `main` |
| Repository has no root Maven POM | README.md; ADR-003; application platform M1 audit | Build and test through documented reactor roots; do not run a fictitious root build |
| Agent safeguards are established | `AGENTS.md` | Follow ownership, dependency, testing, compatibility and PR rules |
| Capability-first architecture is explicit | README.md; ADR-003 | A capability is a business boundary, not automatically a microservice or deployment unit |
| Existing application-platform design exists | `NUMOU-APPLICATION-PLATFORM-M1-FOUNDATION.md` and linked M2-M7 plans | Extend the existing application composition/lifecycle work; do not start a duplicate installer/runtime |
| Workspace is an existing capability area | M1 architecture audit | Keep workspace optional for applications that do not need collaboration |
| Commerce already groups multiple capabilities | M1 architecture audit | Audit and compose existing offering, pricing, promotion, affiliate, subscription, POS, kiosk, omnichannel and marketplace work before adding alternatives |
| Finance is an existing area with distinct lifecycles | M1 architecture audit and capability matrix | Reuse finance contracts; do not conflate payment, wallet, billing, receivables and accounting |
| Capability ownership matrix is normative target guidance | `DOMAIN-CAPABILITY-MATRIX.md` | Reuse shared semantics only when invariants and lifecycle genuinely match |
| Existing maturity is incremental | README.md, ADR-003, AGENTS.md and M1 audit | Architecture statements must not be reported as implementation completion |

### Audit coverage limitation

This is an initial targeted baseline, not a complete source-code audit. It has not yet established:
- a complete inventory of every Maven reactor, artifact, source package, public API and database schema;
- an exhaustive dependency/call graph or duplicate-domain-model report;
- end-to-end test evidence for every advertised product capability;
- a complete UI, accessibility, mobile/offline, deployment, upgrade and operational-support assessment;
- a verified, feature-by-feature current HashMicro product and commercial comparison.

No capability is marked production-ready solely from its presence in the repository or an architecture document.

## 3. Existing architecture and reuse decisions

| Existing area / project | Competitive role | M0 direction |
|---|---|---|
| Foundation, tenancy, identity, security, organization | Enterprise platform foundation | Audit actual contracts and tenant/actor propagation; reuse authoritative owners |
| Party, contact, address and customer-related capabilities | Master data and customer identity | Trace identity ownership and prevent duplicate party/customer models |
| Finance, accounting, billing, payment, wallet, Syirkah | Financial system of record and specialized financial workflows | Keep financial lifecycles separate; reuse Money where semantics fit; verify posting/replay/reconciliation evidence |
| Commerce and product | POS, catalog, pricing, promotions, subscriptions, affiliate, omnichannel and marketplace | Build application compositions from existing owners |
| Supply, inventory, warehouse and procurement | Source-to-stock and operational fulfillment | Verify receipt, reservation, valuation, replenishment and accounting handoffs |
| CRM and support | Lead-to-customer and service workflows | Verify complete customer journeys and tenant-scoped authorization |
| Workforce and attendance | People and field operations | Verify schedule, attendance, policy, payroll and branch/area behavior |
| Construction and project | Neutral project/WBS plus construction extensions | Preserve neutral core; verify consultant, BoQ/AHSP, contract and financial workflows separately |
| Logistics, courier, cargo and mobility | Shipment, fleet, driver and location operations | Reuse Geo, Party, Finance and Workflow through explicit contracts |
| Workspace, knowledge and structured data | Collaboration and business context | Keep optional; do not make it the mandatory data model for typed ERP domains |
| Gamelan | Workflow orchestration | Integrate through documented contracts; avoid duplicate workflow engines |
| Iket | API gateway and traffic infrastructure | Integrate rather than reimplement gateway concerns |
| Application Platform | Application descriptor, dependency resolution, tenant installation and lifecycle | Continue its existing M1-M7 roadmap; reconcile current implementation/CI before extending |
| Developer portal / ecosystem | Developer onboarding, API discovery and extensions | Consume stable business APIs/events; do not own gateway/runtime concerns |

This table records ownership direction, not verified production completeness.

## 4. Competitive capability scorecard

Use the following maturity scale during the detailed source-tracing pass:

- **0 — Not found:** no implementation evidence located.
- **1 — Designed:** architecture/ADR/specification exists; implementation not established.
- **2 — Partial:** code exists, but at least one essential production workflow or boundary is incomplete.
- **3 — Implemented:** end-to-end workflow and automated tests exist for the declared scope.
- **4 — Production evidenced:** security, tenant isolation, failure recovery, observability, migrations, operations and release evidence are verified.

Do not assign a score without linking evidence: source path, contract/API, test path, CI run or deployment/operations artifact.

| Competitive domain | Required audit scope | Evidence needed before scoring |
|---|---|---|
| Finance and accounting | GL, journals, AP, AR, cash/bank, closing, fixed assets, budgeting, consolidation | Posting invariants, reconciliation, period controls, tenant isolation and integration tests |
| Procurement | Request, RFQ, supplier, PO, receipt, invoice handoff | Complete procure-to-pay journey, approvals, idempotency and inventory/AP contracts |
| Sales and CRM | Lead, opportunity, quote, order, invoice, customer service | Lead-to-cash journey, customer ownership, authorization and reporting |
| Inventory and warehouse | Stock ledger, locations, reservations, transfer, valuation, count | Concurrency, stock invariants, valuation and accounting integration |
| POS and commerce | Catalog, pricing, promotion, checkout, payment, returns, omnichannel | Sale-to-settlement and return workflows, offline/retry behavior and reconciliation |
| HR and workforce | Employee, attendance, shifts, leave, payroll, branch/area policy | Policy correctness, effective dating, privacy and payroll evidence |
| Manufacturing | BOM, routing, MRP, work orders, costing, quality | Plan-to-produce workflow and inventory/finance integration |
| Construction and projects | Project, WBS, schedule, BoQ/AHSP, variation, certificates, HSE | Neutral-core reuse and at least one complete consultant/contractor workflow |
| Logistics and mobility | Shipment, custody, dispatch, fleet, driver, geo, settlement | End-to-end custody, location freshness, retries, finance and operational recovery |
| Workspace and collaboration | Pages, blocks, databases, relations, revisions, search, permissions | Tenant-scoped APIs, revision integrity, persistence and authorization tests |
| Business process | Approval, rules, delegation, escalation, SLA, audit | Reusable contracts and at least two real consuming capabilities |
| Analytics and AI | Reporting, metrics, forecasts, assistants and agents | Metric provenance, permission-aware retrieval, action authorization and audit |
| Application platform | Descriptor, catalog, resolver, install, configure, upgrade, rollback | Existing M1-M7 contract and implementation/CI evidence |
| Developer ecosystem | API/event catalog, SDK, CLI, sandbox, extensions, marketplace | Versioned contracts, compatibility, trust and sandbox/security model |
| Runtime and SaaS | Standalone, monolith, distributed, provisioning, billing, metering | Reproducible packaging, dependency resolution, tenant lifecycle and upgrade evidence |
| Localization | Indonesian accounting/tax/payroll/banking/sharia adapters | Versioned policy adapters, effective dates, verified integrations and compliance review |
| Enterprise operations | SSO, authorization, audit, secrets, DR, observability, support | Security testing, backup/restore drills, runbooks, incident and SLA evidence |

## 5. Required end-to-end reference scenarios

The competitive audit must evaluate workflows, not just module presence.

1. **SME retail:** configure company/branch, catalog and price, receive stock, sell through POS, accept payment, issue receipt, update inventory, post accounting and reconcile settlement.
2. **Multi-branch distributor:** onboard supplier/customer, procure goods, receive into warehouse, allocate stock, fulfill sales orders, dispatch fleet, invoice, collect and consolidate branch results.
3. **Manufacturing:** define BOM/routing, plan demand, procure materials, issue materials, execute production, record yield/scrap, receive finished goods, calculate cost and post accounting.
4. **Construction consultant:** establish client/project, WBS, schedule, BoQ/AHSP, consultant assignment, site evidence, variation/approval, progress valuation and financial reporting.
5. **Enterprise group:** operate multiple legal entities and branches with isolated access, intercompany transactions, period controls, consolidated reporting and audit.
6. **SaaS application provider:** compose a product from selected capabilities, resolve dependencies, provision tenant, configure entitlements, upgrade safely and observe runtime health.

For each scenario, record:
- actor and authorization policy;
- initiating command and API/UI entry point;
- owning capability and public contract;
- persisted state and transaction boundary;
- emitted events and idempotency/retry behavior;
- downstream accounting/inventory/customer effects;
- failure and compensation path;
- reports/read models;
- test and CI evidence;
- operator/customer support path.

## 6. M0 implementation work packages

### M0.1 — External competitive product audit
- Build a source-cited HashMicro product catalog from current official product pages, documentation and demonstrations where publicly accessible.
- Separate advertised features, documented workflows, integrations, deployment options and commercial/implementation claims.
- Mark unknowns explicitly; do not infer implementation depth from marketing language.
- Record access date and source URL for every claim.

### M0.2 — Numou repository inventory
- Enumerate every Maven reactor root and its modules.
- Inventory domain, application, API/REST, persistence, messaging, UI and runtime artifacts.
- Record public contracts, database ownership, test suites, CI workflows and deployment artifacts.
- Identify duplicate or overlapping concepts for investigation, not automatic deletion.

### M0.3 — Capability gap and workflow audit
- Score the domains in section 4 using the maturity scale.
- Trace the six reference scenarios in section 5.
- Separate missing features from incomplete integration, UX gaps, operational gaps and documentation gaps.

### M0.4 — Architecture and dependency audit
- Inspect actual Maven dependencies and source-level imports.
- Find reverse dependencies, shared-table coupling, duplicate domain models and unclear owners.
- Inspect tenant/actor/correlation/idempotency propagation and authorization enforcement.
- Review outbox/inbox, retry, dead-letter and reconciliation behavior where relevant.
- Preserve existing ADR-003 and capability matrix boundaries.

### M0.5 — Target architecture and ownership
- Update the capability ownership matrix only where evidence demonstrates ambiguity or duplication.
- Map Numou, Iket and Gamelan responsibilities explicitly.
- Reconcile the ERP competitive plan with the existing Application Platform M1-M7 roadmap.
- Keep application, capability, package, runtime and deployment topology distinct.

### M0.6 — Prioritized implementation backlog
- Rank gaps by customer value, competitive importance, dependency readiness, risk and implementation effort.
- Define milestone-sized work packages, acceptance criteria, affected reactors, migration strategy and test evidence.
- Identify work that should be reused, completed, refactored, integrated or deferred.
- Produce a release-oriented plan; do not create a PR for every small change.

## 7. Architecture guardrails for the competitive program

1. No duplicate owner for tenant, identity, party, money, accounting, payment, inventory, workflow or application installation.
2. No industry-specific requirement in a neutral capability unless the invariant is genuinely shared.
3. No application composition that grants authorization merely because a capability is installed or visible in navigation.
4. No cross-capability direct writes to another capability's persistence model.
5. No claim of production readiness without automated test and operational evidence.
6. No assumption of one root Maven build; discover and invoke real reactor roots.
7. No mass module moves or renames as a substitute for boundary correction.
8. No parallel application runtime, workflow engine or API gateway where existing projects already own those responsibilities.
9. Keep existing public API/event compatibility and provide explicit migration plans.
10. Prefer coherent milestone PRs; do not merge without explicit authorization.

## 8. Exit criteria for M0

M0 is complete only when all of the following are available:

- A source-cited HashMicro product/feature catalog with unknowns called out.
- A repository inventory covering all Maven reactor roots and module roles.
- A scored Numou capability matrix with evidence references.
- Six reference workflows traced end to end, with gaps and owners.
- A dependency/duplication/tenant-security/reliability findings register.
- A reconciled target architecture that builds on ADR-003 and the existing Application Platform roadmap.
- A prioritized milestone backlog with acceptance criteria and test strategy.
- Explicitly documented audit coverage limitations and unresolved questions.

## 9. Immediate follow-up

The next source-tracing pass should enumerate the repository's Maven reactor roots and CI workflows, then inspect high-value finance, commerce, inventory, CRM, workforce, construction, logistics, workspace and application-platform contracts. This document is an initial baseline and does not claim those deeper inspections or M0 exit criteria are complete.


## 10. M0.2 source-tracing update — verified reactor samples

The following aggregator POMs were fetched from `main` on 2026-11-05. This is a targeted sample, not an exhaustive repository reactor inventory.

| Reactor path | Verified declared modules / role | Evidence |
|---|---|---|
| `core/pom.xml` | `numou-platform`; aggregator includes `Framework` | `core/pom.xml` |
| `finance/pom.xml` | Accounting, billing, common, payment, subscription, Syirkah, tax, and wallet SPI/domain/application/adapter/PostgreSQL adapter | `finance/pom.xml` |
| `commerce/pom.xml` | Offering, configuration, pricing, promotion, affiliate, subscription, POS, kiosk, omnichannel, marketplace | `commerce/pom.xml` |
| `workspace/pom.xml` | Workspace domain, application, JDBC persistence, REST adapter and notification adapter | `workspace/pom.xml` |
| `crm/pom.xml` | CRM domain, application and adapter | `crm/pom.xml` |
| `supply/pom.xml` | Inventory, purchasing, fulfillment and warehouse | `supply/pom.xml` |
| `logistics/pom.xml` | Fleet plus courier domain, application and PostgreSQL adapter | `logistics/pom.xml` |
| `mobility/pom.xml` | Mobility kernel, pricing, driver and ride domain/SPI/application/adapters, integration, persistence and API | `mobility/pom.xml` |
| `construction/pom.xml` | Construction domain, SPI, application and adapter | `construction/pom.xml` |
| `workforce/pom.xml` | Workforce domain, SPI, application, adapter, JDBC persistence and HRIS | `workforce/pom.xml` |

### Findings from the sample

1. Finance and Commerce already contain meaningful product breadth. The competitive question is workflow completeness and integration, not whether those module names exist.
2. Finance wallet has explicit SPI, domain, application, adapter and PostgreSQL adapter artifacts in its aggregator. This is evidence of modular decomposition, not proof of production readiness.
3. Workspace is explicitly separated into domain/application/persistence/REST/notification artifacts; its own descriptor states it is optional.
4. CRM, Construction and Workforce use recognizable domain/application/adapter layering, while their complete test and runtime behavior still requires source tracing.
5. Supply and Logistics are distinct aggregators; cross-capability handoffs must be verified through contracts rather than inferred from co-location.
6. Mobility has a relatively broad set of subdomains and integration artifacts; the audit should verify its current contracts and tests rather than introduce parallel driver/ride/geo models.

### Remaining M0.2 work

- Enumerate all aggregator POMs, including nested reactor roots and shared parent relationships.
- Inspect root-level GitHub Actions workflows and map each workflow to its actual Maven reactor commands.
- Inventory test modules, architecture tests, database migrations and deployment/runtime entry points.
- Inspect actual public ports and persistence ownership for the sampled capability groups.
- Build an evidence-backed duplicate-model and reverse-dependency register.

The sampled POMs are structural evidence only. No build, test, CI, runtime, or end-to-end workflow result is claimed by this update.


## 11. M0.2 repository-wide topology and CI inventory — verified

A recursive Git tree was fetched from `main` on 2026-11-06. GitHub reported `truncated=false` for the tree response.

### Repository topology counts

| Measure | Verified result | Interpretation |
|---|---:|---|
| Git tree entries | 15,044 | Complete recursive tree response for this fetch |
| Maven POM files | 482 | Includes parent, aggregator, leaf and nested module POMs; not 482 independent reactors |
| Top-level reactor POMs | 44 | POMs at repository root or one directory below it |
| GitHub Actions workflow YAML files | 15 | Workflows under `.github/workflows/` |

The 44 top-level reactor POM paths are:

```text
advertising/pom.xml
application-platform/pom.xml
asset/pom.xml
attendee/pom.xml
automotive-parts-purchasing/pom.xml
automotive-parts/pom.xml
budget/pom.xml
commerce/pom.xml
common/pom.xml
construction/pom.xml
core/pom.xml
corporate/pom.xml
crm/pom.xml
developer-platform/pom.xml
document/pom.xml
documents/pom.xml
education/pom.xml
events/pom.xml
finance/pom.xml
geo/pom.xml
history/pom.xml
hospitality/pom.xml
identity/pom.xml
logistics/pom.xml
mobility/pom.xml
notification/pom.xml
organization/pom.xml
product/pom.xml
project/pom.xml
quantity/pom.xml
queue-management/pom.xml
reservation/pom.xml
restaurant/pom.xml
scheduling/pom.xml
security/pom.xml
supply/pom.xml
support/pom.xml
survey/pom.xml
talent/pom.xml
tenancy/pom.xml
transport/pom.xml
vehicle-service/pom.xml
workforce/pom.xml
workspace/pom.xml
```

This is a repository-level discovery list, not proof that every POM is a standalone build entry point. Nested aggregators and parent relationships still need to be resolved from each POM's `<modules>`, `<parent>` and `relativePath` declarations.

### Verified workflow catalog

| Workflow | Scope observed in workflow definition |
|---|---|
| `ci.yml` | Module topology and dependency-boundary verifiers; organization invitation, queue, wallet PostgreSQL, framework/logistics verification jobs |
| `application-platform.yml` | Framework plugin manifest/resolver; tenant installation lifecycle; PostgreSQL installation store/outbox |
| `crm-postgres.yml` | CRM migration ordering, tenant relationship constraints, ownership proposal and function privilege checks |
| `payment-capability.yml` | Payment reactor verification |
| `runtime-platform.yml` | Application runtime lifecycle tests in foundation application module |
| `reliability-postgres-integration.yml` | Reliability adapter tests |
| `developer-platform.yml` | Developer platform verification (detailed job mapping remains to inspect) |
| `advertising-capability.yml` | Advertising capability verification (detailed job mapping remains to inspect) |
| `events-ticketing.yml` | Events/ticketing verification (detailed job mapping remains to inspect) |
| `history-postgres.yml` | Historical timeline PostgreSQL verification (detailed job mapping remains to inspect) |
| `mobility-driver-location-postgres.yml` | Driver-location PostgreSQL verification (detailed job mapping remains to inspect) |
| `notification-capability.yml` | Notification capability verification (detailed job mapping remains to inspect) |
| `social-graph.yml` | Social graph verification (detailed job mapping remains to inspect) |
| `transport-domain.yml` | Transport domain verification (detailed job mapping remains to inspect) |
| `workspace-web-client.yml` | Workspace web-client verification (detailed job mapping remains to inspect) |

The central CI workflow explicitly installs independent framework contracts and selected capability artifacts before invoking consumer reactors. It also deliberately isolates some failures so independent verification jobs can still run and upload their reports. This is a useful pattern, but the final job outcome and all per-reactor results must be inspected before declaring the whole pipeline green.

### Important CI coverage observation

The repository has both broad CI and path-filtered capability workflows. A change to a business capability may therefore be verified by a dedicated workflow, central CI, or both depending on the changed paths and trigger. The competitive audit must not equate the existence of a workflow file with a recent successful run. Each workflow's latest relevant run, job outcome, and test reports must be verified separately.

## 12. M0.3 initial source structure findings

Directory inspection of the accounting, payment, POS, inventory and workspace areas confirms these structural decompositions:

- Accounting includes domain, application and adapter modules plus separate consolidation, governance, local-integration, payables, receivables, treasury and capability extension modules.
- Payment separates API, core, gateway, merchant, method, PostgreSQL adapter, REST and gRPC concerns, with additional processor modules.
- POS has separate domain, application and adapter modules.
- Inventory has separate domain, SPI, application and adapter modules.
- Workspace REST adapter is a separate module from workspace domain/application and persistence modules.

These are module-boundary observations only. The next source-level audit must inspect concrete commands, ports, invariants, migrations, authorization and tests before assigning maturity scores or declaring workflows complete.

### M0.3 next source-tracing targets

1. Accounting: journal lifecycle, posting, reversal, period close, AP/AR and reconciliation contracts.
2. Payment: payment intent/attempt/settlement/refund lifecycle, outbox/replay and provider integration.
3. POS: cart/checkout/receipt/return lifecycle and inventory/payment/accounting handoffs.
4. Inventory: stock ledger, reservations, transfers, counts and valuation invariants.
5. CRM: tenant-local relationships, ownership and customer lifecycle.
6. Workspace: tenant-scoped list/read/write APIs, authorization-before-navigation and revision integrity.
7. Workforce and construction: one representative end-to-end workflow each, including policy and financial integration.

No source-level workflow completeness or current CI pass is asserted by this topology update.

## 13. M0.3 source trace — Accounting (2026-11-06)

This pass traced the journal domain model, posting/reversal handlers, fiscal-period and close services, persistence ports, adapter wiring, and selected tests/migrations on current `main`. It is an implementation trace, not a production-readiness certification.

### Verified implemented behavior

| Area | Evidence | What is demonstrated |
|---|---|---|
| Journal lifecycle | `numou-accounting-domain/.../domain/model/JournalEntry.java` | Draft, pending approval, approved, posted and reversed states; maker/checker metadata; non-empty and balanced-entry checks; storno reversal construction |
| Balance precision | `numou-accounting-domain/.../JournalEntryBalanceTest.java` | Numeric comparison avoids scale-sensitive BigDecimal equality; unbalanced entries are rejected |
| Tenant/ledger journal lookup | `numou-accounting-application/.../port/JournalEntryRepository.java`; `numou-accounting-adapter/.../InMemoryJournalEntryRepository.java` | Scoped lookup methods exist; in-memory implementation filters by both tenant and ledger; a scope test covers tenant and ledger separation |
| Journal schema | `numou-accounting-adapter/src/main/resources/db/migration/V2__journal_entry_tables.sql`; `V14__general_ledger_tenant_ledger_scope.sql` | Tenant and ledger columns, scoped account uniqueness, journal-line tenant/ledger columns, composite foreign keys, and supporting indexes |
| Event durability shape | `.../V3__transactional_outbox.sql` | Outbox table stores tenant and ledger context and has an unprocessed-event index |
| Reversal orchestration | `numou-accounting-application/.../ReverseJournalEntryHandler.java` | Loads original journal by tenant/ledger, creates and posts a reversal, marks original reversed, and reverses account movements |
| Close workflow behavior | `numou-accounting-application/.../FinancialCloseServiceTest.java` | Unit test covers task completion, validation, approval, lock and rejection of posting through the lock engine |

### Material gaps and risks requiring remediation

| Priority | Finding | Evidence and impact |
|---|---|---|
| P0 | Journal posting is not shown to be atomic across ledger mutation, journal persistence and outbox persistence | `PostJournalEntryHandler.handle` saves each changed account, then the journal, then outbox events and projections. The handler has no visible transaction boundary. The adapter module exposes in-memory account, journal and outbox repositories as application-scoped beans; the inspected source tree did not expose a PostgreSQL journal/account/outbox repository implementation. A failure between these steps can leave partial accounting state unless a higher composition boundary supplies a transaction (not evidenced in this trace). |
| P0 | Fiscal-period lookup is not tenant/ledger scoped | `FiscalPeriodRepository.findActivePeriod(LocalDate)` accepts only a date; `InMemoryFiscalPeriodRepository` returns the first matching period. `PostJournalEntryHandler` uses this lookup for strict-period checks. This cannot reliably distinguish different tenants' or ledgers' fiscal calendars. |
| P0 | Financial-close state and period locks are process-local | `FinancialCloseService` stores cycles in a `ConcurrentHashMap`; `PeriodLockEngine` stores locks in a process-local set. `AccountingServiceProducer` wires these implementations directly. Restart, horizontal scaling or deployment across multiple instances can lose or disagree on close/lock state. |
| P1 | The accounting `IdempotencyGuard` is process-local and not demonstrated on journal command execution | `IdempotencyGuard` uses a `ConcurrentHashMap` set. The producer exposes it as a bean, but the inspected `PostJournalEntryHandler` and `CommandBus` registration do not use it. The `processed_command` migration creates a table with a globally unique `request_id`, but the inspected path does not establish tenant-scoped reservation, payload fingerprint validation, durable result replay or atomic coupling to journal posting. |
| P1 | Posting handler account updates and journal writes need concurrency protection | The handler loads and saves account aggregates one line at a time. The inspected path does not show optimistic version checks or a single database transaction protecting concurrent postings to the same account. |
| P1 | Reversal outbox aggregate identity needs review | `ReverseJournalEntryHandler` drains events raised by the original aggregate but writes each outbox row with the reversal ID as aggregate ID. Verify the intended event aggregate identity and ensure original/reversal events are not misattributed. |

### Architectural interpretation

1. Do not add another accounting or ledger capability. The existing Accounting capability owns journal, account, fiscal-period, posting, reversal and close invariants.
2. Preserve the current domain/application contracts where sound, but make the production adapter path durable and transactionally consistent.
3. Fiscal periods and close cycles must carry explicit tenant and ledger scope at their owning domain/application boundary; a date-only global lookup is insufficient for a multi-tenant, multi-ledger platform.
4. Journal command idempotency must be durable, scoped, fingerprinted and coupled to the same transaction as ledger mutations and outbox writes. The POS implementation provides an existing repository pattern worth evaluating, but its schema/identity semantics should not be copied blindly.
5. Close/period locks need durable persistence and cross-instance enforcement. The posting path must consult the same authoritative lock state that close operations mutate.
6. Keep projection publication and external side effects behind reliable post-commit/outbox processing; a projection failure must not make an already committed journal appear unposted or encourage unsafe retries.

### M0.3 verification limits and next actions

- The inspected tests demonstrate selected domain, scope and close-service behavior; they do not demonstrate PostgreSQL-backed journal posting, atomic rollback, concurrent posting, durable close recovery or multi-instance lock enforcement.
- The existence of SQL migrations and JDBC dependencies is not evidence that the corresponding production repository adapters are wired.
- No Maven build or test command was run as part of this source-only trace; no CI result is claimed.
- Next: inspect the payment lifecycle and its durable idempotency/outbox/provider recovery; then trace POS-to-payment and POS-to-inventory/accounting handoffs. After those traces, define a single Accounting hardening milestone with transaction, migration, recovery and concurrency acceptance tests.

## 14. M0.3 source trace — Payment and POS handoff (2026-11-06)

This pass traced the neutral Payment application API, PostgreSQL adapter, refund/provider-outcome handling, outbox relay, and POS checkout integration. It also inspected the older entity-oriented PaymentService so the two payment paths are not conflated.

### Payment capability: verified strengths

| Area | Evidence | What is demonstrated |
|---|---|---|
| Tenant-scoped payment obligation | `finance/payment/numou-payment-postgres-adapter/.../PostgresPaymentApplicationAdapter.java`; `V1__payment_obligations_and_outbox.sql` | Payment ID and idempotency uniqueness are tenant scoped; reads and mutations include tenant predicates; tenant authorization is invoked |
| Durable idempotency | Same adapter; `PaymentRequestFingerprint.java` | `createOrGet` fingerprints the request, uses a tenant/idempotency unique constraint, and rejects reuse with a different request |
| Atomic obligation + outbox | Same adapter | The adapter explicitly opens a JDBC transaction and commits/rolls back obligation and outbox changes together |
| Verified provider outcomes | Same adapter; `V3__verified_payment_outcome_inbox.sql` | Provider events are deduplicated by tenant/provider/event ID; amount, currency and provider reference are checked; allowed transitions are explicit; late success after failure/cancellation/expiry emits a reconciliation-required event |
| Refund safety | Same adapter | Payment row is locked; refund idempotency is fingerprinted; refund totals include requested/pending/succeeded/unknown amounts; refund request and outbox event share one transaction |
| Reliable event delivery | `PaymentOutboxRelay.java`; `PostgresPaymentOutboxRelayStore.java`; `V2__payment_outbox_leases_and_dead_letters.sql` | Bounded claims use `FOR UPDATE SKIP LOCKED`; lease tokens fence stale workers; retries are delayed; exhausted events become terminal |
| Integration evidence | `PostgresPaymentApplicationAdapterTest.java`; `PostgresPaymentOutboxRelayStoreTest.java` | Testcontainers PostgreSQL tests cover idempotency, verified outcomes, concurrency, tenant behavior, lease fencing and terminal delivery behavior (tests are present; this audit did not execute them) |

### POS-to-Payment and checkout: verified strengths

1. `PaymentCapabilityCheckoutAdapter` maps POS requests to the neutral `PaymentApplicationPort`, rather than creating a second payment ledger or provider model.
2. The POS checkout application port is deliberately narrow. It carries tenant, idempotency key, checkout reference, amount and currency.
3. `CheckoutOrchestrationService` obtains its payable amount from the trusted pricing port and validates the returned payment's tenant, amount and currency.
4. The checkout lifecycle is a retryable saga. Its documented behavior keeps stock reserved for UNKNOWN/PENDING payment outcomes rather than releasing inventory on timeout.
5. `PostgresCheckoutRepository` scopes reads and writes by tenant and uses optimistic version checks on updates.
6. `PostgresSaleCommandIdempotency` stores request fingerprints and results and describes reservation, sale mutation and result persistence as one JTA transaction. `SaleCommandService` requires a nonblank key and fingerprints operation parameters.

### Gaps and architectural risks

| Priority | Finding | Evidence and impact |
|---|---|---|
| P1 | Two payment application surfaces coexist and need explicit composition/ownership documentation | The older `PaymentService` / Panache `PaymentRepository` entity flow coexists with the neutral `PaymentApplicationPort` / `PostgresPaymentApplicationAdapter` obligation flow. POS uses the latter. The older repository queries by payment ID, invoice, customer email and status without tenant arguments in the inspected contract. Determine which API is legacy, which runtime exposes each, and whether any customer-facing path still relies on the older unscoped model before consolidating or retiring it. |
| P1 | Provider execution is intentionally separate, but operational ownership and reconciliation must be completed at composition level | The PostgreSQL adapter records obligations/refunds and verified provider outcomes; its documentation explicitly delegates provider execution and provider-state transitions to separate workers/adapters. Verify production provider worker registration, credential isolation, webhook authentication, scheduling, alerting and manual reconciliation procedures before assigning production maturity. |
| P1 | POS checkout saga spans independent capability transactions | The checkout orchestrator coordinates stock reservation, payment obligation creation and checkout-state updates through ports. This is correctly a saga boundary, not a distributed database transaction. Verify compensation/recovery behavior for every crash point, including reservation success before checkout persistence, payment creation before payment ID persistence, late success and duplicate provider callbacks. |
| P2 | POS checkout persistence and sale-command idempotency use different transaction mechanisms | Checkout session repository uses its own JDBC connection and optimistic versioning; sale command idempotency uses a JTA transaction around its supplier. Keep these boundaries explicit and test cross-capability crash recovery rather than assuming they are atomic together. |

### Architecture interpretation

- The neutral Payment capability is a strong reusable foundation for POS, billing, commerce and other consumers: consumers use a stable application port; Payment owns obligation/refund state, tenant authorization, idempotency, provider outcome validation and its outbox.
- Keep payment provider execution and external delivery outside the domain model. The outbox is at-least-once, so consumers must be idempotent.
- Do not let POS write Payment tables or own provider-specific lifecycle semantics.
- The legacy entity-oriented PaymentService should be treated as a separate compatibility surface until its runtime consumers and migration path are verified; do not merge its model into the neutral obligation API by assumption.
- Checkout should remain a saga with explicit UNKNOWN/PENDING/late-success handling and observable compensation/recovery, not a cross-capability shared transaction.

### Verification limits and next actions

- Source and PostgreSQL Testcontainers test files were inspected, but no Maven build or test command was executed in this pass.
- Provider adapter implementations, runtime worker wiring, webhook security, production scheduling and end-to-end POS crash-recovery tests remain to be verified.
- Next source trace: inventory stock reservation/commit/release and accounting posting handoffs; then inspect CRM/workforce workflows and map all findings into a single prioritized M0.6 backlog.
