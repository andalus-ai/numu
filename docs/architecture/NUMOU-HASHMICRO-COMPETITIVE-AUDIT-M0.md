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
