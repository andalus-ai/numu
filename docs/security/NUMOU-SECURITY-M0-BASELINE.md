# Numou Security — M0 Baseline Assessment

**Assessment date:** 2026-11-10  
**Repository ref:** `main` at assessment start  
**Assessment branch:** `security/m0-baseline`  
**Status:** Source-traced baseline; not a penetration test or independent security audit.

## 1. Executive summary

The repository already contains meaningful security foundations: a dedicated Identity capability, a separate Security SPI, typed ABAC conditions, fail-closed authorization helpers, tenant-aware permission resolution, deployment-supplied JWT signing keys, and tenant-isolation implementation evidence in several capabilities.

The assessment also found configuration and assurance gaps that should be addressed before describing the platform as production-hardened.

**Important correction to the original password concern:** the current Identity adapter does **not** use PBKDF2 at 16,000 iterations in the inspected implementation. It uses jBCrypt 0.4 with BCrypt work factor 12 in `JBcryptPasswordHasher`. Therefore the OWASP PBKDF2-HMAC-SHA256 600,000 recommendation is not a direct parameter change for the current code. The next password milestone must preserve existing BCrypt verification and introduce a versioned migration strategy before changing algorithms.

This is a source-level assessment. It does not establish that every deployment uses this adapter, that every endpoint is protected, or that every security property is correctly configured in production.

## 2. Scope and method

Inspected repository guidance and representative source files:

- `AGENTS.md`
- Identity adapter password hasher and adapter Maven dependencies
- Identity application password-hasher port and password-hash value object
- Identity ABAC evaluator and its unit tests
- Authorization decision service and trusted authorization resolver
- Identity policy adapters
- Identity runtime `application.properties`
- Existing tenant-bound authentication and JWT signing-key operations documents

The repository contains multiple Maven reactor roots. No repository-wide build or test result is claimed by this assessment.

## 3. Verified findings

### M0-01 — Password hashing algorithm is BCrypt, not PBKDF2

**Observed**

- `identity/numou-identity-adapter/.../JBcryptPasswordHasher.java` calls `BCrypt.hashpw` and `BCrypt.checkpw`.
- The configured BCrypt work factor is 12.
- `identity/numou-identity-adapter/pom.xml` pins `org.mindrot:jbcrypt:0.4`.
- The application exposes a `PasswordHasher` port; the domain stores an opaque `PasswordHash` value object whose `toString()` is redacted.

**Assessment**

The reported PBKDF2 setting of 16,000 iterations was not found in the inspected current Identity password-hashing implementation. OWASP's PBKDF2-HMAC-SHA256 recommendation of 600,000 iterations applies when using that PBKDF2 construction; it must not be copied into a BCrypt work-factor setting.

BCrypt cost 12 is materially stronger than a cost of 10, but the adapter still needs benchmarking, explicit policy/version metadata, and a review of BCrypt's 72-byte password input limit. The current port returns only a hash and exposes no explicit algorithm-upgrade result or rehash-on-login contract.

**Required M1 work**

1. Inventory all password-hash producers and verifiers, including non-Identity deployments and test adapters.
2. Add a versioned encoded-hash format and algorithm-dispatching verifier; preserve verification of existing BCrypt hashes.
3. Benchmark BCrypt cost 12 and candidate Argon2id settings on supported production hardware.
4. Prefer Argon2id for new general-purpose deployments where compatible; support PBKDF2-HMAC-SHA256 at the current OWASP-recommended work factor when FIPS-validated cryptography is a deployment requirement.
5. Rehash after successful authentication when a credential is below the active policy; do not require a mass password reset solely for an algorithm migration.
6. Explicitly handle BCrypt's 72-byte input boundary without silently truncating or changing legacy password semantics.
7. Add tests for legacy verification, new hash creation, wrong-password rejection, malformed/unknown encodings, upgrade decisions, and long UTF-8 inputs.
8. Benchmark authentication concurrency and apply endpoint-level rate limiting to prevent password hashing from becoming a denial-of-service vector.

### M0-02 — Database password has a committed weak fallback

**Observed**

In `identity/numou-identity-adapter/src/main/resources/application.properties`:

```properties
quarkus.datasource.password=${IDENTITY_DB_PASSWORD:changeit}
```

The comment says real secrets belong in environment variables or a secret manager, but the expression supplies a known fallback.

**Risk**

If a deployment omits `IDENTITY_DB_PASSWORD`, the service may attempt to authenticate with the public placeholder. This is unsafe configuration behavior even if the database currently rejects that password.

**Implemented on the M0 branch**

A production-profile override now requires `IDENTITY_DB_PASSWORD` without a fallback. The existing base fallback remains for local development compatibility; production must run with the `prod` profile. A configuration contract test and runtime startup verification remain pending.

### M0-03 — Federation cookie secure flag defaults to false

**Observed**

```properties
numou.identity.federation.cookie-secure=false
```

The nearby comment says production behind TLS should set this to true, but the checked-in default is false.

**Risk**

A production deployment that does not override the property may issue a federation flow cookie without the Secure attribute.

**Implemented on the M0 branch**

A production-profile override now sets `numou.identity.federation.cookie-secure=true`; the existing false value remains for local development compatibility. Verify the emitted Set-Cookie attributes (including HttpOnly, SameSite, Path and expiry), PKCE state binding, and CSRF protections with HTTP-level tests before closing this finding.

### M0-04 — ABAC evaluator has fail-closed behavior in the inspected path

**Observed**

- `PolicyEvaluator.evaluate` converts unknown/missing attribute results to false.
- Nested AllOf/AnyOf conditions preserve unknown state and do not turn missing data into a positive result.
- Unit tests cover unknown attributes and incompatible comparison types.
- `AuthorizationDecisionService` rejects null requests, missing verified scope, blank action/resource, and grants only an explicitly present action.
- `TrustedAuthorizationResolver` rejects missing identity/tenant/action/resource, absent or mismatched evidence, inactive evidence, and resolver runtime failures.

**Assessment**

The earlier broad concern that this evaluator defaults to ALLOW is **not supported by the inspected implementation**. The inspected helpers fail closed. This does not prove every authorization entry point uses these helpers or that policy data is trustworthy.

**Required action**

Keep the fail-closed tests and add integration-level deny tests for missing policy, policy-store outage, stale membership, tenant mismatch, workspace mismatch, resource ownership mismatch, and alternate API entry points. Inventory direct authorization implementations to find paths that bypass the shared decision boundary.

### M0-05 — Tenant-bound human authentication has documented unresolved contract gaps

**Observed**

The existing `docs/security/M3A3-TENANT-BOUND-HUMAN-AUTHENTICATION.md` documents unresolved alignment across human JWT tenant claims, machine token claims, tenant-selection purpose/audience, and eligible-tenant discovery.

**Risk**

A caller-supplied tenant identifier must never become trusted authorization context merely because it appears in a request or header. Human and machine token claim conventions must be consistent and purpose-separated.

**Required action**

Treat the existing M3A.3 contract as a release gate. Do not enable tenant-bound human access until token purpose, audience, canonical tenant claim, server-side membership selection, revalidation, and rejection by ordinary APIs are covered by request-level tests.

### M0-06 — JWT key operations have a documented external-key boundary

**Observed**

The Identity configuration expects externally mounted private/public key material and a deployment-managed JWKS. The existing operations document specifies key rotation, overlap, and retirement. No classpath private-key fallback is described in the inspected runtime configuration.

**Assessment**

This is a good baseline. It still depends on deployment correctness, filesystem permissions, key generation and storage controls, and verifier cache/rollout behavior.

**Required action**

Add deployment checks for private-key file permissions, active `kid` consistency, key algorithm allowlists, key rotation rehearsal, unknown-`kid` behavior, and retirement after maximum token lifetime plus cache overlap. Confirm that private key bytes never enter logs, images, ConfigMaps, or repository artifacts.

### M0-07 — Public authentication endpoints need abuse-control verification

**Observed**

The runtime configuration explicitly permits public registration, local login, tenant login, federated login, and the OAuth token endpoint. The inspected configuration excerpt does not establish rate limits or abuse controls for these routes.

**Risk**

Public authentication and registration routes are exposed to credential stuffing, brute force, account enumeration, automated registration, and resource exhaustion.

**Required action**

Verify edge and application rate limiting, per-account and per-source controls, bounded concurrent password verification, generic authentication errors, abuse telemetry, registration verification, and safe recovery flows. Do not rely exclusively on IP-based controls.

### M0-08 — BCrypt dependency and input semantics require a deliberate compatibility review

**Observed**

The Identity adapter pins jBCrypt 0.4. The adapter's descriptive Maven metadata also states that it was authored outside a network-connected build environment and had not been compiled against the target Quarkus BOM at that time.

**Required action**

Verify dependency provenance and maintenance status, review supported Java/runtime compatibility, scan the resolved dependency graph, and run the owning Identity reactor. Consider a maintained provider or JCA-backed implementation as part of M1, but do not switch implementations without legacy hash compatibility tests.

## 4. Existing positive controls to preserve

- Password hashing is behind an application port; domain code does not perform cryptographic work.
- The password-hash value object's string representation is redacted.
- The ABAC evaluator uses typed conditions rather than arbitrary expression execution.
- Missing attributes and incompatible comparison types fail closed in the inspected evaluator.
- Authorization evidence is checked against the authenticated principal and tenant.
- Identity policy resolution checks tenant membership before returning effective permissions in the inspected adapter.
- JWT signing keys are intended to be supplied by deployment, with a documented JWKS rotation procedure.
- Tenant isolation has existing capability-specific implementation and test evidence; retain these tests and expand cross-capability coverage.

## 5. Prioritized remediation register

| ID | Priority | Finding | Target milestone |
|---|---|---|---|
| M0-02 | Critical | Weak database password fallback | M0 immediate config fix |
| M0-03 | High | Federation cookie Secure default false | M0 immediate config fix |
| M0-01 | High | Password algorithm/version migration and input-boundary review | M1 |
| M0-05 | High | Tenant-bound human token contract | M1/M3 dependency gate |
| M0-07 | High | Public authentication abuse controls not evidenced | M1 |
| M0-06 | High | Operational signing-key assurance | M1/M4 |
| M0-08 | Medium | BCrypt dependency/provenance/runtime verification | M1 |
| M0-04 | Medium | Authorization path inventory and integration deny tests | M2/M3 |

Priorities are engineering triage based on source review, not externally validated severity scores.

## 6. M0 architecture decisions

1. Identity remains the owner of human credentials and authentication lifecycle.
2. Security remains a reusable framework capability exposed through stable SPI/application contracts; domain capabilities must not duplicate credential storage or authorization engines.
3. Password hashes are self-describing and versioned; verification remains backward compatible during migration.
4. Authorization is deny-by-default. Missing identity, tenant, policy, evidence, or required attributes must never grant access.
5. Tenant context is trusted only when derived from verified credentials and server-side membership/authorization evidence.
6. Secrets and signing keys are externalized to an approved secret-management boundary; no committed fallback may be used for production secrets.
7. Security controls must be enforced at application boundaries and reinforced by storage, infrastructure, and deployment controls.

## 7. M0 acceptance criteria

- [x] Repository guidance and representative Identity/Security source paths inspected.
- [x] Password-hashing implementation identified and the PBKDF2 assumption corrected.
- [x] Existing ABAC evaluator and authorization helper behavior inspected.
- [x] Existing tenant-bound authentication and signing-key operations contracts reviewed.
- [x] Findings and milestone ownership documented.
- [x] Add a production-only mandatory database password override (runtime startup verification pending).
- [x] Enforce Secure federation cookies in the production profile (HTTP-level attribute verification pending).
- [ ] Add configuration regression tests for both production overrides.
- [ ] Run the owning Identity Maven reactor and record exact command/result.
- [ ] Complete endpoint and deployment configuration inventory.
- [ ] Review findings with maintainers before declaring M0 closed.

## 8. Verification limitations

No Maven build, unit test, integration test, DAST, SAST, dependency scan, penetration test, or production configuration inspection was executed as part of this source-only assessment. The findings above are limited to the exact files listed in the scope section. Do not treat this document as a security certification or a claim that all Numou capabilities have been audited.
