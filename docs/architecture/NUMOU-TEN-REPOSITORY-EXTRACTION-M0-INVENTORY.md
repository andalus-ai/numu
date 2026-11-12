# Ten-Repository Extraction — M0 Source Inventory

**Status:** Initial source-tree inventory; dependency and migration verification pending  
**Source:** `andalus-ai/numu`, default branch `main`  
**Inventory date:** 2026-11-12  
**Architecture contract:** [DOMAIN-CAPABILITY-MATRIX.md](DOMAIN-CAPABILITY-MATRIX.md)  
**Target repositories:** the Core Platform plus nine capability repositories listed in the domain capability matrix

## 1. Executive result

The source repository is a multi-reactor Maven monorepo with no repository-root pom.xml. The shared, repository-neutral Maven parent is core/framework/parent/pom.xml, coordinates tech.kayys.numou:numou-parent:0.1.0-SNAPSHOT. The platform BOM is core/framework/bom/pom.xml, coordinates tech.kayys.numou:numou-bom:0.1.0-SNAPSHOT. The framework aggregator at core/framework/pom.xml has coordinates tech.kayys.numou:numou-framework:0.1.0-SNAPSHOT; the current Identity and Security root POMs still inherit this aggregator through ../core/framework/pom.xml. Standalone repositories should inherit the neutral numou-parent and import the platform BOM, not inherit the framework aggregator.

The recursive Git tree contained 15,623 entries and was not truncated. The original ten candidate roots accounted for 247 Maven POM files: 241 POMs across the nine capability extraction targets and 6 POMs in Core-owned Tenancy. The revised capability extraction scope is 241 POMs; Tenancy remains in Core.

| Destination | Source roots | POMs | Inventory status |
|---|---|---:|---|
| numou-identity | `identity/`, `organization/` | 11 | Root POMs observed; full consumer/dependency audit pending |
| numou-finance | `finance/`, `budget/` | 85 | Root POMs observed; finance dependency audit pending |
| numou-security | `security/` | 14 | Root POM observed; social-graph adapter boundary noted |
| numou-commerce | `commerce/`, `product/` | 57 | Root POMs observed; product and order dependencies pending |
| numou-customer | `crm/`, `support/` | 11 | Root POMs observed; support integration audit pending |
| numou-supply-chain | `supply/` | 19 | Root POM observed |
| numou-logistics | `logistics/` | 6 | Root POM observed; fleet module requires ownership review |
| Core Platform (`andalus-ai/numu`) | `core/framework/`, `tenancy/` | Core + 6 Tenancy POMs | Foundation and Tenancy remain one platform ownership boundary |
| numou-mobility | `mobility/`, `transport/` | 21 | Root POMs observed; transport is scheduled passenger transport, not cargo logistics |
| numou-workforce | `workforce/`, `talent/` | 17 | Root POMs observed; talent/work-marketplace boundary pending |

The `numou-tenancy` repository contains a historical/staged copy of `tenancy/` (75 source files; [PR #1](https://github.com/andalus-ai/numou-tenancy/pull/1) merged) and its documentation correction ([PR #2](https://github.com/andalus-ai/numou-tenancy/pull/2) merged). The architectural decision is that Core in `andalus-ai/numu` remains the canonical Tenancy owner alongside Foundation. Do not treat the destination copy as an independently owned capability or continue its release path; reconcile it as transitional staging without deleting history. Identity and Security destinations must be checked at each migration milestone because repositories can change independently.

## 2. Exact current Maven root POMs

### 2.1 Identity — 11 POMs

- `identity/pom.xml`
- `identity/bom/pom.xml`
- `identity/numou-identity-domain/pom.xml`
- `identity/numou-identity-application/pom.xml`
- `identity/numou-identity-adapter/pom.xml`
- `organization/pom.xml`
- `organization/bom/pom.xml`
- `organization/organization-domain/pom.xml`
- `organization/organization-spi/pom.xml`
- `organization/organization-application/pom.xml`
- `organization/organization-adapter/pom.xml`

The Organization domain includes canonical Organization, OrganizationUnit, OrganizationMembership, OrganizationPartyAssociation, invitation and lifecycle concepts. Keep Organization membership distinct from Tenant membership. Verify party/organization association history and invitation-secret encryption before moving its persistence and adapters.

### 2.2 Finance — 85 POMs

Current roots:
- `finance/pom.xml` and `finance/bom/pom.xml`
- `finance/accounting/` (accounting domain, application, adapter, governance, consolidation, payables, receivables, treasury and accounting capability submodules)
- `finance/billing/` (core and industry billing extensions)
- `finance/common/` (finance currency/reporting and treasury)
- `finance/payment/` (payment API/core/gateway/methods, REST/gRPC/Postgres adapters and processors)
- `finance/subscription/`
- `finance/syirkah/`
- `finance/tax/`
- `finance/wallet/`

Additional root:
- `budget/pom.xml`
- `budget/bom/pom.xml`
- `budget/budget-domain/pom.xml`
- `budget/budget-application/pom.xml`
- `budget/budget-spi/pom.xml`
- `budget/budget-adapter/pom.xml`

Finance has a broad dependency surface. Preserve the existing shared Money type in Foundation; do not create a Finance-specific duplicate. Verify RevenueRecognition, AP/AR, Budget, Payment, Wallet and Accounting ownership separately. Budget is included in the Finance target but must be integrated as a separately named module, not collapsed into ledger accounting.

### 2.3 Security — 14 POMs

- `security/pom.xml`, `security/bom/pom.xml`
- `security/security-domain/pom.xml`
- `security/security-spi/pom.xml`
- `security/security-application/pom.xml`
- `security/security-adapter/pom.xml`
- `security/social-graph-adapter/pom.xml`
- `security/security-vault-domain/pom.xml`
- `security/security-vault-spi/pom.xml`
- `security/security-vault-application/pom.xml`
- `security/security-vault-postgres/pom.xml`
- `security/security-vault-crypto/pom.xml`
- `security/vault-secret/pom.xml`
- `security/vault-secret-cloud/pom.xml`

The social-graph adapter is a Security integration adapter, not ownership of the social graph domain. The social graph implementation currently lives under `core/framework/social-graph/`; it remains in the shared framework/Foundation source until a dedicated extraction decision is made.

### 2.4 Commerce — 57 POMs

- `commerce/pom.xml`, `commerce/bom/pom.xml`
- `commerce/affiliate/`
- `commerce/configuration/`
- `commerce/kiosk/`
- `commerce/marketplace/`
- `commerce/offering/`
- `commerce/omnichannel/`
- `commerce/pos/`
- `commerce/pricing/`
- `commerce/promotion/`
- `commerce/subscription/`

Additional root:
- `product/pom.xml`, `product/bom/pom.xml`
- `product/product-domain/pom.xml`
- `product/product-spi/pom.xml`
- `product/product-application/pom.xml`
- `product/product-adapter/pom.xml`
- `product/product-catalog/pom.xml`
- `product/product-runtime/pom.xml`

The Product runtime POM is explicitly not in the current Product reactor and its comments identify a legacy duplicate model and a previously failing test suite. Do not re-enable or migrate that prototype as if it were production Product 1.0. Inventory its code and preserve its exclusion until a separate cleanup/migration is verified.

### 2.5 Customer — 11 POMs

- `crm/pom.xml`, `crm/bom/pom.xml`
- `crm/numou-crm-domain/pom.xml`
- `crm/numou-crm-application/pom.xml`
- `crm/numou-crm-adapter/pom.xml`
- `support/pom.xml`, `support/bom/pom.xml`
- `support/support-domain/pom.xml`
- `support/support-application/pom.xml`
- `support/support-api/pom.xml`
- `support/support-adapter/pom.xml`

Support is a reusable support/ticketing bounded context. Keep its ticket lifecycle distinct from Queue Management, reservations, CRM opportunity lifecycle and source business records. Verify all customer references become stable Identity references without duplicating person records.

### 2.6 Supply Chain — 19 POMs

- `supply/pom.xml`, `supply/bom/pom.xml`
- `supply/inventory/` (domain, application, SPI and adapter)
- `supply/purchasing/` (domain, application and SPI)
- `supply/fulfillment/` (domain and application)
- `supply/warehouse/` (domain, application, JDBC persistence and filesystem artifact adapter)

The aggregator description explicitly separates inventory stock ownership, purchasing supplier commitments, fulfillment demand execution and shipment handoff. Logistics must remain authoritative for shipment execution after handoff.

### 2.7 Logistics — 6 POMs

- `logistics/pom.xml`, `logistics/bom/pom.xml`
- `logistics/numou-fleet/pom.xml`
- `logistics/numou-courier-domain/pom.xml`
- `logistics/numou-courier-application/pom.xml`
- `logistics/numou-courier-postgres-adapter/pom.xml`

The current Logistics aggregator includes a Fleet module. The matrix assigns driver/fleet/vehicle operational lifecycle to Mobility, while Logistics owns shipment/courier/consignment/custody. Inspect the Fleet module's classes, migrations and consumers; split or relocate only after its actual invariants and persistence are mapped. Do not duplicate fleet aggregates in both repositories.

### 2.8 Tenancy — 6 POMs (Core-owned; not an extraction target)

- `tenancy/pom.xml`, `tenancy/bom/pom.xml`
- `tenancy/tenancy-domain/pom.xml`
- `tenancy/tenancy-spi/pom.xml`
- `tenancy/tenancy-application/pom.xml`
- `tenancy/tenancy-adapter/pom.xml`

Preserve tenant lifecycle, membership, tenant context and provisioning contracts in the Core `tenancy/` reactor. Organization membership is not automatically tenant membership. Do not let tenant membership alone become an implicit permission grant. The separate `numou-tenancy` repository is transitional staging only.

### 2.9 Mobility — 21 POMs

- `mobility/pom.xml`
- `mobility/mobility-kernel/pom.xml`
- `mobility/mobility-api/pom.xml`
- `mobility/mobility-persistence/pom.xml`
- `mobility/mobility-pricing/pom.xml`
- `mobility/driver/` (driver domain, SPI, application and adapter)
- `mobility/ride/` (ride domain, SPI, application and adapter)
- `mobility/integration/mobility-integration/pom.xml`
- `transport/pom.xml`
- `transport/transport-domain/pom.xml`
- `transport/transport-spi/pom.xml`
- `transport/transport-application/pom.xml`
- `transport/transport-runtime-quarkus/pom.xml`
- `transport/transport-postgres-adapter/pom.xml`
- `transport/transport-payment-adapter/pom.xml`

The Transport aggregator describes scheduled passenger transport catalog and operations. Keep it a separately named passenger-transport sub-capability; do not merge it into parcel/cargo Logistics. Audit its Finance adapter so it consumes public Finance contracts rather than embedding payment logic.

### 2.10 Workforce — 17 POMs

- `workforce/pom.xml`, `workforce/bom/pom.xml`
- `workforce/workforce-domain/pom.xml`
- `workforce/workforce-spi/pom.xml`
- `workforce/workforce-application/pom.xml`
- `workforce/workforce-adapter/pom.xml`
- `workforce/workforce-persistence-jdbc/pom.xml`
- `workforce/hris/pom.xml`
- `workforce/leave/` (domain, SPI, application and adapter)
- `workforce/leave/bom/pom.xml`
- `talent/pom.xml`
- `talent/talent-domain/pom.xml`
- `talent/talent-application/pom.xml`

Talent describes recruitment and work marketplace capabilities. Keep it separately named from core workforce/employee lifecycle. Verify its market-facing offer/search contracts do not duplicate Commerce marketplace or Customer CRM.

## 3. Shared-parent and build blockers

Identity and Security currently inherit tech.kayys.numou:numou-framework:0.1.0-SNAPSHOT through ../core/framework/pom.xml; Core Tenancy also inherits the Core framework parent through ../core/framework/pom.xml. The staged `numou-tenancy` copy has a standalone parent/BOM adaptation, but that does not change canonical ownership. The neutral parent exists at core/framework/parent/pom.xml and the platform BOM exists at core/framework/bom/pom.xml. The monorepo also has a manual publication workflow at .github/workflows/publish-shared-foundation.yml that publishes the parent, framework parent POM, platform BOM and selected shared platform artifacts to GitHub Packages.

The shared parent and BOM architecture is implemented and its validation workflow is documented as passing in PR #1988. However, validation is not publication: no successful package publication or clean remote consumer resolution is assumed until the manual workflow and destination build have both been observed to pass.

Required before first source code transfer:

1. Decide and implement a shared, published Maven parent/dependency-management strategy without creating duplicate divergent parent POMs across the nine capability repositories.
2. Publish or otherwise make available the Foundation artifacts and shared contracts required by the first extraction.
3. Replace relative parent paths and same-reactor dependencies in the extracted capability.
4. Classify every cross-group dependency as public API, implementation, adapter, test fixture or forbidden private dependency.
5. Ensure clean CI can resolve dependencies without relying on developer-local Maven installs.

Do not work around this blocker by copying `core/framework/pom.xml` into every destination repository. That would duplicate platform ownership and make release/version drift inevitable.

## 4. First-wave extraction blockers by group

| Group | Main blocker to resolve | Required pre-migration proof |
|---|---|---|
| Identity | Shared parent; Organization/Identity and Social Graph contracts | Person/Party/Organization IDs, association history, membership/invitation, tenant context and security boundaries |
| Core Tenancy | Core/Foundation reactor alignment and isolation tests | Tenant isolation, membership lifecycle, provisioning, RLS and authorization context |
| Security | Shared parent; identity principal and tenant context dependencies | Password/credential migration, auth/session compatibility, fail-closed authorization and vault crypto |
| Finance | Shared parent; extensive internal finance reactor and Foundation Money | Ledger integrity, idempotency, outbox/replay, payment callback verification, wallet concurrency, AP/AR and Syirkah settlement |
| Commerce | Shared parent; separate Product reactor and finance/inventory/customer integrations | Order/payment/stock reservation separation, POS branch scope, product catalog legacy exclusion |
| Customer | Shared parent; Identity references and Support boundary | CRM/customer ID mapping, support ticket ownership, no duplicate canonical person records |
| Supply Chain | Shared parent and separate Inventory/Warehouse/Purchasing reactors | Stock reservation/idempotency, purchasing/receiving, tenant-scoped inventory and fulfillment handoff |
| Logistics | Shared parent; Fleet currently nested in Logistics | Shipment/custody idempotency, carrier integration, Fleet split decision and proof-of-delivery |
| Mobility | Shared parent; Finance wallet dependency and passenger Transport integration | Driver location privacy, trip/ride lifecycle, fare/settlement contracts, separate passenger transport model |
| Workforce | Shared parent; Talent and Organization structure dependencies | Employee identity mapping, attendance/leave persistence, org hierarchy and payroll/Finance boundary |

## 5. What this inventory does not claim

- It does not claim the 241 capability POMs (or the 6 Core Tenancy POMs) have been independently built or released.
- It does not claim all files under the listed roots belong to the target group without review.
- It does not claim the existing artifact coordinates are suitable for a public release.
- It does not claim the destination repositories have been populated.
- It does not authorize deletion of source directories from the monorepo.
- It does not report Maven tests or CI as passing.

## 6. M0 exit criteria

- [x] Confirm source repository has no root Maven POM.
- [x] Confirm shared parent location and coordinates.
- [x] Enumerate source roots and POM counts for the ten selected repositories.
- [x] Inspect capability root POM declarations for representative ownership boundaries.
- [ ] Enumerate all intra-repository and cross-root artifact dependencies.
- [ ] Inventory migrations, event schemas, API routes and public contracts per source module.
- [ ] Define and publish the shared parent/Foundation dependency strategy.
- [ ] Run baseline builds for every affected source reactor and record pre-existing failures.
- [ ] Approve a migration sequence with a clean destination build for the first extracted group.

The next implementation milestone is to align the Core Tenancy reactor with Foundation, then extract Security and Identity in a dependency-safe order. Do not delete or disable source modules or the transitional Tenancy repository history until destination CI and source-consumer migration are verified.


## 7. Shared Maven parent and M1 reactor inspection

The first three platform roots were inspected at POM level after the initial tree inventory.

### 7.1 Current parent and aggregator layout

- `identity/pom.xml`, `tenancy/pom.xml` and `security/pom.xml` all inherit `tech.kayys.numou:numou-framework:0.1.0-SNAPSHOT` using `../core/framework/pom.xml`. This is the intended Core inheritance for Tenancy; Identity and Security will be externalized.
- `core/framework/pom.xml` is both the parent and aggregator for technical framework modules and selected business capabilities. It includes `../../identity` as a reactor module.
- Tenancy is included in the Core framework aggregator by the staged M1 reactor change; Security remains a separate Maven reactor and is an external capability extraction target.
- A direct source-directory copy would leave invalid parent relative paths and would not establish standalone dependency resolution.

### 7.2 BOM defects requiring correction during extraction

- `identity/bom/pom.xml` exists but is not declared in `identity/pom.xml`'s modules.
- `tenancy/bom/pom.xml` is legacy metadata; Tenancy remains Core-owned and must not publish a separate capability BOM. Manage its artifacts through the shared Core platform BOM.
- The legacy Identity and Security BOMs use artifactId `numou-bom`, which collides with the shared platform BOM if published with the same groupId/version.
- External capability BOMs must use unique artifactIds such as `numou-identity-bom` and `numou-security-bom`, and must be explicit modules of their root reactors. Core Tenancy is managed by the shared platform BOM, not a separate `numou-tenancy-bom`.

### 7.3 M1 dependency observations

Identity's adapter has direct dependencies on Foundation application, Security SPI/application, Observability API/adapter, Cache API/adapter and Quarkus integrations (OIDC, REST, Hibernate Reactive, PostgreSQL, Flyway, Kafka, gRPC, JWT and scheduler). Keep these dependencies in adapter/composition modules. Identity domain and application must not acquire Quarkus, database-driver or Security adapter dependencies.

Tenancy's four declared modules are domain, SPI, application and adapter. Security declares domain, SPI, application, adapter, Social Graph adapter and security-vault modules. Keep the Security Social Graph adapter as an integration adapter; Social Graph domain ownership remains deferred with Foundation/framework.

### 7.4 Shared parent implementation status

A repository-neutral Maven parent was added at `core/framework/parent/pom.xml` with coordinates `tech.kayys.numou:numou-parent:0.1.0-SNAPSHOT`. The framework aggregator now consumes it and includes it in its reactor. The shared platform BOM now inherits directly from `numou-parent`, rather than the framework aggregator.

Two workflows were added:
- `.github/workflows/shared-maven-parent-validation.yml` validates the parent, platform BOM and Foundation reactor on pull requests.
- `.github/workflows/publish-shared-foundation.yml` manually publishes the parent, platform BOM and Foundation artifacts to GitHub Packages.

This is implementation in the current PR branch. The isolated Shared Maven Parent Validation workflow passed on PR #1988 (run 7), covering the standalone parent, non-recursive framework parent model, platform BOM and Foundation reactor. Artifacts have not been published, and no clean consumer repository has resolved them yet.

See [Maven Parent and Cross-Repository Extraction Strategy](MAVEN-REPOSITORY-EXTRACTION-STRATEGY.md) for the exact publication and extraction contract.


## 8. M1 dependency graph and safe extraction order

The Identity, Organization, Tenancy and Security POM dependencies were inspected individually. This changes the safe order from a name-based assumption to a dependency-based sequence.

### 8.1 Capability-to-platform dependencies

| Source capability | Required published shared artifacts | Cross-capability dependencies |
|---|---|---|
| Core Tenancy | Foundation, Foundation Application | Same Core reactor; no cross-repository dependency is required |
| Security | Foundation, Foundation Application, Foundation Adapter, Cache API/Adapter, Observability API/Application | Social Graph Application and JDBC persistence for `security/social-graph-adapter` |
| Identity | Foundation, Foundation Application, Cache API/Adapter, Observability API/Adapter | Security SPI/Application |
| Organization (included in Identity repository) | Foundation, Foundation Application, Ecosystem Domain/Application | Identity Domain and Security Vault SPI |

The Social Graph modules themselves depend on Foundation and Ecosystem. Therefore the shared platform publication order must be Foundation, Ecosystem, Cache, Observability, then Social Graph. Cache and Observability can be built after Foundation; Social Graph requires Ecosystem.

### 8.2 Safe M1 order

1. Publish shared parent, framework parent POM, platform BOM, Foundation, Ecosystem, Cache, Observability and Social Graph artifacts.
2. Keep Tenancy in Core; align its root reactor/BOM and tenant-context contracts with Foundation. No independent Tenancy migration is planned.
3. Migrate Security after shared platform dependencies; keep the Social Graph adapter in Security as an integration adapter.
4. Migrate Identity and Organization together after Security because Identity adapter consumes Security SPI/Application and Organization adapter consumes Security Vault SPI. Organization domain/application also depend on Ecosystem and Identity Domain.
5. Update monorepo consumers and remove source-reactor duplicates only after clean destination builds and compatibility checks pass.

Social Graph remains in `core/framework/social-graph` for this extraction wave. It is a shared platform capability, not part of the Identity repository source transfer. Identity owns canonical subject/person/organization references; graph-engine ownership remains a shared platform concern until a later repository wave is approved.

### 8.3 BOMs must be rebuilt, not copied

The existing Identity and Organization BOM POMs use artifactId `numou-bom` and are not included in the Identity root reactor module list. The Core Tenancy BOM also uses the legacy shared artifactId; it must be corrected or deliberately integrated into the Core platform BOM/reactor rather than published as a separate Tenancy capability BOM. The Security BOM also contains a broad list of artifacts from unrelated groups (including Identity, Workforce, Supply Chain, Commerce and Restaurant modules). These are not reliable capability BOMs.

For destination repositories:
- create a unique BOM artifactId per repository;
- include it as a module in the root reactor;
- manage only artifacts owned by that repository plus explicitly approved public contracts;
- do not include every module from the monorepo platform BOM in each capability BOM;
- validate BOM import with a clean sample consumer project.

### 8.4 Expanded shared platform workflow

The validation and manual publication workflows now include Foundation, Ecosystem, Cache, Observability and Social Graph reactors in addition to the shared parent and platform BOM. The expanded sequential install validation passed on PR #1988 (Shared Maven Parent Validation, run 18). Its first attempt exposed a Social Graph test fixture compile error because a two-method query port was supplied as a lambda; the fixture now implements both methods explicitly and the rerun passed.


## 9. First destination source transfer staged

The first source transfer is staged in the historical Tenancy destination, but Core is now the canonical owner:

| Destination | Source transfer | Current state |
|---|---|---|
| Core (`andalus-ai/numu`) | `tenancy/` — 6 POMs in the source reactor | Canonical source remains in Core; no transfer or source deletion required |
| [numou-identity](https://github.com/andalus-ai/numou-identity) | `identity/` + `organization/` | Repository initialized with migration README only |
| [numou-security](https://github.com/andalus-ai/numou-security) | `security/` — 172 source files; 12 capability modules | Staged in [PR #1](https://github.com/andalus-ai/numou-security/pull/1); independent build/publication not yet verified |
| Other seven destinations | As defined in the matrix | Not started |

The historical destination transfer preserves 75 source files, but it is not the canonical ownership boundary. Core's `tenancy/` source reactor remains authoritative. The staged destination should be treated as transitional until a separately reviewed reconciliation decision is made.

No independent build or package publication is claimed for the historical Tenancy destination. Since Tenancy remains in Core, the next build proof is a Core reactor build covering Foundation and `tenancy/`; no source deletion is authorized.


## 10. M1/M2 execution status — Core Tenancy, Security and Identity

M1 begins with the shared Maven contract already present in the source monorepo. Tenancy remains in Core and must not be re-extracted or overwritten from the staged destination copy.

| Repository | Current observed state | M1 action |
|---|---|---|
| Core (`andalus-ai/numu`) | Canonical `tenancy/` reactor remains in source; staged `numou-tenancy` copy is transitional; PR #1991 adds Tenancy to Core aggregator/BOM | Verify dedicated Foundation + Tenancy reactor CI; no clean Tenancy build is claimed yet |
| [numou-identity](https://github.com/andalus-ai/numou-identity) | README only; source remains in identity/ and organization/ | Transfer Identity + Organization as one bounded-context portfolio; use numou-parent, import numou-bom, create unique numou-identity-bom |
| [numou-security](https://github.com/andalus-ai/numou-security) | 172 source files staged in PR #1; 12 Maven modules; standalone parent/BOM structure added | Verify clean reactor using published neutral parent/platform BOM and Core artifacts; add CI/security tests; preserve Social Graph adapter as integration-only |

### 10.1 M1/M2 dependency and sequencing gates

1. Verify the shared publication workflow against the source main revision and record package versions published for external capability consumers; Core must not resolve its own Foundation/Tenancy artifacts remotely.
2. Verify the dedicated Core reactor job builds Foundation and Tenancy together without remote self-dependency or reliance on developer-local Maven cache.
3. Do not continue the separate Tenancy release path. Preserve PR history; document `numou-tenancy` as transitional and defer archive/redirect decisions until a reviewed reconciliation plan exists.
4. Extract Security and Vault as a coherent capability. Verify its 12 modules, neutral parent, shared BOM, vault persistence migrations, cryptographic adapter and authorization boundary before package publication.
5. Extract Identity and Organization together after Security's public SPI/application contracts are verified, because Identity's adapters consume Security contracts. Organization currently depends on Identity and Ecosystem contracts; do not split the Organization root from Identity without preserving its dependency graph.
6. Do not remove any source reactor modules or consumer dependencies until each destination build and source-consumer migration is verified.
7. Record source commit, exact file count, POM inventory, artifact coordinates, test results, package publication and rollback path in each destination README and migration report.

### 10.2 M1 acceptance criteria

- [ ] Core Foundation + Tenancy reactor clean build passes; transitional destination status is documented without deleting history.
- [ ] Identity and Organization source files transferred with provenance and no omitted tests/resources.
- [ ] Identity root POM uses numou-parent, imports platform BOM, and includes the unique Identity BOM in its reactor.
- [ ] Security and Vault source files transferred with provenance and no omitted tests/resources; staged transfer exists in numou-security PR #1 and must pass clean independent build/security verification.
- [ ] Security root POM uses numou-parent, imports platform BOM, and includes the unique Security BOM in its reactor; the staged PR has this structure but publication/build remain unverified.
- [ ] Identity and Security destination repositories build independently from a clean environment; the Core Foundation + Tenancy reactor builds in the source repository.
- [ ] Tenant membership, subject identity and authorization boundaries are covered by contract/integration tests.
- [ ] Source monorepo consumers are inventoried and migrated before source modules are retired.
- [ ] No source capability is deleted as part of a copy-only PR.
