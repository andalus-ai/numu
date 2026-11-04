# Business Operating Platform — M01-C Dependency and Coupling Analysis (Batch 1)

- **Status:** First dependency-direction pass recorded; full transitive graph and cycle analysis remain open.
- **Repository:** `andalus-ai/numu`
- **Branch:** `docs/business-platform-m01-repository-inventory`
- **Baseline tree:** `8fb64214bf3e4836210624843dd4ddd077fd7897`
- **Date:** 2026-11-04
- **Related:** M01-A Repository Capability Inventory; M01-B Domain Ownership Trace (Batch 1).

## 1. Scope and method

This pass inspected the current Maven POMs for the core framework, CRM, Commerce Subscription, Finance Subscription, Accounting and Application Platform. It distinguishes:

- **Aggregator modules:** `<modules>` lists which projects Maven builds together.
- **Dependency management:** declares versions/default metadata; it does not itself add a runtime dependency.
- **Direct dependencies:** `<dependencies>` in a module POM, which do create a dependency edge.
- **Source imports/runtime wiring:** must be checked separately; a POM edge alone does not prove a runtime call.

This is a directional sample, not yet the complete 459-POM transitive graph. No cycle-free claim is made.

## 2. Confirmed dependency edges

| Consumer module | Direct internal dependencies observed | Initial assessment |
|---|---|---|
| CRM Domain | Foundation; Ecosystem Domain; Identity Domain | Identity dependency deserves domain-boundary review |
| CRM Application | CRM Domain; Foundation Application; Ecosystem Application | Application layer is not CRM-only; clarify whether Ecosystem is a shared party/profile contract or a broad domain dependency |
| CRM Adapter | CRM Application; Ecosystem Application; Foundation Adapter | Adapter composition is plausible, but adapter should not become the source of cross-domain invariants |
| Commerce Subscription Application | Foundation Application; Subscription Domain; Subscription SPI; Offering SPI | Good port-oriented direction: subscription consumes offering contract rather than offering persistence |
| Finance Subscription Core | Quarkus REST/Jackson, Hibernate ORM Panache, Scheduler | Legacy finance subscription module is persistence/runtime-centric; not an application/domain port split in this inspected POM |
| Accounting Application | Accounting Domain and accounting sub-capabilities (payables, receivables, consolidation, treasury, Islamic, MDM, multitenancy, reporting, workflow, tax, cost, currency, compliance); Document Application; Foundation Application | Broad application composition; verify whether accounting application is an umbrella host or a reusable use-case module |
| Application Platform Application | Foundation Application; Plugin; Application Platform Domain | Expected platform integration; confirm Gamelan/workflow contract remains behind plugin/runtime adapter boundary |

### Notes on evidence

- CRM Domain's direct dependency on `numou-identity-domain` is explicitly present in `crm/numou-crm-domain/pom.xml`. This is a **confirmed build-time edge**, not proof of runtime identity calls.
- CRM's dependency on `numou-ecosystem-domain` and `numou-ecosystem-application` is also explicit. Whether Ecosystem is the intended shared party/profile capability or a broad cross-context dependency needs source-level API tracing.
- Commerce Subscription Application directly depends on `numou-offering-spi`, which is the desired direction for a consumer of offering capabilities.
- Accounting Application directly depends on `numou-document`. The exact dependency is a build-time edge; inspect whether accounting uses a narrow document contract or imports document internals.
- The Finance Subscription Core POM directly declares framework runtime dependencies and, in the inspected POM, no internal Finance Subscription domain/application/SPIs. This reinforces the M01-B concern that the finance subscription implementation is a legacy persistence-centric model. It does not prove the module is unused.

## 3. Core framework POM: important interpretation

The `core/framework/pom.xml` contains a large `dependencyManagement` catalog with versions for Foundation, Scope, Project, Construction, Budget, Social Graph, Event, Integration, Security, Plugin, Workflow, Survey and Reservation artifacts.

**This catalog is not itself a runtime dependency graph.** The inspected POM's entries are under `<dependencyManagement>`; modules must still declare the dependencies they actually consume. It is therefore incorrect to infer that every managed artifact is pulled into every application or that the framework is necessarily a monolithic runtime.

However, the parent POM's breadth increases the risk that child modules can conveniently depend on unrelated internal artifacts without an explicit boundary review. The architecture should make the intended dependency rules executable.

## 4. Preliminary coupling findings

### C-01 — CRM Domain directly depends on Identity Domain

**Severity:** Medium (confirmed edge; architectural intent unresolved).

CRM domain directly declares `numou-identity-domain`. CRM's core customer/contact/sales concepts should not require authentication account lifecycle merely to model a customer. If the imported types are only stable identity references, replace the domain dependency with a neutral identity reference/party contract in the appropriate shared kernel or Ecosystem capability. If CRM is actually responsible for identity linking, keep that integration in the application layer through a port.

**Next evidence:** inspect imports in CRM domain, identify exact identity types used, determine whether CRM invariants depend on identity state, and add architecture tests preventing accidental Identity-to-CRM reverse dependencies.

### C-02 — CRM depends on Ecosystem at both Domain and Application layers

**Severity:** Medium (confirmed edge; boundary unclear).

This may be correct if Ecosystem is the authoritative shared Party/Person/Organization capability. The risk is that Ecosystem becomes a catch-all dependency that allows CRM to reach unrelated platform functionality. Define a narrow contract boundary and document which Party/Contact/Organization invariants are owned there versus CRM-specific customer lifecycle.

**Next evidence:** inspect imported packages, exposed interfaces, database ownership and all consumers of Ecosystem.

### C-03 — Accounting Application composes many accounting sub-capabilities and Document Application

**Severity:** Medium (confirmed direct edges; may be intentional application composition).

Accounting application depends on multiple accounting submodules and `numou-document`. Confirm that this artifact is a composition root/use-case layer rather than a shared library required by all accounting consumers. Keep document metadata/version lifecycle owned by Document; accounting should depend on a narrow document reference/port, not own document storage or lifecycle.

**Next evidence:** inspect accounting imports and CDI wiring, persistence boundaries, document APIs, and tests that boot accounting without unrelated document provider implementations.

### C-04 — Finance Subscription module has a persistence/runtime-heavy boundary

**Severity:** High for consolidation risk; confirmed POM shape, runtime use unknown.

The inspected Finance Subscription Core POM directly includes REST, ORM Panache and Scheduler dependencies, with its observed `Subscription` entity combining billing account/product references, lifecycle status, price calculation and tax calculation. This is not a clean reusable application/domain contract in the inspected module. Treat it as legacy until its consumers and data are traced; do not delete or rewrite it during M01.

### C-05 — Commerce Subscription's dependency direction is a positive pattern

Commerce Subscription Application consumes Offering SPI rather than depending on Offering adapter/persistence. Preserve this pattern for cross-capability integrations: consumer owns a port, provider exposes a stable SPI/API, and adapters perform integration.

### C-06 — Multi-reactor Maven topology needs explicit build profiles

The repository has 40 top-level reactor roots and no repository-root POM. A module's successful local build does not prove the full product composition works. CI should declare a supported reactor matrix and host composition profiles, with explicit tests for modular monolith and standalone/service packaging.

## 5. Dependency rules to make executable

These are proposed architecture-test rules, subject to confirmation against `AGENTS.md`, ADR-003 and existing ArchUnit tests:

1. **Domain isolation:** domain modules may depend only on shared kernel/value objects and explicit domain contracts; they must not depend on adapters, REST resources, persistence frameworks or host/bootstrap modules.
2. **Hexagonal direction:** adapters depend inward on application/domain ports; domain/application modules do not depend on concrete adapters.
3. **Cross-domain integration:** one bounded context must not import another context's persistence entities or repositories. Use public SPI/API contracts, stable IDs and versioned events.
4. **Identity separation:** CRM and business domains reference Party/Person/Organization or opaque identity references; authentication account lifecycle remains owned by Identity.
5. **Tenant/security context:** tenant and actor context enter through trusted platform boundaries and are propagated explicitly; business modules must not implement competing tenant identity stores.
6. **Workflow integration:** Gamelan remains the workflow engine; business modules consume workflow ports/contracts and do not embed a second scheduler/DAG engine.
7. **Optional infrastructure:** storage, messaging, database, search and cloud adapters are replaceable composition choices, not mandatory domain dependencies.
8. **No cycles:** enforce acyclic module dependency graph at Maven artifact and package level.
9. **No dependency by convenience:** broad parent dependency management does not justify cross-context imports; each direct dependency must have an owner and purpose.
10. **Test scopes:** architecture-test libraries and test fixtures must remain test-scoped and must not leak into production artifacts.

## 6. Not yet established

This batch does not yet establish:

- all 459 POM direct/transitive edges or cycles;
- the complete package import graph;
- actual Quarkus CDI/runtime composition;
- whether CRM identity dependencies are used by production source;
- whether accounting document dependency is narrow or broad in practice;
- deployment packaging and independent service boundaries;
- tenant/security context correctness;
- whether current CI builds each supported reactor profile.

GitHub code search queries for these exact cross-module references returned no indexed matches; therefore this report relies on inspected POM declarations for the confirmed edges and does not interpret empty search results as proof of no source imports.

## 7. Next M01-C batch

1. Inspect CRM Domain imports and Identity/Ecosystem public APIs; determine the narrowest stable reference contract.
2. Inspect Accounting Application imports and Document Application API; define the required document port.
3. Enumerate all direct internal dependencies across the 459 POM files and construct a complete artifact graph with edge scope and module layer.
4. Run cycle detection and compare results with package-level ArchUnit rules.
5. Add or strengthen architecture tests only after the allowed dependency direction is confirmed.
6. Verify Maven reactor builds and CI coverage for representative compositions.

**Decision:** No dependency is removed in M01-C Batch 1. The first confirmed boundary candidate is CRM Domain → Identity Domain; the first positive pattern to preserve is Commerce Subscription Application → Offering SPI. Complete graph and runtime verification are still required before remediation.
