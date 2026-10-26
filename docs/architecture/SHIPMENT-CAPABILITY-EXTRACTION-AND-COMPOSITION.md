# M39 — Shipment Capability Extraction and Consumer Composition

- **Status:** Extraction contract and implementation gates
- **Repository:** `andalus-ai/numu`
- **Depends on:** M38 Shipment Capability Reuse Audit; Capability Packaging and Composition Contract
- **Scope:** Define the safe first extraction slice and executable acceptance evidence before moving existing shipment classes or changing persistence.

## 1. Outcome

Make shipment lifecycle consumable by e-commerce, fulfillment, 3PL, and enterprise compositions without making shipment-only consumers adopt courier pricing, COD, hub custody, dispatch waves, fleet, Quarkus, or PostgreSQL by default.

This milestone establishes the extraction contract and evidence gates. It does **not** claim that a standalone shipment artifact or runtime has already been implemented. The existing shipment classes and storage schema remain authoritative until a follow-up code change proves compatibility.

## 2. Current baseline confirmed

The `logistics/pom.xml` reactor is `numou-fms` and currently aggregates `numou-fleet`, `numou-courier-domain`, `numou-courier-application`, and `numou-courier-postgres-adapter`.

The current `numou-courier-domain` POM depends on `numou-foundation` and `numou-organization-domain`. The domain package contains shipment and tracking classes alongside pickup/delivery, hub/dispatch, courier commercial, COD, partner, and route-planning types. The application POM depends on the full courier domain and organization domain. The Postgres adapter is an infrastructure-heavy adapter and must remain optional for library consumers.

The source tree alone does not prove all transitive consumers, transaction boundaries, migration ownership, or runtime wiring. Those are mandatory preconditions to moving classes.

## 3. Ownership contract

| Concern | Owner after extraction | Consumer rule |
| --- | --- | --- |
| Shipment identity, accepted lifecycle transitions, shipment-facing invariants | Shipment capability | One authoritative lifecycle implementation |
| Shipment-level tracking facts and stable tracking query contract | Shipment capability, subject to event-source tracing | Do not create a second mutable tracking source |
| Parcel/package facts | Existing owner until lifecycle and persistence references are traced | No duplicate Parcel/Package aggregate in Fulfillment or Warehouse |
| Demand allocation, picking, packing, fulfillment completion | Fulfillment | Holds a shipment reference; does not mutate shipment internals |
| Inventory, stock, warehouse locations and movements | Inventory/Warehouse | Does not own courier shipment status |
| Hub intake, sorting, custody, dispatch waves, delivery task operations | Courier operations extension | Calls shipment public operations and records its own custody state |
| Tariffs, COD obligations and courier partner agreements | Courier commercial policies | Financial posting remains with shared Finance contracts |
| PostgreSQL schema, repositories and migration execution | Selected persistence adapter / owning capability | Host selects the adapter; no shared-table coupling |
| HTTP, messaging, carrier APIs and webhooks | Optional transport/integration adapters | Translate protocols into public application contracts |

This ownership table is a target. Existing classes must not be relocated solely because their names match a row.

## 4. Extraction sequence

### Gate A — Consumer and invariant trace

Before moving any type, produce a class-level dependency map for:

- `Shipment`, `ShipmentId`, `ShipmentAddress`, `ShipmentStatus`, `ShipmentRepository`
- `Parcel`, `ParcelId`
- `TrackingEvent`, `TrackingEventId`, `TrackingEventType`, `TrackingCursor`, `TrackingEventRepository`
- booking/quoting commands and handlers
- JDBC repositories, schema migrations, event publication/outbox, projections and scheduled workers

For each item, record imports/references, persistence tables and migrations, transaction assumptions, event emission, tenant/actor context, authorization boundary, and public consumers. Do not assume that shipment and parcel can be separated until aggregate and storage invariants are known.

### Gate B — Introduce a narrow public contract

Create a public shipment contract only after Gate A identifies the minimum stable surface. The contract must:

- expose stable IDs and consumer-safe command/query/event types;
- carry tenant context and actor/correlation metadata where the operation requires it;
- define idempotency and conflict/not-found/validation semantics for commands;
- avoid exposing persistence entities, SQL rows, internal repository implementations, Quarkus types, or carrier-specific DTOs;
- preserve domain-level validation in the existing authoritative implementation;
- be versioned deliberately, with compatibility rules documented.

Do not publish a duplicate domain model just to obtain a new Maven artifact. If moving existing types is necessary, preserve package/API compatibility through a documented transition or a deliberate breaking-version boundary.

### Gate C — Separate optional adapters

The default shipment contract/domain dependency graph must not pull in PostgreSQL, Flyway, Testcontainers, Quarkus Arc/scheduler, HTTP servers, message brokers, cloud SDKs, COD, fleet, or hub dispatch unless the consumer explicitly selects those features.

Persistence migrations must have one owner and one execution path. Adapter selection must not silently run migrations for unrelated courier capabilities. An embedded host and a separately deployed shipment service must call the same application/domain rules; remote adapters are protocol translations, not alternative business logic.

### Gate D — Prove two compositions

Add consumer composition tests (or small test fixtures using existing build conventions) for at least:

1. **E-commerce:** order/fulfillment code references shipment through the public contract; courier-only commercial and hub/dispatch classes are not required by the consumer.
2. **Fulfillment / 3PL:** fulfillment and shipment compose without importing shipment persistence internals or creating a second shipment lifecycle model.

The tests must assert the dependency boundary, not only that Maven resolves. Prefer existing architecture-test conventions over a new bespoke verifier if one already exists.

### Gate E — Compatibility and operational proof

Before extraction can be called complete, demonstrate:

- existing Courier booking, tracking, pickup, proof-of-delivery and custody flows retain their documented behavior;
- tenant scoping and authorization are enforced at application boundaries, not inferred from possession of an ID;
- idempotent retries do not duplicate state transitions, tracking facts, or published business events;
- outbox/retry/terminal-failure behavior remains observable;
- migration from the current schema works for an existing database and a clean database;
- contract and adapter tests pass, followed by the relevant `logistics` Maven reactor and repository CI.

If any evidence is unavailable, mark it explicitly blocked rather than treating a compiling artifact as production-ready.

## 5. Composition targets

| Consumer | Required shipment pieces | Optional pieces |
| --- | --- | --- |
| Embedded product/library | Shipment contract + application/domain implementation | Chosen repository adapter |
| Modular monolith | Same contract and implementation in-process | Other selected capabilities and adapters |
| Distributed shipment service | Same application/domain rules behind versioned transport contract | Runtime, transport, persistence, telemetry |
| E-commerce | Shipment contract and its selected implementation | Courier quote/label integration when needed |
| Fulfillment / 3PL | Shipment contract and fulfillment-owned workflows | Hub custody, dispatch, carrier adapters as operationally needed |
| Enterprise supply chain | Shipment contract alongside procurement/warehouse/fulfillment/finance contracts | Deployment-specific integration adapters |

A separate service is not a prerequisite for reuse. Do not split runtime deployment unless there is an operational, security, scaling, ownership, or fault-isolation reason.

## 6. Required deliverables for the extraction code PR

The implementation PR that follows this contract must include:

1. class-level consumer/dependency and migration map from Gate A;
2. the minimal new/retained artifact graph and Maven reactor changes, if any;
3. no duplicate shipment lifecycle or parcel source of truth;
4. architecture/dependency tests for both consumer compositions;
5. regression tests for lifecycle, tracking, idempotency, tenant isolation and event publication;
6. clean and upgrade migration verification, or an explicit blocker if the available environment cannot execute it;
7. CI evidence and a compatibility/deprecation note.

## 7. Explicit non-goals

- Moving every courier domain class into shipment.
- Renaming modules solely to match artifact-role terminology.
- Forcing shipment into a microservice.
- Introducing a universal logistics entity or status enum.
- Giving consumer applications direct access to another capability's tables.
- Claiming production readiness based only on documentation or compilation.

## 8. Completion checklist

- [ ] Consumer and class-level dependency trace reviewed.
- [ ] Shipment/parcel aggregate and persistence ownership established.
- [ ] Public contract surface approved.
- [ ] Optional adapter dependency boundary verified.
- [ ] E-commerce composition test passes.
- [ ] Fulfillment/3PL composition test passes.
- [ ] Existing Courier lifecycle and event regressions pass.
- [ ] Tenant/auth/idempotency invariants verified.
- [ ] Clean and upgrade migrations verified.
- [ ] Relevant reactor and CI pass.
- [ ] Compatibility and deployment guidance published.
