# Attendee M4 — REST API boundary

## Contract

The REST adapter publishes:
- `POST /api/v1/attendee-profiles` to create a tenant-owned profile linked to an existing Party.
- `GET /api/v1/attendee-profiles/{profileId}` to read a profile inside the authenticated tenant.

The request DTO intentionally has no tenant or actor fields. The application handlers obtain those only from `AttendeeTenantContextProvider`; the API never trusts a tenant header or body property. Party access authorization remains an independent application check.

## Composition requirements

This module is an inbound adapter, not a runtime. The selected application runtime must provide CDI beans for `CreateAttendeeProfileHandler` and `GetAttendeeProfileHandler`, and must bind their ports to the authenticated request context, authorization policy, canonical Party access, tenant-scoped repository, UnitOfWork, and Clock. The adapter deliberately does not invent an authentication scheme or map untrusted headers into tenant identity.

A runtime must fail closed when no authenticated tenant context is available. The handler's `AttendeeTenantContextProvider` implementation must be request-scoped (or otherwise correctly propagated across the reactive execution) and sourced from the runtime's verified principal and tenant-membership resolution.

## Response and error behavior

Creation returns 201 with profile ID, status, and revision. Reads return 200 or a tenant-scoped 404. Expected application errors are mapped to stable JSON `{code,message}` responses; duplicate profile creation returns 409, authorization errors return 403, and validation errors return 400. Unexpected failures should be handled by the runtime's global exception policy and must not leak stack traces or persistence details.

## Deliberate exclusions

This API does not expose registration, attendance, branch assignment, geo, media, coverage, or policy fields. Those remain separate capabilities or later integrations. It also does not expose cross-tenant administration endpoints.

## Verification status

The adapter and API DTOs are committed as source. Maven compilation, REST integration tests, runtime CDI composition, and end-to-end tenant-isolation tests remain to be run/implemented in the consuming runtime before this API can be called production-ready.
