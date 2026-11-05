# Numou Runtime Platform — M2 Trusted Application Composition

- **Status:** Implementation branch created; CI/build verification pending.
- **Branch:** `runtime/m2-trusted-composition`
- **PR:** #1768 (draft, stacked on M1 #1767)

## Purpose

M2 introduces the trusted composition boundary between the declarative application manifest and executable capability runtime implementations.

The manifest and existing `ApplicationCapabilityResolver` describe and resolve capability/provider metadata. They do not authorize arbitrary class loading or identify an executable class to instantiate. The application composition root owns that decision.

## Delivered

### Trusted runtime catalog

`TrustedCapabilityRuntimeCatalog` is an immutable allowlist from exact provider IDs to trusted `CapabilityRuntimeFactory` instances.

- Registrations are explicit and supplied by trusted bootstrap/application code.
- Duplicate and blank provider IDs are rejected.
- Unknown provider IDs fail closed.
- Null factories, null factory results and provider identity mismatches fail closed.
- The catalog exposes a stable, sorted provider ID set.
- Building a catalog creates an immutable snapshot.
- No classpath scanning, reflection, manifest-supplied class names or dynamic code loading is used.

### Existing plugin lifecycle adapter

`ApplicationPluginRuntimeFactory` adapts explicitly selected `NumouPlugin` instances to the runtime host.

- Only plugins explicitly registered for a resolved provider can be created.
- Provider ID, capability ID and contract version must match the trusted registration.
- The plugin must advertise the registered capability.
- The runtime returns the **resolved provider ID** to the host; plugin identity remains separate.
- Plugin initialization and start/stop are invoked directly. It does not call global `RuntimePluginManager.start()`.
- Each application gets an isolated service registry shared only by its selected plugin contexts; plugin properties and attributes remain scoped to each plugin context.
- Duplicate registration of one plugin instance is rejected to prevent duplicate lifecycle invocation when a plugin advertises multiple capabilities. Supporting one plugin lifecycle for multiple provider contracts requires a grouped-provider abstraction and is deliberately not approximated by starting the same plugin more than once.
- Required plugin dependencies must be registered and also represented in the provider's capability dependency graph, so the existing resolver can enforce startup ordering. Optional plugin dependencies are not promoted to required capability dependencies.
- If initialization partially fails, the host's rollback path calls plugin stop. Plugin implementations should make stop safe after partial initialization.

## Trust and lifecycle boundaries

- Manifest validation and semantic capability resolution remain in `numou-plugin`.
- Provider implementation approval and factory registration remain in the trusted composition root.
- Startup, rollback and shutdown remain in `ApplicationRuntimeHost`.
- Plugin discovery and global plugin lifecycle remain in the existing plugin subsystem.
- This adapter does not create a second global capability registry and does not mutate the global plugin registry.

## Verification

Tests cover explicit registration, fail-closed behavior, provider identity and metadata mismatch, application-scoped selection, shared service visibility, partial initialization cleanup, and host dependency ordering/reverse shutdown.

No Maven build or test result is claimed from this authoring session. CI should run:

```bash
cd core/framework
mvn -pl runtime -am test
```

Then verify the full framework reactor and repository CI.
