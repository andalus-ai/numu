# Identity JWT signing-key operations

## Security invariant

The Identity adapter must never ship with a production JWT private key in its
application artifact or source repository. Signing and verification material
are supplied by the deployment. The application has no bundled-key fallback.

Configure these properties through the runtime environment:

- `IDENTITY_JWT_PRIVATE_KEY_LOCATION`: filesystem path to the active PEM private
  key, mounted read-only from the deployment secret manager (for example, a
  Vault Agent-rendered file).
- `IDENTITY_JWT_PUBLIC_KEY_LOCATION`: filesystem path to the matching active public
  key. This key is used to verify tokens issued by this Identity instance.
- `IDENTITY_JWT_SIGNING_KEY_ID`: stable, non-secret `kid` value stamped into newly
  issued OAuth machine access tokens.
- `IDENTITY_JWT_JWKS_LOCATION`: filesystem path to a deployment-managed public
  JWK Set JSON document. It contains the active public key and any previous
  public keys retained during the rotation overlap.

Do not put PEM contents, Vault tokens, or secret values in environment variables,
container images, ConfigMaps, CI logs, or repository files. Prefer a short-lived
workload identity to authorize Vault access and mount the key with restrictive
permissions. The private key should be readable only by the Identity process.

## Public JWKS endpoint

Identity publishes the configured public key set at GET /.well-known/jwks.json.

The endpoint is public metadata; it never returns private signing material. It
validates that the configured active kid exists in the JWKS and that its RSA
modulus/exponent match the configured active public key before serving the
document. The response is bounded to 64 KiB and 32 keys, includes an ETag, and
uses a 30-second public cache lifetime. A missing, malformed, oversized, or
mismatched key set fails closed with HTTP 503.

The deployment-managed JWKS file contains the active key and may contain
previous public keys during the rotation overlap:

```json
{
  "keys": [
    {
      "kty": "RSA",
      "use": "sig",
      "alg": "RS256",
      "kid": "identity-signing-2026-11",
      "n": "<base64url-rsa-modulus>",
      "e": "AQAB"
    },
    {
      "kty": "RSA",
      "use": "sig",
      "alg": "RS256",
      "kid": "identity-signing-previous",
      "n": "<base64url-previous-rsa-modulus>",
      "e": "AQAB"
    }
  ]
}
```

The active kid is stamped into machine access tokens by the OAuth token
issuer. All Identity instances participating in a rolling rotation must share
a JWKS document containing the active public key for each live signer.

## Rotation procedure

The previously bundled signing key must be considered compromised. Removing it
from the current tree does not remove it from Git history and does not make it
safe to reuse.

1. Generate a new asymmetric signing key pair in the approved KMS/HSM or secret
   management boundary. Never generate the production private key in this repo.
2. Add the new public key to the deployment-managed JWKS document while keeping
   the old public key. Publish the file atomically; never expose private JWK
   members.
3. Roll Identity with the new private/public key pair and matching
   IDENTITY_JWT_SIGNING_KEY_ID. Each Identity instance verifies that its active
   public key is present in the published JWKS before serving the document.
   During a rolling deployment, the shared JWKS must contain both old and new
   public keys so either instance can publish valid metadata.
4. Verify newly issued tokens carry the new kid, validate with the new public
   key, and are rejected by a verifier that trusts only the retired key.
5. Keep the old public key in JWKS until the maximum lifetime of tokens signed
   with it plus the verifier cache/rollout overlap has elapsed. Then remove it
   atomically. Iket refreshes the key set on an unknown kid and periodically
   according to its configured cache TTL.
6. Revoke or invalidate sessions/tokens signed by a compromised key where the
   deployment token/session model permits it. Reduce token lifetime during
   migration if operationally appropriate. Record rotation and verifier
   rollout evidence in the security change log.

## Test fixtures

The adapter's test resources contain a dedicated, generated RSA fixture pair.
It exists solely for deterministic unit/integration tests and is not a
deployment credential. Production packaging must continue to use the main
configuration, which requires externally supplied key paths.
