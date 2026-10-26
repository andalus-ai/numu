# Numou

Numou is a modular business platform organized around reusable business capabilities. Capabilities own business invariants and explicit contracts; applications compose them for their deployment and industry needs.

## Architecture principles

- **Capability-first boundaries:** each capability owns a coherent domain and publishes intentional contracts.
- **Neutral core, domain extensions:** keep reusable concepts industry-agnostic; put specialized semantics in extensions and adapters.
- **Explicit dependencies:** consume public contracts rather than another capability's persistence model or private implementation.
- **Deployment-neutral business rules:** the same rules should work in a standalone application, modular monolith, or distributed deployment.
- **Verifiable boundaries:** use tests, architecture checks, and CI to detect accidental coupling.

These principles describe the intended architecture. Existing modules may still be migrating toward it.

## Start here

- [Contributor and AI agent instructions](AGENTS.md)
- [ADR-003: Reusable capability modules across deployment topologies](docs/architecture/ADR-003-reusable-capability-modules.md)
- [Domain capability ownership matrix](docs/architecture/DOMAIN-CAPABILITY-MATRIX.md)
- [Architecture decisions and plans](docs/architecture/)

This repository contains multiple Maven reactor roots; do not assume a single root-level `pom.xml`. Use the relevant module's documented reactor and the repository CI workflow when building or testing changes.

## Contributing

Before changing a capability, inspect its existing contract, implementation, tests, and related architecture documents. Follow [AGENTS.md](AGENTS.md) for dependency boundaries, test expectations, and AI-agent safeguards. For architectural changes, explain the ownership decision and update the relevant architecture documentation.
