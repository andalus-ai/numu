# Application Platform M02.5 — Runtime Provider Activation

## Goal

Turn M02.4's resolved capability descriptors into actual host-owned runtime providers without executing application package code.

## Reuse and ownership

- Reuses the framework's existing `NumouPlugin`, `PluginContext`, `PluginState`, and lifecycle methods (`initialize`, `start`, `stop`).
- Does not create a second plugin lifecycle or load classes named by an application manifest.
- The application-platform runtime registry binds a `CapabilityProvider` descriptor to a host-supplied `Supplier<NumouPlugin>`.
- A registry instance is one host runtime profile. A tenant predicate further controls provider visibility.
- Provider descriptors and factories are registered together, so compatibility resolution and activation use the same trusted catalog.

## Activation

1. Resolve the validated manifest against the tenant-visible provider catalog.
2. Follow the resolver's dependency-ordered initialization list.
3. Re-check registration equality and tenant availability immediately before instantiation.
4. Create one plugin instance and one isolated `PluginContext` per tenant + installation + provider.
5. Verify plugin ID and advertised capability ID/contract version against the host registration.
6. Initialize and start. A provider must report `PluginState.ACTIVE` before activation succeeds.
7. Publish active runtime state only after every required provider has started.

On failure, stop already-started providers in reverse order. Stop failures are retained as suppressed exceptions on the activation failure.

## Deactivation and health

- Stop providers in reverse dependency order.
- A repeated deactivation is a no-op when the installation is already inactive.
- Repeated activation for an already-active installation converges to the existing state.
- Health is derived from the live plugin states and reports INACTIVE, READY, or DEGRADED.
- If shutdown fails, the installation remains tracked so an operator/retry can attempt cleanup again.

## Isolation and recovery

- Runtime instances are never shared across tenants or installations.
- A custom `ApplicationPluginContextFactory` can provide only approved host services and tenant-scoped configuration.
- The default context exposes tenant, installation, application, capability and correlation metadata; it does not expose secrets.
- Durable desired state remains the installation database and outbox. Host startup must replay enabled installations to reconstruct in-memory runtime state.

## Deployment profile

This implementation is the in-process modular-monolith/standalone adapter. A distributed host will implement the same `ApplicationHostCompositionPort` with versioned remote contracts and explicit tenant-scoped credentials; it must not share in-process plugin instances across process boundaries.

## Verification status

Unit tests are added for dependency ordering, reverse shutdown, rollback, missing/mismatched registrations, idempotency and tenant isolation. Maven and CI verification must be run before merge. This milestone does not claim startup reconciliation wiring, operator APIs, distributed provider activation, or production readiness probes.
