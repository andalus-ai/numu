# M43 — Shipment public application contract

## Decision

The first reusable shipment contract is implemented inside the existing `numou-courier-application` module as `ShipmentCapability`. It is an in-process, framework-neutral facade; it does not introduce a second aggregate, new persistence model, or separate deployable service.

The facade provides:
- shipment booking by mapping consumer DTOs to the existing `BookShipmentCommand` and `BookShipmentHandler`;
- tenant-scoped lookup by shipment ID or tenant-scoped reference through `ShipmentRepository`;
- tenant-scoped tracking timeline reads through `TrackingEventRepository`, retaining both `occurredAt` and event ID in the exclusive cursor;
- consumer DTOs rather than returning mutable shipment aggregates or persistence rows.

The application module still depends on the existing courier domain artifact. This milestone deliberately does not claim a separately publishable, courier-independent shipment artifact; that requires the remaining dependency/call-site proof and potentially a later package split.

## Security and semantics

- Tenant IDs are explicit inputs and are always passed to tenant-scoped repository ports. A caller must derive the tenant from trusted authenticated context; this facade does not authenticate callers or authorize access.
- A transport adapter must not accept an arbitrary tenant identifier from an untrusted request body and treat it as authorization.
- Booking delegates to the existing handler so aggregate invariants and shipment-plus-parcel persistence behavior remain authoritative.
- The existing booking operation does not provide a durable idempotency key contract. This facade does not pretend to add one; command idempotency must be designed against the actual persistence transaction before it is advertised.
- Tracking uses the existing repository's stable composite cursor. Page size is constrained to 1–100.
- POD, COD, carrier pricing, hubs, fleet, and dispatch are not pulled into this first contract.
- No database schema or Flyway migration was changed.

## Verification status

M44 adds behavioral tests for booking delegation, aggregate/parcel mapping, tenant-explicit lookup, composite tracking cursors, and request bounds. See [M44 Shipment contract behavioral verification](SHIPMENT-CONTRACT-VERIFICATION.md). The M43 GitHub Actions run was cancelled after the PR merged, so no passing Maven result is claimed. Before treating the contract as stable, run the logistics reactor tests and complete the source-wide consumer, authentication, and dependency inventory described in M42.
