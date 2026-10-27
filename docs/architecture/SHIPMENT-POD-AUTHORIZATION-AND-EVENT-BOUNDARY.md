# M41 — Shipment POD, authorization, and event-boundary review

- **Status:** Source-level review; implementation and runtime verification remain pending.
- **Depends on:** M39 shipment extraction/composition contract and M40 dependency/persistence trace.
- **Scope:** Proof-of-delivery (POD) command boundary, tracking projection, tenant context, and implications for a reusable Shipment capability.
- **Non-goal:** This review does not declare the existing courier workflow production-ready or authorize moving classes/tables.

## Findings

### 1. POD application command accepts trusted identity; it does not establish it

`RecordProofOfDelivery` validates the request's required values, timestamp ordering, digest format, and field lengths, then forwards a tenant-explicit command to `ProofOfDeliveryStore`. Its class documentation explicitly says tenant identity and evidence authorization must be established by trusted callers.

That is a valid application-layer design only if every exposed composition establishes those preconditions. The command itself does not prove that the actor belongs to the tenant, can deliver the shipment, or may submit evidence for the referenced delivery attempt. An identifier, tenant ID, or actor reference supplied by an untrusted HTTP client must not be treated as authorization.

**Extraction gate:** identify and test the HTTP/runtime authorization path and any non-HTTP callers before publishing POD as a reusable public contract. Keep identity resolution and policy enforcement in the host/composition boundary or an explicit authorization port; do not embed Quarkus or HTTP types in the domain contract.

### 2. POD evidence recording and shipment delivery completion are separate stages

The application command records immutable evidence through `ProofOfDeliveryStore`; its own documentation says it does not directly advance shipment state. The PostgreSQL projector consumes `courier.proof-of-delivery.recorded.v1` from `courier_event_outbox`, projects the event into tracking, and acknowledges the outbox row within the same PostgreSQL transaction. It claims rows with `FOR UPDATE SKIP LOCKED`, validates lifecycle fields, and records retry state for per-event failures.

This creates a meaningful boundary:

1. Accept and durably record POD evidence with idempotency.
2. Project the committed event into tracking.
3. Apply the delivery-completion lifecycle under the existing projector/lifecycle rules.

Consumers must not interpret “POD accepted” as “shipment is delivered” unless they are explicitly consuming a contract that guarantees the latter. The event name/version and terminal lifecycle semantics must remain explicit in any remote adapter.

### 3. Tracking is a tenant-scoped append-only timeline, not the Shipment aggregate

The JDBC tracking adapter validates that the requested tenant matches the event tenant, inserts using a tenant-scoped idempotency key, and treats an identical fact as a replay while rejecting a different fact with the same key. Timeline reads scope by tenant and shipment and use a stable descending `(occurred_at, id)` cursor.

A public Shipment contract should expose these semantics rather than exposing JDBC rows, table names, or the adapter class. Pagination must preserve the composite cursor; a timestamp-only cursor can skip or repeat events with equal timestamps.

### 4. Packaging boundary is already partly favorable

The courier application Maven module depends on `numou-courier-domain` and organization domain, with test libraries scoped to tests. The PostgreSQL adapter separately declares Quarkus Arc, scheduler, PostgreSQL JDBC, and Flyway dependencies. This supports the intended layering at the Maven level, but it does not by itself prove that all public APIs are framework-free or that consumers avoid internal adapter dependencies.

### 5. Migration and persistence ownership still block a safe extraction

The M40 trace records that courier migrations include shipment/parcel, tracking, quote/COD, and outbox concerns in one adapter migration history. Existing deployed Flyway history and checksums must be considered before splitting migrations or database ownership. A reusable in-process API can be introduced before a service/database split; a separate deployment requires a separately designed compatibility and data-ownership migration.

## Consumer inventory status

The repository tree contains a courier application module, PostgreSQL adapter, courier HTTP transport work, and milestone documents through M31. This is a useful inventory starting point, **not a complete repository-wide proof of all Shipment consumers**. Repository code-search results were not sufficient to certify that no consumers depend on internal classes or tables.

Before implementing the first public contract, complete a reproducible inventory using repository-wide source search and Maven dependency inspection. For each consumer, record:
- consumed public API or internal type/table;
- tenant and actor identity source;
- authorization enforcement point;
- idempotency and retry behavior;
- whether it needs booking, lookup, tracking, POD, quotes/COD, or dispatch;
- in-process versus remote composition;
- migration/data ownership assumptions.

## M42 implementation gate

Proceed with a narrow public application contract and in-process adapter only after the inventory above is verified. The first slice should be limited to capabilities already owned by the Shipment application boundary (booking, tenant-scoped lookup, and tracking timeline) and should:

- carry tenant and actor/correlation context explicitly where needed;
- use typed outcomes for validation, not-found, lifecycle, and idempotency conflict/replay;
- preserve existing aggregate and parcel transaction behavior;
- preserve the tracking composite cursor and replay semantics;
- avoid leaking Quarkus, JDBC, Flyway, COD, fleet, or adapter implementation types;
- add tests for tenant mismatch, identical replay, conflicting replay, cursor stability, and dependency direction;
- leave existing schema and migration history unchanged.

POD should remain a separately specified contract until caller authorization, event delivery guarantees, and the relationship between evidence acceptance and delivery completion have explicit tests.

## Validation record

- Inspected source: `RecordProofOfDelivery`, `ProofOfDeliveryStore`, `JdbcProofOfDeliveryTrackingProjector`, `JdbcTrackingEventRepository`, and courier application/PostgreSQL adapter POMs.
- Inspected repository guidance: root `AGENTS.md` and ADR-003.
- No Maven build, integration test, migration test, or CI run was executed for this review.
