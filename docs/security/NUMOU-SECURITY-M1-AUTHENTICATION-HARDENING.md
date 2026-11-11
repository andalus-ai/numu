# Numou Security M1 — Authentication hardening

Status: implementation branch; review and CI verification pending.

## Scope

M1 begins the password credential migration without invalidating existing accounts. The current Identity adapter previously wrote unversioned jBCrypt hashes at work factor 12. New credentials are written in an explicit Numou format using PBKDF2-HMAC-SHA256 with 600,000 iterations, a random 16-byte salt, and a 256-bit derived key.

The format is self-describing:

`$numou$pbkdf2-sha256$v=1$i=600000$<base64url-salt>$<base64url-derived-key>`

## Implemented

- Replaced the BCrypt-only adapter with `VersionedPasswordHasher`.
- New password hashes use PBKDF2-HMAC-SHA256 via JCA; each hash gets a fresh cryptographically random salt.
- Existing jBCrypt records remain verifiable during migration.
- A successful login using a legacy hash triggers re-encoding and persistence of the new format.
- Both local login routes are covered: `/api/v1/auth/login` and the tenant-selection `/api/v2/auth/login`.
- Password inputs are bounded to 1,024 UTF-8 bytes. Empty passwords are rejected during hashing.
- Malformed or unsupported encoded hashes fail verification without exposing parser errors.
- PBKDF2 verification bounds accepted iteration counts (100,000–2,000,000) to prevent attacker-controlled excessive CPU work.
- BCrypt verification rejects candidates longer than BCrypt's 72-byte input boundary rather than silently ignoring a suffix.
- The application repository port exposes a compare-and-set password-hash upgrade operation. The PostgreSQL adapter updates only when the persisted hash still matches the hash verified at login, preventing a delayed migration from overwriting a concurrent password change. If another operation wins the race, the login flow reloads and re-verifies the credential before issuing a token.

## Compatibility and migration

No bulk password reset or database migration is required. Existing BCrypt hashes remain in place until the account next completes a successful password login. At that point, the application verifies the supplied password with the legacy verifier, writes a PBKDF2 hash, and then issues the access credential.

The historical BCrypt 72-byte input behavior is a compatibility edge case: credentials whose UTF-8 encoding exceeds 72 bytes are not accepted by the legacy verifier. Such accounts need a controlled recovery/reset path; accepting arbitrary suffixes would preserve BCrypt's truncation ambiguity.

## Login throttling scope and operational requirements

The login filter currently enforces a per-process, per-source-address budget (default burst 10, refill 0.1 tokens/second, bounded to 100,000 tracked keys). It does not yet provide a distributed/global budget or a per-account credential-stuffing budget. In a multi-replica deployment, enforce a shared limit at the trusted ingress/API gateway and add account-keyed throttling in a later slice. The filter uses the socket peer address and does not trust arbitrary X-Forwarded-For input. If the service is behind a reverse proxy, configure Quarkus proxy handling with an explicit trusted-proxy allowlist so the effective remote address is correct; otherwise ingress-side controls must be authoritative.

## Operational requirements

PBKDF2 at 600,000 iterations is intentionally CPU-expensive. Before production rollout:

1. Benchmark p50/p95/p99 hashing and verification latency on the actual production CPU/runtime.
2. Size authentication worker capacity and concurrency limits; do not allow unbounded concurrent password verification.
3. Load-test login throughput and monitor authentication latency, CPU saturation, and failed-login rates.
4. Confirm the deployed JCA provider and cryptographic module meet any required FIPS validation. Selecting a JCA algorithm alone does not make a deployment FIPS validated.
5. Keep login rate limiting and credential-stuffing protection enabled at the edge and application boundary.

Argon2id remains the preferred memory-hard option for deployments without a FIPS constraint. This M1 slice uses JCA PBKDF2 to avoid introducing an unverified native/runtime dependency; a future Argon2id provider can be added behind the same port after runtime and compliance review.

## Tests added

- Versioned format and successful/unsuccessful verification.
- Random salt uniqueness.
- Unicode password handling.
- Legacy BCrypt verification and rehash indication.
- Malformed encoded hash handling.
- UTF-8 byte-length enforcement.
- BCrypt 72-byte boundary behavior.
- Compare-and-set repository tests proving stale migrations cannot overwrite a newer credential.
- Token bucket tests for burst capacity, refill behavior, independent keys, and invalid configuration.
- A bounded token-bucket filter for both public password-login routes, returning HTTP 429 and Retry-After when the source budget is exhausted.
- Reuse of the platform's shared TokenBucketRateLimiter rather than introducing a second token-bucket implementation.

## Verification status

The source changes have been committed to `security/m1-authentication-hardening`. The repository connector used for this implementation can read and write GitHub files but does not execute the Maven reactor. Therefore no compilation, unit test, integration test, benchmark, or CI result is claimed here. Run the Identity reactor and review the resulting test output before merge.

Suggested narrow verification from the repository root:

```bash
mvn -f identity/pom.xml test
```

If the parent reactor requires previously installed sibling artifacts, build the required framework reactor first using the repository's documented Maven workflow.
