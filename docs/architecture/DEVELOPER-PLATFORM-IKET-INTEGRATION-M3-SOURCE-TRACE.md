# M3 — Developer Registry Source Trace and Tenant-Isolation Readiness

- **Status:** Source audit; implementation intentionally gated on security and persistence prerequisites.
- **Repository:** \`andalus-ai/numu\`
- **Depends on:** M0 integration boundary, M1 source audit, M2 registry contract.
- **Scope inspected:** Identity application services and ports/tests, Tenancy reactor metadata, contributor guardrails.
- **Important:** This is a bounded source trace, not a complete security audit of every adapter, HTTP resource, database schema or runtime composition.

## 1. Executive decision

Do not implement the developer application registry on top of the currently inspected service-account or PAT application APIs as if they already provide tenant isolation.

The source currently shows:

1. Identity has a real \`ResourceAuthorizationService\` that accepts a principal, tenant ID and permission requirement. It can be reused as an authorization mechanism only after its policy configuration and fail-closed behavior are explicitly selected for the developer control plane.
2. The inspected \`ServiceAccountService\` and its repository port expose global-looking create/find/list operations without tenant context in their method contracts.
3. The inspected \`PersonalAccessTokenService\` accepts request data without authenticated actor/tenant context for issuance; owned-token revocation compares the actor to the token principal, but does not establish tenant authorization. Principal listing and repository lookup are not tenant-scoped in their signatures.
4. The Tenancy adapter module's POM describes in-memory repository/provisioner implementations for development and tests. The inspected metadata does not establish a production persistence adapter or a production-ready tenant-resolution/membership boundary.
5. The identity tests exercise these global service APIs using in-memory repositories and a passthrough unit of work. They do not establish multi-tenant isolation, database uniqueness races, transactional rollback or cross-tenant denial.

Therefore M3 must be split into a tenant/security foundation hardening vertical slice followed by the developer registry. Adding a new registry aggregate now would risk institutionalizing cross-tenant ambiguity.

## 2. Exact source observations

### 2.1 Authorization service exists, but policy default is configurable and backwards-compatible ALLOW

File:
\`identity/numou-identity-application/src/main/java/tech/kayys/numou/identity/application/service/ResourceAuthorizationService.java\`

Observed behavior:

- Rejects null/unauthenticated principal and blank tenant ID.
- Calls \`AuthorizationPort.isAllowed(principal, tenantId, requirement)\` as an RBAC gate.
- Resolves resource attributes and loads policies by tenant and permission.
- Rejects mismatched tenant/permission in the resolved authorization context.
- Deny policy matches override allow policy matches.
- The four-argument constructor defaults ABAC behavior to \`AbacDefaultEffect.ALLOW\` when no policies match. The source comments explicitly describe this as backwards-compatible behavior.

Implication:

- The developer control plane must not silently inherit an unspecified default. It must explicitly configure strict behavior or prove that RBAC alone is the complete authorization policy for the protected operation.
- A missing policy configuration, policy repository failure, attribute-resolution failure or cache uncertainty must not be converted into an allow result by an adapter or HTTP layer.
- This observation is not a claim that RBAC itself is fail-open; the inspected service uses RBAC as the first gate.

### 2.2 Service-account application API has no tenant or actor context

Files:

- \`identity/numou-identity-application/src/main/java/tech/kayys/numou/identity/application/service/ServiceAccountService.java\`
- \`identity/numou-identity-application/src/main/java/tech/kayys/numou/identity/application/port/ServiceAccountRepository.java\`
- \`identity/numou-identity-application/src/main/java/tech/kayys/numou/identity/application/dto/CreateServiceAccountRequest.java\`
- \`identity/numou-identity-application/src/test/java/tech/kayys/numou/identity/application/service/ServiceAccountServiceTest.java\`

Observed method/port shapes:

- \`create(CreateServiceAccountRequest)\`; request fields are \`name\`, \`description\`, \`ownerUserId\`.
- \`disable(UUID)\` and \`disable(UUID, UUID actorId)\`.
- \`listAll()\`.
- Repository methods: \`findById(id)\`, \`findByName(name)\`, \`existsByName(name)\`, \`findAll()\`, \`save(serviceAccount)\`.
- Creation checks name existence without a tenant key and creates the aggregate with a null tenant argument.
- The service-account test uses an in-memory map keyed only by service-account ID and tests global duplicate names and list-all behavior.
- The outbox call is made with a null tenant argument in the inspected service.

Implication:

- This API is not an acceptable developer application registry repository contract.
- If service accounts are later needed as machine principals, their tenant ownership and authorization semantics must be designed explicitly. A service account is not automatically equivalent to a developer application.
- Do not add tenant scoping only in the REST resource; the application port, persistence query, uniqueness constraint, audit and outbox context must all enforce it.

### 2.3 PAT service has principal ownership checks but no tenant-aware application contract

Files:

- \`identity/numou-identity-application/src/main/java/tech/kayys/numou/identity/application/service/PersonalAccessTokenService.java\`
- \`identity/numou-identity-application/src/main/java/tech/kayys/numou/identity/application/port/PersonalAccessTokenRepository.java\`
- \`identity/numou-identity-application/src/test/java/tech/kayys/numou/identity/application/service/PersonalAccessTokenServiceTest.java\`

Observed method/port shapes:

- \`issue(CreatePatRequest)\` accepts principal type/ID, label and expiry in the request; no authenticated actor or tenant context is part of the method signature.
- The service generates a random \`pat_\` secret, stores its SHA-256 hash and returns the raw token in the one-time creation response.
- \`revokeOwned(tokenId, actorId)\` checks that the actor ID equals the PAT principal ID, then persists revocation and appends an outbox event.
- Repository methods include \`findById(id)\`, \`findByTokenHash(hash)\` and \`findByPrincipal(type, principalId)\`; the latter is not tenant-scoped.
- The inspected issuance and revocation paths pass null as the tenant argument to the identity outbox port.
- Tests cover issuance/validation, principal ownership on revoke, listing by principal and cache invalidation. They use an in-memory repository and passthrough unit of work.

Implication:

- Hash-backed, one-time secret issuance is a useful primitive, but the service must not be treated as a tenant-scoped developer credential manager without further work.
- Token validation by hash may remain a global lookup if the hash is globally unique and the resulting principal/tenant context is independently resolved and validated before authorization. Listing, issuance, revocation and audit must carry trusted tenant/actor context.
- The registry must not issue PATs as application credentials. Developer application credentials need a distinct, least-privilege machine-client lifecycle and Vault-backed secret handling.

### 2.4 Tenancy adapter metadata does not establish production persistence

File:
\`tenancy/tenancy-adapter/pom.xml\`

The module description states that it contains in-memory adapter implementations (repositories and provisioners) for development and test. The tenancy aggregator and domain POM describe tenant lifecycle, membership, settings and provisioning aggregates, but this inspected metadata alone does not prove a production persistence adapter, transactional membership query or trusted request tenant resolver.

Implication:

- Before implementing the registry, inspect and identify actual production tenancy persistence, membership authorization, tenant-context propagation and runtime wiring.
- If production adapters are absent, the prerequisite milestone must deliver them or explicitly select an already-existing production-grade tenancy adapter elsewhere in the repository. Do not put developer registry records in an in-memory adapter or infer that the domain model alone provides tenant isolation.

### 2.5 Contributor rules reinforce the implementation gate

Root \`AGENTS.md\` requires actual module/source/test inspection, explicit capability ownership, tenant isolation at application boundaries, durable side-effect handling and honest verification reporting. It also says not to create PRs without explicit user approval. The user's repeated “ok go next” instruction is treated as approval for this next milestone only; no PR is merged automatically.

## 3. What can be reused, and what cannot

| Existing capability | Reuse decision | Required qualification |
|---|---|---|
| Identity Principal | Reuse as authenticated subject representation | Resolve from trusted authentication middleware; do not construct from client JSON |
| AuthorizationPort / ResourceAuthorizationService | Reuse the authorization boundary | Confirm permission catalog, tenant membership semantics, strict policy configuration and failure behavior |
| Tenancy Tenant / TenantMembership domain | Reuse as tenant and membership authority | Verify concrete public application ports, production persistence, membership status and tenant resolver |
| ServiceAccount aggregate/service | Do not use as Developer Application | It represents a machine principal with different lifecycle; current service/port APIs lack tenant scope |
| PersonalAccessToken aggregate/service | Reuse only as a user/service token primitive after hardening | PAT is not an application credential; issue/list/revoke/audit require trusted actor and tenant context |
| Identity outbox port | Potentially reuse | Verify durable adapter, tenant/correlation propagation, transaction enlistment and failure behavior |
| Gamelan | Potential workflow orchestrator later | Verify runtime/contract compatibility; never use it to compensate for missing authorization or durable state |

## 4. Required M3A prerequisite — tenant-safe machine identity boundary

Before the developer registry vertical slice, implement and test the following in the owning Identity/Tenancy capabilities, after tracing concrete adapters and runtime composition:

1. Define a trusted tenant execution context derived from authenticated request/service identity plus verified membership. A client-provided tenant ID is only a selector and must be checked against this context.
2. Add explicit actor, tenant, correlation and idempotency context to application commands that mutate service accounts and PATs. Keep compatibility overloads only if they cannot bypass authorization and are not exposed to untrusted adapters.
3. Make service-account repository operations tenant-scoped: lookup by (tenantId, id), name existence by (tenantId, normalizedName), and bounded deterministic listing by tenant.
4. Enforce tenant-local uniqueness in the production database, not just a preflight existence check.
5. Add an authorization check before service-account create/read/update/disable/list operations. Verify membership/permission using the existing authorization boundary.
6. Scope PAT issuance, listing and revocation to the authenticated owner/service principal and tenant. Do not weaken globally unique token-hash validation; instead bind the validated token to a stable principal and resolve its allowed tenant context before protected actions.
7. Make outbox events carry the real tenant and actor/correlation metadata. Do not append tenant-owned events with null tenant context.
8. Define cache invalidation/revocation semantics across instances. A local L1 cache must not allow a revoked credential to remain usable beyond the documented bounded revocation window.
9. Add integration tests with two tenants and two principals for same-name service accounts, cross-tenant read/disable/list attempts, PAT ownership and tenant isolation, revocation propagation, duplicate concurrent registration, rollback and durable outbox delivery.
10. Verify production tenancy persistence and request-context wiring; in-memory adapters remain test/dev only.

This work must preserve existing user PAT behavior through explicit compatibility and migration tests. It must not silently change global role semantics or the authorization contract for unrelated identity consumers.

## 5. Developer registry implementation gate (M3B)

Only after M3A evidence is complete:

- Select the owning module and public application ports from the real source tree; do not create a new Maven reactor based only on the proposed architecture.
- Add a tenant-scoped application aggregate and environment model with immutable owner tenant, revisioned lifecycle and no secret material.
- Implement register, get/list, profile update and lifecycle commands with authorization-before-read, durable idempotency, optimistic concurrency and audit/outbox.
- Add database tenant-local slug uniqueness and repository APIs that require tenant scope.
- Add negative cross-tenant tests at both application and persistence boundaries.
- Keep API products, scopes, subscriptions, credentials, Vault references and Iket bindings out of the first registry vertical slice.

## 6. Open questions requiring source evidence, not assumptions

- Where is the production tenant repository/persistence adapter, if one exists?
- Which application service resolves and verifies tenant membership for a request?
- Does the runtime inject a trusted tenant context into Identity services, and which HTTP/gRPC adapters enforce it?
- What is the canonical permission catalog and how are tenant roles mapped to the existing RBAC gate?
- Which identity outbox adapter durably persists tenant-scoped events in the same transaction as the aggregate?
- Which current module owns API client/application registration, if any, beyond service accounts and PATs?
- Which persistence constraints guarantee global PAT hash uniqueness and tenant-local service-account naming?

These questions must be answered from implementation and integration tests before code is selected.

## 7. Verification status

- Inspected source files and tests listed above on the repository's \`main\` branch.
- No code changed in this milestone.
- No tests were run; this milestone is a source audit.
- This audit does not certify the complete Identity or Tenancy subsystem and does not claim every inbound adapter is exposed or vulnerable.
- The current findings are sufficient to block implementing a developer application registry that assumes tenant-safe service-account/PAT APIs.

## 8. Next milestone

**M3A — Tenant-safe Identity/Tenancy application boundary**

Trace the actual tenancy persistence and request-context wiring, then harden the tenant-scoped machine identity/service-account contract with cross-tenant integration tests. Once that foundation is verified, proceed to M3B registry implementation. Iket S1 remains a separate independent-project security milestone; no gateway provisioning should be enabled before it is complete.
