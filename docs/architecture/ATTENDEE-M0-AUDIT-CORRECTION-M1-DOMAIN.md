# Attendee Capability — M0 Audit Correction and M1 Domain Implementation

## M0 audit correction

A complete recursive repository tree inspection supersedes the earlier limited code-search observation in `ATTENDEE-PARTICIPANT-M0-M1-FOUNDATION.md`.

The repository already contains:
- Canonical `Party` identity and `PartyId` in `core/framework/ecosystem/ecosystem-domain`.
- Ecosystem `Participant` aggregate and `ParticipantId`, representing ecosystem actors/providers/tenants, not event attendance records.
- Tenant-scoped `PartyParticipantAssociation` for business-role relationships; explicitly not an authorization grant.
- Party contact points and address primitives.
- Organization profile and Organization capability, including organization-unit application commands and repositories.
- Existing tenant primitives in Foundation.

Therefore, Attendee must not create a second Person/Party master, repurpose the Ecosystem Participant aggregate, duplicate Contact/Address, or create a parallel Organization hierarchy.

## Domain distinction

- **Party**: canonical person/organization identity.
- **Ecosystem Participant**: an actor participating in the Numou business ecosystem (tenant, provider, individual, platform, etc.).
- **AttendeeProfile**: tenant-scoped, contextual presentation/lifecycle profile used by an attendee-consuming application. It references Party by PartyId and does not own the Party.
- **Registration**: enrollment in a specific event/program/session; belongs to the relevant registration capability.
- **Attendance**: actual presence/check-in/out; belongs to the relevant attendance capability.

An attendee profile is not proof of registration, attendance, membership, branch eligibility or authorization.

## M1 implementation delivered

New capability root:
- `attendee/pom.xml`
- `attendee/attendee-domain/pom.xml`

New pure domain types:
- `AttendeeProfile`
- `AttendeeProfileStatus`

The profile contains:
- capability-owned UUID;
- mandatory TenantId;
- mandatory canonical PartyId reference;
- namespaced-by-convention profile kind;
- display name, optional preferred name and optional BCP-47 language tag;
- explicit PENDING / ACTIVE / SUSPENDED / ARCHIVED lifecycle;
- created/updated instants and monotonic revision.

It intentionally excludes contact/address copies, images/documents, GPS, date of birth, sensitive personal attributes, branch assignments, policy decisions, registration, tickets and attendance. Those remain in their authoritative capabilities or later milestones.

Lifecycle operations are immutable and caller-clocked. This avoids hidden system-clock calls in domain logic and allows deterministic tests. Updates preserve profile, tenant and Party identity and advance the revision. Archived profiles cannot be reactivated or edited.

## Build integration

The new attendee capability aggregator is included in `core/framework/pom.xml`. The domain depends only on Foundation and the public Ecosystem domain contract. It has no HTTP, CDI, ORM, database, PostGIS, broker or storage dependencies.

## Tests added

`AttendeeProfileTest` covers:
- creation and canonical Party reference;
- optional presentation fields;
- immutable lifecycle transitions and revision increments;
- invalid transitions and archived immutability;
- mandatory tenant/party and timestamp invariants;
- presentation update preserving identity and tenant scope.

## Verification status

The GitHub contents API confirms commits were created on `feat/attendee-participant-m1-domain`. It does not execute Maven. No compile, test, or CI success is claimed here; CI must verify the framework reactor and its dependent modules.

## Next M1 work

1. Add application ports and commands only after confirming existing command, tenant context, authorization and idempotency conventions.
2. Add tenant-scoped persistence and optimistic locking through the selected adapter boundary.
3. Add API transport only after authorization and field-visibility rules are wired.
4. Add durable outbox publication only when the existing event/outbox contract is integrated.
5. Update capability matrix and API/event compatibility docs.
