# Developer Platform M1c — Token and Machine Identity Contract

- **Status:** contract and verification gate; not an implementation claim
- **Owner:** Numou Identity and Developer Platform
- **Consumers:** Iket gateway adapter; future external API consumers
- **Predecessor:** M1 source compatibility audit
- **Scope:** define the trust contract required before Numou-issued credentials can authorize machine-to-machine gateway traffic

## 1. Purpose and non-goals

M1c closes the identity contract gap identified by the M1 source audit. It defines how a machine application is represented, how access tokens are validated, and which claims are authoritative when Iket forwards a request.

This document does not claim that Numou currently implements OAuth 2.0 client credentials, OpenID Connect discovery, JWKS publication, audience enforcement, service-account lifecycle, or token revocation. Those capabilities must be verified against the current Identity implementation and covered by executable tests before they are advertised or enabled.

This milestone does not introduce a second identity store, a parallel PAT mechanism, or an Iket-owned credential lifecycle.

## 2. Existing source evidence and limits

The M1 audit inspected Numou's login/token service, `SmallRyeJwtTokenAdapter`, `AuthResource`, `PersonalAccessTokenResource`, PAT repository, and authentication port. It recorded the following source-level behavior:

- Human login delegates credential exchange to the existing Identity token service.
- Issued JWTs include issuer, subject, UPN, `principalType`, groups, and configured expiry (default 900 seconds in the inspected configuration).
- The verifier extracts subject, principal type, and display name.
- PAT issue, revoke, and list operations exist. The raw PAT is shown once; persistence stores a hash and metadata.
- PAT management is permission-gated and the inspected resource restricts issuance/listing to a USER principal.

The audit did not establish:
- a published JWKS endpoint or OIDC discovery document;
- asymmetric signing and a documented algorithm allowlist;
- `kid` selection and safe signing-key rotation;
- strict issuer and audience validation by every resource server;
- an OAuth 2.0 client registration or client-credentials grant;
- a dedicated machine/service principal lifecycle;
- immediate JWT revocation or a documented bounded-revocation strategy.

These are explicit verification gaps, not assumed features.

## 3. Principal taxonomy

Identity must distinguish the following principal classes:

| Principal | Meaning | Credential lifecycle |
|---|---|---|
| USER | Human user acting interactively | Existing login/session and human PAT policy |
| SERVICE | First-class non-human workload identity | Managed by Identity; narrowly scoped, auditable, revocable |
| APPLICATION | Developer-owned application registered in Developer Platform | Application record references one or more SERVICE credentials; tenant ownership and scopes are explicit |
| OPERATOR | Privileged human or workload administering infrastructure | Separate operator-plane policy; not a public developer credential |

A PAT issued to a USER is not a SERVICE credential and must not be silently accepted as a client-credentials token. A Developer Platform application is a business/resource record, not itself proof of authentication.

The stable machine subject must be an opaque, immutable Identity identifier. Display names, email addresses, tenant names, and caller-provided headers are not identifiers.

## 4. Machine token contract

When implemented, the preferred machine access-token contract is a short-lived signed JWT issued by Numou Identity after authenticating a registered SERVICE credential.

Required protected header:

- `alg`: an explicitly configured asymmetric signing algorithm; reject `none` and algorithms outside the allowlist.
- `kid`: identifier of the active signing key; required when multiple keys can be published or rotated.
- `typ`: `JWT` (or a more specific registered media type if adopted consistently).

Required claims:

| Claim | Requirement |
|---|---|
| `iss` | Exact configured Numou issuer identifier |
| `sub` | Immutable SERVICE principal identifier |
| `aud` | Exact resource audience; Iket must reject tokens not issued for its configured audience |
| `iat` | Issued-at timestamp; validate reasonable clock skew |
| `nbf` | Not-before timestamp when emitted; validate if present |
| `exp` | Mandatory short expiry; maximum lifetime is server policy |
| `jti` | Unique token identifier for audit and optional deny-list/replay controls |
| `principalType` | Must identify a machine principal, never infer SERVICE from a missing or arbitrary value |
| `tenant_id` | Tenant established by Identity after validating the service principal's tenant binding |
| `scope` | Explicit granted scopes; never trust a requested scope that was not authorized |
| `client_id` | Stable Developer Platform application/client identifier, when the token is issued on behalf of an application |

Optional correlation claims may include `azp`, `trace_id`, or `correlation_id` only where semantics are documented. Do not place secrets, personal data, or mutable authorization decisions in the token.

The issuer must derive `tenant_id`, `sub`, `client_id`, and granted scopes from authoritative persisted records. It must not copy these values from arbitrary request headers or accept them as caller-selected claims.

## 5. Resource-server validation contract

Iket's identity adapter must validate, before establishing request identity:

1. Signature against trusted Numou public signing keys.
2. Explicit algorithm allowlist and `kid` resolution; no algorithm fallback.
3. Exact issuer match.
4. Exact audience match for the Iket resource.
5. Expiry and not-before with a small, bounded clock-skew allowance.
6. Required subject, principal type, tenant, and scope claims.
7. Machine principal type and active credential/principal status according to the chosen revocation model.
8. Scope authorization for the matched route/product.
9. Token size and parsing limits; malformed tokens fail closed.

Unknown `kid`, unavailable/invalid key material, invalid claims, wrong audience, expired tokens, and revoked principals must fail closed. A key-refresh failure must never fall back to accepting an unverified token.

Iket must strip inbound identity/tenant headers at the public edge and construct its internal identity context only after successful validation. Downstream services may trust only a separately authenticated gateway assertion or their own verified token. A client-supplied `X-Tenant-ID`, `X-Actor-ID`, or `X-Client-ID` must never override verified claims.

## 6. Key publication and rotation

Before production use, Identity must expose a stable, authenticated-over-TLS public-key distribution contract (JWKS or an explicitly versioned equivalent). Requirements:

- Publish public keys only; never expose private signing material.
- Every signing key has a unique `kid`, algorithm, key type, and lifecycle state.
- Sign with one active key; publish active and retiring verification keys concurrently.
- Retain the previous public key for at least the maximum token lifetime plus configured clock skew and propagation/cache allowance.
- Remove retired keys only after all tokens they could have signed are no longer valid.
- Cache public keys with bounded freshness; support refresh on unknown `kid` with rate limiting/single-flight behavior.
- Define emergency key compromise: stop signing with the compromised key, invalidate affected credentials/tokens as supported, publish replacement key, and document propagation bounds.
- Never distribute private keys to Iket or store signing private keys in application route configuration.

If the existing implementation uses symmetric signing or lacks key identifiers/public-key publication, this is a blocker for distributed JWT verification. Do not share a symmetric signing secret with the gateway as a shortcut.

## 7. Credential issuance and lifecycle

The intended machine credential flow is a dedicated service-principal/client-credentials contract, not human PAT reuse.

Required lifecycle semantics:

- Create a SERVICE principal and bind it to exactly one owning tenant.
- Bind the principal to a Developer Platform application through a stable application ID.
- Grant scopes from the canonical Numou scope registry; enforce least privilege.
- Authenticate the client using a one-time-displayed secret or stronger supported credential.
- Persist only a non-reversible verifier/hash for client secrets; keep recoverable gateway secrets and signing keys in Vault only where retrieval is necessary.
- Support secret rotation with overlap bounded by policy, explicit activation, and audit.
- Support immediate client-secret revocation and principal disablement.
- Emit durable audit/outbox events for create, rotate, revoke, disable, scope change, and tenant/application reassignment.
- Make create/rotate/revoke commands idempotent and tenant-scoped.
- Never return raw credentials in list/get responses, logs, events, telemetry, or exception messages.

If the current Identity capability cannot implement this safely without a broader lifecycle change, M1c must remain a documented blocker and a separate Identity milestone must be proposed. Do not add a second credential store inside Developer Platform.

## 8. Revocation and authorization freshness

Short-lived access tokens limit exposure but do not alone provide immediate revocation. The implementation must choose and document one or more bounded mechanisms:

- short access-token lifetime plus refresh through an active client credential;
- online introspection/status validation for high-risk routes;
- a distributed deny-list keyed by `jti` or principal/credential version;
- signed authorization-version claims checked against a bounded cache.

Define maximum revocation propagation time, cache behavior during Identity outages, and fail-open/fail-closed policy by route risk. High-risk management and provisioning operations must fail closed when credential status cannot be established.

Scope removal and tenant reassignment must invalidate old authorization within the documented bound. A token's tenant claim must not be treated as proof that the tenant still owns the principal after a reassignment.

## 9. Tenant, actor, and application context

Keep these identifiers distinct:

- `tenant_id`: security and data-isolation boundary;
- `sub`: authenticated principal (USER or SERVICE);
- `actor_id`: the principal or explicitly delegated human actor responsible for an action;
- `client_id`: Developer Platform application;
- `gateway_id`: Iket deployment/instance identity.

For machine calls, audit records should include tenant, service subject, application, gateway, scopes used, correlation/trace ID, and credential/token identifier where safe. Never log bearer tokens or client secrets.

Delegation or “on behalf of user” must be an explicit, separately authorized contract. A machine principal must not fabricate a human actor.

## 10. Contract tests and acceptance criteria

The Identity implementation and Iket verifier must eventually share versioned test vectors covering:

- valid machine token for the configured issuer and audience;
- wrong issuer and wrong audience;
- invalid signature, unsupported algorithm, and `alg=none`;
- unknown `kid`, key refresh, overlapping rotation, and retired key;
- missing/blank `sub`, `tenant_id`, `principalType`, or required scope;
- USER token presented to a machine-only route;
- SERVICE token presented to a human-only route;
- expired and not-yet-valid tokens, including clock-skew boundaries;
- disabled principal, revoked secret, revoked `jti`, and stale authorization version;
- cross-tenant application/principal binding and caller-supplied tenant-header spoofing;
- scope escalation and unknown scopes;
- key-service outage and introspection outage behavior;
- token/secret redaction in logs, audit payloads, and error responses.

Acceptance for production enablement:

- [ ] Existing Identity issuer and verifier behavior is verified from current source and tests.
- [ ] JWKS/discovery or an equivalent versioned asymmetric key distribution contract exists and is tested.
- [ ] Issuer, audience, algorithm, expiry, `kid`, and required claims are validated by Iket.
- [ ] SERVICE principal and application binding are persisted, tenant-scoped, and auditable.
- [ ] Machine credential issuance, rotation, disablement, and revocation are implemented without PAT reuse.
- [ ] Revocation and key-rotation propagation bounds are documented and tested.
- [ ] Shared contract test vectors pass in both Numou and Iket CI.
- [ ] Inbound identity headers cannot override verified context.
- [ ] Secrets and bearer tokens are absent from logs and durable event payloads.

## 11. Delivery boundary

Numou Identity owns principals, credential lifecycle, token issuance, key publication, and authoritative tenant/scope decisions. Developer Platform owns application registration and the mapping from an application to authorized Identity principals. Iket owns token verification at the gateway edge, route-level enforcement, and gateway telemetry. Gamelan may orchestrate approval/provisioning workflows through a Numou-owned port, but it does not own identity or credential truth.

The integration remains versioned and replaceable: no shared database, no direct dependency on another repository's internal classes, and no private signing key or raw long-lived credential copied into Iket configuration.
