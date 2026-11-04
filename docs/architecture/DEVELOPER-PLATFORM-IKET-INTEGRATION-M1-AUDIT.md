# Developer Platform — M1 Source Audit and Iket Integration Findings

Status: source-level audit in progress; this document records verified source observations and the integration contract. It does not claim runtime or CI verification.

## Scope and repositories

- Numou: `andalus-ai/numu`
- Iket: `bhangun/iket` (Go module declares `github.com/bhangun/iket`)
- Iket source snapshot inspected: commit `8662f6475ab495cdb1d2aaa38825095562b72761`
- Purpose: establish a safe, replaceable gateway integration for Numou Developer Platform while keeping Iket independently deployable and usable by other control planes.

## Ownership boundary

### Numou owns

- Developer identities, organizations, tenant membership and authorization.
- Application registration, ownership, lifecycle and environment separation.
- API products, versions, scopes, subscriptions, plans and entitlements.
- Issuance, storage references, rotation, expiry and revocation of developer credentials.
- Tenant-aware usage attribution, commercial metering, billing and developer-facing audit history.
- Business webhook registration, delivery policy and developer portal experience.

### Iket owns

- Gateway runtime, upstream/service routing, route-level policy enforcement and edge operations.
- Gateway-local configuration, certificate operations and management API.
- Gateway-local request telemetry and health signals.
- Gateway-local credential validation only where configured as an edge enforcement mechanism.

Iket must remain a standalone gateway. Numou must integrate through a versioned, documented HTTP control-plane contract and must not import Iket Go packages, share its database, or depend on its internal structs. The same contract should be implementable by other platforms and other gateways.

## Verified Numou structure

The current Maven aggregators establish these module boundaries:

- `identity/pom.xml`: `numou-identity-domain`, `numou-identity-application`, `numou-identity-adapter`.
- `tenancy/pom.xml`: `tenancy-domain`, `tenancy-spi`, `tenancy-application`, `tenancy-adapter`.
- `security/pom.xml`: security domain/SPI/application/adapter and Vault domain/SPI/application/Postgres/crypto modules.
- `identity/numou-identity-application/pom.xml` describes CQRS identity use cases and `UserRepository` / `PasswordHasher` ports. It also documents a cache port used for effective-permission and PAT validation hot paths.

This confirms that Numou has the appropriate bounded-context homes to reuse. The POM descriptions alone do not prove token issuance, PAT semantics, tenant resolution, authorization evaluation, or Vault secret-reference behavior. Those concrete implementations and tests remain to be traced before wiring credentials.

## Verified Iket implementation observations

### Authentication and principals

- `pkg/core/gateway/gateway_middleware_chain.go` installs client-credential Basic Auth only when `security.clients` is non-empty, and JWT middleware only when `security.jwt.enabled` is true.
- The same middleware chain does not install `authMiddleware`, even though `pkg/core/gateway/http_middleware.go` implements global `security.enableBasicAuth` handling. Treat global Basic Auth as unverified/inactive until call sites and runtime tests establish otherwise.
- `pkg/core/gateway/jwt_middleware.go` validates the signing method as RS256 when an RSA public key was successfully loaded, otherwise HS256. It maps claims into an Iket principal. The inspected implementation does not enforce a configured issuer or audience, and it does not require an expiration claim to be present. If RS256 public-key loading fails, it logs a warning and continues with the HS256 path; this is unsafe for a deployment configured expecting RS256 and must be changed to fail closed.
- Iket principal context is provided by `pkg/core/authcontext` and used by JWT, API-key, mTLS and Basic Auth middleware.

### API keys and usage hooks

- `pkg/plugin/apikey/apikey.go` supports key hashes, fingerprints, scopes, groups, enable/disable and usage observers.
- The client model still includes a plaintext `Key` field and the lookup path accepts plaintext-key configurations for compatibility. A Numou integration should only provision hash-backed keys and must not export raw secrets to Iket after initial issuance.
- Usage observer hooks are useful for telemetry integration, but they are not by themselves a durable delivery guarantee. The observer path has timeout/async settings and an in-memory bounded queue; it must not be treated as the billing system of record.

### Management API and transport security

- `cmd/iket/main.go` constructs `ManagementAPI`, registers its routes on the gateway router, and then initializes the gateway.
- `pkg/api/management_routing.go` registers a broad set of mutating and sensitive `/api/v1/*` routes (gateway config/reload, plugins, routes, services, clients, certificates, backups, revisions, proposals, logs and metrics). Its own CORS middleware allows all origins and does not perform authentication.
- `pkg/core/gateway/gateway_routes.go` explicitly wraps `/admin/config` and `/admin/version` in `adminAuthMiddleware`; this does not establish protection for the separate `/api/v1/*` management routes.
- The inspected `setupMiddleware` installs only configured client Basic Auth and JWT global middleware. The management routes therefore appear to depend on those optional gateway-wide settings rather than a mandatory management-specific authorization boundary. This needs an integration test and a fail-closed management-auth design.
- `pkg/core/gateway/gateway_server.go` can start a plaintext listener as well as a TLS listener when TLS uses a different port. Therefore mTLS on the TLS listener alone does not prove that management routes are unreachable over the plaintext listener.
- The management WebSocket upgrader currently accepts all origins. CORS and WebSocket origin checks are not authorization and should be configurable/restricted for operator surfaces.

### Rate limits and telemetry

- `pkg/plugin/ratelimit/ratelimit.go` implements a process-local token bucket. It is not a distributed, durable tenant or application quota service.
- Iket has route-level limit/concurrency policy mechanisms and gateway metrics. These are useful for edge protection but do not replace Numou subscription entitlement checks or financial usage ledgers.
- `pkg/core/gateway/http_middleware.go` records raw URL paths in request logs and gateway metrics. For high-cardinality and privacy-safe integration, publish normalized route identifiers rather than arbitrary paths or query strings.

## Integration decisions

1. Numou remains the authority for developer identity, tenant identity, application ownership, scopes, plans and credential lifecycle.
2. The gateway receives only short-lived, audience-restricted, signed access tokens or derived gateway credentials. Tenant identity must come from validated claims/control-plane state, never an arbitrary caller-supplied tenant header.
3. Iket must validate issuer, audience, expiry and allowed algorithms explicitly. Invalid/missing key material must fail closed; do not silently downgrade RS256 configuration to HS256.
4. Management/control-plane routes require a separate mandatory authorization boundary, independent of public API authentication. Prefer dedicated management listener/bind address plus mTLS and an explicit authenticated operator/control-plane identity. If a listener is shared, every management route and WebSocket must still be protected by a dedicated middleware.
5. Do not expose Iket's management API as the developer-facing API. Numou's adapter calls it over a private control-plane channel with mTLS, least-privilege credentials and network restrictions.
6. Reconciliation is desired-state based and idempotent: stable resource IDs, monotonic revisions/ETags, compare-before-apply, explicit conflict handling, retries for transient errors, terminal error reporting and drift detection.
7. Credential provisioning must be hash-only at the gateway where possible. Return raw credentials only once to the developer; never log them or persist them in Numou outside Vault-backed secret material.
8. Edge rate limits protect the gateway; Numou owns subscription quotas, tenant/application entitlements and billable usage.
9. Usage events need stable event IDs, application/tenant attribution from authenticated context, normalized route ID, timestamp, status class, latency and byte counts. Deliver through a durable retryable channel before using them for billing. Exclude auth headers, secrets, payloads and sensitive query values.
10. Iket's public integration contract must not contain Numou-specific domain names or assumptions. Use neutral names such as platform, tenant, application, credential reference and route binding.

## Compatibility matrix

| Capability | Iket evidence | Numou evidence | Readiness |
|---|---|---|---|
| JWT validation | Middleware exists; issuer/audience and mandatory-expiry policy absent in inspected code | Identity/security module boundaries exist; issuer/token implementation not yet traced | Blocked pending strict token contract |
| API key validation | Hash/fingerprint and client lifecycle support | PAT cache port mentioned in Identity application POM; implementation not traced | Partial; gateway must not own developer lifecycle |
| Tenant propagation | Route attribution has realm field; principal context exists | Tenancy bounded context exists; resolver implementation not traced | Blocked pending trusted claim mapping |
| Scope enforcement | Route/service scope middleware exists | Security module exists; concrete evaluator not traced | Contract needed |
| Management API | Extensive route surface; no obvious mandatory dedicated auth on registered /api/v1 routes | Numou adapter not implemented | Security blocker |
| mTLS | TLS config supports client CA and client auth mode | Vault/certificate capabilities need further tracing | Transport primitive exists; listener exposure must be verified |
| Quotas | Local/process gateway limiters exist | Commercial entitlement/metering integration not traced | Numou remains source of truth |
| Usage telemetry | API-key observer and gateway metrics exist | Audit/outbox modules require implementation tracing | Not billing-grade yet |
| Gateway provisioning | Management API has route/service/client operations | Platform app model not implemented in this milestone | Contract design next |

## Required Iket follow-up

Before production integration, Iket should receive an independent hardening milestone that:
- makes management API authentication explicit, mandatory and testable on every route and WebSocket;
- provides a dedicated management listener/bind policy or otherwise prevents management exposure on an unintended plaintext listener;
- fails closed on JWT key-loading and algorithm configuration errors;
- supports strict issuer/audience/expiry validation without forcing Numou-specific assumptions;
- makes WebSocket origin policy configurable and secure by default;
- documents the management API's supported auth modes and threat model;
- adds tests proving unauthenticated requests cannot read or mutate management resources over every listener.

These changes benefit Iket users integrating any external platform, not only Numou.

## Next milestone

M2 should define the neutral developer organization/application registry contracts and gateway control-plane API contract. In parallel, Iket should address the management-plane security blocker before Numou enables automated provisioning. Do not implement portal UI or gateway provisioning against the current unverified management security posture.

## Verification status

- Source inspection only; no repository test suite was run during this audit.
- No runtime management API exposure test was run.
- This audit is not a security certification.
