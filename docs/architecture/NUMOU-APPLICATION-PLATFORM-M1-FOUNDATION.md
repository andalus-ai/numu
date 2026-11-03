# Numou Application Platform — M1 Foundation and Capability Architecture Audit

- **Status:** M1 architecture baseline; implementation is incremental and not implied by this document.
- **Repository:** `andalus-ai/numu`
- **Baseline:** `main`, inspected 2026-11-03
- **Related:** `AGENTS.md`, [Ecosystem Vision and Engineering Guardrails](../ECOSYSTEM_VISION_AND_ENGINEERING_GUARDRAILS.md), [ADR-003](ADR-003-reusable-capability-modules.md), [Capability Packaging and Composition Contract](CAPABILITY-PACKAGING-AND-COMPOSITION.md), [Domain Capability Matrix](DOMAIN-CAPABILITY-MATRIX.md), [Workspace UI Integration Contract](../../workspace/WORKSPACE-UI-INTEGRATION.md)

## 1. Product decision

Numou is a reusable business-capability platform. The Salesforce-/Shopify-like product direction is to let a host application compose, configure, secure, operate and extend those capabilities as complete business applications.

**The missing abstraction to design is an application composition and lifecycle layer—not a second domain layer and not a universal business-object engine.**

This document defines the boundary and implementation sequence. It does not claim that an application catalog, installer, tenant application lifecycle, metadata runtime, extension marketplace or application builder already exists.

## 2. Repository evidence and limits

The following facts were verified against the default branch:

- The repository is public and its default branch is `main`.
- There is no repository-root `pom.xml`. Multiple Maven reactor roots exist and top-level aggregators resolve `numou-framework` through `core/framework/pom.xml`.
- The root contains separate capability/product areas including `core`, `tenancy`, `identity`, `security`, `workspace`, `crm`, `commerce`, `product`, `finance`, `document`, `documents`, `notification`, `support`, `supply`, `logistics`, `mobility`, `hospitality`, `construction`, `workforce` and others.
- `workspace/pom.xml` composes domain, application, JDBC persistence, REST and notification adapter artifacts. The REST adapter documents that it is an optional transport and requires a host runtime to supply authenticated identity, authorization, persistence and service composition.
- `tenancy/pom.xml`, `identity/pom.xml`, `security/pom.xml`, `crm/pom.xml` and `commerce/pom.xml` are distinct aggregators with their own artifact boundaries.
- `commerce/pom.xml` already groups offering, configuration, pricing, promotion, affiliate, subscription, POS, kiosk, omnichannel and marketplace capabilities.
- `AGENTS.md`, ADR-003 and the packaging contract explicitly distinguish a business capability, Maven artifact, application, runtime/deployment unit and deployment topology.

**Audit limitation:** this is a targeted architecture and composition audit, not a complete 369-POM dependency graph, source call graph, persistence-schema inventory, deployment inventory or API compatibility audit. No module is marked complete or safe to retire solely from its directory or POM name. The next source-tracing pass must validate real consumers and runtime composition before introducing code dependencies or migrations.

## 3. Target architecture and ownership

| Layer | Owns | Must not become |
|---|---|---|
| Experience / channels | Admin UI, CRM UI, POS, storefront, mobile, partner/developer UI | A source of tenant identity, authorization or business truth |
| Product application | A named user-facing business solution and its workflow composition | A fork of reusable capability invariants |
| Application platform | Application descriptors, catalog, tenant installation state, configuration, dependency resolution, navigation contribution and lifecycle orchestration | A replacement for tenancy, identity, security or capability domains |
| Business capabilities | Their domain invariants, application use cases, persistence ownership and public contracts | Generic UI metadata or a hidden shared database |
| Shared platform capabilities | Identity, tenancy, authorization, audit, secrets, events, documents, notification and other proven reusable primitives | A universal aggregate or cross-domain mutable model |
| Runtime / composition | Host wiring, transport, persistence adapters, process lifecycle, deployment profile and observability | Business rules duplicated from capabilities |
| Ecosystem / distribution | Signed application packages, publisher identity, compatibility, installation source and marketplace distribution | An unrestricted code-execution mechanism |

### 3.1 Terms

- **Capability:** a business boundary that owns a coherent invariant and lifecycle.
- **Application:** a versioned composition of capabilities, adapters, user journeys and optional extensions for a defined business outcome.
- **Application package:** a distributable, declarative description of an application and its compatibility requirements. It is not automatically an executable plugin.
- **Tenant installation:** the tenant-scoped activation and configuration of an application package, with explicit entitlement and lifecycle state.
- **Runtime profile:** the host composition that provides the selected capabilities and adapters in-process or remotely.
- **Extension:** an independently versioned contribution that uses documented extension points and permissions.
- **Marketplace:** a distribution, discovery, trust and commercial channel for packages; it is not the application runtime.

### 3.2 Ownership map (initial, evidence-based)

| Existing area | Target platform role | M1 disposition |
|---|---|---|
| `tenancy` | Tenant lifecycle and membership authority | Reuse; do not create a second tenant registry |
| `identity` | Principal / identity authority | Reuse; authenticate through the host |
| `security` | Authorization, policy and audit authority | Reuse; application activation never grants resource access by itself |
| `security/security-vault-*` | Secret lifecycle and protected secret references | Reuse for credentials; never store secret values in application manifests or tenant config |
| `workspace` | Optional collaboration, knowledge and structured-workspace capability | Reuse; it is not the application catalog or host runtime |
| `crm`, customer and party-related capabilities | Customer relationship and sales workflows | Compose; preserve customer/party ownership and avoid duplicate identities |
| `commerce` and `product` | Catalog/offering/pricing/channel/checkout/POS commerce capabilities | Compose; no new parallel catalog, order, payment or inventory authority |
| `finance` | Accounting, billing, payment and wallet financial responsibilities | Compose through contracts; preserve distinct financial lifecycles |
| `notification`, `document` / `documents` | Communication, document lifecycle and binary storage | Reuse existing ports/adapters; resolve known duplication through their own migration plans |
| `support`, `supply`, `logistics`, `mobility`, `hospitality`, `construction`, `workforce`, etc. | Industry-neutral or industry-specific operational applications/capabilities | Compose through published contracts; do not pull all modules into every product |

This table is a target composition map, not a claim that all listed modules have complete contracts, production runtimes or proven compatibility. The module owner remains authoritative for its own state.

## 4. Application platform responsibilities

The application platform should be introduced in independently testable slices.

1. **Application descriptor and catalog:** identify an application, publisher, package version, display metadata, required capabilities/contracts, optional integrations, configuration schema, declared permissions and UI/navigation contributions.
2. **Compatibility and dependency resolution:** validate required capability IDs and contract ranges against the host's installed runtime profile. Optional dependencies must remain optional. Detect cycles and incompatible versions before installation.
3. **Tenant installation lifecycle:** record which application version is installed for which tenant, who initiated the operation, lifecycle state, timestamps, configuration revision and failure/recovery metadata.
4. **Configuration:** validate tenant-scoped, versioned configuration against a package schema. Store sensitive values only in the Vault and retain secret references, not secret material.
5. **Entitlement and authorization integration:** evaluate commercial/host entitlements separately from actor permissions. Application enabled status, subscription entitlement, role membership and resource authorization are distinct checks.
6. **Navigation and experience contributions:** allow declarative, permission-aware navigation and UI metadata. A visible menu item is not authorization; every capability endpoint re-authorizes the request.
7. **Lifecycle orchestration:** install, configure, enable, suspend, upgrade, rollback and uninstall with idempotency, audit, bounded retries and operator-visible failure.
8. **Distribution and extensions (later):** package signing, publisher trust, compatibility policy, sandboxing, extension permissions and marketplace operations.

Do not implement all eight responsibilities as one aggregate or one service. Each has distinct ownership and failure semantics.

## 5. Declarative application package contract (target, not implemented)

A first package format should be declarative and versioned. The following is illustrative; it is not an existing Numou schema and must not be treated as a production manifest until M2 contract tests define it.

```yaml
apiVersion: platform.numou/v1alpha1
kind: Application

metadata:
  id: com.example.retail
  version: 1.0.0
  publisher: com.example
  displayName: Retail Operations

requires:
  capabilities:
    - id: numou.party
      contract: ^1
    - id: numou.commerce.catalog
      contract: ^1
    - id: numou.commerce.pos
      contract: ^1
  optionalCapabilities:
    - id: numou.crm
      contract: ^1

permissions:
  - commerce.catalog.read
  - commerce.pos.operate

configuration:
  schema: retail-config/v1

contributions:
  navigation:
    - id: retail.sales
      label: Sales
      route: /retail/sales
      requiredPermission: commerce.pos.operate
```

Contract rules:

- IDs are stable, namespaced and immutable; display names are localizable metadata.
- Package versions and capability contract versions are distinct.
- Dependencies declare required versus optional semantics and supported version ranges.
- A package may declare permissions it needs; it cannot grant those permissions to actors.
- Navigation and UI metadata are untrusted presentation input, not executable authorization policy.
- Configuration schemas are versioned and migration-aware. Tenant values are not silently discarded on upgrade.
- Secrets are external references resolved by an authorized host runtime. Plaintext secrets are prohibited in manifests, logs and ordinary configuration exports.
- Package installation must validate publisher/signature, package integrity, compatibility and policy before activation.
- No arbitrary package code runs in the host merely because a manifest was accepted.

## 6. Tenant installation lifecycle and invariants

The first lifecycle should be modeled as a tenant-scoped orchestration with durable state, not as a boolean `installed` flag.

Proposed state vocabulary:

`REQUESTED → VALIDATING → PROVISIONING → INSTALLED → ENABLED`

Operational branches include `SUSPENDED`, `UPGRADE_PENDING`, `FAILED`, `UNINSTALLING` and `UNINSTALLED`. Exact transitions and terminal/retry semantics belong in the M2 domain contract.

Required invariants:

- Every installation and configuration record is scoped to one tenant and one stable application ID.
- A tenant cannot install a package unless the host runtime satisfies its required capability contracts and the tenant is entitled to use it.
- A tenant administrator may request an install, but the application service verifies actor authorization at the authoritative boundary.
- Repeating the same install/upgrade command with the same idempotency scope does not duplicate provisioning, grants, subscriptions or events.
- Tenant identity is derived from trusted authenticated context, not accepted from an untrusted browser payload.
- Application enablement does not create role grants or bypass resource-level policy.
- A failed provisioning step is visible and recoverable; partial external side effects are retried or compensated explicitly.
- Upgrade preserves tenant configuration and data by default. Destructive migration requires a separately authorized, versioned migration with backup/recovery policy.
- Uninstall removes application activation and owned integration registrations according to policy; it must not cascade-delete business records owned by capabilities.
- Every state transition records tenant, actor/service identity, correlation/idempotency context, package version and audit provenance.
- Tenant isolation, cross-tenant negative tests and permission checks are required before a lifecycle endpoint is exposed.

## 7. Application composition profiles

| Profile | Application composition | Required evidence |
|---|---|---|
| Embedded | Consumer links selected capability contracts and adapters | No unrelated runtime dependency leakage |
| Standalone | One host runtime starts the app and selected capabilities | Reproducible configuration, persistence and health checks |
| Modular monolith | Several capabilities run in one process | Enforced boundaries and owner-controlled transactions |
| Distributed | Some capabilities are remote behind versioned APIs/events | Semantic parity, tenant/actor propagation, idempotency, timeout/retry and observability |
| Managed cloud / private cloud / on-premises | Same app descriptor on a different host profile | Replaceable infrastructure adapters, secrets, audit, backup/restore and upgrade policy |

A package declares capability requirements; the host decides how those capabilities are supplied. The package must not assume that every capability is a microservice or require one deployment topology.

## 8. Explicit non-goals and safeguards

- Do not create a universal Salesforce-style custom-object store before identifying real shared metadata semantics and transactional boundaries.
- Do not replace typed domain models for money, inventory, orders, contracts, payments, accounting or regulated workflows with generic dynamic records.
- Do not create a second tenant, organization, user, party, customer, permission, vault, document, notification, product, order or payment model.
- Do not make workspace mandatory for every business application.
- Do not make every capability a microservice, Maven artifact, database or team boundary.
- Do not let application manifests define arbitrary SQL, database access, secret values or unrestricted executable code.
- Do not let the UI or marketplace decide authorization, tenant scope or billing entitlement.
- Do not introduce a module registry that duplicates Maven/runtime dependency management without a concrete host-runtime need.
- Do not couple the application platform to CRM, POS, Commerce, Workspace or one industry. They are consumers/compositions, not the platform's domain owner.

## 9. M1 audit disposition and open questions

| Question | M1 finding | Next evidence / decision |
|---|---|---|
| Is there a reusable capability architecture? | Yes, documented in ADR-003 and the packaging contract; conformance remains incremental | Continue auditing actual contracts and dependency graphs |
| Is there a workspace capability? | Yes, with domain/application/JDBC/REST/notification artifacts | Keep optional; do not turn it into application runtime |
| Is there a complete application catalog/installer? | Not identified in the inspected top-level aggregators and targeted searches | Search all runtime/composition roots and existing configuration services before creating a module |
| Is there a trusted tenant and identity foundation? | Separate tenancy and identity aggregators exist | Trace host integration and security context propagation before lifecycle APIs |
| Is there a security authority? | Separate security and Vault aggregators exist | Reuse policy/authorization and Vault contracts; test installation-specific permissions |
| Are product capabilities already grouped? | Commerce groups several related capabilities; CRM is separate | Build application compositions on top of these contracts |
| Is the repo ready for one root application build? | No root POM; multiple reactor roots are intentional today | Add a host composition only with explicit reactor/build evidence; do not add a fake root aggregator |

### Risks to resolve before implementation

1. Actual host runtime(s) and their CDI/DI composition roots are not fully inventoried.
2. The current contract and persistence owner for commercial entitlements/subscriptions must be traced before application licensing is designed.
3. Tenant membership and administrator authority must be mapped to existing tenancy and security APIs.
4. Capability IDs and contract versions are not yet established as a repository-wide machine-readable registry.
5. UI navigation integration differs between workspace, CRM, POS and future storefront clients; one presentation contract must not leak into domain modules.
6. Upgrade/uninstall data-retention policy differs for application-owned configuration versus capability-owned business data.

## 10. M1 acceptance criteria

- [x] Read and follow `AGENTS.md` and the accepted capability packaging contract.
- [x] Verify the default branch, root layout and relevant capability aggregators.
- [x] Record the application/capability/runtime distinction and ownership map.
- [x] Define initial application package, tenant installation and security invariants as target contracts.
- [x] Record evidence limitations and avoid claiming unverified implementation.
- [ ] Trace actual host composition roots and existing entitlement/subscription APIs.
- [ ] Validate tenant/actor authorization integration against existing security and tenancy use cases.
- [ ] Agree on the M2 descriptor and installation domain contract with tests before creating a new runtime module.

M1 establishes the architecture baseline. It does not deliver an application registry, installer, manifest parser, marketplace, low-code builder or production runtime.

## 11. Next milestone

**M2 — Application Descriptor and Capability Compatibility Contract**

M2 should:

1. Trace the existing runtime composition roots and capability dependency/version conventions.
2. Inspect tenancy, identity, security, Vault and subscription/entitlement public contracts and their persistence owners.
3. Define stable application ID, publisher identity, package version, capability ID and contract-range semantics.
4. Define a strict manifest schema with validation, unknown-field policy, signature metadata and deterministic dependency resolution.
5. Add schema/contract tests for valid, invalid, incompatible, cyclic, missing-required and optional dependencies.
6. Keep package parsing separate from tenant installation persistence and from code execution.
7. Produce a decision on the owning module and reactor only after the source trace demonstrates that no existing module owns the responsibility.

No new Maven module or runtime should be created until that source trace proves the correct boundary.
