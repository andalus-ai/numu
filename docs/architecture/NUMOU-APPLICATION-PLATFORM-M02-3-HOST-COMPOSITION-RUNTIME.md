# Application Platform M02.3 — Host Composition Runtime

## Goal

Connect the durable application-installation lifecycle to a host-owned runtime composition boundary. An enabled application contributes approved capabilities and declarative metadata; suspension, uninstall, or terminal failure removes those contributions.

## Implementation boundary

- The installation outbox remains the durable source of lifecycle transitions.
- `ApplicationHostLifecycleDispatcher` consumes a claimed transition and routes ENABLED to activation, and SUSPENDED / UNINSTALLING / UNINSTALLED / FAILED to deactivation.
- Activation re-reads the publisher-verified package from `ApplicationPackageCatalogPort` and checks the immutable package digest before invoking the host.
- Deactivation does not need to load package code or package content. It uses the committed installation identity.
- The outbox event ID is the operation ID. Host adapters must make activate/deactivate idempotent for this ID because delivery is at-least-once.
- `ApplicationHostCompositionPort` is an SPI. Its implementation must compose only host-registered capability providers, apply tenant and permission policy, and never execute application package code.

## Deployment profiles

The same host composition port can be implemented by:
1. a modular-monolith adapter that activates in-process capability providers;
2. a standalone service adapter that exposes only the selected capability endpoints;
3. a distributed host adapter that resolves versioned service contracts and tenant-scoped credentials.

The application platform does not depend on individual business capability implementations.

## Security and failure behavior

- Package digest mismatch fails closed before activation.
- Tenant identity is supplied by the worker's tenant-scoped outbox claim, not trusted from package metadata.
- Host adapters must independently enforce tenant scope and authorization for every capability operation.
- Failed or repeated delivery must converge to the same desired state.
- Deactivation is idempotent and must tolerate an already inactive installation.

## Verification still required

Run the application-platform application reactor tests and add adapter-level tests for the actual host runtime implementation. This milestone defines and tests the lifecycle orchestration boundary; it does not claim a production host adapter, REST wiring, or executable plugin loading.
