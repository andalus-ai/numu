# Numou Runtime Platform — M3 Composition Plan Integration

- **Status:** Implementation submitted; CI verification pending.
- **Module:** `core/framework/runtime` (`numou-runtime-host`)
- **Baseline:** Java 21.
- **Depends on:** M1 host lifecycle and M2 trusted provider composition.

## 1. Problem

M1's host accepted an application manifest, available provider list and capability resolver, then resolved the application composition inside the runtime host. M3 of the Application Platform subsequently introduced `ApplicationCompositionService` and immutable `ApplicationComposition` in `numou-plugin`.

Keeping a second resolution entry point in the host risks duplicated composition policy and makes the runtime host responsible for a concern already owned by the application composition boundary.

## 2. Decision

The application composition service owns capability resolution and returns an immutable `ApplicationComposition` plan. The runtime host consumes that plan and performs lifecycle operations only.

The preferred construction path is:

```text
Validated ApplicationManifest
        |
ApplicationCompositionService
        |
ApplicationComposition (immutable plan)
        |
ApplicationRuntimeHost
        |
Trusted CapabilityRuntimeFactory
        |
Capability runtimes
```

The host verifies that the plan's application ID and version match the supplied manifest. It does not re-run dependency resolution, reinterpret capability requirements, or authorize providers.

## 3. Implementation

- Add a preferred `ApplicationRuntimeHost(ApplicationManifest, ApplicationComposition, CapabilityRuntimeFactory)` constructor.
- Drive startup exclusively from `ApplicationComposition.initializationOrder()`.
- Read unavailable optional capabilities from `ApplicationComposition.resolutionReport()`.
- Expose the immutable composition through `composition()` for diagnostics and later health reporting.
- Retain the prior resolver-based constructor as a deprecated compatibility path. It delegates immediately to the existing `ApplicationCompositionService`; it does not maintain a second resolver implementation.
- Add tests for plan-driven startup, optional capability reporting and manifest/plan identity and version mismatch.

## 4. Ownership and security

- Manifest validation remains in the application manifest validator.
- Capability compatibility and dependency ordering remain in `ApplicationCompositionService` / `ApplicationCapabilityResolver`.
- Provider implementation approval remains in the trusted runtime catalog and composition root.
- Runtime lifecycle and rollback remain in `ApplicationRuntimeHost`.
- Tenant installation, entitlements and actor authorization remain in the Application Platform and host security boundaries.
- The composition plan is not an authorization token and must only be created from already validated/approved inputs by trusted application code.

## 5. Compatibility

The existing resolver-based constructor remains available but is deprecated. Existing callers continue to compile while new composition roots can pass the immutable plan directly. No manifest schema, capability contract, persistence schema or event contract changes.

## 6. Verification

Run the focused runtime reactor:

```bash
cd core/framework
mvn -B -ntp -pl runtime -am test
```

Then run the complete framework reactor and relevant repository CI. This authoring change does not claim a Maven build result; report CI status separately.

## 7. Non-goals

- No HTTP server, CLI, container packaging or Kubernetes integration.
- No tenant context or secret provider implementation.
- No duplicate capability registry, resolver, application catalog or installation lifecycle.
- No arbitrary class loading or manifest-provided executable code.
- No conversion of capabilities into microservices.
