# M5 — Developer API Catalog Domain

- **Status:** Initial executable domain slice. Persistence, application API, OpenAPI parsing/validation, authorization, and portal UI are not included.
- **Owner:** Numou Developer Platform.
- **Related:** M0 Iket boundary, M1 source audit, M2 application registry contract, M1c machine identity contract, Identity OAuth client credential aggregate.

## Scope
M5 introduces tenant-owned API product metadata, immutable API versions, OpenAPI artifact references/digests, and versioned authorization-scope vocabulary. It does not store OpenAPI bodies, issue credentials, grant scopes, provision Iket routes, enforce quotas, or own subscriptions.

## Ownership
- Developer Platform owns API product identity, provider tenant ownership, product lifecycle, API version lifecycle, and published scope vocabulary.
- Identity owns authenticated actors and OAuth client credentials; Tenancy owns tenant identity/membership; Vault owns secrets; Iket owns routes and edge enforcement.
- Artifact storage owns OpenAPI bytes. The catalog stores only an opaque artifact reference and SHA-256 digest.
- A catalog scope is descriptive vocabulary; publishing `orders.read` does not grant it to an application or credential.

## Model
- `ApiProduct`: immutable ID and provider tenant, normalized slug, name/description, status, revision, timestamps and versions.
- `ApiVersion`: semantic version, immutable OpenAPI specification reference/digest, scope definitions and lifecycle.
- `ApiSpecification`: OpenAPI 3.0/3.1 version, opaque artifact reference and SHA-256 digest.
- `ApiScope`: validated scope identifier and human-readable description.

Slug uniqueness is tenant-scoped and must be enforced by the future application repository and a database unique constraint, not by an in-memory aggregate check.

## Lifecycle
Product: `DRAFT -> ACTIVE <-> SUSPENDED -> RETIRED` (retirement terminal).
Version: `DRAFT -> PUBLISHED -> DEPRECATED -> RETIRED`.
Published version content and scope vocabulary are immutable. Corrections require a new semantic version. Retirement is allowed only after deprecation. Publishing the first version activates the product. Suspension prevents new version publication.

## Invariants
1. Every product has exactly one immutable provider tenant.
2. Product slug follows lowercase kebab-case syntax; cross-record uniqueness is repository/database responsibility.
3. Every API version is a semantic version and unique within its product.
4. Only OpenAPI 3.0 and 3.1 are accepted by this initial catalog contract.
5. OpenAPI bytes are stored externally; catalog metadata stores an opaque reference and SHA-256 digest.
6. Every scope is a bounded validated identifier with a non-empty description.
7. Published versions can only be deprecated, then retired.
8. Scope vocabulary and specification reference cannot mutate after publication.
9. Suspension/retirement blocks new version publication.
10. Successful product mutation advances a monotonic revision.
11. Tenant authorization, uniqueness, artifact existence, digest verification and OpenAPI semantic validation are application/adapter responsibilities.

## Security and integration
Application services must derive tenant context from authenticated request context and verify membership. The domain has no HTTP, database, Vault, gateway, or Gamelan dependencies. Iket consumes published API projections through a versioned control-plane contract; it must not read catalog tables or import Java domain types.

## Acceptance and follow-up
- [x] Pure Java domain module with no runtime/framework dependency.
- [x] Tenant-owned API product aggregate and lifecycle.
- [x] Immutable semantic API version and OpenAPI artifact metadata.
- [x] Scope vocabulary validation.
- [x] Domain tests for lifecycle, duplicate versions, digest, tenant and slug invariants.
- [ ] Run Maven domain tests and ArchUnit checks in CI.
- [ ] Add tenant-scoped application ports/handlers and PostgreSQL persistence.
- [ ] Verify artifact existence/digest and parse/validate OpenAPI documents.
- [ ] Add authorization and audit/outbox integration.
- [ ] Add REST endpoints, portal read model and Iket projection after its management-plane hardening.

No production persistence, endpoint, gateway route or OpenAPI validator is claimed by this milestone.
