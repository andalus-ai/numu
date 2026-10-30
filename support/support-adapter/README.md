# Support security adapter

This module composes Support with the shared Identity, Ecosystem, and Security capabilities.

## Actor resolution

`DefaultSupportActorProvider` resolves the current actor from:

1. `CurrentPrincipalPort` for the authenticated Identity principal.
2. `CurrentSecurity` for the request's verified tenant and principal identity.
3. `MembershipManagementPort` to confirm active user membership in that tenant.
4. `EffectivePermissionPort` for tenant-scoped Support staff permissions.
5. `PartyIdentityAssociationRepository` for an explicit active issuer + subject to Party link.
6. `SupportPartyParticipantResolver` for a tenant-verified Party-to-Participant mapping and requester role.

The provider fails closed when any required identity, tenant, membership, association, or mapping evidence is absent or inconsistent. It does not trust request-body user IDs, tenant headers, arbitrary JWT groups, or a PartyId/ParticipantId UUID coincidence.

## Permission mapping

The initial Support permission codes are:

- `support.ticket.manage` or `support.ticket.admin` -> `MANAGER`
- `support.ticket.resolve` -> `AGENT`

All other authenticated users must resolve through the Party identity association and tenant-specific participant mapping. Only `CUSTOMER` and `PARTNER` requester roles are mapped to portal actors. Other requester roles are denied until an explicit Support policy is added.

These codes must be granted through Identity's tenant-scoped role/permission model; token groups alone do not grant Support staff access.

## Deployment composition

The runtime must include the shared Identity adapter and Security adapter so their CDI providers are available. A deployment that enables customer/partner portal access must also provide a concrete `SupportPartyParticipantResolver` backed by authoritative Party/participant relationship data. This resolver is intentionally optional at CDI startup so deployments without portal linking can still serve staff workflows; portal actor resolution is denied when it is absent.

The provider never maps service principals to `SYSTEM`. Trusted system workflows require a separate, explicit application entry point and authorization policy.
