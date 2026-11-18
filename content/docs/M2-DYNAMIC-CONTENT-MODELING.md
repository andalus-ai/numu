# M2 — Dynamic Content Modeling

## Delivered scope
- Tenant-scoped content type identity and stable type keys.
- Immutable, numbered schema snapshots; field changes create a new draft version.
- Explicit draft-to-published schema transition with optimistic publication guard.
- Portable field contracts: string, text, integer, decimal, boolean, date, datetime, email, URL and structured JSON.
- Required/nullability, length, numeric range, regular-expression and allowed-value constraints.
- Deterministic validation results with field-level error codes.
- PostgreSQL persistence for type definitions, schema versions, field definitions and allowed values.
- Application use cases for create, fork version, publish, retrieve and validate.
- Dedicated domain and application tests.

## Invariants
- Every schema read/write is scoped by trusted tenant context.
- A published schema version is immutable; a change is represented by a new draft version.
- Validation names an exact schema version; "latest" is intentionally not accepted.
- Draft validation is explicitly separate from validation against a published version.
- Field names and type keys are stable lowercase identifiers.
- Content values are supplied as decoded maps. JSON parsing and HTTP transport are adapter responsibilities.

## Current boundary
M2 establishes schema definition, versioning and validation as a reusable capability. It does not yet attach a schema version foreign key to each ContentRevision; that integration is a follow-on content-entry contract change so historical revisions can remain validated against their original schema. Do not treat schema validation as enforced by the legacy untyped ContentApplicationService create/revise methods.

## Validation
The Content Platform workflow is configured to run for pull requests targeting main and the M1 branch. The M1 base branch now contains that trigger configuration; the M2 PR's first authoritative result is pending. PostgreSQL adapter compilation is covered by Maven verification, but a live PostgreSQL integration environment and migration execution are not yet included.
