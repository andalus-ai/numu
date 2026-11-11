# Advertising M1 — REST API and Runtime Composition

- **Branch:** `feat/advertising-m1-rest-api-runtime`
- **Scope:** HTTP boundary and CDI composition for advertiser/publisher account and publisher inventory use cases.
- **Builds on:** Advertising domain, application, PostgreSQL persistence, trusted Identity/Security authorization adapter, and canonical Ecosystem Party verification adapter.

## Runtime composition

The host adapter provides a CDI producer for `AdvertisingAccountService`, composing:

- `AdvertisingAccountStore` — PostgreSQL persistence adapter.
- `AdvertisingAuthorizationPort` — trusted Identity/Security adapter.
- `AdvertisingPartyVerificationPort` — canonical Ecosystem Party lookup adapter.
- `Clock.systemUTC()` — application time source.

The host runtime must still include the platform's Identity, Security and Ecosystem adapters, a configured PostgreSQL datasource, and the Advertising Flyway migrations. This producer does not instantiate or own those platform capabilities.

## HTTP contract

Base path: `/api/v1/advertising`

| Method | Path | Purpose |
|---|---|---|
| POST | `/advertisers` | Register advertiser against an active canonical Party |
| POST | `/publishers` | Register publisher against an active canonical Party |
| POST | `/advertisers/{id}/activate` | Activate advertiser |
| POST | `/publishers/{id}/activate` | Activate publisher |
| POST | `/publishers/{id}/properties` | Register publisher website/application |
| POST | `/properties/{id}/verify` | Verify publisher property |
| POST | `/properties/{id}/placements` | Define a placement on verified property |
| POST | `/placements/{id}/enable` | Enable placement after parent-state checks |

Request DTOs intentionally contain no tenant ID, actor ID, role, permission, or account ownership override. Tenant is resolved from `CurrentSecurity.require().tenantId()`; the authorization adapter independently validates that tenant against the trusted principal and current Identity membership.

## HTTP behavior

- `201 Created` for advertiser/publisher/property/placement registration.
- `200 OK` for lifecycle transitions.
- `400 Bad Request` for malformed or invalid input.
- `403 Forbidden` for denied authorization.
- `409 Conflict` for invalid lifecycle transitions or state conflicts.
- `500 Internal Server Error` for unexpected failures; internal exception details are not exposed.

The resource is marked blocking because the current PostgreSQL adapter uses JDBC. Do not run these endpoints on a reactive event loop without moving persistence to a non-blocking adapter.

## Security and tenancy

- Caller-supplied tenant identifiers are not accepted.
- Caller-supplied actor or permission values are not accepted.
- Party references are resolved through Ecosystem and must identify an active Party.
- Repository calls remain tenant-scoped; the HTTP boundary does not replace persistence tenant predicates or composite foreign keys.
- This initial API exposes account and inventory commands only; read/list endpoints and full account-management query models remain future work.

## Verification

The repository's dedicated `.github/workflows/advertising-capability.yml` workflow verifies the Advertising Maven reactor, including PostgreSQL Testcontainers and host integration tests, when triggered by a pull request touching Advertising or its host contracts.

No successful CI run or Maven execution is claimed until GitHub Actions reports it for this branch.
