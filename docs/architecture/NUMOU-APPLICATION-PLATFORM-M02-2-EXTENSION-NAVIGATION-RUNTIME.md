# M02.2 — Application Extension and Navigation Runtime

- **Status:** implementation branch; compile, test and host-composition verification pending.
- **Depends on:** M1–M7 application descriptor, resolver and tenant installation lifecycle; M02.1 tenant configuration.
- **Ownership:** Application Platform owns tenant application contributions and their lifecycle. The framework plugin reactor continues to own executable host plugins and capability providers.

## 1. Existing contracts reused

- The v1alpha1 application manifest already declares navigation contributions with a local route and a required permission.
- M3 validates navigation contribution IDs, known permissions, and that each navigation permission is declared by the package.
- ApplicationPackageCatalogPort returns only publisher-verified, integrity-checked and manifest-validated packages.
- ApplicationInstallation owns tenant application lifecycle. Only ENABLED installations contribute to the active host experience.
- The framework PluginRegistry and CapabilityRegistry remain host-runtime registries for executable host plugins. Application packages are declarative and are not loaded as NumouPlugin instances.

## 2. Tenant navigation resolution

The application layer exposes a separate ApplicationInstallationQueryPort.findEnabled(tenantId). The PostgreSQL adapter implements it using the authenticated tenant's transaction-local RLS context and returns installations in deterministic application-ID order.

The navigation handler:

1. Derives tenant and actor from the verified host actor provider.
2. Queries enabled installations for that tenant only.
3. Resolves each installed package by application ID and package version through the verified package catalog.
4. Requires manifest identity, version and SHA-256 digest to match the installation record.
5. Evaluates each navigation contribution's required permission through a host authorization port.
6. Returns only authorized local routes, ordered by application ID and contribution ID.

A manifest permission is a request, not a grant. Hiding a navigation item is only an experience concern; every capability endpoint must independently authorize the actual operation. No package code, script, URL or arbitrary component is executed by this flow.

Catalog inconsistency, missing verified package metadata, cross-tenant query results or digest mismatch fail closed rather than rendering untrusted contributions.

## 3. Extension-point registry

ApplicationExtensionPoint is a host-owned, reverse-DNS identified contract with a SemVer version and schema reference. ApplicationExtensionPointRegistry provides registration, lookup, deterministic enumeration and version-fenced unregistration. The in-memory implementation is intended for host composition: registering an identical definition is idempotent, while replacing an existing definition under the same ID requires an explicit versioned host deployment.

This registry does not load or execute application code. It records extension contracts exposed by the host. The current v1alpha1 manifest schema supports navigation contributions only; it does not yet declare arbitrary extension contributions. A future manifest version must add typed, schema-validated contribution descriptors and explicit compatibility ranges before tenant packages can contribute data to other extension points. Do not silently add an extensions property to the strict v1alpha1 schema.

## 4. Persistence and deployment

The registry contains host composition metadata and is rebuilt by host configuration at startup; it is not tenant business state and is not persisted per tenant. Tenant application activation remains owned by the existing installation aggregate and PostgreSQL store. Navigation is derived from enabled installation state plus the immutable, verified package manifest.

For a distributed deployment, the same query, catalog and authorization ports can be backed by remote adapters. The tenant ID must be propagated from trusted service context and independently enforced by the remote installation/catalog services. No shared database access is introduced.

## 5. Verification expectations

- Unit tests cover deterministic extension-point registration, idempotency, conflict rejection and version-fenced removal.
- Navigation tests cover enabled-only visibility, tenant isolation, package identity/version/digest matching, permission filtering and deterministic ordering.
- PostgreSQL integration tests cover enabled-installation filtering, tenant RLS and deterministic application-ID ordering.
- Host composition must wire the verified package catalog and real authorization policy before exposing navigation through a transport endpoint.

## 6. Explicitly deferred

- Application-declared non-navigation extension contributions and their v1alpha2 schema.
- UI component rendering, arbitrary frontend code, script execution and remote module loading.
- A persisted per-tenant extension registry; contributions are derived from the immutable installed package.
- REST transport and frontend navigation shell integration.
- Runtime capability provider activation/deactivation beyond the existing resolver and installation lifecycle. This requires a separate host-composition contract and must not conflate declarative applications with executable NumouPlugin lifecycle.
