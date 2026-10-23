# Cargo and Courier — Milestone 1: Tenant, Branch, and Hub Foundations

**Status:** Implementation in progress  
**Scope:** Domain and application foundation; database persistence and operational workflows follow in later milestones.

## Boundary decisions

- A **tenant** is the security and data-ownership boundary. Cargo and courier records must always carry tenant ownership.
- An **organization** is the legal/operating organization already modeled by Numou.
- A **branch** is an organizational unit (`OrganizationUnitId`) and is not itself a physical facility.
- A **hub** is a tenant-owned physical or operational facility attached to an organization and a branch. One branch may operate multiple hubs.
- A facility is not shared across tenants implicitly. Cross-tenant facility use will require an explicit facility-sharing agreement, authorization, and audit trail in a later milestone.
- A hub's timezone is explicit and must be used for local cutoffs, shift schedules, and operational timestamps. Persist timestamps as instants.
- Authorization scope checks are defense-in-depth. Application handlers and persistence adapters must still enforce tenant predicates and, where supported, database row-level security.

## Implemented in M1

- Strongly typed `HubId` and hub classifications for branch depots, origin/transit/destination hubs, sorting, cross-dock, fulfillment, lockers, and partner facilities.
- Hub address validation, ISO alpha-2 country code normalization, paired/validated coordinates, and explicit timezone.
- Hub lifecycle: draft → active → suspended → active; active/suspended → closed. Closed hubs cannot be renamed or resumed.
- `HubAccessScope` enforces tenant identity and branch/hub-level access, including permission to register a hub at a branch.
- `HubRegistrationPolicy` rejects organization mismatch, non-branch units, and dissolved branches.
- `HubRepository` defines tenant-explicit save, lookup, uniqueness, and deletion contracts. Concrete adapters must enforce tenant predicates at query level.
- `RegisterHubHandler` checks authorization first, loads the branch scoped to its organization, applies registration policy, checks tenant-local code uniqueness, and saves the hub.
- Unit tests cover lifecycle, address validation, access isolation, branch registration, duplicate codes, and application-handler authorization.

## Known gaps / not claimed complete

- No concrete database schema/migration or repository adapter exists yet; uniqueness must ultimately be backed by a tenant+normalized-code database constraint to close concurrent registration races.
- Tenant-to-organization ownership validation is not available in the current organization model. It must be enforced through a canonical tenant-organization mapping or an authorized application port before production use.
- The current `OrganizationUnitLookup` contract requires adapters to scope the lookup by organization and to ensure the requesting actor may see that unit.
- REST endpoints, event publication, API authentication wiring, cross-tenant shared-facility agreements, shipment booking, parcel identity, labels, tracking events, manifests, courier dispatch, delivery proof, billing, and COD are later work.
- Build/test execution has not yet been verified in the repository's intended Maven reactor.

## Acceptance criteria

1. Tenant is mandatory and immutable on a hub.
2. A hub belongs to exactly one tenant, organization, and branch.
3. Hub access rejects cross-tenant access even for tenant administrators.
4. Only an active branch in the requested organization can be used to register a hub.
5. Duplicate hub codes are checked per tenant; the persistence layer must enforce the same invariant atomically.
6. Repository lookups require explicit tenant scope and enforce it in storage queries.

## Tenant ownership and persistence boundary (M1 follow-up)

Hub registration now resolves the organization before resolving its branch. The application flow rejects missing organizations, organizations whose `tenantId` does not match the command tenant, and inactive organizations. The organization lookup port contract requires adapter implementations to tenant-scope the lookup at query time; filtering a globally loaded organization in application memory is not sufficient for production.

A PostgreSQL-oriented Flyway migration is included at `logistics/numou-courier-domain/src/main/resources/db/migration/V1__courier_hubs.sql`. It stores tenant ownership on each hub and enforces `UNIQUE (tenant_id, normalized_code)` in the database, including coordinate and lifecycle checks. This is the concurrency-safe uniqueness boundary; the application pre-check is only for early feedback.

**Remaining before M1 is production-ready:** implement and wire a concrete repository adapter whose reads, updates and deletes all predicate on `tenant_id`; map database unique violations to a stable domain/application conflict; confirm the actual Flyway discovery and database dialect in the runtime; add database integration tests; and verify the complete Maven reactor/CI. The migration alone does not constitute a completed persistence adapter.
