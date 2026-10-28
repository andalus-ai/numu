# Transport inventory — M6: Public booking API and tenant authorization

## Scope

Expose the M4 booking application contract through an optional Quarkus REST adapter. The API does not move business invariants into HTTP resources and does not force HTTP dependencies on Transport domain or application consumers.

## Endpoints

- `POST /v1/transport/departures/{departureId}/holds` — create a passenger-capacity hold; requires `Idempotency-Key`.
- `POST /v1/transport/departures/{departureId}/holds/{holdId}/confirm` — confirm a hold.
- `POST /v1/transport/departures/{departureId}/holds/{holdId}/release` — release a hold.

Create requests include `fromSequence`, `toSequence`, `passengerCount`, and an explicit `expiresAt`. Explicit expiry is part of the idempotent request fingerprint, so clients must reuse the same request body and key when retrying. The runtime enforces a configurable maximum hold duration (default 900 seconds, maximum supported configuration 86400 seconds).

Responses expose hold identity, segment, passenger count, lifecycle status, expiry, and timestamps. They do not expose tenant internals or persistence versions.

## Authentication and tenant isolation

All endpoints require an authenticated Quarkus `SecurityIdentity`. The host authentication integration must validate credentials and populate the configured identity attributes from verified claims (or an equivalent trusted identity source):

- `numou.security.tenant-claim` (default `numou_tenant_id`) — selected tenant identifier.
- `numou.security.user-id-claim` (default `numou_user_id`) — authenticated actor identifier.

The tenant and actor identifiers must be UUIDs to match the shared `TenantId` and `PrincipalId` contracts. The adapter fails closed when either trusted identity attribute is absent or invalid. It does not accept tenant or actor IDs from request JSON, query parameters, or caller-provided headers.

In M7, the booking adapter separately checks active tenant membership through the public `TenantMembershipResolver` contract and requires an explicit `AuthorizationServicePort` policy decision for each create/confirm/release operation. Verified claims alone are not treated as proof of current membership or booking permission. The host composition must wire authoritative membership and policy implementations; no permissive fallback is provided.

A globally unique departure or hold ID is not authorization: all application calls remain tenant-scoped. Missing tenant-scoped inventory/holds map to a generic 404 response to avoid confirming the existence of another tenant's records.

## Idempotency and errors

- Missing or oversized `Idempotency-Key`: 400.
- Capacity conflict or key reuse with a different request fingerprint: 409.
- Invalid command: 400.
- Missing tenant-scoped departure inventory or hold: 404.
- Invalid lifecycle transition / optimistic state conflict: 409.
- Missing trusted tenant/actor context, inactive membership, or denied booking permission: 403.
- Membership or policy provider unavailable / timeout: 503.
- Unexpected failures: generic 500 with server-side logging; internal exception details are not returned.

The idempotency scope is owned by the Transport inventory aggregate and persistence adapter. Clients should persist the key and exact request until they receive a definitive response.

## Capability and deployment boundaries

The REST resource and exception mapping live only in `transport-runtime-quarkus`. Domain and application modules remain framework-neutral. Hosts may compose `DepartureBookingService` in-process without deploying HTTP; distributed hosts may expose this versioned API through the runtime adapter.

A capacity hold is not payment authorization, captured funds, a ticket, or a confirmed commercial order. Payment, ticket issuance, customer notifications, and durable cross-capability events remain separate workflows and must use explicit contracts.

## Verification

M6 added fail-closed identity-context tests. M7 adds unit coverage for membership and permission checks plus safe error mapping. Host-level API integration tests using the actual credential verifier, authoritative membership provider, and policy store remain required before production exposure; this adapter deliberately does not invent an authentication issuer or tenant-management policy.
