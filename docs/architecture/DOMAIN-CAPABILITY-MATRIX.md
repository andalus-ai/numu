# Numu capability ownership matrix

**Status:** Working architecture contract  
**Repository:** `andalus-ai/numu`  
**Related decision:** [ADR-001 — Neutral project management core](ADR-001-neutral-project-management-core.md)

## Purpose

Numu must support project-based work across industries without turning the project module into a construction module with optional fields. Reuse the concepts that have stable shared meaning; keep specialized commercial and operational semantics in extensions.

This matrix describes target ownership, not a claim that every capability is already complete.

## Capability ownership

| Capability | Neutral core responsibility | Extension responsibility | Boundary / non-goal |
|---|---|---|---|
| Geo | Coordinate reference/units, validated geographic primitives, approximate great-circle distance, geographic bounds and future provider-neutral spatial contracts | PostGIS storage/indexes, geocoding/routing provider adapters, versioned administrative-boundary datasets | Do not treat straight-line distance as road distance/ETA; do not make map vendors or precise location logging mandatory for consumers |
| Events and ticketing | Event publication/schedule, ticket products, ticket inventory, booking snapshots, admission entitlements and check-in lifecycle | Venue/seat-map adapters, online event delivery, QR/barcode/RFID/NFC credential transports, channel adapters and organizer policies | Payment owns provider execution/refunds; passenger reservations are not admission tickets; all credentials for one entitlement share one single-use admission state |
| Project identity and lifecycle | Identity, name, description, lifecycle, ownership references, project profile and generic governance | Construction site context; software product/repository context; manufacturing plant/order context | No mandatory site, drawing, repository, sprint, production line or permit fields on the generic Project |
| Scope breakdown / WBS | Hierarchical structures, stable IDs, parent/child integrity, ordering, codes/labels, structure type and revision references | Construction work packages/trades; software epics/features; manufacturing work breakdown | Do not require quantities or a construction WBS node type in the neutral model |
| Work items | Generic work item identity, title, description, lifecycle, owner, priority, dates, labels and links | Stories, defects, pull requests, site activities, inspection actions and production tasks | Avoid one enum containing every industry's workflow state |
| Scheduling | Activities, dependency links, calendars, constraints, schedule versions and baseline comparisons | Construction CPM and work calendars; software iterations/releases; manufacturing routings and capacity planning | A WBS hierarchy is not a schedule; avoid coupling all tasks to a single scheduling algorithm |
| Queue Management | Tenant-scoped queue definitions and entries, deterministic ordering, queue-entry lifecycle, concurrency-safe claims, audit and queue metrics | Waiting/service queues, dispatch offers, and operational work queues over source-owned references | Does not own customer identity, schedules, workforce records, reservations, support tickets, work orders or shipment outcomes; queue membership is not source-record completion |
| Progress | Source-aware observations, submissions, verification/approval where required, status and history | Installed quantities; accepted stories; production yield; service deliverables | Do not assume all progress is a physical quantity or percentage |
| Budget and controls | Budget versions, commitments, actuals, forecasts, variance and source-linked snapshots | Construction cost codes and earned-value basis; software labor/cloud budgets; manufacturing material and conversion costs | Keep budget, actual cost, earned value, certified value and cash paid distinct |
| Quantity schedule | Reusable quantities, typed units, line IDs, ordering, explicit unit compatibility and aggregation | Construction BOQ, takeoff, measurement rules, rate analysis and valuation; manufacturing BOMs where semantics fit | A quantity schedule is not automatically a BOQ, bill of materials, or software backlog |
| Estimates and rates | Shared money/quantity primitives only when semantics and precision are compatible | Construction unit-rate analysis; software effort estimation; manufacturing standard cost | Do not put industry-specific formulas into a generic universal calculator |
| Change control | Change proposal, status, decision, rationale, impact references, authorization and audit metadata where genuinely shared | Construction variation/EOT; software change request/release approval; engineering change order | Approval must not mutate approved baselines or historical commercial records |
| Risk and issue | Generic risk/issue lifecycle, ownership, treatment/action links and audit history | Industry taxonomies, severity scales and compliance requirements | Keep classification configurable; avoid hardcoding construction-only categories |
| Contracts and commercial terms | Generic parties/terms only if independently reusable and modeled without assumptions | Construction contract administration, claims, retention, advance recovery and payment certificates; software SOWs and subscriptions | Do not classify every project change as a construction change order |
| Procurement and resources | Shared references and capability contracts where stable | Construction RFQ/PO/material delivery; software vendor/license/cloud procurement; manufacturing materials and capacity | Keep inventory, workforce, and supplier lifecycles in their own bounded contexts |
| Documents and evidence | Versioned document references, relationships, provenance and access policy | Drawings/RFIs, source/release artifacts, inspection records, manufacturing specifications | Store binary content through document/storage capabilities, not project aggregates |
| Quality and acceptance | Configurable criteria, review/acceptance decisions and evidence references | Construction inspections/NCRs; software tests/defects/release gates; manufacturing quality plans | Do not make HSE permits or site inspections mandatory for every project |
| Reporting | Common project, schedule, cost and status read models with metric provenance | Industry-specific KPIs and domain dashboards | Metrics must declare definitions, units, time window and source basis |

## Dependency rules

1. Neutral project, scope, scheduling, control and quantity domains may depend on platform primitives and narrow stable contracts, never on construction/software/manufacturing packages. The CI rule set currently enforces the neutral Project and Quantity reverse-dependency guards; other boundaries are added only after ownership and actual contracts are reviewed.
2. Extensions may depend inward on neutral contracts. The reverse dependency is forbidden.
3. Cross-context relationships use stable identifiers, explicit ports, domain events or read models; do not share another bounded context's mutable aggregate.
4. Domain policy belongs with the concept it governs. Application services orchestrate workflows but do not reimplement invariants.
5. Extract a shared abstraction only when two real use cases have the same semantics. Similar field names are not sufficient evidence.
6. Keep authorization, tenant isolation, idempotency, transaction boundaries, audit and outbox delivery intact during extraction.
7. Approved baselines, accepted snapshots and commercial revisions are historical facts. A new plan or revision supersedes them explicitly; it does not rewrite them.
8. A project profile selects enabled capabilities and policy sets. It must not cause construction conditionals to spread throughout neutral domain code.

## Representative profiles

### Construction

Neutral project + scope/WBS + schedule + budget/controls + quantity schedule, with construction BOQ/revisions, takeoff, site execution, procurement, contracts, QA/QC, HSE, variations, payment certificates and closeout.

### Software delivery

Neutral project + scope/work items + schedule/iterations + resources + budget/controls, with backlog types, story/effort estimation, repository/CI links, test and defect workflows, release approvals and deployment evidence. No construction BOQ or site execution dependency.

### Manufacturing

Neutral project/work breakdown + schedule + resources + controls, with manufacturing-specific BOMs, routings, work orders, production output, inventory and quality. Do not force a manufacturing BOM into construction BOQ rules.

## Completion evidence

A capability is not considered generalized merely because its package was renamed. Evidence should include:

- architecture tests that forbid reverse dependencies;
- domain tests for invariants and invalid transitions;
- application tests for authorization and orchestration;
- persistence tests for tenant isolation, rollback, concurrency and idempotent retries where applicable;
- at least one construction workflow and one non-construction workflow consuming the neutral contract;
- migration and compatibility tests for existing construction records and public APIs.


## Finance wallet ownership and extension boundary

| Capability | Shared responsibility | Domain-specific responsibility | Boundary / non-goal |
|---|---|---|---|
| Finance Wallet (target capability; implementation not implied by this row) | Candidate neutral account identity, currency/precision, explicitly defined available balance, supported holds, immutable movements, durable idempotency, freeze/restriction primitives and reconciliation references | Mobility fare capture/shortfall, driver earnings and cash commission; Commerce/Hospitality credits, deposits and provider payouts; Logistics advances/settlements; any Syirkah contractual allocation | Do not make negative balances, withdrawal, interest/profit treatment, safeguarding or payout universally enabled. Each use case must declare policy and legal/operational profile |
| Payment execution | Provider/method execution, provider status, refund/reversal workflow and verified callback correlation | Domain-specific payment intent, allowed methods, capture timing, refund eligibility and compensation | Payment transaction state is not wallet balance state and is not a general-ledger journal |
| Accounting | Journal posting, chart-of-accounts mapping, period controls, financial reporting and reconciliation | Source-to-account mapping and domain-specific recognition/classification | A wallet movement is not automatically a balanced journal entry; avoid direct cross-capability table writes |

### Finance wallet dependency rules

1. The target shared wallet capability belongs under `finance/wallet`; consumers depend on its documented public domain/application contracts, never Mobility wallet internals or payment/accounting persistence entities.
2. The shared kernel is not a legal product classification. The host/product profile must define authorization, limits, KYC/KYB, safeguarding, payout/withdrawal, dispute, audit-retention and reporting requirements where applicable.
3. Domain extensions own business decisions such as whether a balance may become negative, whether funds are withdrawable, when holds expire, and whether a movement is eligible for settlement.
4. Payment execution, operational wallet movements and Accounting journals retain separate identifiers, lifecycles and idempotency scopes; connect them through explicit ports and reconciliation.
5. Do not migrate the Mobility aggregate until durable replay protection, concurrent mutation, tenant isolation, migration compatibility and domain-specific shortfall behavior are tested.
6. Validate reuse with at least two real consumers whose shared invariants are demonstrated; do not generalize solely from similar class or field names.
7. This section records target ownership only. It does not claim that `finance/wallet` or its production adapters have already been implemented.

## Revenue and spend management ownership

| Capability | Shared responsibility | Domain-specific responsibility | Boundary / non-goal |
|---|---|---|---|
| Revenue Management (proposed coordination capability) | Stable revenue-source references/classification, earning-evidence references where not source-owned, orchestration to Billing/Receivables/Accounting, operational read models and reconciliation | POS sale, hospitality booking, subscription, shipment, fare, construction milestone, donation, government fee and contractual earning facts remain source-owned | Do not duplicate Billing invoices/schedules/pricing/dunning, AR balances/collections, Subscription lifecycle, Accounting recognition/journals, Payment execution or Wallet movements. A new Revenue aggregate/module requires proof of shared invariants across at least two real consumers and reconciliation with Billing's existing RevenueRecognition model |
| Spend Management (proposed capability) | Internal spend request and expense-claim lifecycle, approval decisions/evidence, spend classification, and orchestration references to Budget/Purchasing/AP/Payment | Domain-specific procurement, employee reimbursement, project expenditure and statutory expense rules remain in their owning extensions/policies | Does not own Budget balances/commitments, Purchasing requisitions/POs/receipts, AP supplier invoices/payment scheduling, Payment execution, Wallet movements or Accounting journals/recognition |
| Billing | Existing billing agreements, schedules, pricing/dunning integration, invoices and current revenue-recognition model | Industry billing extensions and source-specific charge calculation | Invoice status is not revenue recognition, cash receipt or ledger posting |
| Receivables / Payables | Customer and supplier obligations, invoice posting, payment allocation and settlement scheduling within existing AP/AR boundaries | Tax, withholding, retention and jurisdiction-specific policy adapters | Do not treat invoices as payment execution or write Accounting journals outside the posting contract |
| Budget | Budget versions, lines, commitments, controls, forecasts and allocations | Industry-specific cost codes, funds and approval policies | Approval is not commitment; commitment is not invoice, expense recognition or payment |
| Purchasing | Requisitions, purchase orders, goods receipts and procurement lifecycle | Sourcing and category-specific procurement policies | Spend request is not a requisition/PO; purchasing does not own AP or payment execution |

### Revenue and spend dependency rules

1. Revenue and Spend orchestration may call public application contracts but must not depend on another capability's mutable aggregate, persistence entity, private implementation or database table.
2. The receiving capability remains authoritative for its own state. A spend approval does not mean Budget accepted a commitment; a billing request does not mean an invoice was issued; a settlement request does not mean payment succeeded.
3. Side-effecting commands carry tenant, actor, correlation/causation and idempotency context. The receiving application boundary performs authorization and deduplication.
4. Use the existing shared Money value object where its semantics fit; do not introduce a second Money or a universal financial Transaction aggregate.
5. Revenue recognition, tax, government accounting, nonprofit funds, cooperative rules and Sharia contract semantics are policy/domain extensions. The neutral capability records policy identity/version and does not hardcode a single treatment.
6. This section records target ownership and M1 design only. It does not claim that standalone Revenue Management or Spend Management modules, APIs, persistence adapters or production workflows already exist.

See [Revenue and Spend Management — M1 Capability Contracts](REVENUE-AND-SPEND-M1-CAPABILITY-CONTRACTS.md) for command vocabulary, lifecycle boundaries, reliability requirements and M2 acceptance gates.

