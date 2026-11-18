# Application Platform M02.4 — Capability Resolution Runtime

## Goal

Resolve an application's declared capability requirements against the capability providers available to its tenant and selected host profile. Reuse the existing framework resolver; do not introduce a second capability model or load package code.

## Design

- `ApplicationCapabilityProviderCatalogPort` supplies a tenant-scoped list of host-owned `CapabilityProvider` descriptors.
- `ResolvingApplicationCapabilityCompatibilityAdapter` delegates SemVer range matching, required/optional handling, dependency ordering, duplicate provider detection and cycle detection to the existing `ApplicationCapabilityResolver`.
- Required capabilities with no compatible provider fail closed. Optional capabilities may be unavailable and are reported in `CapabilityResolution`.
- More than one compatible provider is ambiguous and fails closed; provider selection must be an explicit host/profile decision, not incidental iteration order.
- Provider descriptors are metadata only. Runtime implementations remain registered and owned by the host; application packages never supply executable provider code.
- Tenant identity is passed into the provider catalog boundary. Catalog adapters must enforce tenant/profile visibility and must not return providers unavailable to that tenant.

## Lifecycle integration

The compatibility adapter is invoked by the installation provisioning flow before provisioning. The host composition activation path must also invoke the same compatibility boundary before making a committed installation active, so stale host profiles or removed providers cannot silently activate an application.

## Deployment profiles

The catalog port can be backed by:
1. an in-process registry for a modular monolith;
2. a fixed provider set for a standalone capability service;
3. a tenant/profile-aware service catalog for distributed deployments.

The application platform owns orchestration; it does not own the business capability implementations.

## Verification still required

Unit tests cover required/optional requirements, missing providers, ambiguous providers and tenant-scoped catalog lookup. Run the application-platform application reactor and framework plugin reactor tests together before merge. Production catalog adapters, provider health/readiness, remote contract negotiation and runtime adapter integration remain follow-up work.
