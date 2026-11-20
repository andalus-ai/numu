# Numou Accounting Standalone Boundary — M5

**Status:** Optional integration dependency extraction implemented; standalone production distribution not yet complete  
**Branch:** `runtime/m5-accounting-standalone-boundary`

## Observed coupling

The existing `numou-accounting-adapter` directly depended on Project, Budget, and Inventory application/adapter artifacts. Its `AccountingServiceProducer` also created all four cross-capability services using in-memory repositories. This made those unrelated capabilities mandatory transitive dependencies of every consumer of the Accounting transport adapter.

The Accounting application itself has a broader set of Accounting-owned sub-capabilities (AP, AR, consolidation, treasury, tax, workflow, etc.). Those are not removed by this milestone.

## M5 change

- Removed Project, Budget, and Inventory dependencies from `numou-accounting-adapter`.
- Removed their in-memory bean producers from `AccountingServiceProducer`.
- Added `numou-accounting-local-integrations`, an opt-in module that owns those four CDI producers.
- The optional module declares the dependencies it needs explicitly and is not a dependency of `numou-accounting-adapter`.
- The optional module is included in the accounting reactor so it can be selected by applications that need the local integrations.
- Added producer tests proving each optional service can be constructed.

## Selecting the optional integrations

A host that needs these local in-process integrations adds `numou-accounting-local-integrations` explicitly. A host that needs Accounting without Project/Budget/Inventory does not add it.

The new module uses in-memory Project, Budget, and Inventory adapters. It is for local development, demos, and isolated tests; it must not be selected as a production persistence strategy.

## Remaining blockers before a production Accounting standalone distribution

1. The Accounting adapter currently supplies in-memory account, journal, fiscal-period, fixed-asset, and outbox repositories. Durable, tenant-scoped PostgreSQL implementations and transaction semantics must be verified/implemented before claiming production standalone readiness.
2. The Accounting adapter POM still includes PostgreSQL/Flyway dependencies at transport level. Persistence adapters should be split from HTTP transport so the product can select its database implementation intentionally.
3. Runtime bootstrap must be wired by a product-owned Quarkus composition root, with explicit provider allowlisting and lifecycle management.
4. The selected API resources need an application profile and security/tenant authorization verification before being exposed as a standalone product.

## Verification

Focused producer tests were added but have not been run in this environment. Suggested command:

```bash
cd finance
mvn -pl accounting/numou-accounting-local-integrations -am test
```

Also verify the dependency graph:

```bash
cd finance/accounting/numou-accounting-adapter
mvn dependency:tree -Dincludes=tech.kayys.numou:numou-project-*,tech.kayys.numou:numou-budget-*,tech.kayys.numou:numou-inventory-*
```

Expected result: no Project, Budget, or Inventory artifacts in the adapter's dependency tree. The optional local-integration module should contain those dependencies. No test or build success is claimed by this document.
