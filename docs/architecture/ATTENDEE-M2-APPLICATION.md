# Attendee Capability — M2 Application Layer

## Delivered

M2 adds application orchestration and outbound contracts without introducing transport or persistence framework dependencies.

- CreateAttendeeProfileCommand and CreateAttendeeProfileHandler.
- GetAttendeeProfileQuery and GetAttendeeProfileHandler.
- AttendeeProfileRepository, with explicit tenant predicates on every lookup.
- AttendeeTenantContextProvider, whose context must be created from authenticated identity and verified tenant membership.
- AttendeeAuthorizationPort for create/read permission checks.
- AttendeePartyAccessPort for tenant- and actor-scoped permission to reference a canonical Party.
- Fixed/injected Clock for deterministic domain timestamps.
- UnitOfWork around the profile uniqueness check and save operation.

## Security and ownership

Tenant and actor IDs are not accepted from the create command. They are read from trusted request context. The repository API requires TenantId for reads and its implementation must include that tenant in every SQL predicate.

Create authorization and Party-reference authorization are separate checks. The Party access adapter must consult the authoritative identity/tenant relationship and must not disclose whether an inaccessible Party exists. A caller's permission to create attendee profiles does not grant permission to attach arbitrary Party identities.

The attendee capability references PartyId; it does not create or mutate Party. Registration, attendance, branch assignment, geo position, media, coverage and policy evaluation remain outside this application slice.

## Persistence contract implemented in M3

The PostgreSQL adapter enforces a unique constraint on (tenant_id, party_id, profile_kind). The application pre-check improves error quality but is not a concurrency guarantee. A database uniqueness violation is translated to the stable ATTENDEE_PROFILE_ALREADY_EXISTS application error.

Profile reads use both tenant and profile ID. The adapter never loads by profile ID alone and filters in application memory. See [ATTENDEE-M3-PERSISTENCE.md](ATTENDEE-M3-PERSISTENCE.md) for schema, migration and revision semantics.

## Verification

M2 application tests and M3 PostgreSQL Testcontainers tests have been added. They have not been executed in this environment; do not treat these milestones as CI-verified until the framework reactor runs the new module tests.
