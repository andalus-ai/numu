# Numou Runtime Platform — M1a Runtime Lifecycle Hardening

- **Status:** implementation branch; focused CI verification pending.
- **Baseline:** Java 21, existing `core/framework/foundation/numou-foundation-application` reactor.
- **Depends on:** M0 runtime boundary audit; existing Application Runtime foundation.

## 1. Purpose

M1 must evolve the existing Application Runtime foundation rather than introduce a parallel runtime kernel. This first implementation slice hardens the lifecycle substrate that a future standalone host and application composition root will use.

The existing `ApplicationRuntime`, `ApplicationRuntimeBuilder`, `ApplicationCompositionRoot`, `RuntimeComponent`, `ManagedWorker`, startup validator, and readiness/liveness contracts remain the canonical lifecycle APIs.

## 2. Changes in this slice

1. **Duplicate registrations fail closed.** The default runtime component and application module registries reject blank IDs and duplicate IDs instead of silently replacing an existing registration.
2. **Worker identity is validated.** Runtime construction rejects null workers, blank worker IDs, and duplicate worker IDs.
3. **Health follows lifecycle state.** The builder's `RuntimeHealth` reports healthy only while the runtime is `RUNNING`; it no longer reports healthy unconditionally before startup or after shutdown.
4. **Partial startup is compensatable.** Components and workers are recorded for rollback before their start operation is awaited, so a failed start that acquired resources still receives a stop attempt.
5. **Rollback failures remain observable.** Startup rollback continues cleaning up remaining resources and records stable failure codes without copying exception messages or secrets into availability reasons.
6. **Shutdown failures are not hidden.** Shutdown continues attempting all worker/component cleanup, records stable failure reasons, transitions to `FAILED` and returns a failed operation if any cleanup failed. It no longer reports `STOPPED` after unsuccessful cleanup.

## 3. Compatibility

- Existing `DefaultApplicationRuntime` constructor remains available; the new lifecycle-state listener is an additional overload.
- No business capability, application manifest, tenant model, plugin registry, worker interface, or workflow contract is changed.
- No new Maven module or infrastructure dependency is introduced.
- Existing component startup order remains the registration order supplied by the default ordered registry; shutdown and rollback remain reverse order.
- Failure details exposed through availability reasons contain component identity and exception class only, not arbitrary exception text.

## 4. Verification

Focused command:

```bash
mvn -B -ntp -f core/framework/pom.xml -pl foundation/numou-foundation-application -am test
```

The new `.github/workflows/runtime-platform.yml` runs this reactor on pull requests that touch the runtime foundation. CI results are the acceptance evidence; a successful GitHub write is not a build result.

## 5. Remaining M1 work

This slice does **not** yet make application manifests executable or package standalone applications. Follow-up M1 work must:

- inspect the actual capability provider implementations and their composition entry points;
- define the host-side binding from M3's validated `CapabilityResolution` to concrete component factories without creating a second global capability registry;
- implement a default `ApplicationCompositionRoot`/host bootstrap using the existing runtime lifecycle;
- prove one minimal allowlisted application composition can start and stop independently of the Application Control Plane.

The runtime must continue to reuse the plugin registry/resolver, tenancy, identity/security, existing worker lifecycle, and capability-owned adapters. No arbitrary code execution from application manifests is permitted.
