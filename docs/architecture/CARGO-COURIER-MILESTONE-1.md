# Cargo and Courier — Milestone 1: Tenant, Branch, and Hub Foundations

**Status:** Implementation in progress  
**Scope:** Domain foundation only; persistence, APIs, and operational workflows follow in later milestones.

## Boundary decisions

- A **tenant** is the security and data-ownership boundary. Cargo and courier records must always carry tenant ownership.
- An **organization** is the legal/operating organization already modeled by Numou.
- A **branch** is an organizational unit (`OrganizationUnitId`) and is not itself a physical facility.
- A **hub** is a tenant-owned physical or operational facility attached to an organization and a branch. One branch may operate multiple hubs.
- A facility is not shared across tenants implicitly. Cross-tenant facility use will require an explicit facility-sharing agreement, authorization, and audit trail in a later milestone.
- A hub's timezone is explicit and must be used for local cutoffs, shift schedules, and operational timestamps. Persist timestamps as instants.
- Authorization scope checks are defense-in-depth. Application handlers and persistence adapters must still enforce tenant predicates and, where supported, database row-level security.

## Implemented in M1

- Strongly typed `HubId`.
- Hub types for origin, transit, destination, sorting, cross-dock, fulfillment, locker, branch depot, and partner facilities.
- Hub address validation, ISO alpha-2 country code normalization, and paired/validated coordinates.
- Hub lifecycle: draft → active → suspended → active; active/suspended → closed. Closed hubs cannot be renamed or resumed.
- `HubAccessScope` enforces tenant identity and branch/hub-level access for application use.
- Unit tests cover lifecycle, invalid coordinates, and access-scope isolation.

## Explicitly not implemented yet

- Database schema, migrations, repositories, REST endpoints, event publication, and API authentication wiring.
- Branch existence/type validation. The application boundary must load the organization unit and confirm that it belongs to the same organization and is of kind `BRANCH` before creating a hub.
- Cross-tenant shared-facility agreements.
- Shipment booking, parcel identity, labels, tracking events, manifests, courier dispatch, delivery proof, billing, and COD.

## Acceptance criteria for the milestone

1. Domain code is independently reusable and does not depend on fleet or shipment workflows.
2. Tenant is mandatory and cannot be changed after creation.
3. A hub belongs to exactly one tenant, organization, and branch.
4. Hub access checks reject cross-tenant access even for tenant administrators.
5. Lifecycle transitions are explicit and invalid transitions fail closed.
6. Build and tests pass in the repository's intended Maven reactor; if reactor wiring is unavailable, that limitation is recorded rather than assuming success.