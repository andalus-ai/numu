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

This document records an evidence-based architecture and audit of the existing Advertising capability. Existing domain, application, PostgreSQL, and host-adapter code is present; the audit distinguishes implemented behavior from production gaps and does not claim that the full AdSense-style product is complete.

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

An Advertising reactor already exists at `advertising/pom.xml`, inherits from `../core/framework/pom.xml`, and is included by `core/framework/pom.xml`. It currently contains five artifacts: `numou-advertising-domain`, `numou-advertising-spi`, `numou-advertising-application`, `numou-advertising-postgres-adapter`, and `numou-advertising-host-adapter`. Do not create a parallel reactor or duplicate these modules. Extend the existing boundaries; introduce new artifacts only when an independently useful contract or runtime boundary is justified.

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


## 15. Source-level contract verification — follow-up audit

This section records source-level inspection on the repository's current `main` branch. It refines the initial POM-level assumptions above. It is not a substitute for build or runtime integration tests.

### Verified public contracts

| Capability | Source inspected | Verified contract behavior | Advertising integration consequence |
|---|---|---|---|
| Payment | `finance/payment/numou-payment-api/.../PaymentApplicationPort.java`, `CreatePaymentObligation.java` | `createOrGet` uses tenant-scoped idempotency; `find` requires tenant ID; refund is a distinct operation; payment command requires caller reference, positive amount and explicit ISO-4217 currency | Advertising can request advertiser payment obligations and query/refund through this API when a payment flow is required. It must treat timeout as indeterminate and reconcile, not assume failure. Payment status is not an invoice or accounting posting. |
| Identity | `identity/numou-identity-application/.../AuthorizationPort.java`, `CurrentPrincipalPort.java`, `TenantMembershipPort.java` | Trusted principal resolution, tenant membership, and tenant-scoped authorization requirement ports exist | Advertising application commands should receive trusted principal/tenant context and authorize at application boundary. Do not accept actor/tenant claims from SDK payload as authority. |
| Accounting | `finance/accounting/numou-accounting-application/.../AccountingCommandService.java`, `InvoiceService.java` | Existing command services cover invoice creation, payment recording, journal posting, and invoice lifecycle/query operations | Potential accounting integration exists, but command DTO semantics and idempotency/tenant guarantees must be inspected before choosing it for Advertising. Do not use Accounting invoice APIs as a substitute for Finance Billing without a deliberate ownership decision. |
| Wallet | `finance/wallet/numou-wallet-spi/.../WalletRepository.java`, `LedgerRepository.java` | Wallet persistence ports are versioned; ledger append and lookup are tenant-scoped and replay-safe by entry ID/reference | This is not yet evidence of a generic publisher payout contract. The current artifacts are named/described as Mobility wallet. Do not couple Advertising directly to Wallet domain/repository. Define a narrow payout orchestration adapter only after a supported shared application contract is established. |
| Documents | `documents/numou-document-storage-spi/.../DocumentStorage.java` | Provider-neutral binary storage port supports put/get/head/delete and explicitly says storage key is not authorization | Advertising owns creative metadata and review. A creative asset reference must be tenant-bound and authorized before storage access; storage key alone is never authorization. |
| Events | `core/framework/event/event-application/.../EventDispatchService.java` | Domain events are translated to public business events and wrapped in envelopes with tenant, participant, actor and correlation metadata; publisher port handles delivery | Use this for selected business lifecycle events. It is not proof of a high-volume impression/click ingestion system, nor does the inspected dispatcher itself expose an Advertising event idempotency contract. Measurement needs a separate validated ingestion/deduplication design. |
| Marketplace | `commerce/marketplace/numou-marketplace-application/.../MarketplaceOrderAuthorization.java`, checkout ports/services | Marketplace has explicit authorization and checkout payment ports; its order workflow has idempotent creation/recovery services | Sponsored marketplace placements should reference marketplace-owned listings/sellers by stable IDs. Advertising must not read marketplace tables or create a second listing/order lifecycle. |
| Commerce Promotion | `commerce/promotion/pom.xml` and promotion module description | Promotion evaluates rules/conditions/actions/stacking over a priced cart | Keep ad campaign delivery, impression/click charging and publisher share outside Promotion. Promotion may remain an optional presentation or checkout integration, not the Ad Campaign engine. |

### Contract gaps and cautions

1. **Party/Organization public contracts are not yet verified at source level.** The M1 account model should use opaque references, but the exact canonical identifier type and validation/lookup port must be confirmed before implementation.
2. **Finance Billing application ports and command DTOs are not yet verified.** The Billing aggregator has core/extensions and is the intended billing owner, but the exact consumer-facing API and idempotency/tenant semantics remain a gate.
3. **Accounting and Billing are not interchangeable.** Accounting currently exposes invoice-oriented APIs as well; the integration choice must be made after comparing Finance Billing's active invoice contract and accounting recognition/posting responsibilities.
4. **Wallet is not yet proven as a generic publisher payout facility.** Its inspected SPI is repository-oriented and its metadata is Mobility-specific. Advertising must not directly depend on those repository interfaces.
5. **EventDispatchService is a business event dispatch abstraction, not a raw analytics ingestion API.** Do not push every ad impression through it by default.
6. **No end-to-end adapter composition was verified.** Public Java types exist, but runtime CDI wiring, module dependencies, security adapters, and deployment profiles still need inspection.
7. **No Maven build or CI baseline was run.** Source inspection alone does not establish that the referenced reactors compile together or that a new Advertising reactor can be composed cleanly.

### Updated M1 design constraints

- Use Payment's `PaymentApplicationPort` only behind an Advertising-owned application port/adapter. Preserve tenant-scoped idempotency and indeterminate timeout semantics.
- Use Identity's trusted principal, tenant membership, and authorization ports (or the host's established equivalent) at application boundaries. SDK publisher credentials require a separate narrowly scoped service identity/property authorization flow.
- Use Documents' `DocumentStorage` behind an Advertising creative-asset application port; persist only opaque asset/storage references plus Advertising-owned metadata and checksum/version state.
- Publish only meaningful business lifecycle/finalization events through Event's translator/envelope/publisher flow. Design measurement ingestion separately.
- Keep Billing, Accounting, Payment, and Wallet as separate integration boundaries. M1 must not introduce billing/payout dependencies before the campaign and inventory contracts need them.
- Keep Marketplace and Promotion optional integrations; neither is required for the core advertiser/publisher/inventory model.
- Keep Party/Organization references opaque until the authoritative Party API is verified.

### M0 evidence checklist update

- [x] Read repository contributor/agent rules and architecture ownership contracts.
- [x] Inspect relevant aggregator POMs.
- [x] Verify source-level Payment, Identity authorization, Accounting, Wallet SPI, Documents storage, Event dispatch, Marketplace authorization/checkout, and Promotion boundaries.
- [ ] Verify Party/Organization public contracts and canonical IDs.
- [ ] Verify Finance Billing public application contract and distinguish it from Accounting invoice APIs.
- [ ] Inspect full active reactor dependency graph and runtime wiring for selected integrations.
- [ ] Establish reproducible Maven build/CI baseline for the exact reactors selected for M1.
- [ ] Finalize M1 artifact list and dependency direction based on the verified contracts.

M0 remains partially complete until the unchecked evidence gates are resolved or explicitly deferred with a reason. No build/test/CI success is claimed by this source audit.


### Additional Billing and Organization contract verification

| Capability | Source inspected | Verified behavior | Advertising consequence |
|---|---|---|---|
| Finance Billing | `finance/billing/core/numou-billing-application/.../BillingService.java` | Public API is centered on billing schedules, cycle processing, retries, dunning and history. | It is suitable for recurring advertiser billing only if the Advertising commercial model requires a recurring schedule and its tenant/idempotency guarantees are verified. It is not yet a proven one-off finalized-usage invoice API for CPM/CPC spend. |
| Finance Billing ports | `.../port/InvoicePort.java`, `.../port/PaymentPort.java` | Invoice port generates and reads invoice details using customer ID and Money; payment port processes tokenized payments/refunds. These ports do not themselves show explicit tenant scope or idempotency keys in their signatures. | Do not call these lower-level ports directly from Advertising. An adapter must be backed by an authorized, tenant-scoped, idempotent public application contract. The current payment port is not a replacement for the separate PaymentApplicationPort contract. |
| Organization | `organization/organization-spi/.../OrganizationPartyAssociationRepository.java`, `OrganizationRepository.java` | Organization has repository-level persistence contracts and a Party association history; PartyId is a first-class reference. | This is useful evidence for stable organization/party references, but these are persistence SPI contracts, not a consumer-facing Organization application API. Advertising must not depend directly on these repository interfaces. A public lookup/reference-validation contract still needs to be located or designed with the owning capability. |

### M0 integration decision refined

- **Advertiser charging:** finalized Advertising billable evidence should first be accumulated into a versioned billing statement/charge batch owned by Advertising. A future Finance adapter should submit an authorized, idempotent billing request through a suitable Billing application contract. Current Billing schedule APIs alone do not prove this use case is supported.
- **Recurring contracts:** if an advertiser purchases a recurring sponsorship/subscription, Finance Billing's schedule lifecycle may be a fit, subject to contract and policy verification.
- **One-off/usage-based billing:** do not build on Billing's lower-level InvoicePort or PaymentPort directly. A public one-off billing contract with tenant authorization, idempotency, currency/tax handling, and reconciliation semantics must be verified or added in Finance as a separate approved milestone.
- **Organization/Party:** store opaque `organizationId` and/or `partyId` references only after confirming the canonical ID type and a supported application-level lookup/authorization route. Do not import Organization repository SPI into Advertising.
- **Wallet/publisher payouts:** no direct wallet repository dependency. The absence of a generic payout application contract remains an explicit M5 integration gap, not a reason to create an Advertising wallet.

### Updated M0 evidence checklist

- [x] Read repository contributor/agent rules and architecture ownership contracts.
- [x] Inspect relevant aggregator POMs.
- [x] Verify source-level Payment, Identity authorization, Accounting, Wallet SPI, Documents storage, Event dispatch, Marketplace authorization/checkout, Promotion boundaries.
- [x] Inspect Billing public application API and its lower-level invoice/payment ports.
- [x] Inspect Organization/Party association persistence contracts.
- [ ] Locate or agree an Organization/Party consumer-facing lookup contract.
- [ ] Verify tenant/idempotency guarantees on Billing and Accounting commands and identify the supported usage-billing integration.
- [ ] Inspect full active reactor dependency graph and runtime wiring for selected integrations.
- [ ] Establish reproducible Maven build/CI baseline for the exact reactors selected for M1.
- [ ] Finalize M1 artifact list and dependency direction based on the verified contracts.

M0 remains partially complete. The source audit confirms several useful integration contracts, while also showing that Billing usage invoicing and generic Wallet payouts are not yet proven. No build/test/CI success is claimed by this source audit.


## 15. Follow-up contract and packaging verification (2026-11-10)

A second pass inspected additional exact reactor and child POMs. These findings refine the integration decisions above.

| Contract/package evidence | Verified fact | Advertising consequence |
|---|---|---|
| `finance/billing/pom.xml` and `finance/billing/core/pom.xml` | Billing is a nested reactor: `finance-billing` -> `numou-billing-services` -> domain/application/adapter/pricing/dunning | Correct Maven coordinates and paths must follow the nested reactor. The earlier shorthand path `finance/billing/numou-billing-application` is not valid. |
| `finance/billing/core/numou-billing-application/pom.xml` | Billing application describes CQRS commands, queries, and ports; depends on billing domain and foundation application, with CDI APIs | Advertising should integrate at the Billing application boundary, but exact use-case/port types still require Java source inspection. |
| `finance/payment/numou-payment-api/pom.xml` | Payment API is explicitly described as a framework-neutral consumer contract for payment execution and refunds; its POM has no runtime framework dependency | This is the preferred Payment dependency for advertiser collection/publisher payout orchestration. Do not depend on Payment Core or gateway implementation. Exact request/result and idempotency semantics remain to be inspected. |
| `finance/wallet/numou-wallet-domain/pom.xml`, `numou-wallet-application/pom.xml`, `numou-wallet-spi/pom.xml` | Artifacts are explicitly named/described as Mobility wallet; wallet domain depends on aggregate kernel and foundation; application depends on domain/SPI and Mutiny | Do not yet treat Wallet as a generic platform-wide publisher payout ledger. Verify whether its public contract is sufficiently generic; otherwise Advertising should orchestrate payout through Payment and Finance contracts without modeling wallet balances itself. Any shared-wallet generalization is a separate Finance milestone. |
| `core/framework/event/event-domain/pom.xml` | Domain describes transport-neutral `BusinessEvent`, `EventEnvelope`, `EventMetadata`, `EventVersion`, `EventId`, `CorrelationId`, and `CausationId` | Advertising business lifecycle events should use this envelope/identity/versioning contract rather than inventing a parallel event envelope. |
| `core/framework/event/event-application/pom.xml` | Application orchestrates envelope construction, event store/publisher ports, subscription registration and dispatch | Use the event application boundary for finalized business events; do not make raw ad impressions synchronous business-bus writes. |
| `core/framework/event/event-spi/pom.xml` | SPI names `EventPublisherPort`, `EventSubscriberPort`, `EventStorePort`, and `EventSerializerPort` | Event infrastructure is broker-neutral; adapter selection remains runtime composition. |
| `documents/pom.xml` and `documents/numou-document-storage-spi/pom.xml` | Documents reactor contains provider-neutral storage SPI plus local, S3, and config modules; storage SPI POM is framework-neutral | Reuse binary storage for creative assets. Advertising still owns creative metadata, approval/version, destination, and serving eligibility. |
| `identity/numou-identity-application/pom.xml` | Identity application describes CQRS use cases and owns UserRepository/PasswordHasher ports; it depends on identity domain and foundation application | This inspected Identity module is user identity, not proof of complete tenant/organization authorization semantics. Do not infer publisher/advertiser tenant ownership from user identity alone. |
| `commerce/marketplace/numou-marketplace-application/pom.xml` | Marketplace application depends on Marketplace domain and Payment API | Marketplace integration can be contract-based, but this POM alone does not prove an ad inventory or promoted-listing API exists. |
| `commerce/promotion/numou-promotion-application/pom.xml` | Promotion application depends on Foundation Application, Promotion Domain, Promotion SPI and Mutiny; described as stateless discount resolution | Confirms Promotion is cart/discount resolution, not the Advertising campaign or ad-decision owner. |

### Corrected integration confidence

- **High confidence at POM boundary:** Event envelope/application/SPI layering; Documents storage SPI; Payment API as the consumer-facing framework-neutral payment contract; Billing as a CQRS application capability; Promotion as discount resolution.
- **Candidate only:** Wallet integration. Current module naming and description are Mobility-specific; generic publisher wallet reuse is not established.
- **Not established by inspected POMs:** tenant/organization authorization contract, Party/Organization reference type, advertiser invoice creation use case, payment idempotency contract, creative malware scanning/moderation, Marketplace sponsored inventory contract, and raw event ingestion guarantees.

### Remaining source-level blocker

The GitHub source retrieval/search available during this pass exposed file fetches and POMs but did not provide a repository directory listing; attempted code searches for guessed Java symbols returned no matches. Therefore no Java API/class names are asserted beyond names explicitly documented in POM descriptions. Before M1 creates dependencies, retrieve the exact Java source files (or a source archive/worktree) and verify public types, package names, tenant context, authorization, transaction, and idempotency behavior.

### M0 status after follow-up

The capability ownership and package-level integration map are now more precise. M0 is still **not fully accepted** because source-level contract verification and a reproducible Maven/CI baseline have not been performed. The Advertising module must not yet add dependencies against guessed artifact names or assumed generic Wallet behavior.


## 16. Existing Advertising implementation audit (2026-11-10)

The repository Git tree was inspected recursively for `advertising/`. This revealed an existing implementation that was not captured by the initial high-level audit. The earlier proposal to create a new `advertising/pom.xml` is superseded: the reactor and five modules already exist and are already included by `core/framework/pom.xml`.

### 16.1 Existing implementation inventory

| Existing artifact / source | Verified implementation | Assessment |
|---|---|---|
| `advertising/pom.xml` | Top-level reactor with five modules; parent is `numou-framework` at `../core/framework/pom.xml` | Existing capability boundary; extend in place |
| `numou-advertising-domain` | AdvertiserAccount, PublisherAccount, PublisherProperty, AdPlacement, Campaign, Creative, AdDecision, serving request/result/inventory/candidate and typed IDs/statuses | Useful domain foundation already exists |
| `numou-advertising-spi` | Tenant-scoped stores for accounts/inventory, campaigns/creatives/assignments, and ad-serving inventory/candidates/decision audit | Existing ports are explicit and tenant-scoped |
| `numou-advertising-application` | AdvertisingAccountService, AdvertisingCampaignService, AdvertisingAdServingService; authorization and canonical Party verification ports | Core onboarding/campaign/serving use cases exist |
| `numou-advertising-postgres-adapter` | JDBC stores for accounts, campaigns, serving read model, and decision audit | Existing PostgreSQL persistence |
| Migrations V1–V3 | Accounts/inventory, campaigns/creatives/assignments, serving targets and append-only ad-decision audit | Existing relational schema with composite tenant keys and owner foreign keys |
| `numou-advertising-host-adapter` | Account/inventory REST resource, CurrentSecurity-based authorization adapter, Ecosystem Party lookup adapter, CDI producer for AdvertisingAccountService | Host integration exists but is incomplete for campaign and serving operations |
| Existing tests | Domain lifecycle tests, application service tests, PostgreSQL Testcontainers tests for account/campaign/serving stores, host authorization/Party adapter tests | Meaningful baseline, but not a complete acceptance suite |

### 16.2 Confirmed behavior

- Advertiser and publisher accounts are tenant-scoped and reference canonical Party through an opaque `PartyReference`.
- Account registration calls a Party verification port; the host adapter resolves that reference using Ecosystem `PartyLookupPort`.
- Host authorization obtains the actor/tenant from `CurrentSecurity`, verifies tenant membership, checks active Identity membership, and asks Security for an effective permission decision.
- Publisher properties, placements, campaigns, creatives and ad decisions carry tenant identifiers.
- PostgreSQL account/property/placement/campaign/creative writes use optimistic version compare-and-set for updates.
- The serving store scopes joins by tenant and requires explicit campaign-to-placement and campaign-to-creative assignments.
- Serving validates placement enabled state, property verified state, publisher active state, request page origin and requested format.
- Candidate eligibility checks campaign active/time window, advertiser active state, creative approved state and creative format.
- Every filled or no-fill decision is appended to `advertising_ad_decision`; decision constraints tie selected campaign, creative, advertiser and placement to the same tenant and assignment records.
- The serving application explicitly does not create billable impressions; measurement/billable qualification is deferred to a later milestone.

### 16.3 Production gaps identified in existing implementation

1. **Property verification is currently a privileged state transition, not proof of ownership.** `verifyProperty` checks authorization and publisher state, then calls `property.verify()`. No DNS TXT, HTTPS well-known file, meta-tag challenge, verification token, expiry, or re-verification evidence was found in the inspected source tree. This must be fixed before publishers can self-activate inventory in production.
2. **Campaign REST surface is absent.** The host adapter tree contains `AdvertisingAccountResource` but no Campaign/Creative REST resource. The campaign service exists as Java application code but is not exposed through a verified HTTP API in the inspected tree.
3. **Serving transport is absent.** `AdvertisingAdServingService` exists, but no public serving REST endpoint or publisher SDK/resource was found. The service is therefore not yet an end-to-end publisher ad-serving product.
4. **CDI composition is incomplete.** `AdvertisingServiceProducer` produces only `AdvertisingAccountService`; no corresponding producer for `AdvertisingCampaignService` or `AdvertisingAdServingService` was found in the host adapter tree.
5. **No delivery safety controls beyond basic eligibility were found.** There is no campaign budget reservation, spend pacing, bid/priority model, frequency capping, consent policy gate, geo/context targeting engine, or per-publisher inventory policy in the inspected implementation. Selection is deterministic by campaign creation time and IDs, not an auction.
6. **No measurement pipeline exists in the inspected modules.** Decision audit is not an impression/click/conversion event ledger. There is no event ingestion authentication, event idempotency, invalid-traffic adjudication, attribution, billable-evidence finalization, or analytics projection.
7. **No monetization lifecycle exists in Advertising.** There is no advertiser chargeable usage/billing request, publisher earning calculation, revenue-share contract, settlement reconciliation, payout orchestration, or Finance integration.
8. **Creative asset lifecycle is incomplete.** Creative stores an opaque document reference, but no Documents SPI adapter or asset authorization/scan/metadata validation integration was found in the Advertising module tree.
9. **Property verification and creative approval need operational evidence.** Creative approval is currently a state transition; no moderation queue, review actor/audit record, malware scanning, landing-page safety inspection, or policy-version evidence was found in the inspected files.
10. **Blocking persistence needs a clear execution contract.** PostgreSQL adapters use synchronous JDBC while returning already-completed `CompletionStage` values. This is explicitly described as blocking for serving persistence. Every transport/composition must run this path on a blocking executor or the adapter must be redesigned; do not assume the return type makes JDBC non-blocking.
11. **Tenant-level authorization is not account-level delegation.** The authorization adapter verifies tenant membership and a tenant permission action. The inspected code does not yet prove scoped agency delegation or per-advertiser/per-publisher resource grants within one tenant.
12. **Domain and persistence tests do not prove runtime readiness.** Existing Testcontainers coverage is valuable, but no Advertising-specific CI job invoking `mvn -f advertising/pom.xml verify` was found in `.github/workflows/ci.yml`. The Advertising modules are included in the broader framework reactor build, which is not equivalent to a dedicated capability acceptance gate.

### 16.4 Wallet integration correction

The Wallet capability has real payout and idempotent ledger behavior, and CI refers to installing shared wallet artifacts. However, the inspected domain/application implementation is still explicitly driver-oriented:
- `OwnerKind` is built around DRIVER, RIDER and PLATFORM ownership.
- `WalletService.requestPayout` accepts a driver ID and applies driver payout policy.
- `PayoutService` is documented as driver withdrawals and uses driver-specific payout aggregates/events.
- The Wallet aggregate contains mobility-specific overdraft and booking/cash-ride semantics.

Therefore it is **not yet a generic Publisher Wallet contract**. Advertising must not create a fake `OwnerKind.PUBLISHER` dependency or couple publisher revenue to driver withdrawal semantics. A generic finance-owned beneficiary wallet/payout contract would need a separately scoped Finance design and migration, or Advertising may orchestrate direct payout through an appropriate general Payment/Finance contract once verified.

### 16.5 Updated milestone interpretation

The previous M1/M2/M3 roadmap must now be interpreted as implementation stages, not greenfield module creation:

- **M1 — Account and inventory hardening:** existing advertiser/publisher/property/placement domain and persistence exist. Complete property ownership proof, resource-scoped authorization, lifecycle audit, tenant/RLS tests, and host API coverage.
- **M2 — Campaign and creative hardening:** existing campaign/creative domain and application services exist. Add campaign/creative REST APIs, durable review evidence, Documents integration, moderation/policy versioning and stronger validation.
- **M3 — Production serving:** existing eligibility service and decision audit exist. Add a safe serving transport/SDK, publisher property credentials, delivery policy, budget reservation/pacing, frequency caps, ranking/auction extension points, and bounded-latency execution.
- **M4 — Measurement:** still largely new work; add authenticated, idempotent measurement ingestion, invalid traffic handling, attribution and finalized billable evidence.
- **M5 — Monetization:** still largely new work; integrate advertiser billing, publisher earning/revenue share, reconciliation and payout with Finance-owned contracts.
- **M6+ — Exchange, advanced fraud and network scale:** later extensions.

### 16.6 CI and verification baseline

The shared framework parent `core/framework/pom.xml` includes `../../advertising`, so the broader framework reactor can compile/test the Advertising modules when that reactor is run. The CI workflow also runs module topology, domain dependency boundary, and capability dependency direction checks. However, the current CI file has no explicit Advertising job with an Advertising-specific `verify` command or dedicated report upload.

The relevant reproducible commands to validate in CI/local execution are:
- `python3 tools/verify-module-topology.py`
- `python3 tools/verify-domain-boundaries.py`
- `python3 tools/verify-capability-boundaries.py`
- `mvn -B -ntp -f core/framework/pom.xml install` (includes Advertising through the framework reactor)
- `mvn -B -ntp -f advertising/pom.xml verify` (dedicated Advertising reactor gate; confirm parent/dependency prerequisites in a clean environment)

These commands were inspected from repository structure/CI configuration but were **not executed in this audit session**. No passing build or CI result is claimed.

### 16.7 Revised M0 outcome

M0 has now identified the actual existing Advertising codebase and the major missing production capabilities. The next work should be a focused gap-hardening milestone on existing modules, not a greenfield scaffold. Before implementation, establish a clean build baseline and add a dedicated Advertising CI gate; then prioritize proof-based publisher property verification and account-level authorization as the highest-risk M1 issues.
