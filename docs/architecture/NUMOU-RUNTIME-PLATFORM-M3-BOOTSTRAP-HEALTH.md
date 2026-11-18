# Numou Runtime Platform — M3 Bootstrap and Health Lifecycle

- **Status:** Implementation authored; build and CI verification pending.
- **Branch:** `runtime/m3-bootstrap-health`
- **Base:** M2 trusted composition commit `3ad70dd5bb513563f9c25b1980936f2f82565181`
- **Pull request:** Not opened. Repository `AGENTS.md` requires explicit user approval before creating PRs.

## Objective

Turn the application host into a process-level lifecycle API that a product launcher can use for standalone services, modular monoliths, or embedded applications. Keep HTTP server, dependency injection, secret providers, and product-specific adapters outside the runtime core.

## Delivered

### Runtime configuration

`RuntimeConfiguration` reads Java properties and overlays `NUMOU_RUNTIME_*` environment variables. Environment names map to `numou.runtime.*` keys, underscores become hyphens, and environment values take precedence. Application ID is mandatory; profile defaults to `standalone`.

Example:

```properties
numou.runtime.application-id=accounting
numou.runtime.profile=standalone
numou.runtime.http-port=8080
```

### Trusted application definition

`RuntimeApplicationDefinition` binds the validated application manifest, selected provider descriptors, and a trusted `CapabilityRuntimeFactory`. Executable classes and factories are not loaded from application properties or manifests.

### Process bootstrap

`ApplicationRuntimeBootstrap` verifies that configured application ID matches the manifest, starts the existing `ApplicationRuntimeHost`, exposes immutable health snapshots, and supports an opt-in JVM shutdown hook. The shutdown hook is opt-in so embedded hosts can own lifecycle themselves.

### Health contract

`RuntimeHealthSnapshot` reports application ID, deployment profile, readiness state, liveness/readiness booleans, active provider IDs, unavailable optional capabilities, observation time, and a sanitized failure summary.

Product transport adapters can expose this contract as:
- `/health/live`: process liveness (`live`)
- `/health/ready`: composition readiness (`ready`)

The runtime module does not start an HTTP server or impose a transport dependency.

## Tests authored

- Environment overrides file/property configuration.
- Standalone profile default and required application ID.
- Immutable configuration values.
- Configured application ID must match the manifest.
- Startup transitions to READY and reports active providers.
- Shutdown is idempotent and releases providers once.
- Startup failure remains unready and exposes failure information.

## Verification

No Maven build or tests have been executed in this authoring session. Run:

```bash
cd core/framework
mvn -pl runtime -am test
```

Then run the complete framework reactor and repository CI. Do not treat this milestone as complete until the relevant checks pass.

## Deliberate follow-up

M3 currently provides a transport-neutral bootstrap library, not a runnable product distribution. The next implementation tranche should add a product-owned launcher/packaging example (Accounting first), select a concrete HTTP/metrics adapter, and prove that unrelated capability artifacts are absent from the resulting dependency graph.
