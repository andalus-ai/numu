# M5d — Developer API Catalog security and OpenAPI validation adapters

## Identity integration
- Uses the existing Identity `CurrentPrincipalPort`, `TenantMembershipPort`, and `ResourceAuthorizationService`.
- Enforces tenant membership before catalog permission evaluation.
- Requires the actor in trusted `ExecutionContext` to match the authenticated principal subject; this prevents a caller from pairing a valid token with a different actor context.
- RBAC and ABAC are evaluated through the shared identity service using `developer.api-catalog.read` and `developer.api-catalog.manage`.
- Missing principal, actor mismatch, wrong tenant context, non-membership, and denied permission all fail closed.

## OpenAPI artifact validation
- Uses the provider-neutral `DocumentStorage` SPI; no direct RustFS/S3 SDK dependency is introduced.
- Resolves only `s3://bucket/key` and `rustfs://bucket/key` artifact references into a tenant-scoped `StorageKey`.
- Enforces a configurable 10 MiB default maximum, stored length consistency, and SHA-256 integrity (including provider checksum metadata when present).
- Parses OpenAPI 3.0/3.1 content with Swagger Parser and checks the parsed document version against catalog metadata.
- External `$ref` resolution is disabled to avoid network access during publication validation.
- Blocking document reads and parser work execute on Mutiny's default worker pool.

## Reactor
The framework reactor includes the existing Identity and Documents aggregators before Developer Platform so these adapter dependencies resolve in a single Maven reactor build. No duplicate identity, tenant, document-storage, or gateway abstractions are introduced.

## Deferred
The transactional EventPublisher/outbox adapter is still a separate M5e concern. The API catalog JDBC repository owns its own transaction; do not assume the generic UnitOfWork and this JDBC connection are automatically the same transaction. Production event publication must be wired through a transaction-sharing outbox implementation before external publication workflows are enabled.
