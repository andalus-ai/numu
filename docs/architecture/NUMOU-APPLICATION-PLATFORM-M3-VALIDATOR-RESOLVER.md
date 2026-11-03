# M3 — Application Manifest Validator and Capability Resolver

- **Status:** merged via PR #1649; isolated Java 21 verification is now tracked by `.github/workflows/application-platform.yml`.
- **Depends on:** merged M1 (#1646) and M2 (#1647).
- **Implementation owner:** existing `core/framework/plugin` reactor, under `plugin.application`.
- **Manifest contract:** `platform.numou/v1alpha1`.
- **Workflow engine:** Gamelan remains the workflow engine; no second engine is introduced.

## 1. Ownership decision

The repository already has a framework plugin runtime, capability registry and dependency resolver in `core/framework/plugin`. M3 places the application-package validator and application capability resolver in a separate `plugin.application` package within that existing reactor. This avoids a new Maven module and reuses the host's capability composition boundary.

An application manifest is **not** a `NumouPlugin`, does not load classes through `ServiceLoader`, and cannot execute code. Existing plugin lifecycle APIs remain separate and are not used as a substitute for tenant application installation.

## 2. Delivered implementation

- Packages the existing M2 JSON Schema 2020-12 as a classpath resource.
- Uses NetworkNT JSON Schema Validator for structural validation, with Jackson for JSON parsing.
- Produces an immutable typed `ApplicationManifest` after schema validation.
- Enforces cross-field rules: duplicate capability IDs across required/optional lists, known permission catalog, unique navigation contribution IDs, and navigation permission declaration.
- Parses SemVer 2.0.0 precedence and supports only exact, caret and tilde range syntax.
- Resolves required and optional capability providers against the selected host runtime profile.
- Rejects missing required providers, incompatible or ambiguous providers, duplicate provider IDs and dependency cycles.
- Produces deterministic dependency-first initialization order by sorting capability IDs and provider dependencies.

The resolver deliberately requires exactly one compatible provider. It does not silently select an arbitrary provider or use "highest version wins"; provider selection policy must remain explicit and reproducible.

## 3. Security and lifecycle boundaries

- Permission names are checked against a host-supplied permission catalog. A valid manifest only requests permissions; it never grants them.
- Tenant identity, credentials, service URLs, arbitrary database connections and executable code remain outside the manifest schema.
- The validator does not install, enable, provision, authorize or execute an application.
- Catalog-level publisher ownership and package signature verification remain registry/trust-layer responsibilities; this validator does not claim to authenticate a publisher.

## 4. Gamelan workflow integration

M3 does not add a fake Gamelan HTTP client or compile against Gamelan's Java 25 SDK from Numou's Java 21 reactor. The M2 audit established the ownership and tenant propagation requirements, but the exact deployed REST/gRPC contract, service-to-service authentication handshake and create-run deduplication/reconciliation behavior still need wire-level verification.

The existing `GamelanWorkflowEngineAdapter` is still an in-memory DAG implementation despite its name. It must not be represented as production Gamelan integration. Renaming or replacing it requires a full consumer/test audit and is not bundled into this validator milestone.

**Gate before enabling workflow-triggered business actions:** a stable tenant-scoped idempotency key, durable Numou dispatch intent, and a Gamelan-side deduplication or strongly safe reconciliation contract for ambiguous timeouts. Correlation ID alone is not treated as an idempotency key. Exactly-once execution is not claimed.

## 5. Verification status

- Source tracing confirmed that `core/framework/plugin` already owns the host plugin/capability registry and dependency-resolution primitives.
- Unit tests have been added for manifest shape/semantic validation, SemVer ranges, and capability resolution.
- The schema resource is copied from the merged M2 schema rather than maintained as a second hand-written schema.
- Maven tests and CI have **not** yet been verified for this branch. The NetworkNT dependency/API compatibility and the exact reactor test command remain CI gates.
- No Gamelan adapter, tenant workflow dispatch, application installer, marketplace, or package execution runtime is claimed as delivered.

### M3 post-merge CI evidence

The PR-triggered Numou CI run associated with the M3 head completed with failures. Inspection of the failed framework job shows that its prerequisite build stopped in `security-vault-application` because `VaultSecretService` could not resolve `SecretAlreadyExistsException`. The framework/plugin reactor step was skipped, so that run does not establish whether the M3 code compiles or its unit tests pass. The separate focused workflow added in M4 removes that unrelated security-reactor prerequisite and tests the plugin reactor directly on Java 21.

## 6. Acceptance and next gates

- [x] Use the existing framework plugin reactor rather than create a new Maven module.
- [x] Keep application descriptors separate from executable plugin lifecycle.
- [x] Add schema-backed manifest parsing and typed descriptor output.
- [x] Add permission and cross-field semantic checks.
- [x] Add exact/caret/tilde SemVer compatibility.
- [x] Add deterministic required/optional capability resolution.
- [x] Add fixture-based unit tests.
- [ ] Verify the `core/framework` Maven reactor on Java 21 and fix any dependency/API/test failures.
- [ ] Verify Gamelan wire protocol and authentication from a deployed runtime contract.
- [ ] Add tenant-aware workflow orchestration port and idempotent create/reconciliation contract only after that protocol evidence is available.
- [ ] Audit existing workflow adapter consumers before renaming or replacing the in-memory implementation.
- [ ] Add durable tenant installation lifecycle only after the descriptor and entitlement/authorization contracts are agreed.
