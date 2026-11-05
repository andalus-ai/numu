# Advertising Platform — M0 Architecture Audit and Foundation

- **Status:** M0 architecture baseline; initial repository/POM audit, source-contract verification pending
- **Date:** 2026-11-05
- **Repository:** `andalus-ai/numu`
- **Target branch:** `main`
- **Guidance:** `AGENTS.md`, ADR-003, Capability Packaging and Composition Contract, Domain Capability Matrix, and ecosystem engineering guardrails

## 1. Objective

Establish the ownership, integration, security, data, and deployment boundaries for a reusable Numou Advertising capability inspired by publisher monetization and ad-management platforms.

The capability must support:
- advertiser and agency account management;
- publisher and inventory management;
- direct-sold and network-mediated campaigns;
- ad creative lifecycle and policy review;
- ad decisioning and delivery;
- impression, click, and conversion measurement;
- campaign spend controls and billing evidence;
- publisher earnings, revenue share, reconciliation, and payout orchestration;
- optional programmatic demand/supply integrations;
- standalone, embedded, modular-monolith, and distributed compositions where explicitly supported.

This document records an initial evidence-based design. It does not claim that Advertising code, runtime, APIs, schemas, or integrations already exist.

## 2. Repository guidance and constraints

The design follows these repository rules:
1. A capability is a business boundary, not automatically a microservice, Maven artifact, database, or deployment unit.
2. Domain code stays independent of HTTP, CDI, persistence frameworks, brokers, and vendor SDKs.
3. Consumers use documented application ports or versioned remote contracts, never another capability's private entities, tables, or implementation packages.
4. Commands and events carry tenant and actor/correlation context and idempotency semantics where relevant.
5. Persistence ownership stays with the capability that owns the invariant.
6. Runtime composition selects only the capabilities and adapters required by the host.
7. No PR is opened and no code is described as tested until the corresponding action and evidence exist.

The repository has multiple Maven reactor roots and no root `pom.xml`. The shared parent is `core/framework/pom.xml`. New reactor paths must use the established parent path convention and must be added to the appropriate reactor deliberately.

## 3. Initial repository evidence

The following repository files were inspected on the current `main` baseline:

| Evidence | Observation | Architectural consequence |
|---|---|---|
| `AGENTS.md` | Requires ownership-first inspection, explicit contracts, tenant/security controls, and milestone-sized changes | Advertising starts with a boundary audit; no speculative empty module fan-out |
| `docs/architecture/ADR-003-reusable-capability-modules.md` | Supports embedded, standalone, modular-monolith, distributed, and enterprise compositions | Advertising domain rules remain topology-neutral |
| `docs/architecture/CAPABILITY-PACKAGING-AND-COMPOSITION.md` | Defines domain/application/SPI/adapter/runtime roles and persistence ownership | Use only artifact roles justified by actual code |
| `docs/architecture/DOMAIN-CAPABILITY-MATRIX.md` | Documents Finance, Payment, Accounting, Wallet and Revenue/Spend ownership boundaries | Reuse finance contracts; do not create duplicate money/payment/ledger models |
| `core/framework/event/pom.xml` | Event domain/application/SPI/adapters separate envelope, durable store, publisher, and subscriber concerns | Reuse event contracts for business lifecycle and durable integrations; do not assume they are the raw analytics data plane |
| `identity/pom.xml` | Identity has domain/application/adapter artifacts | Integrate with identity through public contracts |
| `finance/pom.xml` | Finance reactor includes accounting, billing, payment, common, syirkah, tax, and wallet artifacts | Reuse finance capabilities through explicit ports |
| `finance/wallet/numou-wallet-domain/pom.xml`, `numou-wallet-application/pom.xml`, `numou-wallet-spi/pom.xml` | Wallet is separately layered, currently described as a Mobility wallet in its artifact metadata | Treat as a candidate shared integration only after its current public contract and supported policies are verified |
| `finance/billing/pom.xml` | Billing has core and extension modules | Billing remains owner of invoice/receivable lifecycle |
| `finance/payment/pom.xml` | Payment exposes API, gateway, method, core, Postgres adapter, merchant, REST and processor modules; some legacy modules are parked | Payment execution remains Finance-owned; verify the active payment API before integration |
| `commerce/promotion/pom.xml` | Promotion owns rules, conditions, actions, stacking, and applying promotions to a priced cart | Commerce Promotion is not the owner of ad campaigns, ad delivery, or publisher monetization |
| `commerce/pricing/pom.xml` | Commerce Pricing resolves storefront prices over offerings/configuration | Ad auction price, advertiser billing, and publisher revenue share are distinct from storefront price resolution |
| `commerce/marketplace/pom.xml` | Marketplace is a separate domain/application/Postgres capability | Marketplace can be a publisher/inventory host or an advertiser channel via contracts |
| `notification/pom.xml` | Notification is a separate domain/application/persistence/provider capability | Reuse for lifecycle notifications; no advertising-owned notification transport |
| `documents/pom.xml` | Provider-neutral document storage SPI and local/S3 adapters exist | Use Documents for creative binary assets; Advertising owns creative metadata, review, and policy state |
| `core/framework/foundation/pom.xml` | Foundation separates kernel, domain, application, testing, and adapter | Reuse appropriate stable primitives; do not introduce another Money/value-object foundation |

### Evidence limitations

This pass inspected repository guidance, architecture documents, and selected aggregator/module POMs. It did not yet trace every Java dependency/import, CDI composition, API route, event schema, database migration, runtime profile, or test fixture. In particular, the public contracts of Finance Wallet, Billing, Payment, Identity, Documents, Event, Commerce Marketplace, and Promotion require source-level inspection before production adapters are implemented.

No Maven build or test command was run in this audit. No CI result is claimed.

## 4. Capability ownership decision

Create a top-level **Advertising** capability. It is independent of Commerce, Finance, Social Graph, and Developer Platform, and integrates with them through public contracts.

Advertising owns:
- advertiser campaign intent and campaign lifecycle;
- publisher account and inventory commercial participation;
- ad placement definitions and inventory eligibility;
- creative metadata, review state, and advertising policy decisions;
- ad decisioning inputs and delivery eligibility;
- campaign-specific targeting policy and frequency policy;
- ad event validation, deduplication, attribution and billability adjudication;
- campaign spend accounting evidence and pacing state;
- publisher earning calculation and revenue-share evidence;
- advertising-specific reporting definitions and reconciliation cases.

Advertising does **not** own:
- person, organization, user, tenant, or authentication identity;
- general customer/CRM lifecycle;
- commerce product, offering, catalog, storefront pricing, cart, order, or marketplace seller lifecycle;
- generic discount/promotion rule execution;
- payment provider execution, payment status, or refunds;
- wallet balances, wallet holds, or wallet movements;
- invoices, accounts receivable/payable, accounting journals, or tax ledgers;
- document binary storage or generic notification delivery;
- generic event broker infrastructure or universal analytics infrastructure.

## 5. Reuse and integration boundaries

| Existing capability | Advertising use | Must remain owned by existing capability |
|---|---|---|
| Identity / Organization / tenancy | Resolve tenant, actor, organization and authorized principal references | Identity lifecycle, authentication, organization membership and tenant authority |
| Party / Customer / CRM | Reference advertiser, agency, publisher, merchant and commercial counterparties | Party identity and customer relationship lifecycle |
| Documents | Store creative binaries, previews and evidence attachments | Object storage, document versioning and access policy |
| Event | Publish campaign, creative, billing-evidence, and payout-orchestration events | Event envelope, durable delivery, broker adapters and subscriber infrastructure |
| Finance Billing / Receivables | Request advertiser invoices or chargeable billing obligations from finalized billable evidence | Invoice, receivable, collection and dunning lifecycle |
| Finance Payment | Collect advertiser funds and execute approved payouts where supported | Provider execution, payment state, refunds and settlement execution |
| Finance Accounting | Submit classified, approved posting/reconciliation requests | Chart of accounts, journal integrity, period controls and accounting recognition |
| Finance Wallet | Optional publisher balance/hold/payout integration, only where the wallet contract supports it | Wallet accounts, balance, holds, movements and idempotency |
| Commerce Promotion | Optional interaction for sponsored offer presentation or a clearly separate promotion workflow | Promotion rule evaluation and cart discount semantics |
| Commerce Pricing / Offering | Resolve sponsored product references and display prices where needed | Product offering and storefront price resolution |
| Commerce Marketplace | Provide promoted listings and sponsored seller inventory through stable references | Seller, listing, order and marketplace transaction lifecycle |
| Social Graph | Optional audience/relationship signals subject to consent and policy | Social identities, graph edges and relationship lifecycle |
| Notification | Campaign approval, pacing, billing, policy and payout notifications | Delivery channels, inbox and notification preferences |
| Developer Platform | Publish APIs, SDKs, integration docs and extension registration | Developer identity, portal and API catalogue lifecycle |

No direct cross-capability table reads/writes are permitted. Cross-capability actions use public application ports in-process or versioned API/event contracts remotely.

## 6. Initial domain model (candidate vocabulary)

The following are Advertising-owned concepts. This is a vocabulary baseline, not a final class or table design.

### Advertiser and publisher

- `AdvertiserAccount`: advertising commercial account associated with an external Party/Organization reference.
- `AgencyAccount`: optional agency acting for one or more advertiser accounts with scoped delegation.
- `PublisherAccount`: publisher participation and monetization configuration associated with an external Party/Organization reference.
- `PublisherProperty`: website, application, channel, or other property where inventory is offered.
- `AdPlacement`: publisher-defined location/slot with supported formats, policy, and delivery constraints.
- `InventoryOffer`: commercial availability/terms for a placement or inventory package.

### Campaign and creative

- `Campaign`: advertiser intent, objective, lifecycle, schedule, delivery constraints, and budget policy reference.
- `CampaignLine`: independently targetable/deliverable allocation against inventory or placement criteria.
- `Creative`: advertising metadata, asset/document references, format, destination, review status, and version.
- `TargetingSpecification`: versioned, validated contextual/geographic/audience constraints.
- `CampaignBudget`: campaign-owned spend ceiling, pacing configuration, reservation and reconciliation state; not a duplicate general ledger or Finance Budget aggregate.
- `DeliveryPolicy`: eligibility, priority, pacing, frequency cap, and safety constraints.

### Delivery and measurement

- `AdDecision`: auditable decision result with selected creative/campaign and non-sensitive reason codes.
- `AdEvent`: validated event envelope for impression, click, view, conversion, invalidation, or adjustment.
- `AttributionRecord`: policy-versioned relationship between eligible interaction and conversion.
- `BillableEvidence`: finalized, deduplicated and policy-adjudicated evidence eligible for billing.
- `PublisherEarning`: publisher-specific earning calculation snapshot and status.
- `RevenueShareRule`: versioned contractual share/fee policy with effective dates.
- `ReconciliationCase`: discrepancy between campaign spend, billable evidence, publisher earnings, invoices, payments, or accounting postings.

Identifiers for external concepts remain opaque references. Advertising must not embed external mutable aggregates.

## 7. Lifecycle and invariant boundaries

### Campaign lifecycle

Proposed states: `DRAFT`, `PENDING_REVIEW`, `APPROVED`, `SCHEDULED`, `ACTIVE`, `PAUSED`, `COMPLETED`, `REJECTED`, `CANCELLED`.

Transitions must be explicit and authorized. Approval does not imply activation; activation does not guarantee delivery; pausing stops new eligible delivery but does not erase prior evidence or liabilities. Campaign and creative approval histories are immutable audit facts.

### Creative lifecycle

Proposed states: `DRAFT`, `SUBMITTED`, `UNDER_REVIEW`, `APPROVED`, `REJECTED`, `SUSPENDED`, `ARCHIVED`.

Creative approval is version-specific. A changed asset, destination, claim, or material metadata must trigger re-review according to policy. Serving must reject unapproved, expired, suspended, or otherwise ineligible creative versions.

### Measurement and billability

- Raw received events are not automatically valid impressions, clicks, views, conversions, or billable events.
- Event identity and idempotency keys are scoped and persisted; conflicting reuse is rejected or quarantined.
- Deduplication and invalid-traffic decisions are deterministic and auditable.
- Attribution uses an explicit policy/version, window, and eligible touchpoint definition.
- Billable evidence is finalized from validated events and policy outcomes, not directly from client-reported counters.
- Corrections use adjustment/reversal records; historical finalized evidence is not silently overwritten.
- Campaign spend and publisher earning calculations must be reproducible from retained evidence and versioned commercial rules.

### Budget and pacing

Campaign budget and delivery pacing are Advertising-owned operational controls. They are not a generic Finance Budget, Wallet balance, or accounting journal.

The ad-decision path must not serve against stale budget state beyond a declared risk bound. Budget reservation/consumption must be concurrency-safe, idempotent, tenant-scoped, and reconciled against finalized billable evidence. A campaign must fail closed when its spend authorization cannot be safely established.

## 8. Measurement data plane versus business event plane

Do not send every raw impression/click as a synchronous transaction through the general business event bus by default.

Separate:
1. **Decision/serving path:** low-latency eligibility and response; bounded dependencies and predictable timeout behavior.
2. **Measurement ingestion path:** authenticated/validated ingestion, event identity, deduplication, rate limits, bot signals, and durable buffering.
3. **Business event plane:** campaign/creative lifecycle, finalized billable evidence, invoice requests, earning finalization, reconciliation and payout orchestration.
4. **Analytics plane:** high-volume append/read/aggregation and reporting projections.

Initial storage direction:
- PostgreSQL is the transactional source of truth for advertiser/publisher accounts, campaigns, creatives, placements, campaign budget controls, policy versions, finalized billable evidence, earning snapshots and reconciliation cases.
- A durable ingestion buffer/stream is selected after reviewing existing event infrastructure and the required delivery/retention characteristics.
- ClickHouse or another analytical store is optional and introduced only with measured query/volume needs; it is a projection, never the authority for campaign state or finalized financial evidence.
- Redis or another cache may accelerate delivery, but cache state cannot authorize spend without a safe reservation/consistency strategy.
- Creative binaries use the existing Documents storage contract and configured provider (including S3-compatible/RustFS where selected).

The exact ingestion technology and consistency strategy are an M1/M3 design gate, not an assumed existing integration.

## 9. Security, privacy, policy, and Indonesia-first requirements

Mandatory design constraints:
- Tenant scope is explicit on all tenant-owned records and commands.
- Authorization is checked at authoritative application boundaries, not inferred from object IDs or SDK tokens.
- Publisher property ownership is verified before inventory activation.
- Advertiser/agency delegation is scoped, revocable, and auditable.
- Public ad-serving endpoints use signed/rotatable property credentials with bounded privileges; secrets are never embedded as long-lived SDK credentials.
- Client event claims are untrusted. Server-side validation, replay protection, rate limits, bot/invalid-traffic detection, and anomaly controls are required.
- Audience targeting is purpose-limited and consent-aware; do not introduce sensitive-category targeting or infer sensitive traits.
- Minimize personal data and identifiers; define retention, deletion, aggregation, and access controls before audience-level measurement.
- Consent and privacy policy versions are captured where required; a consent signal is not treated as blanket permission for all processing.
- Creative review includes destination validation, malware/content checks, prohibited claims/categories, disclosure labels, and policy version.
- Geographic and legal policy is configurable by market; Indonesia is the first market, but jurisdiction-specific rules must be validated by qualified legal/compliance review before launch.
- Provide advertiser/publisher dispute, invalid-traffic appeal, and correction workflows with immutable decision evidence.

## 10. Deployment and module packaging direction

Candidate top-level reactor: `advertising/pom.xml`, inheriting from `../core/framework/pom.xml`.

Do not create every candidate module immediately. Begin with the smallest artifact set justified by public contracts and reactor conventions:

- `advertising-domain`: advertiser/publisher, inventory, campaign, creative, policy, budget-control and billability invariants.
- `advertising-application`: use cases, authorization, orchestration, ports, transaction boundaries and event publication.
- `advertising-spi`: stable consumer-facing ports and versioned integration contracts where these need independent consumption.
- `advertising-persistence-postgres`: Advertising-owned persistence and migrations.
- `advertising-adapter-rest`: publisher/advertiser/serving transport endpoints only after API contracts are defined.
- Optional runtime/composition module only when a real runnable profile and required adapters are selected.

Serving and measurement may become independently deployable runtime workloads later, but are not separate business owners by default. A separate deployment requires demonstrated scaling, latency, security, operational, or failure-isolation reasons.

Supported profile targets to prove incrementally:
- embedded Advertising library/adapter in a host product;
- standalone Advertising application with selected adapters;
- modular monolith sharing process with other Numou capabilities;
- distributed serving/measurement workloads with versioned contracts;
- cloud or customer-managed deployment with replaceable infrastructure.

## 11. Explicit non-goals for M0/M1

- No real-time bidding/OpenRTB exchange implementation.
- No third-party demand partner integrations.
- No behavioral cross-site tracking or sensitive audience profiling.
- No custom payment processor, wallet, invoice, accounting journal, or Money implementation.
- No raw analytics pipeline or ClickHouse requirement before throughput/query evidence.
- No independent microservice per domain concept.
- No ad-serving algorithm or production SDK before inventory, authorization, policy, and event contracts are established.
- No claim of regulatory compliance without reviewed jurisdictional policy and operational evidence.

## 12. Milestone gates

### M0 — Architecture and repository audit

- [x] Read `AGENTS.md` and applicable architecture guardrails.
- [x] Inspect relevant reactor aggregators and selected existing capability POMs.
- [x] Establish Advertising ownership and reuse boundaries.
- [x] Define candidate vocabulary, lifecycle, data-plane separation, security constraints, and packaging direction.
- [ ] Trace source-level public contracts and consumers for Identity, Party, Documents, Event, Billing, Payment, Accounting, Wallet, Marketplace and Promotion.
- [ ] Inspect existing tenant/actor/idempotency conventions and event envelope schema.
- [ ] Inspect active CI/reactor entry points and establish a reproducible narrow build baseline.
- [ ] Validate storage/analytics/event volume assumptions with representative workloads.

M0 is complete only after the remaining evidence gates are resolved or explicitly documented as deferred with an owner and reason.

### M1 — Core domain and account/inventory foundations

- Advertiser and Publisher account contracts using opaque Party/Organization references.
- Publisher Property, Ad Placement and Inventory Offer lifecycle.
- Tenant isolation, authorization and audit history.
- PostgreSQL persistence with deterministic ordering, optimistic concurrency where required, and migration tests.
- Domain/application tests and architecture dependency tests.
- No serving or billing behavior is considered production-ready in M1.

### M2 — Campaign and creative management

- Campaign and CampaignLine lifecycle.
- Creative metadata/version/review lifecycle and Documents integration.
- Campaign budget controls and scheduling.
- Versioned contextual targeting and policy evaluation.
- Approval/audit workflows and negative authorization tests.

### M3 — Ad serving and decisioning

- Low-latency decision API and publisher integration.
- Eligibility, ranking, pacing, frequency capping and safe budget reservation.
- Cache behavior, timeout/fail-closed policy, tenant isolation and load tests.
- Serving does not make a billable financial commitment until validated measurement is finalized.

### M4 — Measurement and attribution

- Secure event ingestion, idempotency, deduplication, invalid-traffic handling.
- Versioned attribution and billable-evidence finalization.
- Rebuildable analytics projections and provenance.
- Retention, privacy, and correction/appeal evidence.

### M5 — Billing, publisher earnings and settlement

- Advertiser billing requests from finalized billable evidence.
- Publisher revenue-share snapshots and reconciliation.
- Finance Billing/Receivables, Payment, Accounting and optional Wallet integration through public contracts.
- Payout eligibility, holds, dispute handling and idempotent orchestration.
- Financial reconciliation and audit evidence.

### M6+ — Exchange, advanced fraud, portals and network scale

- OpenRTB/demand integrations only after direct-sold delivery is proven.
- Advanced fraud scoring and appeal operations.
- Self-service advertiser/publisher portals and SDK distribution.
- Multi-network, agency, white-label and regional deployment support.

## 13. M0 acceptance criteria and evidence still required

M0 architecture baseline acceptance:
- [x] Advertising is a separate capability and does not become a Commerce Promotion submodule.
- [x] Existing finance/payment/accounting/wallet ownership is preserved.
- [x] Serving, measurement, business events, and analytics are separated.
- [x] Tenant isolation, authorization, idempotency, privacy, audit, and fail-closed budget safety are explicit.
- [x] Deployment topology is a composition decision, not a domain rewrite.

Before marking M0 fully complete:
- [ ] Publish a verified capability dependency map based on source/POM/runtime inspection.
- [ ] Record exact public ports/events and supported versioning from existing consumers.
- [ ] Confirm Maven reactor parent path and selected test command using current CI/build evidence.
- [ ] Record open integration gaps and unsupported deployment profiles.
- [ ] Review the final M1 artifact set against actual contract reuse and compile-time dependency requirements.

## 14. Decision summary

Proceed with a distinct `advertising` capability, initially focused on direct-sold and publisher-network advertising. Keep programmatic exchange as a later extension. Advertising owns campaign delivery and the evidence that makes an advertising event billable; Finance owns invoices, payment execution, wallet movements, and accounting journals; Commerce owns products, offers, marketplace transactions, and generic promotion rules; Documents owns binary storage; Event owns durable business-event infrastructure.

This is an architecture baseline, not implementation completion. M1 must begin only after the source-level contract and build-baseline evidence gates are satisfied.
