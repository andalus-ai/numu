# M5b — Developer API Catalog Application Services

## Status
Application-layer use cases and ports are implemented as a follow-on to M5. PostgreSQL adapters, HTTP resources, OpenAPI parsing, and concrete authorization policy wiring are deliberately deferred.

## Module
`developer-platform/developer-api-catalog-application`

Depends on `numou-developer-api-catalog-domain` and `numou-foundation-application`.
The module does not depend on Quarkus, Hibernate, a database driver, Iket, or Gamelan.

## Use cases
- Create a tenant-owned API product.
- Add a draft semantic API version.
- Publish a draft API version.
- Deprecate a published API version.
- Retire a deprecated API version.
- Get an API product by ID.
- List API products with bounded shared PageRequest pagination.

## Ports
- ApiProductRepository: aggregate-specific persistence contract. Every operation receives provider TenantId. Save receives expected revision; create uses expected revision zero.
- ApiCatalogAuthorizationPort: separate read/manage authorization boundary, supplied with trusted ExecutionContext and tenant.
- Shared ExecutionContextProvider: tenant and operation time come from trusted request execution context, never command payloads.
- Shared UnitOfWork and EventPublisher: mutations execute inside transaction boundary; product creation and version publication events are published within that boundary. Concrete event adapter must use the same transaction/outbox where supported.

## Security and concurrency requirements for adapters
1. Repository SQL must include tenant ID in every lookup, update, delete, and uniqueness predicate.
2. save(tenant, aggregate, expectedRevision) must enforce optimistic concurrency and fail on stale revision; it must not silently overwrite.
3. Enforce a database unique constraint on (provider_tenant_id, slug); preflight lookup is only a friendly error, not a race-safe uniqueness guarantee.
4. Authorization must run before catalog data is read or changed.
5. Missing tenant context fails closed through ExecutionContext.requireTenant().
6. Authorization implementations derive actor identity from trusted execution context; do not accept actor or tenant IDs from request DTOs.
7. Event publication must be atomic with persistence through transaction/outbox adapter.
8. OpenAPI artifact verification and semantic parsing are delegated to ApiSpecificationValidationPort. PublishApiVersionCommandHandler requires this port and refuses publication unless tenant-owned artifact integrity and OpenAPI semantic validation succeed.

## Read model
ApiProductView exposes catalog metadata, version status, OpenAPI artifact pointer/digest, and declared scope vocabulary. It does not expose secrets, OAuth credentials, gateway runtime state, or provider internals.

## Verification
- [x] Reactive CQRS command/query contracts.
- [x] Tenant context derived from ExecutionContextProvider.
- [x] Separate read/manage authorization port.
- [x] Tenant-scoped repository contract and expected-revision write contract.
- [x] Transactional create/publish orchestration and event publication.
- [x] Create/list/get/add/publish/deprecate/retire use cases.
- [x] Create-handler unit test for tenant propagation and event publication.
- [x] Required OpenAPI validation port invoked before publication.
- [ ] Run Maven reactor build and ArchUnit/unit tests in CI.
- [ ] Implement PostgreSQL repository, unique constraints and optimistic-lock mapping.
- [ ] Implement concrete authorization policy adapter and transactional outbox integration.
- [ ] Implement artifact-store and OpenAPI parser adapter for ApiSpecificationValidationPort.