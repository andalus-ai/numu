# Cargo & Courier — Milestone 7: Network Partners and Integrations

## Scope
M7 introduces tenant-owned partner directory entries, versioned partner lane/service agreements, and webhook endpoint metadata for external integrations.

## Partner network
- A partner has a tenant-local unique code, legal name, ISO alpha-2 country code and explicit onboarding/active/suspended/terminated status.
- Agreements bind a partner to a service level, origin/destination zones and an effective window. Only ACTIVE agreements within their effective window are usable.
- Partner and agreement persistence uses composite tenant foreign keys to prevent cross-tenant references.

## Integration security
- Endpoints require absolute HTTPS targets and tenant-local unique codes.
- Event types are allowlisted per endpoint; status is explicit.
- Credentials and certificates are not stored in this schema. `secretReference` points to an external secrets manager or certificate store.
- Delivery workers must implement request signing, bounded retries, timeouts, SSRF protections (including DNS/IP validation and redirect restrictions), endpoint health/circuit breaking, and per-tenant quotas before enabling arbitrary destinations.

## Production follow-up
- Add tenant-explicit repositories and application handlers with actor/branch authorization.
- Add transactional outbox dispatch, delivery-attempt history, idempotency/deduplication, backoff and dead-letter handling.
- Enforce non-overlapping ACTIVE agreements per partner/lane/service in PostgreSQL.
- Run Maven domain tests and PostgreSQL migration/Testcontainers suites; they have not been executed in this environment.
