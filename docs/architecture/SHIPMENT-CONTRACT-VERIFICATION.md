# M44 — Shipment contract behavioral verification

## Scope

M44 verifies that the in-process `ShipmentCapability` is a facade over existing courier behavior, not a parallel implementation of shipment rules.

## Behavioral checks added

- Booking maps a consumer request into the existing `BookShipmentCommand` path through `BookShipmentHandler`.
- The repository receives one booked aggregate containing all submitted parcels under the explicit tenant.
- The returned view reflects the authoritative aggregate status, service level, booking timestamp, and parcel list.
- Tenant-scoped lookup forwards both tenant and shipment ID to the repository port.
- Tracking timeline reads preserve the two-part cursor and return consumer DTOs.
- Oversized tracking page requests and empty-parcel booking requests are rejected.

The tests instantiate the final `BookShipmentHandler` rather than relying on mocking the concrete handler class. Persistence is mocked at the repository port, which keeps these tests infrastructure-independent.

## What this does not prove

- These tests do not replace JDBC integration tests for transaction atomicity or tenant-safe SQL.
- They do not prove authentication/authorization wiring at a remote entry point.
- They do not establish a durable booking idempotency contract.
- They do not establish an exhaustive repository-wide call-site inventory.
- CI status for the M43 merge was reported as cancelled; no passing Maven test result is claimed by this document.

## Next gate

Run the logistics Maven reactor and its existing domain/adapter integration tests. Then inventory all source references to the shipment and tracking ports and identify the actual runtime entry points and trusted tenant-context source before adding a network transport adapter.
