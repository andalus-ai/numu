# Numou Domain Capability Matrix

**Status:** Normative ownership and repository-boundary contract  
**Source repository:** `andalus-ai/numu`  
**Initial extraction wave:** Ten repositories listed below  
**Architecture basis:** `AGENTS.md`, [ADR-003 — Reusable capability modules](ADR-003-reusable-capability-modules.md), and [Capability Packaging and Composition Contract](CAPABILITY-PACKAGING-AND-COMPOSITION.md)

## 1. Purpose and status semantics

This document defines the intended ownership of Numou business capabilities and the first repository extraction wave. It is the source of truth for deciding where new code belongs, which repository owns an invariant, and how repositories may integrate.

The matrix distinguishes three states:

- **Existing:** source code or an established contract has been observed in the monorepo. This does not imply production completeness.
- **Target:** the capability is assigned to a repository, but its implementation and source paths still require inventory/verification.
- **Deferred:** intentionally remains in the monorepo or a future repository until a later extraction wave is approved.

A repository name or directory name is not evidence that a capability is implemented. A capability is not considered migrated until its source, tests, build metadata, migrations, documentation, and dependency edges have been accounted for.

## 2. Initial repository portfolio

The first extraction wave is deliberately limited to these ten repositories. Do not create or move code into additional repositories until this wave has a verified source-to-target inventory and the user approves the next wave.

| Repository | Capability group | Primary ownership | Key non-ownership |
|---|---|---|---|
| [numou-identity](https://github.com/andalus-ai/numou-identity) | Identity | Canonical people, organizations, principals, profiles, contact points, addresses and identity relationship references | Authentication policy, authorization decisions, tenant lifecycle, customer lifecycle |
| [numou-finance](https://github.com/andalus-ai/numou-finance) | Finance | Money-related business invariants, accounting, ledger, payment execution, wallet, billing, AR/AP, settlement, reconciliation, Syirkah finance | Commerce order lifecycle, source-domain earning facts, identity, security policy |
| [numou-security](https://github.com/andalus-ai/numou-security) | Security | Authentication, authorization, credentials, sessions, policies, roles/permissions, security audit and cryptographic/security services | Canonical identity records, tenant lifecycle, business ownership |
| [numou-commerce](https://github.com/andalus-ai/numou-commerce) | Commerce | Product/catalog, offer, pricing, promotion, cart, checkout, order, POS, merchant, marketplace and sales-channel lifecycle | Payment execution, accounting, inventory authority, customer identity |
| [numou-customer](https://github.com/andalus-ai/numou-customer) | Customer and CRM | Customer lifecycle, CRM, leads, opportunities, segmentation, customer interactions, consent, loyalty and customer 360 projections | Canonical person/organization identity, payment, order, inventory |
| [numou-supply-chain](https://github.com/andalus-ai/numou-supply-chain) | Supply Chain | Inventory, stock, warehouse, procurement, purchasing, supplier relationship, receiving, fulfillment, manufacturing and quality | Supplier identity master, AP/payment execution, sales order authority |
| [numou-logistics](https://github.com/andalus-ai/numou-logistics) | Logistics | Shipment, parcel, cargo, courier, consignment, dispatch, delivery, tracking, custody, freight and proof of delivery | Fleet/driver master, inventory authority, payment/accounting execution |
| [numou-tenancy](https://github.com/andalus-ai/numou-tenancy) | Tenancy | Tenant lifecycle, hierarchy, membership-in-tenant, tenant configuration, provisioning, isolation context and tenant lifecycle events | User/person identity, authorization policy, application entitlement |
| [numou-mobility](https://github.com/andalus-ai/numou-mobility) | Mobility | Driver operations, fleet, vehicle, trip/ride, dispatch, fare policy, location, geofence and mobility operations | Canonical driver identity, financial wallet/ledger, generic shipment lifecycle |
| [numou-workforce](https://github.com/andalus-ai/numou-workforce) | Workforce | Employee/worker lifecycle, workforce profile, attendance, shift, scheduling, leave, skills, recruitment, performance and allocation | Canonical person identity, authentication, payroll ledger/accounting |

All ten destination repositories exist as separate GitHub repositories. The Tenancy repository already contains a staged source transfer from tenancy/ (75 source files; destination PR #1), while Identity has only a migration-status README and Security is still empty. The remaining destinations must be inspected individually before transfer. A staged copy is not an accepted migration: the destination must build independently, preserve contracts and data semantics, and have its consumers migrated.

## 3. Capability ownership matrix

| Capability | Owning repository | Owned responsibilities | Explicit boundary |
|---|---|---|---|
| Person and legal-entity identity | Identity | Person, organization, party, identifiers, identity lifecycle and relationships | Does not own customer, employee, supplier or driver business lifecycle |
| Principal and identity profile | Identity | Principal identity references, profile attributes, contact points, addresses, external identity links | Authentication factors and credentials are Security-owned |
| Social graph | Deferred shared platform / Foundation | Generic follow/connect/block graph engine, relationship graph queries, graph persistence and provider-neutral graph contracts | Identity supplies canonical subject references; domain-specific relationships such as supplier contracts or employee assignments remain in their business capability |
| Authentication | Security | Credential verification, password hashing/upgrade, MFA, authentication flows, session issuance/revocation, federation protocol security | Identity supplies canonical subject; Security does not become the person registry |
| Authorization | Security | RBAC, ABAC, permissions, policy evaluation, resource/action decisions and enforcement contracts | Tenant and resource context are inputs; Security does not own the resource's business state |
| Security audit | Security | Authentication/security events, policy decisions where appropriate, credential/security lifecycle audit | Business audit facts remain with their owning capability; shared audit transport may be a later Foundation capability |
| Tenant lifecycle | Tenancy | Tenant creation, activation, suspension, hierarchy, membership association, tenant configuration and provisioning state | Identity owns the member subject; Security owns the authorization decision |
| Tenant isolation | Tenancy | Tenant context contracts, tenant-scoped identifiers and isolation policy metadata | Storage enforcement remains in persistence adapters and must be verified with integration tests/RLS where applicable |
| Accounting | Finance | Chart of accounts, journal, posting, periods, close, financial statements, accounting policy and ledger integrity | Other groups request postings through contracts; no direct ledger-table writes |
| Money and financial primitives | Foundation initially; Finance owns financial semantics | Shared Money/value primitives stay in the existing Foundation owner until a deliberate Foundation extraction; financial account/balance/movement semantics belong to Finance | Do not introduce a second Money implementation in Finance or consumer groups |
| Payment execution | Finance | Payment intent/execution, method/provider integration, callback verification, refunds, reversals and payment status | Payment status is not wallet balance, invoice status or a journal entry |
| Wallet | Finance | Wallet accounts, balance policy, holds, immutable movements, idempotency, freeze/restriction and reconciliation | Consumer capabilities own their own eligibility, fare, credit or earning decisions |
| Billing and receivables/payables | Finance | Billing agreements/invoices, AR/AP obligations, allocation, collection and settlement scheduling | Source capability owns the underlying sale, booking, service or procurement fact |
| Revenue and spend coordination | Finance, subject to existing contract verification | Reusable orchestration only after proving shared invariants; source-linked evidence and reconciliation | Do not duplicate Billing revenue recognition, Budget, Purchasing, AP, Payment, Wallet or Accounting |
| Syirkah finance | Finance | Syirkah-specific financial agreements, allocations, profit distribution, liability settlement and accounting integration | General Syirkah/member/relationship identity is not automatically Finance-owned |
| Product and catalog | Commerce | Product/variant, catalog, offering, channel assortment and commercial availability projection | Physical stock quantity and reservation are Supply Chain-owned |
| Pricing and promotion | Commerce | Price lists, pricing rules, discount, coupon, promotion and offer evaluation | Tax and accounting recognition use Finance contracts/policies |
| Cart, checkout and order | Commerce | Cart, checkout orchestration, sales order lifecycle, returns initiation and order commercial snapshot | Payment execution belongs to Finance; inventory reservation belongs to Supply Chain |
| POS and omnichannel | Commerce | POS sale/session, terminal, branch sales configuration, channel orchestration, kiosk and sales-channel behavior | Branch is an organizational/location reference; inventory and payment authority remain in their owners |
| Marketplace and merchant | Commerce | Marketplace listing, merchant onboarding state, offer and marketplace order lifecycle | Merchant legal identity references Identity; merchant payout uses Finance |
| Customer and CRM | Customer | Customer lifecycle, leads, opportunities, sales pipeline, segmentation, interactions, campaigns, consent and loyalty | Customer is a business role/relationship, not a duplicate canonical person/organization registry |
| Customer 360 | Customer | Customer-centric projections and read models assembled from authorized source contracts/events | Must not become the authoritative database for order, invoice, shipment, support or identity state |
| Inventory and warehouse | Supply Chain | Stock ledger, stock movement, reservations, warehouse, bins, replenishment and stock counts | Commerce may request reservation; it cannot mutate stock tables |
| Procurement and purchasing | Supply Chain | Requisition, RFQ, purchase order, receiving, supplier operational relationship and procurement workflow | Supplier canonical identity belongs to Identity; payable/payment belongs to Finance |
| Fulfillment and manufacturing | Supply Chain | Pick/pack/ship preparation, fulfillment work, BOM, production/work orders, material planning and quality | Shipment execution and tracking belong to Logistics |
| Shipment and courier | Logistics | Shipment/parcel/cargo/consignment lifecycle, carrier assignment, tracking, dispatch, delivery and proof | Logistics references customer, driver, vehicle, inventory and payment through contracts |
| Custody and discrepancy | Logistics | Custody handoff, scan/evidence, discrepancy, exception and claims workflow | Financial settlement is Finance-owned; source order remains Commerce-owned |
| Driver, fleet and vehicle | Mobility | Driver operational status, fleet, vehicle, trip/ride, availability, assignment and maintenance | Driver person/identity and employment relationship remain Identity/Workforce-owned |
| Geo and location | Mobility for mobility-specific semantics; neutral geo primitives remain Foundation pending extraction | Location updates, geofence, trip route and mobility-specific spatial operations | Do not force map provider, GPS precision or continuous tracking on every consumer; logistics may own shipment-specific route execution |
| Workforce | Workforce | Employee/worker engagement, workforce profile, attendance, shifts, leave, scheduling, skills, recruitment and performance | Person identity is Identity-owned; authorization is Security-owned |
| Payroll integration | Workforce + Finance | Workforce calculates/exports approved compensation inputs; Finance owns payment and accounting posting | Do not create a second payroll ledger or financial transaction model |
| Feedback and survey | Deferred | Keep current owner in monorepo pending later group decision | Do not assign to Customer merely because feedback may be customer-submitted |
| Developer portal and Iket integration | Deferred | Keep current developer-platform ownership until a dedicated repository is approved | Iket remains independent; integrate through stable APIs/contracts |
| AI, agents and MCP | Deferred | Keep current AI/tooling ownership pending a later repository | Agents use normal authenticated and authorized capability contracts |
| Workspace, knowledge and project management | Deferred | Keep current workspace/project ownership pending a later repository | Industry project semantics must not leak into neutral project/workspace domains |
| Content, documents and storage | Deferred | Keep current content/document ownership pending a later repository | Business capabilities own document references and access intent, not binary storage internals |
| Communication and notification | Deferred | Keep current notification/messaging ownership pending a later repository | Domain groups publish intent/events; provider delivery is not copied into each domain |
| Data and analytics | Deferred | Keep current reporting/analytics ownership pending a later repository | Analytics is not the operational source of truth |
| Application platform/runtime | Deferred | Keep current runtime/control-plane ownership pending a later repository | Control plane resolves/composes capabilities; it does not duplicate capability business logic |
| Industry extensions | Deferred | Construction, hospitality, government, agriculture, events, field service and other verticals remain in the monorepo until separately assigned | Industry modules compose the ten groups; they do not become generic platform owners |

## 4. Cross-repository dependency and contract rules

### 4.1 Dependency direction

1. The existing Foundation/framework remains the shared technical base during this first extraction wave. Do not create an eleventh repository as part of this task.
2. Tenancy, Identity and Security are separate bounded contexts. Their integration is through stable subject, tenant-context and authorization contracts.
3. Business capabilities depend on public contracts, not another repository's private packages, persistence entities, database schema or internal implementation.
4. Finance is the sole owner of financial ledger, wallet, payment execution and accounting invariants.
5. Commerce owns sales/order lifecycle; Supply Chain owns inventory and procurement; Logistics owns shipment lifecycle; Mobility owns driver/fleet/trip operations; Workforce owns employment/worker operations; Customer owns customer relationship lifecycle.
6. Avoid cyclic compile-time dependencies. Where two capabilities need to collaborate, use a narrow port, published contract, versioned event or composition adapter.
7. Runtime topology is not implied by repository boundaries. Each capability remains usable in-process in a modular monolith or remotely through an API/event adapter.

### 4.2 Identity, tenancy and security integration

- Identity issues stable subject/person/organization identifiers and owns their lifecycle.
- Tenancy associates an identity subject with tenant membership and tenant lifecycle.
- Security authenticates a subject and evaluates authorization using subject, tenant, action, resource and policy context.
- A tenant membership is not a role grant by itself unless an explicit Security policy says so.
- A tenant identifier, object identifier or capability manifest is never proof of authorization.
- Do not duplicate password hashes, MFA secrets, permission policy or tenant membership state in business repositories.

### 4.3 Finance integration

- Business capabilities publish source facts and request financial actions through Finance application contracts.
- Payment execution, wallet movements, accounting journals and invoice/receivable state keep distinct identifiers, lifecycles and idempotency scopes.
- A successful payment does not itself prove that a journal was posted; a wallet movement is not automatically a balanced journal entry.
- Financial posting uses explicit source references, tenant/actor/correlation context and durable idempotency.
- Reconciliation and outbox/inbox delivery guarantees must survive extraction.

### 4.4 Events and API contracts

Cross-repository messages must carry, where applicable:

- stable event/contract name and schema version;
- tenant identifier and actor/principal reference;
- aggregate/source identifier and revision;
- event identifier, correlation and causation identifiers;
- occurred-at timestamp and producer identity;
- idempotency key or deduplication identity for side-effecting commands.

Do not publish persistence entities as integration events. Do not rely on shared database tables as an integration contract. Breaking changes require an explicit compatibility/migration plan.

## 5. Maven and artifact ownership rules

The repository extraction does not require changing every Java package or artifact coordinate immediately. Existing coordinates are preserved until a separately reviewed compatibility migration is ready.

- Each destination repository will own its own Maven reactor/aggregator and capability BOM.
- The existing shared framework parent and Foundation artifacts remain in their current source repository until a deliberate Foundation extraction is approved.
- No destination may use a relative Maven parent path into the source monorepo.
- Cross-repository dependencies must resolve from a published artifact repository or an explicitly configured development repository; local reactor success alone is insufficient.
- Group BOMs manage versions for their own artifacts and only import prerequisite BOMs that actually exist and are published.
- Do not create placeholder dependency entries for artifacts that have not been migrated/published.
- Keep adapter artifacts optional. Domain/application consumers must not pull JDBC, REST, Kafka, Quarkus or cloud SDK dependencies by default.
- Use Java 21 compatibility unless an existing capability has an explicitly documented and approved runtime requirement.
- Test fixtures and examples are not production runtime dependencies.

## 6. Migration inventory requirements

Before moving a capability, record the following for every source module:

| Inventory field | Required evidence |
|---|---|
| Source path | Exact directory and module POM in `andalus-ai/numu` |
| Maven identity | Group ID, artifact ID, version, packaging and parent |
| Destination | One of the ten repositories above |
| Target module | Proposed artifact and package ownership |
| Source consumers | All reactor modules and external consumers referencing it |
| Dependencies | Internal and third-party dependencies, classified as API/runtime/test |
| Persistence | Tables, migrations, repositories, transaction boundaries and tenant isolation |
| Events | Published/consumed event names, schemas, outbox/inbox and replay behavior |
| API | REST/RPC endpoints, DTOs, compatibility and security behavior |
| Tests | Unit, integration, architecture and contract tests |
| Documentation | ADRs, capability docs, runbooks and examples |
| Migration state | Not inventoried / inventoried / staged / migrated / verified |

A module may be split only when its invariants and public contracts can be separated safely. If a module contains multiple capability owners, first create a reviewed split plan; do not assign the whole directory based on its name.

## 7. Migration sequence for the ten repositories

The sequence below is an ordering constraint for dependency safety, not a claim that the work is complete.

1. **M0 — Inventory and contract freeze:** enumerate all Maven reactors and modules; map every source path, consumer, persistence migration, event and API. Record existing artifact coordinates and identify shared-parent dependencies.
2. **M1 — Tenancy, Security, then Identity:** Tenancy can migrate independently; Security depends on shared Foundation/Cache/Observability/Social Graph artifacts; Identity and Organization consume Security SPI/application/vault contracts and therefore follow Security.
3. **M2 — Finance:** extract financial primitives' consumers carefully, accounting, payment, wallet, billing, Syirkah and settlement without duplicating Money or financial invariants.
4. **M3 — Supply Chain and Commerce:** establish order-to-inventory contract and preserve stock reservation/idempotency semantics.
5. **M4 — Customer:** migrate customer/CRM ownership and replace duplicate person/organization models with Identity references.
6. **M5 — Logistics and Mobility:** preserve shipment/custody and driver/fleet/trip boundaries; keep wallet and inventory ownership external.
7. **M6 — Workforce:** integrate canonical Identity and Finance contracts; preserve attendance and payroll-related behavior.
8. **M7 — Compatibility and decommission:** verify all consumers use published contracts, remove obsolete source copies only after proof, and update monorepo composition/build documentation.

A milestone can be reordered only after dependency analysis demonstrates that its prerequisites are satisfied. Keep the work in substantial milestone pull requests rather than one PR per small module.

## 8. Migration acceptance criteria

A capability is **migrated** only when all applicable criteria pass:

- [ ] Source modules and all consumers are inventoried.
- [ ] Destination repository contains the complete intended source, tests, resources, migrations and documentation.
- [ ] Git history is preserved where practical, or source commit/path provenance is recorded.
- [ ] Maven builds without a relative path into `andalus-ai/numu`.
- [ ] Public artifacts are published/resolvable at the intended coordinates.
- [ ] No duplicate authoritative implementation remains active in the monorepo.
- [ ] Cross-repository dependencies use public contracts and do not form cycles.
- [ ] Tenant isolation, authentication and authorization guarantees are preserved.
- [ ] Idempotency, transaction boundaries, outbox/inbox, replay and audit semantics are preserved where applicable.
- [ ] API/event/persistence compatibility and data migration are documented and tested.
- [ ] CI runs the affected unit, integration, architecture and contract tests.
- [ ] The source monorepo's composition and CI are updated to consume the extracted artifacts.
- [ ] A migration report records commit, PR, artifact versions, tests and remaining limitations.

A repository being created, a directory being copied, or a README being added is not migration completion.

## 9. Deferred repository groups

The following are intentionally out of scope for the current ten-repository wave. Their current ownership remains in the monorepo until a later matrix revision assigns them to a new repository:

- Foundation and framework;
- Workspace and project management;
- Communication and notification;
- Content, documents and storage;
- Developer platform and Iket integration;
- AI, agents and MCP;
- Data, reporting and analytics;
- Application platform and runtime;
- Feedback and survey;
- Industry extensions (construction, hospitality, government, agriculture, education, healthcare, events, queue and field service).

Do not create these repositories or move their code as part of the current wave.

## 10. Governance

- This matrix is normative for new code and migration decisions.
- Ownership changes require an update to this matrix and, when durable architecture decisions change, an ADR.
- A repository owner may propose changes but may not silently take over another capability's invariant.
- Every migration PR must identify the affected matrix rows and explain any deviations.
- The monorepo remains the source of truth for modules not yet migrated; destination repositories become authoritative only after migration acceptance criteria are met.
- Do not delete or disable source modules until consumers, releases, persisted data, deployment manifests and rollback paths have been verified.



## 11. Observed monorepo source-root mapping (M0 initial inventory)

The source repository tree was inspected on 2026-11-12. The following are observed top-level Maven roots and POM counts, not a statement that every artifact is ready to publish. The exact transitive consumer/dependency graph, migrations, and package-level ownership still require review.

| Destination | Observed source roots | POM count | Notes |
|---|---|---:|---|
| numou-identity | `identity/`, `organization/` | 11 | Organization contains Organization, OrganizationUnit, membership, invitation and party-association models. Verify tenant-membership distinctions before moving. |
| numou-finance | `finance/`, `budget/` | 85 | Finance includes accounting, billing, payment, Syirkah, tax, wallet, treasury and subscription finance. Budget is a separate current reactor root and must be dependency-audited. |
| numou-security | `security/` | 14 | Includes security-vault modules and a `social-graph-adapter`; the latter is not social-graph domain ownership and requires relocation/re-wiring review. |
| numou-commerce | `commerce/`, `product/` | 57 | Commerce includes POS, offering, pricing, promotion, marketplace, affiliate, kiosk, omnichannel and subscription modules. Product is a separate current root. |
| numou-customer | `crm/`, `support/` | 11 | CRM and reusable support/ticketing are separate current roots; confirm support case/customer references use Identity IDs. |
| numou-supply-chain | `supply/` | 19 | Contains inventory, purchasing, fulfillment and warehouse. |
| numou-logistics | `logistics/` | 6 | Current aggregator includes fleet and courier modules. Fleet/driver ownership must be separated from shipment/courier ownership before extraction. |
| numou-tenancy | `tenancy/` | 6 | Tenant lifecycle, SPI, application and adapter. |
| numou-mobility | `mobility/`, `transport/` | 21 | Mobility is ride-hailing/driver/ride/pricing. Transport is a distinct scheduled passenger-transport capability and should remain a separately named module inside this repository if its contracts fit. |
| numou-workforce | `workforce/`, `talent/` | 17 | Workforce includes HRIS/leave and workforce domain/application/adapters. Talent is a separate talent/recruitment/work marketplace capability; verify it does not duplicate Customer marketplace or Identity profiles. |

### 11.1 Explicitly deferred or split-review roots

| Source root | Current decision | Reason |
|---|---|---|
| `core/framework/` | Deferred Foundation/framework | Shared parent, technical platform, Foundation, plugin, runtime, workflow, events, reliability and social-graph domain. Do not copy this root into any of the ten repositories. |
| `geo/` | Deferred Foundation/geo contract | Provider-neutral geographic primitives should not be silently owned by Mobility. Mobility may own its location/trip behavior and adapters. |
| `vehicle-service/` | Deferred industry/vehicle-service extension | Workshop/service-order capability is not the same invariant as fleet/vehicle assignment. |
| `asset/` | Deferred asset-management capability | Asset lifecycle may be shared by multiple industries; do not assume Supply Chain inventory is its owner. |
| `automotive-parts/`, `automotive-parts-purchasing/` | Deferred automotive extension | Industry-specific catalog and procurement policies; reuse Supply Chain contracts. |
| `restaurant/`, `hospitality/` | Deferred industry extensions | Restaurant operations and hospitality workflows compose Commerce, Finance, Workforce and Reservation contracts. |
| `reservation/`, `events/`, `attendee/` | Deferred reservation/events capability | Reservation and admission entitlement semantics need an explicit owner; do not absorb into Commerce order or Customer. |
| `corporate/` | Deferred corporate governance extension | Company registry/compliance semantics need review against Identity Organization before assignment. |
| `organization/` | Assigned to Identity, with contract review | Canonical Organization and Party association belong with Identity; workforce-specific org chart projections remain Workforce-owned. |
| `transport/` | Assigned to Mobility as a separate passenger-transport sub-capability | Its POM describes scheduled passenger transport, not parcel/cargo shipment. |
| `talent/` | Assigned to Workforce as a separate talent capability | Recruitment/talent lifecycle is workforce-adjacent; preserve marketplace-specific contracts. |
| `feedback/`, `survey/`, `notification/`, `documents/`, `document/`, `workspace/`, `project/`, `queue-management/`, `developer-platform/`, `application-platform/`, `agent-integration/` | Deferred | Outside the ten-repository extraction wave. |

### 11.2 Source-root extraction is not a blind directory copy

The current roots inherit `tech.kayys.numou:numou-framework:0.1.0-SNAPSHOT` through relative paths to `../core/framework/pom.xml`. That parent path will not exist in the new repositories.

Before a source root is moved:

1. Replace the relative parent with a published parent artifact or a repository-local parent whose version is released and independently resolvable.
2. Replace same-reactor assumptions with published dependency coordinates or narrow public contracts.
3. Remove modules from the source reactor only after the destination build and source consumer migration are verified.
4. Preserve existing package names and artifact coordinates initially unless a separate compatibility plan explicitly changes them.
5. Keep capability-specific Maven BOMs, but do not invent dependencies for artifacts that are not yet published.
6. Preserve schema migrations, SQL resources, tests, fixtures and operational documentation.
7. Run destination builds in a clean environment, not only against locally installed artifacts from the old monorepo.

This inventory corrects the earlier assumption that all target repositories can be populated by copying a single similarly named directory: several groups require multiple source roots, and some roots require splitting or explicit deferral.


## 12. Shared Maven parent and capability BOM rule

The extraction foundation uses a repository-neutral shared parent:

- Parent: `tech.kayys.numou:numou-parent`, sourced from `core/framework/parent/pom.xml`.
- Platform BOM: `tech.kayys.numou:numou-bom`, sourced from `core/framework/bom/pom.xml`.
- Foundation artifacts remain owned by the current Numou source repository until a later Foundation extraction is approved.

Capability root aggregators must inherit from the published `numou-parent` with an empty `relativePath`, import the published platform BOM, and declare only their own modules. Capability BOMs must have unique artifactIds (for example `numou-identity-bom`, `numou-tenancy-bom`, `numou-security-bom`) and must be included in their root reactor. The legacy Identity and Tenancy BOM POMs currently both use `numou-bom` and are not listed as root modules; they must be corrected before publishing.

The shared parent contains build conventions and managed versions only. It does not add capability or infrastructure dependencies to consumer classpaths. The platform BOM aligns published Foundation/platform artifacts. Each group BOM aligns its own artifacts and approved public cross-capability dependencies.

The source branch includes the parent POM, framework aggregator linkage, BOM parent decoupling, and validation/publication workflows. The isolated PR validation has passed (PR #1988, Shared Maven Parent Validation run 7). These artifacts are not considered released until the manual package publication succeeds and a clean consumer build resolves the parent, BOM and Foundation artifacts.

See [Maven Parent and Cross-Repository Extraction Strategy](MAVEN-REPOSITORY-EXTRACTION-STRATEGY.md).
