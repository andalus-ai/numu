# Developer Platform M4g — OAuth token introspection and live revocation

- Status: Identity introspection endpoint and live credential-state validation submitted for review.
- Contract: \`POST /internal/v1/oauth2/introspect\`, form-urlencoded, contract v1.
- Owner: Numou Identity.
- Consumer: Iket resource server.
- Predecessors: M4d client-credentials grant, M4e credential-state validation, M4f Iket local JWT validation.

## Contract

The caller submits the access token as the \`token\` form field. Identity returns an RFC 7662-style response:

\`\`\`json
{
  "active": true,
  "token_type": "Bearer",
  "client_id": "nmc_client_example",
  "tenant_id": "8d5d8a7e-6d6b-4f0a-9d1f-7cfa8f35c2b1",
  "application_id": "b2b1b84d-23d8-4a9b-8b42-4f2e7f2d6c11",
  "scope": "orders.read orders.write"
}
\`\`\`

Inactive, malformed, unknown, expired, revoked, rotated, mismatched and over-scoped tokens return \`{"active":false}\`. The endpoint deliberately does not disclose why a token was rejected.

## Security boundary

- The endpoint is under \`/internal\` and requires the dedicated \`oauth-introspect\` role via \`@RolesAllowed\`.
- The deployment must additionally restrict network access to Iket and require mTLS between Iket and Identity. Application role authorization and mTLS are both required; do not expose this endpoint publicly.
- The Iket service credential must be a workload identity issued for this role, not a developer user token.
- Identity parses and cryptographically verifies the submitted JWT using the configured trusted public key and issuer, checks the configured resource audience, and then resolves the live credential record.
- Tenant, application, client and scopes are taken from the verified JWT and authoritative credential row, never from caller headers.
- The private signing key and Identity database remain inside Numou.

## Revocation and rotation semantics

- Revocation is observed on the next successful introspection call.
- Rotation increments \`credential_version\`; tokens minted with an earlier version become inactive on the next introspection.
- Scope reductions in the credential record also make tokens with excess scopes inactive.
- Database errors fail closed: the caller receives \`active=false\` rather than a positive authorization result.
- Responses include \`Cache-Control: no-store\`; caching is a consumer decision, not a server guarantee.

## Iket cache and failure policy

Iket should call introspection only after local RS256 signature, issuer, audience and temporal validation. Recommended cache policy:
- Positive result TTL: at most 5 seconds.
- Negative result TTL: at most 1 second.
- Cache key: SHA-256 digest of the raw token, never the raw token itself.
- Bound cache size and use single-flight request coalescing.
- Do not cache beyond JWT expiry.
- On Identity timeout, TLS failure, 5xx, malformed response or unavailable credential store, fail closed for protected routes. Do not treat network failure as \`active=true\`.
- Maximum ordinary revocation delay is positive-cache TTL plus request/propagation latency. A five-second cache is not immediate revocation.

## Operational requirements

- Terminate TLS only at a trusted service-mesh/ingress boundary that authenticates the Iket workload certificate; preserve the authenticated workload identity to Identity.
- Configure Identity OIDC role mapping so only the Iket workload principal receives \`oauth-introspect\`.
- Rate-limit and monitor introspection calls by authenticated workload identity.
- Never log the submitted token, Authorization header, or secret-bearing request body.
- Emit metrics for active/inactive decisions, latency, rejected caller identity and dependency failures without recording token values.
- Rotate signing keys with an overlap-capable trusted key set before enabling multiple active keys.

## Not included / rollout gates

This milestone does not yet implement an Iket introspection HTTP client, cache, service credential provisioning, mTLS deployment manifests, JWKS key-set rotation, or route-level scope policy. The M4f local JWT validation remains useful as a fast first check but is not a substitute for this online state check. End-to-end revocation is complete only after Iket consumes this endpoint and deployment integration tests prove revocation/rotation behavior.
