# Developer Platform M1 — Source-Level Integration Compatibility Audit

- **Status:** targeted source-level audit snapshot; findings are scoped to inspected files on each repository's `main` branch.
- **Date:** 2026-11-03.
- **Repositories:** `andalus-ai/numu`, `andalus-ai/iket`, `andalus-ai/gamelan`.
- **Predecessor:** M0 architecture and Iket integration boundary, merged as Numou PR #1648 (merge commit `65904047be7357b43dda83d3537e326242bcf1c6`).
- **Scope warning:** this is not a complete line-by-line review, runtime penetration test, or proof that every component is wired into a deployed environment.

## 1. Executive outcome

The repositories already contain substantial reusable capabilities. The Developer Platform should compose them rather than create parallel identity, secrets, authorization, workflow, or gateway systems.

| Area | Evidence found | M1 decision |
|---|---|---|
| Numou identity/JWT | Login/token service, JWT issuer/verifier, PAT lifecycle and REST resource | Reuse Identity; do not add a second identity store |
| Numou authorization | Authorization ports/policies, role and scoped-role persistence, verified actor filter | Reuse Security/Identity; define explicit app/client principal contract |
| Numou Vault | Tenant-scoped secret service, immutable encrypted versions, lifecycle/revocation, audit outbox ports | Reuse Vault; add adapter only where required |
| Iket API keys | API-key plugin docs, typed API-key client context, scopes/groups, client configuration | Config/plugin-oriented model; dynamic tenant-aware credential authority not yet proven |
| Iket management | REST API for gateway, services, routes, plugins, certificates, logs and metrics; documented mTLS + Basic Auth | Candidate control plane; hardening and stable contract required |
| Iket traffic limits | Route/service policy and limiter configuration/management handlers | Capabilities exist; exact semantics need verification |
| Iket telemetry | Gateway metrics endpoints and WebSocket metrics/log streams | Define export/aggregation contract; transient dashboard counters are not billing evidence |
| Gamelan workflow | WorkflowRunManager SPI, tenant-aware APIs, retry/DLQ, recovery leases, wakeup outbox/audit | Suitable orchestration engine through replaceable Numou adapter |
| Tenant safety | Numou tenant-aware patterns; Gamelan JWT and header filters | Critical trust-boundary mismatch identified; fix before integration |

## 2. Numou findings

### 2.1 Identity and tokens

Inspected:
- `identity/numou-identity-adapter/.../AuthResource.java`
- `identity/numou-identity-adapter/.../SmallRyeJwtTokenAdapter.java`
- `identity/numou-identity-adapter/.../PersonalAccessTokenResource.java`
- `identity/numou-identity-adapter/.../PostgresPersonalAccessTokenRepository.java`
- `identity/numou-identity-application/.../AuthenticationPort.java`

Verified source behavior:
- `POST /api/v1/auth/login` delegates local credential exchange to `TokenService`.
- JWT issuer sets issuer, subject, UPN, `principalType`, empty groups and configured expiry (default 900 seconds).
- JWT verifier parses token and extracts subject/principal type/display name.
- PAT management supports issue, revoke and list; raw token is returned once and list responses are metadata-only.
- PAT persistence stores token hash, display prefix, status, timestamps and audit actor fields.
- PAT resource requires `identity.pat.manage` and restricts issue/list to the authenticated USER principal.

Decision:
- Reuse Numou Identity for developer sign-in and human principals.
- PAT is not automatically equivalent to OAuth2 client credentials: current resource is principal-owned and USER-focused.
- No complete OAuth2 authorization-server/client-registration contract was established by inspected files. Do not advertise authorization-code/PKCE or client-credentials as already implemented without further source/runtime verification.
- Verify issuer keys, key rotation, JWKS/discovery, audience validation, revocation strategy and service-account token semantics before Iket consumes Numou-issued tokens.

### 2.2 Actor and tenant context

Inspected `VerifiedActorFilter.java`, foundation `TenantContext.java`, and tenant-aware repository patterns.

Verified:
- `VerifiedActorFilter` rejects anonymous API v1 requests (with login/federation exceptions), derives actor from authenticated principal subject, rejects a mismatching supplied `X-Actor-Id`, and writes the canonical actor.
- Numou has tenant-aware foundation context/repository contracts and capability-specific tenant-isolation tests.

Decision:
- Actor and tenant are separate concepts; never use actor ID as tenant ID.
- Gateway-to-service context must come from verified identity or trusted network-bound assertion, never a freely client-controlled `X-Tenant-ID`.
- Capability APIs remain responsible for authorization.

### 2.3 Vault

Inspected `VaultSecretService.java`, `EncryptedSecret.java`, `SecretLifecyclePort.java`, and Vault Postgres/crypto/audit packages.

Verified:
- Vault service is tenant-scoped and authorizes secret operations.
- Plaintext enters as `char[]`; persisted domain model is encrypted-only and ciphertext accessors defensively copy.
- Rotation appends immutable versions with compare-and-append concurrency control.
- Lifecycle supports status and idempotent revoke; source comments require revocation to prevent subsequent reveal.
- Vault has encryption/key-encryption ports, Postgres adapters, migrations for secret versions and audit outbox/leases, and an AES-GCM envelope adapter.

Decision:
- Use Vault for OAuth client secrets, webhook signing secrets and gateway administrative credentials where authorization permits.
- Store only Vault references and metadata in Developer Platform records.
- Never put long-lived raw secret material in route manifests or logs.
- Verify reveal API, KMS/key-management boundary, tenant/vault ownership checks and rotation/revocation propagation before defining Iket secret delivery.

## 3. Iket findings

### 3.1 API key model

Inspected `docs/API_KEY_MANAGEMENT.md`, `pkg/core/authcontext/api_key.go`, `pkg/core/authcontext/principal.go`, and `pkg/config/security_config.go`.

Verified:
- API-key plugin docs describe `X-API-Key` and query-parameter authentication, client groups and scopes.
- Client definitions are documented in gateway configuration with client ID, name, key, group and scopes.
- Authenticated API-key client metadata is attached to Go request context using a typed context key (legacy keys are retained).
- Normalized principal context can expose client ID, issuer, audience, expiry, roles, groups and scopes.

Risks/decisions:
- Disable query-string API keys in production: URLs leak into logs, browser history, referrers and observability systems.
- Static configuration is not evidence of a tenant-aware, dynamically provisioned developer credential store.
- Do not duplicate Numou credential lifecycle inside Iket. Define a token/key validation contract or trusted identity assertion.
- Verify hashed-at-rest support, constant-time comparison, rotation, revocation propagation and per-client tenant binding in actual plugin implementation.
- Define canonical scope mapping; Iket scopes are not automatically equivalent to Numou authorization permissions.

### 3.2 Management API and security

Inspected `pkg/api/management.go` and `docs/MANAGEMENT_API_INTEGRATION.md`.

Verified:
- Management routes cover gateway status/config/reload/metrics, services, routes, plugins, logs, WebSockets and certificates.
- Documentation describes management access protected by mTLS and HTTP Basic Authentication.
- Management API constructor configures WebSocket upgrader `CheckOrigin` to return true.
- Management API CORS middleware sets wildcard `Access-Control-Allow-Origin: *`.

Decision:
- Treat management API as privileged operator control plane, never as developer-facing public API.
- Verify actual server wiring enforces mTLS and Basic Auth on every HTTP and WebSocket management route before remote provisioning.
- Restrict CORS to explicit trusted operator origins (or disable browser CORS where unnecessary); CORS is not authorization.
- Replace permissive WebSocket origin acceptance with explicit policy or non-browser-only access.
- Require TLS/mTLS, least-privilege operator identity, network restrictions, audit and replay-safe idempotent mutations.
- Use a dedicated service account for Numou adapter, not shared human Basic Auth credentials.

### 3.3 Routes, limits and telemetry

Verified from management route registration and repository tree:
- Route/service CRUD and enable/disable endpoints exist.
- Gateway/system metrics endpoints and real-time metrics/log WebSocket endpoints exist.
- Limiter configuration, route policy and management handlers exist in source tree.

Not yet proven:
- Stable route schema/IDs, ETag or revision support, optimistic concurrency.
- Idempotency of create/update/delete.
- Exact rate-limit algorithm, storage backend, distributed consistency, key dimensions and failure mode.
- Durable, loss-accounted request telemetry suitable for commercial billing.
- Whether tenant/application identity is authenticated and trustworthy where metrics are recorded.

Decision:
- Iket management and metrics interfaces are candidate integration surfaces, not yet a production-ready contract.
- Billing must use authenticated, deduplicated, attributable telemetry with retention/correction rules, not transient dashboard counters.

## 4. Gamelan findings

Inspected:
- `framework/gamelan-engine-spi/.../WorkflowRunManager.java`
- `runtime/gamelan-engine/.../JwtTenantFilter.java`
- `runtime/gamelan-engine/.../TenantHeaderFilter.java`
- `runtime/gamelan-engine/.../WorkflowRecoveryService.java`
- `runtime/gamelan-engine/.../PostgresWorkflowRecoveryLeaseRepository.java`
- Workflow recovery, retry, wakeup outbox and dead-letter/audit source/test tree.

Verified:
- WorkflowRunManager exposes tenant-aware lifecycle/query operations and embeds tenant in create requests.
- Gamelan has JWT tenant extraction, tenant header extraction, retry/DLQ, recovery leases, recovery service, wakeup outbox and dead-letter audit.
- Postgres recovery lease uses atomic upsert/expiry ownership semantics.
- Recovery service has bounded scan/concurrency controls, replay consistency modes and optional distributed lease.

### Critical tenant-context finding

`JwtTenantFilter` runs at priority `AUTHENTICATION + 1` and sets tenant from JWT claim `tenant_id`. `TenantHeaderFilter` runs at `AUTHENTICATION + 2` and unconditionally sets tenant from caller-supplied `X-Tenant-ID` when present. The later header filter can overwrite the JWT-derived tenant context.

This is a tenant-confusion risk if callers can choose that header. It must be treated as a security blocker until verified otherwise.

Required follow-up:
1. Define trusted identity propagation.
2. Reject or ignore external tenant headers unless a trusted gateway assertion is verified.
3. Make JWT-derived tenant authoritative, or validate override against authenticated principal membership.
4. Add tests proving spoofed `X-Tenant-ID` cannot switch workflow tenant for create/read/update/signal/recovery/admin operations.
5. Give trusted internal service-to-service execution a distinct authentication path.

Decision:
- Gamelan is a strong candidate for application approval/provisioning/revocation workflows.
- Do not connect production Developer Platform lifecycle until this trust issue is fixed and regression-tested.
- Keep workflow definitions/execution behind a Numou-owned port; Developer Domain must not depend on Gamelan implementation classes.

## 5. Compatibility matrix and gaps

| Contract | Existing capability | Gap before integration | Owner |
|---|---|---|---|
| Human developer login | Numou Identity login/JWT | Verify issuer keys, JWKS, audience, discovery and rotation | Numou |
| Machine application identity | Numou service accounts/PAT; Iket API-key plugin | Define app principal, client credentials, audience and revocation | Numou + Iket |
| API scopes | Numou RBAC/ABAC and Iket scopes | Canonical scope registry and translation contract | Numou |
| Tenant propagation | Numou tenant context; Gamelan JWT/header filters | Fix untrusted header override; gateway assertion verification | Gamelan + Iket |
| Secret management | Numou Vault | Define short-lived retrieval/reference protocol for Iket | Numou |
| Gateway management | Iket management REST API | Harden CORS/WebSocket origins; verify mTLS/auth wiring; version schema | Iket |
| Route reconciliation | Iket route/service CRUD | Revision, idempotency, drift and retry contract | Iket + Numou |
| Rate limits | Iket limiter configuration/handlers | Prove algorithms, dimensions and distributed semantics | Iket |
| Usage billing | Iket metrics endpoints | Durable authenticated event export, dedupe and corrections | Iket + Numou |
| Workflow orchestration | Gamelan SPI/runtime/recovery | Fix tenant trust boundary; define Numou adapter | Gamelan + Numou |
| Audit | Numou Identity/Vault audit/outbox; Gamelan DLQ audit | Unified correlation and actor/tenant attribution | Numou |

## 6. Proposed integration contracts

### 6.1 Gateway control-plane port (Numou-owned)

Conceptual operations:
- registerGateway(instanceDescriptor)
- getGatewayCapabilities(gatewayId)
- applyDesiredConfiguration(gatewayId, revision, desiredConfig, idempotencyKey)
- getAppliedConfiguration(gatewayId)
- disableApplicationRoutes(gatewayId, applicationId, idempotencyKey)
- getGatewayHealth(gatewayId)

Iket adapter implements this port over a versioned, mTLS-protected management contract. Mutations must be idempotent and revision-aware.

### 6.2 Identity propagation contract

For each request, define exactly one accepted authentication mode:
- Numou-issued access JWT validated by Iket using published asymmetric verification material; or
- opaque/API credential introspected or validated through an explicit service contract.

Trusted context includes stable subject/client ID, tenant ID, audience, scopes, issuer, expiry and correlation ID. Tenant ID is accepted only from verified claims or gateway-signed internal assertion. Client-provided identity headers are stripped at the edge.

### 6.3 Usage event contract

Required fields:
- globally unique event ID for deduplication
- occurred-at and gateway-received timestamps
- gateway ID, route ID, API product/version
- application/client ID and tenant ID from verified identity
- HTTP method and normalized route template
- status code/class, latency and bytes
- correlation/trace ID and schema version

Exclude raw credentials, Authorization headers, request/response bodies and sensitive query parameters. Define clock skew, duplicate handling, late-arrival window, retention and correction policy.

### 6.4 Gamelan workflow port

Numou-owned operations submit a versioned workflow command with tenant, actor, correlation ID, idempotency key and payload reference. Gamelan handles durable orchestration; business state remains owned by Numou. Workflow events are correlated and idempotently consumed.

## 7. Follow-up milestones

| Milestone | Work |
|---|---|
| M1a | Gamelan tenant-header trust fix and regression tests |
| M1b | Iket management API security hardening and operator auth contract |
| M1c | Numou token/JWKS/audience and machine-principal contract verification |
| M1d | Vault reference/reveal/rotation integration contract |
| M1e | Iket route revision/idempotency and telemetry export assessment |
| M1 exit | Compatibility tests and signed-off schemas across repositories |

M1a and M1b block remote gateway/workflow administration. M1c and M1d block production machine credentials. M1e blocks automated provisioning and billing.

## 8. Acceptance and verification limits

- [x] Inspect Numou identity token, PAT, actor verification and Vault service/domain/SPIs.
- [x] Inspect Iket API-key context, security config, management routes and integration documentation.
- [x] Inspect Gamelan tenant filters, workflow SPI, recovery lease, retry/DLQ and audit/outbox source.
- [x] Identify concrete integration blockers and ownership.
- [ ] Verify Iket management route middleware wiring and all WebSocket authentication paths.
- [ ] Add Gamelan spoofed-tenant regression tests and fix trust boundary.
- [ ] Add Iket management CORS/WebSocket origin and mTLS enforcement tests.
- [ ] Define/test token/client identity, Vault adapter and gateway control-plane schemas.
- [ ] Run relevant repository CI/test suites and attach evidence.

This audit inspected repository source and documentation through GitHub. No repository was cloned or built in this session, and no unit/integration/penetration tests were executed. Source findings are not proof of deployed configuration. Follow-up PRs must add executable regression tests and CI evidence.
