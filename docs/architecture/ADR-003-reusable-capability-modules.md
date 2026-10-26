# ADR-003: Reusable capability modules across deployment topologies

- **Status:** Accepted as the target architecture; implementation is incremental
- **Date:** 2026-10-25
- **Decision owners:** Numou platform maintainers

## Context

Numou serves applications that may be standalone products, modular monoliths, distributed services, cloud deployments, or enterprise/on-premises installations. A business capability such as Shipment must be reusable by e-commerce, fulfillment, logistics, and enterprise applications without duplicating its business rules. Requiring every capability to be a microservice would add operational cost without necessarily improving boundaries.

The repository is intentionally organized as multiple Maven reactor roots. That layout must remain buildable and understandable without assuming a single root Maven project.

## Decision

### 1. A capability is a business boundary, not a deployment unit

A module owns a coherent set of business invariants and has a documented public contract. Its implementation may be embedded in a host application or exposed through a remote adapter. Deployment is a composition decision based on operational needs, team ownership, scaling, security, and failure isolation.

### 2. Keep domain behavior deployment-neutral

Domain code must not require an HTTP server, a specific database, a broker, a cloud vendor, or a service-discovery system. Framework and infrastructure integrations belong in adapters or runtime modules. A domain module may depend on stable domain contracts from another capability, but must not reach into that capability's persistence model or private implementation.

### 3. Support multiple composition profiles

- **Standalone application:** a small runtime composes only the capabilities and adapters it needs.
- **Modular monolith:** multiple capabilities run in one process, with explicit module boundaries and local transactions where valid.
- **Distributed deployment:** selected capabilities are exposed through versioned HTTP or messaging contracts and can be deployed/scaled independently.
- **Cloud or on-premises enterprise:** infrastructure adapters, identity, configuration, secrets, audit, observability, and operational controls are selected for the target environment.

These profiles must reuse the same capability rules; they must not fork business logic.

### 4. Make contracts and side effects explicit

Cross-capability calls use public application ports in-process or versioned contracts across a network. Commands that cause side effects carry tenant, actor/correlation, and idempotency context where applicable. Events are published through durable outbox/inbox or equivalent mechanisms when reliability requirements demand it. Distributed workflows explicitly model retries, timeouts, compensation, and operator-visible failure.

### 5. Depend on capabilities intentionally

Consumers declare only the modules they use. Do not make every adapter, runtime, database driver, cloud integration, or capability a transitive dependency of the whole framework. Public APIs are versioned and compatibility is tested. Internal entities, tables, and implementation classes are not integration contracts.

### 6. Preserve logical boundaries while the Maven layout evolves

The repository has multiple Maven reactor roots and no root pom.xml. Top-level capability aggregators must resolve the shared numou-framework parent from core/framework/pom.xml, rather than relying on a nonexistent repository-root parent. Repository-level CI must be located under root .github/workflows/ and invoke real reactor entry points explicitly.

### 7. Example compositions

- E-commerce: Order + Inventory + Fulfillment + Shipment.
- Fulfillment platform: Warehouse + Inventory + Fulfillment + Shipment.
- Third-party logistics: Shipment + Transportation + carrier adapters + Tracking.
- Enterprise supply chain: Planning + Purchasing + Warehouse + Fulfillment + Transportation + Finance.

These are composition examples, not a mandate to deploy each item separately. Existing code boundaries must be inspected before introducing new models or splitting modules.

## Acceptance criteria

1. Each top-level capability reactor resolves its parent POM from a tracked path.
2. CI is discoverable by GitHub Actions from the repository root and invokes existing Maven reactor roots rather than an assumed root POM.
3. Domain rules can be tested without starting a network server or cloud environment.
4. Adapters and runtimes can be selected independently from the core business capability.
5. Representative monolith and distributed compositions reuse the same contracts and business rules.
6. A capability can be consumed without pulling unrelated capabilities or infrastructure by default.

## Implementation contract

The detailed artifact-role, dependency-direction, composition-profile, persistence-boundary, and reuse-evidence requirements are maintained in [Capability Packaging and Composition Contract](CAPABILITY-PACKAGING-AND-COMPOSITION.md). That contract is normative for new work and incremental migrations; it does not imply that existing modules already comply.

## Consequences

- The shared framework parent remains at core/framework/pom.xml until a deliberate root build-layout migration is designed.
- The first repository CI baseline will verify the framework and selected high-value capability reactors. Expanding coverage to every reactor is a tracked follow-up, not something this first workflow will claim.
- Existing boundary violations are fixed incrementally with architecture tests and compatibility-preserving migrations; this ADR does not claim that the entire repository already satisfies every criterion.
