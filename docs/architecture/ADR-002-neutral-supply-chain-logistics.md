# ADR-002: Neutral Supply Chain and Logistics Boundaries

- **Status:** Proposed for implementation through Milestones 1–6
- **Date:** 2026-10-21
- **Decision owners:** Numou platform maintainers

## Context

Numou must support SMEs through enterprise deployments, including standalone applications, modular monoliths, and distributed services. Inventory, procurement, warehousing, shipment execution, and fleet management are related but have different invariants and lifecycles. Coupling them into one domain or prematurely deploying each as a microservice would make reuse and transaction safety harder.

## Decision

### 1. Define bounded contexts before splitting deployment units

- **Catalog:** canonical product/item identity, classifications, identifiers, and governed units.
- **Inventory:** authoritative stock movement ledger, reservations, stock state, and availability.
- **Purchasing:** supplier sourcing/terms, requisitions, purchase orders, acknowledgements, and receipt workflow.
- **Warehouse:** receiving tasks, putaway, replenishment, picking, packing, counting, handling units, and reverse logistics tasks.
- **Transportation:** shipments/consignments, stops, carrier/service selection, milestones, ETA, proof of delivery, freight settlement and claims.
- **Fleet:** vehicles, drivers, fuel, maintenance, telematics and fleet compliance.
- **Planning:** demand/lead-time assumptions, replenishment proposals, capacity constraints, scenarios, forecasts, and optimization.
- **Shared platform:** tenant context, authorization, audit, clocks, identifiers, idempotency, event contracts, observability and configuration.

These are logical ownership boundaries. They do not require one Maven module or deployable service per context in M1.

### 2. Preserve one authoritative owner per invariant

Inventory owns the stock ledger and available quantity. Warehouse tasks may request/post stock commands but must not maintain a competing stock truth. Purchasing owns PO state and receipt workflow; the inventory posting boundary owns the stock effect. Transportation owns shipment state; fleet supplies optional execution resources. Third-party carriers must work without Numou fleet installed.

### 3. Make integration explicit

Use typed application ports for in-process composition and versioned contracts for remote integration. Commands that cause side effects must carry tenant context, actor/correlation metadata, and a stable idempotency key. Publish integration events through a transactional outbox where persistence supports it; consumers deduplicate through an inbox or equivalent durable idempotency store. Cross-context workflows use a process manager/saga with explicit retry, timeout, compensation, and operator-visible failure states—never pretend distributed transactions are local ACID transactions.

### 4. Support deployment profiles without changing domain behavior

- **Embedded/standalone:** in-process adapters and a local persistence implementation.
- **Modular monolith:** one application and database deployment, with enforced module boundaries and local transactions where valid.
- **Distributed enterprise:** remote adapters, versioned messages/APIs, independent scaling, and explicit eventual-consistency workflows.

The same domain contracts and invariants apply to all three. Microservice boundaries are selected from operational needs, ownership, data volume, and failure isolation—not merely because a domain has a folder.

### 5. Keep industry-specific workflows as extensions

Construction, manufacturing, retail, e-commerce, and software delivery can compose the shared capabilities. Domain-neutral product, inventory, purchasing, and transportation modules must not import those verticals.

## Consequences

- Existing APIs and constructors should be migrated compatibly where possible; breaking tenant/UOM changes require explicit migration steps.
- Repository ports must encode atomicity and tenant-scoping requirements, not leave them as comments.
- More advanced forecasting and AI can propose decisions but cannot become the source of truth for stock, supplier terms, shipment milestones, or posted financial facts.
- M2 prioritizes stock correctness, tenant boundaries, idempotency, and purchasing/receipt contracts before advanced optimization.
