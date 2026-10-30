# Support M2 — Authenticated actor resolution

## Scope

Support consumes the shared Identity, Security, and Ecosystem capabilities. It does not own authentication, tenant membership, role assignment, or Party identity linkage.

## Trust sequence

- Read the authenticated user through Identity's `CurrentPrincipalPort`.
- Read tenant and principal identity from Security's request-scoped `CurrentSecurity`; never accept tenant or actor identity from a Support request payload.
- Require the verified Security principal ID to equal the Identity `UserId`, and require the JWT subject to equal the Identity subject.
- Require an active Identity membership for that user in the verified tenant.
- Resolve tenant-scoped effective permission codes for staff roles.
- If the user is not a Support staff actor, resolve the JWT issuer + subject to an active Ecosystem Party identity association in the same tenant.
- Resolve the Party to a Participant and requester role through `SupportPartyParticipantResolver`. Party and Participant identifiers are distinct types and must not be cast or copied across boundaries without an authoritative mapping.
- Map only CUSTOMER and PARTNER requester roles to Support portal actor types. Unknown or unsupported roles are denied.

## Security invariants

- Missing, anonymous, service, mismatched, inactive, or unresolved principals are denied.
- Missing or inconsistent verified tenant context is denied.
- A Party identity association is not a tenant membership or a role grant.
- JWT groups are not used as Support authorization evidence.
- A Party link does not itself prove that the Party is a customer or partner of the current tenant.
- No implicit SYSTEM actor is created from an authenticated service principal.
- Every association lookup is tenant-scoped; Party-to-Participant resolution must also be tenant-scoped.

## Runtime composition requirement

The `support-adapter` module composes the Identity adapter, Security adapter, and Ecosystem JDBC persistence adapter. The host application must apply the Ecosystem migration before using identity associations. Portal workflows additionally require a concrete `SupportPartyParticipantResolver` that reads the authoritative tenant-to-Party-to-Participant relationship. If it is not installed, portal actor resolution fails closed; staff actor resolution remains available.

## Initial permission codes

| Permission | Support actor |
|---|---|
| `support.ticket.manage` | MANAGER |
| `support.ticket.admin` | MANAGER |
| `support.ticket.resolve` | AGENT |

These are Identity permission codes, not JWT group names. They must be assigned using the tenant-scoped role/permission system.
