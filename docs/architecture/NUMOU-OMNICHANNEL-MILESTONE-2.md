# Numou Omnichannel — M2: Provider-Neutral Channel Contracts

## Included

- Adds an optional `commerce/omnichannel` Maven aggregator and `numou-omnichannel-core` module.
- Defines provider-neutral `ChannelType`, tenant/branch-aware `ChannelContext`, `IdempotencyKey`, `ChannelCapabilities` and `ChannelAdapter`.
- Uses Numou's existing `TenantId` foundation type rather than introducing a competing tenant identifier.
- Keeps the core independent of Quarkus, HTTP, messaging brokers, marketplace SDKs, databases and provider-specific dependencies.
- Adds unit tests for context normalization, optional branch scope, required metadata and idempotency key validation.

## Architectural constraints

- A channel context is resolved by a trusted adapter/authentication boundary; it is not proof of authorization by itself.
- A branch may be absent for tenant-wide integrations. Branch-level commands must validate branch membership and permissions in the owning application.
- Idempotency persistence must scope keys by tenant, provider account, operation and a documented retention policy. This value object does not itself guarantee replay safety.
- Connector capabilities are declared explicitly; orchestration must not infer support from provider names.
- Existing commerce and operational domains remain the source of truth. This module only defines shared channel contracts.

## Validation status

Source and tests are committed to this branch. The Maven build and tests have not been run from this environment; CI and the full reactor must verify the module path, foundation API compatibility and dependency management before merge.

## Follow-up

M3 will adapt existing catalog/pricing/promotion/order use cases to these contracts without duplicating their domain entities.
