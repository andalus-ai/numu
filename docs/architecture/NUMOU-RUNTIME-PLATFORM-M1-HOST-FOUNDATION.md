# Numou Runtime Platform — M1 Host Foundation

- **Status:** Implementation branch created; CI/build verification pending.
- **Branch:** `runtime/m1-host-foundation`
- **Base:** `runtime/m0-runtime-boundary-audit`
- **Java baseline:** 21, inherited from `core/framework/pom.xml`.

## Delivered in this milestone

A new `core/framework/runtime` Maven module, published as `numou-runtime-host`, is added to the existing framework reactor. It depends on the existing `numou-plugin` module and reuses its declarative `ApplicationManifest`, `ApplicationCapabilityResolver`, `CapabilityProvider` and `CapabilityResolution` contracts.

The host provides:
- `CapabilityRuntime`: lifecycle contract for one selected provider runtime.
- `CapabilityRuntimeFactory`: trusted composition-root bridge from resolved provider metadata to an executable runtime.
- `ApplicationRuntimeHost`: deterministic dependency-order startup, reverse-order shutdown, state reporting, optional-capability reporting and startup rollback.
- `ApplicationRuntimeState` and `ApplicationRuntimeException`: explicit lifecycle state and failure surface.
- Focused unit tests for ordering, idempotent stop, optional absence, failed startup rollback, provider identity mismatch and invalid repeated start.

## Lifecycle semantics

1. The host accepts an already validated `ApplicationManifest`; it does not duplicate JSON schema validation, permission policy or tenant installation checks.
2. The existing `ApplicationCapabilityResolver` selects exactly one compatible provider per required capability and returns dependency order.
3. The trusted application composition root supplies `CapabilityRuntimeFactory`; manifest data cannot name arbitrary Java classes or cause arbitrary class loading.
4. Each runtime is registered for rollback before its `start()` is invoked. This allows cleanup when a provider partially acquires resources and then fails.
5. On failure, already attempted runtimes are stopped in reverse order and cleanup failures are attached as suppressed exceptions.
6. Normal shutdown attempts every runtime in reverse order. The host reaches STOPPED even if one or more providers fail to stop; the aggregate exception exposes those failures.
7. A host instance is single-use: NEW -> STARTING -> RUNNING -> STOPPING -> STOPPED, or STARTING -> FAILED. A new application start requires a new host instance.

## Explicit limitations

This is a host lifecycle foundation, not yet a full standalone product runtime:
- No HTTP server, CLI, process supervisor, container image or Kubernetes integration.
- No ServiceLoader scanning or arbitrary dynamic plugin loading in the host.
- No adapter connecting `CapabilityRuntimeFactory` to CDI or the existing `RuntimePluginManager`; that requires an explicit trusted composition adapter and tests.
- No tenant runtime context, secrets/configuration provider, migration coordinator, health endpoint or runtime-state reporter to the control plane.
- No hot reload or restart of a failed host.
- No claim that application-platform installation/provisioning is already wired to this host.

## Verification status

The PR includes focused JUnit 5 / AssertJ tests. The GitHub integration used to author this branch does not provide a local Maven execution environment in this session; therefore no Maven compile or test result is claimed. CI must verify at minimum:

```bash
cd core/framework
mvn -pl runtime -am test
```

Also verify the complete framework reactor and repository CI before merging. Any CI failure must be diagnosed rather than bypassed.

## Follow-up

M2 should implement a trusted composition adapter that bridges the existing capability/plugin provider lifecycle to this host, with deterministic provider registration and no second registry. Before M2, confirm whether the existing plugin manager can safely support application-scoped selection; if not, extend its public lifecycle contract rather than duplicating it.
