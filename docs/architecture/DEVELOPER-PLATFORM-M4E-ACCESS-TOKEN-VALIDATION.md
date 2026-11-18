# Developer Platform M4e — Access-token validation and revocation

- Status: application-layer live credential-state validator implemented; resource-server wiring and Iket integration remain follow-up gates.
- Owner: Numou Identity.
- Consumers: Numou resource-server adapters and the Iket identity adapter.
- Predecessor: M4d OAuth 2.0 client-credentials grant.

## Trust boundary

M4e separates two checks that must both succeed:

1. Cryptographic JWT validation by the consuming resource server: signature, explicit algorithm policy, exact issuer, exact audience, exp, nbf (when present), and token size/parse limits.
2. Live credential-state validation by OAuthClientAccessTokenStateValidator: token_use=access, globally unique client_id, tenant and application binding, current credential version, active status/expiry, and scope subset.

The state validator deliberately does not parse JWTs or claim to verify signatures. Callers must only pass claims extracted from an already verified token. A caller must never populate these arguments from request headers.

## Revocation and rotation semantics

- Revoking a credential immediately makes state validation reject its tokens.
- Rotating a credential increments credential_version; tokens minted against the prior version are rejected.
- Scope claims must be a subset of the current persisted credential scopes.
- Missing clients, malformed ownership claims, inactive/expired credentials, stale versions and scope escalation all return one generic invalid-token exception.
- This is online validation: consumers must define bounded caching and fail-closed behavior. If state is cached, the maximum revocation delay is cache TTL plus invalidation propagation. Do not advertise immediate revocation when using a cache.

## Required resource-server integration

Before production traffic is enabled, each resource server must:
- Verify JWT signature, issuer, audience and time claims before invoking the state validator.
- Require the expected audience for that service; do not accept a token intended for a different service.
- Invoke the validator for every machine access token before establishing tenant/principal context.
- Derive tenant, application and scopes from the returned verified result, not caller-supplied headers.
- Apply route/product scope authorization after authentication; a valid token does not imply permission for every route.
- Fail closed when credential-state storage is unavailable unless a documented bounded-cache policy explicitly governs the outage.

## Iket contract

Iket must implement the same semantics in its versioned Numou identity adapter. It must verify the JWT cryptographically using trusted Numou public keys, then check client_id, tenant_id, application_id, credential_version, token_use, and scope against an authenticated Numou validation contract or a bounded cache populated from that contract. It must not share Numou's database or trust caller-provided tenant headers.

## Non-goals and rollout blockers

This slice does not publish JWKS, rotate the exposed signing key, configure Vault-backed signing-key delivery, add a token introspection endpoint, wire the validator into all Numou REST services, or modify Iket. The documented signing-key operations remain a mandatory production gate. Do not describe M4e as end-to-end enforcement until those integrations and tests are merged.
