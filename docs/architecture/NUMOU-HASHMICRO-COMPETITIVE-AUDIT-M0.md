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

## 13. M0.3 source-tracing update — Accounting, Payment and POS

Source files and tests were fetched from the current `main` tree on 2026-11-06. This is a focused code review, not a completed integration-test run. The evidence below distinguishes implemented contracts from gaps visible in the inspected composition.

### 13.1 Accounting — broad domain model, persistence/composition gap

**Verified implementation evidence**

- `JournalEntry` enforces non-empty, non-zero, balanced double-entry journals and supports draft, pending approval, approved, posted and reversed lifecycle states:
  - `finance/accounting/numou-accounting-domain/src/main/java/tech/kayys/numou/accounting/domain/model/JournalEntry.java`
- Command handlers exist for posting, approval and reversal:
  - `finance/accounting/numou-accounting-application/src/main/java/tech/kayys/numou/accounting/application/handler/PostJournalEntryHandler.java`
  - `.../ApproveJournalEntryHandler.java`
  - `.../ReverseJournalEntryHandler.java`
- Fiscal period and financial close services exist; AP/AR services and REST resources are present.
- PostgreSQL migrations define journal entries, journal lines, outbox, AP/AR, processed commands, audit records, receivables repository support and tenant/ledger-scoped GL schema:
  - `finance/accounting/numou-accounting-adapter/src/main/resources/db/migration/V1__init_accounting_schema.sql` through `V14__general_ledger_tenant_ledger_scope.sql`.
- The adapter configures PostgreSQL and Flyway migration at startup in `finance/accounting/numou-accounting-adapter/src/main/resources/application.properties`.

**Material gap found in the inspected runtime wiring**

- The adapter source tree contains `InMemoryAccountRepository`, `InMemoryJournalEntryRepository`, `InMemoryFiscalPeriodRepository` and `InMemoryOutboxRepository` as the implementations of the core GL ports. The inspected adapter tree did not contain PostgreSQL implementations of `AccountRepository`, `JournalEntryRepository`, `FiscalPeriodRepository` or `OutboxRepository`.
- `AccountingServiceProducer` injects those ports into the command/query buses and services; the in-memory implementations are CDI application-scoped beans. Thus the existence of GL PostgreSQL migrations must not be mistaken for durable GL repository wiring.
- `JournalEntryResource.listEntries()` and `getEntry(id)` call unscoped repository methods. Tenant/ledger-scoped repository methods exist, but these read endpoints do not use them in the inspected resource.
- `PostJournalEntryHandler` updates account aggregates one line at a time, then saves the journal and outbox events through separate ports. No accounting unit-of-work/transaction boundary is visible in the handler or those port contracts. Failure between these steps can therefore leave partial effects unless an outer transaction/composition guarantee is supplied; none was established by this source pass.
- `FiscalPeriodService.performYearEndClosing` uses deprecated unscoped `accountRepository.findAll()`, constructs a default-tenant/primary-ledger journal, and the inspected method does not persist updated account balances or emit outbox/projection events. This requires a tenant/ledger-aware redesign or removal from tenant-facing use.

**Audit disposition:** Accounting has substantial domain and workflow code, but the inspected general-ledger runtime must be treated as **not yet production-evidenced**. Durable tenant/ledger-scoped GL persistence, atomic posting/reversal, tenant-scoped read APIs, and tenant-aware year-end close are priority gaps to resolve before relying on this service as a financial system of record.

### 13.2 Payment — a stronger durable capability boundary

**Verified implementation evidence**

- The public `PaymentApplicationPort` exposes create-or-get, tenant-scoped find, refund request and idempotency conflict/rejection semantics.
- `PostgresPaymentApplicationAdapter` performs payment-obligation creation, idempotency fingerprint checks and outbox insertion in one JDBC transaction. It authorizes the tenant before mutation.
- Database uniqueness on `(tenant_id, idempotency_key)` prevents duplicate obligations; a reused key with a different request fingerprint is rejected.
- Payment migrations include durable obligations/outbox, outbox leases/dead letters and a verified provider-outcome inbox with uniqueness on `(tenant_id, provider_name, provider_event_id)` and a tenant/payment foreign key.
- `PostgresPaymentOutboxRelayStore` uses bounded batch claims, `FOR UPDATE SKIP LOCKED`, leases, lease tokens, retry metadata and terminal failure state.
- PostgreSQL adapter tests use Testcontainers and cover migration setup, concurrency/idempotency and persistence behavior; a separate outbox relay store test suite exists.
- Gateway adapters include Midtrans and Xendit; provider-specific modules include bank transfer and QRIS implementations.

**Audit limitations / follow-up**

- The modern payment obligation API and the older `PaymentService`/gateway/entity stack coexist. The audit has not yet proven that all legacy REST endpoints and processors delegate to the neutral obligation contract rather than creating a parallel payment lifecycle.
- Provider callback authentication/signature verification, replay behavior, settlement reconciliation and refund completion must be traced end-to-end before assigning a production maturity score.
- The inspected Testcontainers tests are evidence of intended PostgreSQL verification, not evidence that the latest CI run passed.

**Audit disposition:** The neutral payment obligation/outbox boundary is materially more mature in durable persistence design than the inspected GL path. Continue with callback verification, refund lifecycle, provider settlement and consumer integration tests; do not replace this capability with a new POS-specific payment model.

### 13.3 POS — explicit orchestration and retry design

**Verified implementation evidence**

- POS separates domain, application and adapter modules. Domain includes `Sale`, `CheckoutSession`, `Register` and `RegisterShift`.
- `SaleCommandService` enforces tenant-scoped sale loading, open-shift access, optimistic version checks and command idempotency with a SHA-256 request fingerprint. Lifecycle events are appended through a POS outbox port.
- `PostgresSaleCommandIdempotency` stores reservation, sale mutation and completed result in a JTA transaction; it rejects reuse of a key for a different operation/fingerprint and detects incomplete records.
- `CheckoutOrchestrationService` uses a persisted sale and trusted pricing quote, reserves inventory, creates/reuses a shared Payment capability obligation, verifies tenant/amount/currency and reconciles payment state. Unknown/pending payments do not release stock; failed/cancelled/expired payments release the reservation; successful payment commits stock.
- `PaymentCapabilityCheckoutAdapter` maps POS payment operations to the shared `PaymentApplicationPort`, rather than defining a second payment obligation store.
- PostgreSQL checkout persistence is tenant-scoped and uses optimistic version updates. POS migrations include checkout sessions, outbox delivery leases, consumer inbox and outbox operations.
- Unit tests cover checkout retry/idempotency, quote currency mismatch, stock commit only after successful payment, unknown payment safety, failed-payment release and bounded recovery. PostgreSQL persistence tests also exist.

**Audit limitations / follow-up**

- The checkout saga coordinates separate capability calls; the audit must still verify crash-window recovery when inventory reservation/commit/release succeeds but the following checkout-state write fails.
- The inspected checkout service has explicit payment/inventory ports, but a complete sale-to-accounting posting, receipt/return/refund and settlement-reconciliation journey was not established in this pass.
- The POS PostgreSQL checkout repository uses tenant-scoped reads and optimistic version checks; branch/register/shift authorization and access-control behavior need a separate endpoint-to-policy trace.
- No latest CI or production deployment evidence was queried for these tests.

**Audit disposition:** POS already has a credible capability-composition pattern and useful recovery tests. Prioritize saga crash-window tests and full accounting/returns/settlement handoffs rather than building a parallel POS or payment foundation.

### 13.4 Cross-capability priority register

| Priority | Finding | Owning capability | Required next evidence / action |
|---|---|---|---|
| P0 | Core GL ports are wired to in-memory repositories despite PostgreSQL schema migrations | Accounting | Implement and wire durable tenant/ledger-scoped GL repositories; add PostgreSQL integration tests |
| P0 | Journal posting/reversal has no visible atomic unit-of-work spanning account balances, journal state and outbox | Accounting | Introduce a transaction-owning application port/use-case boundary; test rollback on each injected failure point |
| P0 | Journal list/get resource methods use unscoped repository methods | Accounting | Require authenticated tenant/ledger context and scoped repository calls; add cross-tenant negative tests |
| P0 | Year-end close uses unscoped account lookup and default tenant/ledger journal construction | Accounting | Make close command explicitly tenant/ledger scoped and persist journal, balances and events atomically |
| P1 | Payment has both neutral obligation and legacy payment processing stacks | Payment | Trace all entry points and document migration/ownership; ensure one authoritative lifecycle per use case |
| P1 | POS saga crash-window coverage is not established | POS + Inventory | Add fault-injection tests around reserve/commit/release and checkout persistence; verify idempotent recovery |
| P1 | End-to-end POS settlement/returns/accounting handoff is not established | POS + Payment + Accounting | Add one integration scenario from sale through settlement, ledger posting and refund/reversal |

### 13.5 Scope and maturity decision

This update is source evidence for the inspected paths only. It does not claim that the three capabilities are fully audited, that the listed gaps are the only gaps, or that any build/CI workflow passed. No numeric maturity score is assigned until the relevant persistence, authorization, integration and failure-recovery tests are run and reviewed.

## 14. M0.3.1 implementation slice — durable PostgreSQL chart of accounts

**Branch:** `numou/accounting-gl-postgres-account-repository`

This slice implements the first durable GL repository, without claiming the full general ledger persistence milestone is complete.

### Implemented

- Added `PostgresAccountRepository` implementing the existing `AccountRepository` application port.
- Reads by ID, account number and list are tenant-and-ledger constrained in SQL, not filtered after loading.
- Reconstitution preserves account type/status, currency and balance, parent account, active flag and Shariah-compliance flag.
- Writes use a single PostgreSQL upsert. The conflict update is permitted only when the stored tenant and ledger match the incoming aggregate; an account ID cannot be moved across scopes.
- Legacy unscoped compatibility methods fail closed in the PostgreSQL adapter.
- JDBC work is scheduled on Mutiny's worker pool rather than blocking the reactive request/event-loop thread.
- Marked `InMemoryAccountRepository` as a Quarkus `@DefaultBean`, allowing the durable PostgreSQL implementation to take precedence in CDI composition while retaining the in-memory fallback for tests or compositions without a durable adapter.
- Added Testcontainers PostgreSQL coverage for aggregate round-trip, tenant/ledger isolation, cross-scope ID reassignment rejection and unscoped method rejection.

### Remaining in M0.3.1

- PostgreSQL journal-entry persistence and aggregate reconstitution.
- Tenant/ledger-aware fiscal-period persistence and query contracts.
- Durable accounting outbox persistence and relay/lease behavior.
- A transaction-owning application boundary spanning account updates, journal persistence and outbox writes.
- Runtime-level CDI verification proving the intended adapters are selected together.
- Tenant-context-derived REST scope and cross-tenant authorization tests.
- Year-end close correction and rollback/failure-injection coverage.

### Verification status

The source and test files are committed, but no Maven build or Testcontainers run is claimed in this update. The integration test is configured to skip when Docker is unavailable. Run the accounting reactor from `finance/accounting` after installing the shared framework artifacts required by the repository's multi-reactor build.
