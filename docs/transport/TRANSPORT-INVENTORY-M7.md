# Transport M7 — Tenant membership and booking authorization

## Scope

M7 hardens the optional Quarkus booking adapter by connecting it to the public Numou Security SPI. Authentication and trusted identity attributes are necessary but are not sufficient: every booking command now checks active tenant membership and an explicit policy decision before invoking the Transport application service.

## Boundary and dependencies

- Transport runtime depends on `numou-security-spi`, not Identity's private persistence or adapter implementation.
- The host composition must provide a `TenantMembershipResolver` and an `AuthorizationServicePort` backed by its authoritative identity/security provider.
- Missing providers must not be replaced with permissive defaults. The runtime is not ready to deploy until the host has wired these providers.
- Tenant and actor claims remain sourced only from the authenticated `SecurityIdentity`. Tenant membership is separately checked by the membership port.
- The configured tenant and actor IDs must be UUIDs because the shared `TenantId` and `PrincipalId` contracts use UUID identifiers. The human-readable authenticated principal name is retained as the principal handle.

## Authorization requests

All three endpoints check membership first, then submit an `AccessRequest` to the policy service:

| Operation | Resource type | Action | Context |
| --- | --- | --- | --- |
| Create hold | `transport.booking.hold` | `CREATE` | `operation=create` |
| Confirm hold | `transport.booking.hold` | `UPDATE` | `operation=confirm` |
| Release hold | `transport.booking.hold` | `UPDATE` | `operation=release` |

The resource ID is the hold ID for confirm/release and the departure ID for create. Policy configuration must explicitly grant the relevant operation context; authentication alone does not imply permission.

The security port calls have a configurable timeout (`numou.transport.authorization.timeout-millis`, default 2000 ms, allowed range 100–10000 ms). Timeout or provider errors fail closed. The REST resource is marked blocking because the existing booking application service and the security SPI are synchronous/reactive boundaries that are awaited in this adapter.

## Verification added

- Unit tests cover active-member/allowed policy flow and verify the tenant, principal, resource, and operation context.
- Unit tests cover inactive membership (policy is not called), explicit policy denial, and malformed tenant identifiers.

## Required host-level verification before production

1. Wire real membership and authorization providers in the deployment composition.
2. Add API integration tests with the real credential verifier and membership provider for anonymous requests, inactive membership, policy denial, cross-tenant attempts, and each allowed operation.
3. Verify the production policy engine interprets `resourceType`, `ActionType`, and `context` as documented; do not treat this adapter's unit tests as proof of policy-store configuration.
4. Confirm audit attribution persistence. The booking application currently receives tenant scope but does not yet receive actor/correlation context as a command contract; M7 does not claim actor-level durable booking audit is complete.
5. Run the Transport Maven reactor and relevant broader CI. Any unrelated Mobility wallet integration failures should be reported separately.

## Not included

No identity issuer, tenant-membership database, default allow policy, payment capture, ticket issuance, or cross-capability private persistence dependency is introduced.
