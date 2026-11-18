# Developer Platform — M0 Architecture and Iket Integration

- **Status:** M0 architecture proposal; no runtime integration is implemented by this document.
- **Repository baseline:** `andalus-ai/numu`, `main`, inspected 2026-11-03.
- **Gateway project:** [andalus-ai/iket](https://github.com/andalus-ai/iket) (module path currently declares `github.com/bhangun/iket`).
- **Related:** [AGENTS.md](../../AGENTS.md), [Application Platform M1 foundation](NUMOU-APPLICATION-PLATFORM-M1-FOUNDATION.md), [Application Platform M2 descriptor/Gamelan contract](NUMOU-APPLICATION-PLATFORM-M2-DESCRIPTOR-GAMELAN-CONTRACT.md), [ADR-003](ADR-003-reusable-capability-modules.md).

## 1. Decision

Build a first-class Numou Developer Platform for internal developers, external developers, implementation partners, and enterprise integration teams.

Integrate Iket as the API gateway and edge-management product. Numou remains the authority for developer identity, tenant membership, application registration, API product/plan definitions, authorization policy, credential lifecycle, usage entitlements, and commercial metering. Iket owns gateway runtime concerns: listener and route configuration, upstream routing, traffic enforcement at the edge where supported, gateway operations, and gateway-local telemetry.

This is a product boundary and integration direction, not a claim that either repository already implements the complete contract.

## 2. Repository evidence and limits

### Numou

The inspected `main` branch establishes:

- `AGENTS.md` requires capability ownership, explicit contracts, tenant-aware authorization, audit/idempotency, and verified implementation claims.
- `identity`, `tenancy`, and `security` are separate Maven capability aggregators.
- `security` already composes security domain/SPI/application/adapter and Vault domain/SPI/application/Postgres/crypto modules.
- The Application Platform M1 document identifies identity, tenancy, security, Vault, audit, events and runtime composition as existing authorities to reuse, and explicitly warns that the audit is not a complete dependency/source/runtime inventory.
- The Application Platform M2 document describes a draft descriptor and compatibility contract; it does not implement an application installer or production Gamelan integration.

No dedicated Developer Platform implementation was verified in this M0 pass. This is not a full repository-wide source, API, schema, or dependency audit.

### Iket

The inspected repository README and `AGENT.md` describe Iket as a Go API gateway with:

- Route matching and upstream path rewriting.
- A management API for configuration, services, routes, plugins, logs, certificates, proposals and backups.
- mTLS-secured remote administration.
- File-backed and database-backed configuration.
- CLI and operator workflows.
- Plugin support and rollout tooling.

Its `go.mod` declares module path `github.com/bhangun/iket` and Go 1.26. Its README links to API-key-management and API-reference documentation. The exact runtime semantics, supported authentication modes, quota/rate-limit behavior, analytics export contract, and API-key storage/rotation implementation still require a focused source-level audit before they are treated as integration guarantees.

## 3. Ownership boundaries

| Concern | Authority | Integration rule |
|---|---|---|
| Developer principal and login | Numou Identity / host identity | Do not create a second developer identity store in Iket |
| Tenant and organization membership | Numou Tenancy / Organization contracts | Gateway tenant context must be derived from trusted authentication/policy, never an arbitrary client header |
| Actor/resource authorization | Numou Security | Gateway checks are an edge layer; capability endpoints remain authoritative and re-authorize |
| Developer application registration and lifecycle | Numou Developer Platform | Stable application ID and lifecycle state |
| API products, versions, scopes and plans | Numou Developer Platform | Versioned catalog and policy contracts |
| Client credentials and OAuth registrations | Numou Developer Platform using Identity/Security contracts | Persist only hashes or protected references as appropriate; secret material is revealed only at issuance |
| Secret storage and rotation | Numou Security Vault | Iket receives short-lived configuration or secret references resolved through a controlled adapter; never share Vault tables |
| Routes, upstreams and gateway runtime | Iket | Managed through a versioned, authenticated control-plane adapter |
| Request enforcement | Iket plus Numou Security policy contract | Enforce only policy semantics that are explicitly supported and tested |
| API usage aggregation and billing | Numou | Consume authenticated gateway usage telemetry; billing is not calculated from untrusted client reports |
| Webhook events and delivery lifecycle | Numou event/outbox capabilities | Gateway is not the owner of business webhook delivery state |
| Workflow orchestration | Gamelan via Numou workflow contract | Use for approval/provisioning/rotation workflows only after real Gamelan adapter/runtime compatibility is verified |

## 4. Target architecture

```text
Developer / Partner / Enterprise Admin
                 |
          Numou Developer Portal
                 |
       Numou Developer Platform API
                 |
   +-------------+-------------------------+
   |             |                         |
Identity /   Developer App & API       API Product /
Tenancy /    Registry / Credential     Plan / Usage
Security     Lifecycle / Policy        Entitlement
   |             |                         |
   +-------------+-------------------------+
                 |
       Gateway Control-Plane Port
                 |
       Iket Integration Adapter
                 |
        Iket Management API
                 |
       Iket Gateway Data Plane
                 |
       Numou Capability APIs
                 |
     Capability-level authorization
```

The adapter is a replaceable integration boundary. Numou must not import Iket Go packages, depend on Iket persistence, or share gateway database tables. A remote Iket deployment and an in-process/test adapter must implement the same Numou-owned port.

## 5. Integration contract requirements

Before enabling production provisioning, define and version a gateway control-plane contract with at least:

- **Gateway instance identity:** stable ID, environment, region, deployment profile and supported contract version.
- **Desired route state:** application/API product reference, API version, upstream service reference, route/method policy, enabled state and configuration revision.
- **Security policy reference:** issuer/audience or trusted policy reference, allowed scopes/claims, tenant-context mapping, and explicit authentication mode. Never put raw signing keys or long-lived client secrets in route manifests.
- **Traffic policy:** rate/burst/concurrency/body-size/timeouts only where Iket proves support; distinguish per-client, per-application, per-tenant and global dimensions.
- **Reconciliation:** idempotent create/update/delete, optimistic revision checks, drift detection, retryable vs terminal errors, and observable last-applied revision.
- **Telemetry:** request timestamp, gateway/route/application IDs, authenticated principal/client reference, tenant reference from trusted context, status class, latency, bytes and correlation ID. Exclude credentials, authorization headers, payloads and sensitive query values.
- **Health and compatibility:** gateway version, contract version, capabilities, readiness and last successful reconciliation.
- **Audit:** actor, tenant, request/correlation ID, desired-state revision and outcome for every administrative change.

Use explicit request/response schemas and compatibility tests. Do not infer that an Iket management endpoint exists or has stable semantics until verified against its source and API specification.

## 6. Security invariants

1. The public gateway management API is never exposed as the developer-facing API.
2. Iket remote administration remains protected by mTLS and a restricted network/control-plane path.
3. Developer credentials are distinct from gateway operator credentials.
4. Client secrets are shown once at creation/rotation, never logged, and never stored in plaintext.
5. JWT validation, API-key validation, scopes and tenant context have one documented source of truth per authentication mode.
6. A caller cannot choose or override its tenant by sending a header or query parameter.
7. Gateway authentication does not replace authorization in Numou capability APIs.
8. Route changes are tenant/application scoped, authorized, audited, revisioned and idempotent.
9. Emergency credential revocation and route disablement have bounded propagation objectives and observable completion.
10. Telemetry is minimized, access-controlled, tenant-scoped and retention-governed.

## 7. Developer Platform capability slices

Avoid a single oversized developer module. Establish these ownership slices only after source tracing:

- Developer organization and membership integration.
- Application registry and lifecycle.
- API catalog, product versions and declared scopes.
- Credential/OAuth client registration and revocation.
- Gateway registration, route provisioning and reconciliation.
- Webhook subscription and delivery management using existing event/outbox capabilities.
- Usage ingestion, aggregation, quotas and billing integration.
- Documentation/OpenAPI publication, SDK generation and compatibility checks.
- Sandbox environment provisioning and test-data isolation.
- Marketplace publication/distribution integration.

The exact Maven artifact boundaries are deferred until M1 inspects existing package conventions and contracts. Do not create speculative modules just to match this list.

## 8. Milestones

| Milestone | Outcome |
|---|---|
| M0 | Architecture and Iket integration contract proposal (this document) |
| M1 | Source-level inventory of Numou identity/security/Vault/API/runtime and Iket management/auth/config/telemetry contracts |
| M2 | Developer organization and application registry contracts |
| M3 | Credential/OAuth lifecycle integrated with Numou Identity/Security/Vault |
| M4 | Iket gateway registration and idempotent route reconciliation |
| M5 | API catalog, OpenAPI publication and developer documentation |
| M6 | Webhook subscription and reliable delivery integration |
| M7 | Usage ingestion, quotas, analytics and billing integration |
| M8 | Isolated sandbox and test credentials |
| M9 | SDK generation, marketplace and partner onboarding |
| M10 | Enterprise operations, audit, SLOs and production hardening |

M1 must produce a verified source map and test/build commands before runtime dependencies or schema migrations are introduced.

## 9. M0 acceptance and known gaps

- [x] Confirm Numou identity, tenancy, security and Vault aggregators exist.
- [x] Read Numou agent guidance and application-platform architecture baselines.
- [x] Confirm Iket's documented role as a Go API gateway and remote administration product.
- [x] Establish ownership boundaries and a replaceable gateway adapter direction.
- [ ] Trace Iket authentication/API-key implementation and exact management API schema.
- [ ] Trace Numou identity token issuance/validation, authorization, Vault secret-reference and API runtime composition.
- [ ] Verify gateway telemetry and rate-limit capabilities from code and tests.
- [ ] Define contract schemas and compatibility/contract tests.
- [ ] Run affected repository test suites after implementation begins.

No production integration, code compilation, or runtime tests are claimed by this architecture-only milestone.
