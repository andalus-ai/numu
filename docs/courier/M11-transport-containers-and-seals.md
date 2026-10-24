# M11 — Transport containers and sealed custody

## Objective

Model reusable physical groupings used between sortation and hub dispatch without coupling the courier domain to a specific warehouse, carrier, scanner, or container vendor.

## Included

- Container types: bag, cage, pallet, roll cage, and tote.
- Tenant-owned container identity, route endpoints, and lifecycle.
- Parcel membership changes while the container is open.
- Required non-empty membership before sealing.
- Seal reference and actor reference recorded at sealing.
- Dispatch only after sealing, with a transport-manifest reference.
- Receiving only after dispatch and only when the observed seal matches the recorded seal.
- Tenant-scoped PostgreSQL container and parcel-membership tables, tenant-local unique container/seal references, and route/status indexes.
- Domain tests for empty sealing, duplicate membership, lifecycle restrictions, seal mismatch, and monotonic timestamps.

## Invariants and boundaries

1. A parcel reference can belong to at most one active persisted container per tenant; removing membership releases that uniqueness constraint.
2. Membership is immutable after sealing in this domain lifecycle.
3. A seal mismatch does not advance receiving custody and must be handled by a separate exception workflow.
4. Container references are tenant-local; all persistence operations must include tenant identity.
5. This aggregate records actor references but does not authenticate actors or grant permissions.

## Follow-ups required for end-to-end operation

- Tenant-scoped JDBC repository and application use cases with optimistic concurrency.
- Explicit seal-break/exception workflow with actor, reason, evidence reference, and audit event.
- Membership integration with the canonical parcel registry and dispatch-wave manifests.
- Durable outbox events for sealed, dispatched, received, and exception states.
- PostgreSQL/Testcontainers tests and Maven reactor verification.
- Container capacity/weight limits and route compatibility policies, configured by container type or operator policy.

This is a domain-and-schema milestone. It does not claim that repositories, authorization, device integration, or end-to-end production validation are complete.
