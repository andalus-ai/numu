# Identity JWT signing-key operations

## Security invariant

The Identity adapter must never ship with a production JWT private key in its
application artifact or source repository. Signing and verification material
are supplied by the deployment. The application has no bundled-key fallback.

Configure these properties through the runtime environment:

- `IDENTITY_JWT_PRIVATE_KEY_LOCATION`: filesystem path to the active PEM private
  key, mounted read-only from the deployment secret manager (for example, a
  Vault Agent-rendered file).
- `IDENTITY_JWT_PUBLIC_KEY_LOCATION`: filesystem path to the matching public
  key. This is the key used to verify tokens issued by this Identity instance.

Do not put PEM contents, Vault tokens, or secret values in environment variables,
container images, ConfigMaps, CI logs, or repository files. Prefer a short-lived
workload identity to authorize Vault access and mount the key with restrictive
permissions. The private key should be readable only by the Identity process.

## Mandatory rotation after the repository key exposure

The previously bundled signing key must be considered compromised. Removing it
from the current tree does not remove it from Git history and does not make it
safe to reuse.

Before deploying this change:

1. Generate a new asymmetric signing key pair in the approved KMS/HSM or secret
   management boundary. Never generate the production private key in this repo.
2. Publish the new public key to every verifier through a coordinated trust
   update. During a bounded migration, verifiers may need both old and new
   public keys; remove the old key after all old tokens have expired and the
   verifier rollout is complete.
3. Mount the new private key and matching public key into Identity and configure
   both location variables. Validate file ownership and permissions.
4. Restart/roll Identity instances and verify newly issued tokens validate with
   the new public key and fail with the retired key.
5. Revoke or invalidate sessions/tokens signed by the exposed key where the
   deployment's token/session model permits it. Reduce token lifetime during
   the migration if operationally appropriate.
6. Record the rotation, affected deployments, verifier rollout, and completion
   evidence in the security change log.

The old key must not be used as a compatibility fallback. If the new key pair
is not available, Identity startup/token issuance must fail closed rather than
fall back to a repository resource.

## Test fixtures

The adapter's test resources contain a dedicated, generated RSA fixture pair.
It exists solely for deterministic unit/integration tests and is not a
deployment credential. Production packaging must continue to use the main
configuration, which requires externally supplied key paths.
