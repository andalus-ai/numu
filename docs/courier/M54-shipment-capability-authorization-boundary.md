# M54 — Courier shipment capability authorization boundary

**Status:** Implemented on branch; verification pending  
**Capability owner:** Courier / Cargo  
**Base:** `main` at `1d74285783438e1a5c73f706cd876d07771e2267`

## Problem

The existing `ShipmentCapability` provides a useful framework-neutral application contract for booking, lookup and tracking. It accepts a tenant identifier, but it does not authorize an actor. Tenant-scoped repository calls prevent cross-tenant key lookups only when the tenant is trusted; they do not prove that the authenticated caller may perform the requested action.

The unguarded capability must therefore not be exposed directly by HTTP, messaging, partner, or other untrusted inbound adapters.

## Implementation

- Add `CourierActorContext`, carrying trusted `TenantId`, actor reference and correlation ID. Its documentation requires the host composition/authentication boundary to derive these values from authenticated identity and request/message context.
- Add `CourierShipmentAccess`, an application port for authoritative policy decisions. Requests include tenant, actor, correlation, action and resource reference.
- Add `AuthorizedShipmentCapability`, a framework-neutral facade that checks policy before booking, shipment reads and tracking reads.
- Booking rejects a request whose tenant does not match the trusted actor context before calling the policy port or shipment repository.
- Policy denial and policy-engine failures fail closed; shipment repositories are not called unless authorization succeeds.
- Clarify that the existing `ShipmentCapability` is an unguarded internal composition primitive, not an authorization boundary. Existing method signatures remain compatible.

## Contract and ownership

Courier owns shipment booking and shipment read semantics. The host application's identity/authorization adapter owns principal authentication and maps its policy engine to `CourierShipmentAccess`. No HTTP framework, identity provider, database, broker or deployment topology is added to the application module.

The access adapter must evaluate current tenant membership, branch/organizational scope and action permissions. It must not trust caller-supplied tenant or actor fields. For distributed deployments, the host must authenticate the caller and propagate a verifiable actor/tenant context; a serialized `CourierActorContext` alone is not a credential.

## Tests added

- Authorized booking calls policy before persistence and propagates trusted tenant, actor and correlation context.
- Denied booking does not invoke shipment persistence.
- A tenant mismatch is rejected before authorization or persistence.
- Shipment lookup is authorized and uses tenant from the trusted context.
- Policy-engine failure fails closed and does not read shipment data.

## Remaining M54 scope

This milestone secures the current public shipment contract (booking, shipment lookup and tracking reads). It does not yet expose or authorize every hub, pickup, dispatch, proof-of-delivery, COD or partner workflow. Those operations must receive equivalent actor-aware public contracts before being exposed to untrusted adapters. The low-level command handlers and persistence ports remain internal composition APIs.

## Acceptance

1. The application module compiles and its focused tests pass.
2. Denial, tenant mismatch and policy outage cases are proven not to reach shipment persistence.
3. No transport or identity-provider dependency is introduced into the application artifact.
4. The public contract and security limitation are documented.
5. Independent Courier reactor CI passes before the milestone is considered verified.
