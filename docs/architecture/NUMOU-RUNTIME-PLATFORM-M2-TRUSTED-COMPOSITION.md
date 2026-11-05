# Numou Runtime Platform — M2 Trusted Application Composition

- **Status:** Implementation branch created; CI/build verification pending.
- **Branch:** `runtime/m2-trusted-composition`
- **Base:** `runtime/m1-host-foundation`

## Purpose

M2 introduces the trusted composition boundary between the declarative application manifest and executable capability runtime implementations.

The manifest and existing `ApplicationCapabilityResolver` describe and resolve capability/provider metadata. They do not authorize arbitrary class loading or identify an executable class to instantiate. The application composition root owns that decision.

## Delivered

`TrustedCapabilityRuntimeCatalog` is an immutable allowlist from exact provider IDs to trusted `CapabilityRuntimeFactory` instances.

- Registrations are explicit and supplied by trusted bootstrap/application code.
- Duplicate and blank provider IDs are rejected.
- Unknown provider IDs fail closed.
- Null factories, null factory results and provider identity mismatches fail closed.
- The catalog exposes a stable, sorted provider ID set.
- Building a catalog creates an immutable snapshot; later builder changes do not mutate an existing catalog.
- No classpath scanning, reflection, manifest-supplied class names or dynamic code loading is used.

The catalog implements the existing `CapabilityRuntimeFactory` contract and can be supplied directly to `ApplicationRuntimeHost`.

## Trust and lifecycle boundaries

- Manifest validation and semantic capability resolution remain in `numou-plugin`.
- Provider implementation approval and factory registration remain in the trusted composition root.
- Startup, rollback and shutdown remain in `ApplicationRuntimeHost`.
- Plugin discovery and plugin lifecycle remain in the existing plugin subsystem.
- This catalog does not create a second capability registry or replace `RuntimePluginManager`.

## Verification

Focused tests cover explicit registration, unknown-provider rejection, duplicate/blank registration rejection, provider identity mismatch, null runtime rejection and immutable snapshots.

No Maven build or test result is claimed from this authoring session. CI should run:

```bash
cd core/framework
mvn -pl runtime -am test
```

Then verify the full framework reactor and repository CI.

## Remaining M2 integration

This milestone supplies a trusted allowlist and an executable composition seam. It does not yet adapt `NumouPlugin` instances or CDI-managed capability services into `CapabilityRuntime`. That adapter must reuse the existing plugin lifecycle safely and must be application-scoped; it must not call the global `RuntimePluginManager.start()` and accidentally start every discovered plugin. The next integration step must also address shared services/context between selected providers and lifecycle rollback semantics.
