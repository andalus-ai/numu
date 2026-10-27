# M45 — Shipment consumer and runtime-boundary audit

## Status and confidence

This is a source-tree and selected-file audit of the default branch, not an exhaustive repository-wide call-site proof. GitHub code search returned no usable matches for `ShipmentCapability`, `BookShipmentHandler`, and tenant-context searches in this review, so the absence of a match is not evidence that no consumer exists.

The latest M44 merge workflow (run 40450414652) completed with failure. The module-topology and dependency-boundary job passed. The framework/Courier verification job failed earlier at **“Verify Mobility reactor and wallet PostgreSQL invariants”**; the organization/identity/quantity and Courier/Cargo steps were skipped. Therefore this run does not establish whether M44's shipment tests pass or fail. Treat the Courier Maven verification as outstanding, not as a shipment-specific failure or a green build.

## Observed source ownership

| Concern | Observed owner | Reuse decision |
| --- | --- | --- |
| Shipment aggregate, parcel lifecycle, IDs/status, repository ports | `logistics/numou-courier-domain` | Keep the current aggregate as the single source of truth. |
| Booking orchestration | `logistics/numou-courier-application`: `BookShipmentHandler` | `ShipmentCapability` delegates to this handler; do not duplicate booking rules. |
| Consumer-facing booking/lookup/tracking DTOs | `logistics/numou-courier-application`: `publicapi/ShipmentCapability` | Suitable as an in-process facade, subject to validation/security follow-up. |
| Shipment + parcel persistence | `logistics/numou-courier-postgres-adapter`: `JdbcShipmentRepository` | Keep adapter optional for consumers that provide their own adapter. Preserve deployed Flyway history. |
| Tracking persistence | `logistics/numou-courier-postgres-adapter`: `JdbcTrackingEventRepository` | Keep tenant scope and the composite cursor contract. |
| POD evidence and asynchronous projection | Application POD use case plus PostgreSQL store/projector/worker | Keep outside the first shipment facade until caller authorization and lifecycle guarantees are established. |
| Courier event delivery | PostgreSQL outbox publisher/worker, including opt-in `HttpCourierEventPublisher` | This is outbound event transport, not an inbound shipment API. Receiver auth and durable deduplication remain required. |

The Maven reactor at `logistics/pom.xml` contains the courier domain, application, and PostgreSQL adapter alongside fleet. The application POM depends on courier domain and organization domain; the PostgreSQL adapter is a separate infrastructure module.

## Runtime and authorization findings

- The tree scan did not find a dedicated inbound courier HTTP/resource/controller class or a separate courier HTTP transport Maven module under `logistics/`.
- `CourierIntegrationEndpoint` is a domain record describing outbound webhook destinations; it is not an HTTP request handler.
- `HttpCourierEventPublisher` sends outbound outbox events. Its existence must not be presented as evidence that booking, lookup, or tracking has an inbound HTTP API.
- Existing M29/M30 documents describe the outbound outbox gateway and its retry/deduplication contract, not shipment request routing.
- `ShipmentCapability` accepts a tenant ID in its request/lookup methods and forwards it to tenant-scoped ports. It has no authentication principal, actor context, authorization policy, or trusted tenant resolver. A transport/composition layer must derive tenant identity from authenticated context, authorize the operation, then invoke the facade. A client-supplied tenant string is not proof of access.
- No durable booking idempotency key is accepted by this first facade. Do not advertise retry-safe booking until a tenant-scoped idempotency record and shipment creation are atomic, with same-key/same-request replay and same-key/different-request conflict behavior defined.

## Composition decision

Do **not** add an inbound HTTP endpoint or split the shipment module based on the current evidence alone. The next implementation must first establish where application/runtime composition belongs and how identity/tenant context is supplied. The same facade can be wired in-process in a modular monolith; a separately deployed API can later map a versioned transport contract to it without moving the aggregate or duplicating business rules.

Before introducing transport, complete these gates:

1. Run the Courier/Cargo Maven reactor after the earlier Mobility/wallet CI blocker is resolved or isolated; capture actual test results for the application, domain, and PostgreSQL adapter.
2. Run a checked-out-workspace source search for shipment/tracking ports, public facade imports, SQL table references, HTTP resources, scheduled workers, message consumers, and tenant construction. Record each external entry point and its auth/tenant source.
3. Confirm the intended runtime module and existing authentication/authorization extension points; add tests proving unauthorized tenants cannot read or mutate another tenant's shipment.
4. Define booking idempotency and typed transport errors before exposing a remote contract.
5. Keep migrations in their current deployed history unless an explicit compatibility plan proves a safe alternative.

## Scope limits

This audit is based on the repository tree and selected source/POM/document files available through GitHub. It is not a full local checkout, import graph, SQL search, dependency tree, Maven test run, or deployment authorization test. It does not claim exhaustive consumer discovery or production readiness.
