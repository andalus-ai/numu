# Agent Integration M1 — Tool Catalog Contract

## Purpose

Provide a transport-neutral application contract for discovering Numou operations that are enabled in an application and discoverable by a verified caller.

## Design constraints

- Numou remains independent of Andalus.
- No dependency on Andalus Java 25 artifacts is introduced into the Java 21 framework.
- No second capability registry is introduced.
- No tool can be discovered merely because it exists in source code.
- Discovery is not an execution authorization decision.
- The owning business capability remains authoritative for data access and mutations.

## Implemented application contracts

- `AgentExecutionContext`: application, tenant, actor, granted permissions and correlation ID, constructed by a trusted host adapter.
- `AgentToolDescriptor`: stable tool ID/version, owning capability, purpose, required permission, operation class, approval requirement and input schema.
- `AgentToolCatalogPort`: returns tools enabled by the selected application composition.
- `AgentToolAuthorizationPort`: filters tools according to caller policy.
- `AgentToolProvider` and `ApplicationCapabilitySelectionPort`: capability-owned metadata providers and selected application composition boundary.
- `CapabilityAwareAgentToolCatalogAdapter`: exposes only tools whose capability is selected in the application composition.
- `DiscoverAgentToolsService`: deterministic, authorization-filtered discovery.
- `DescribeAgentToolService`: describes a tool only through the same authorized discovery path.
- `ListDiscoverableAgentCapabilitiesService`: derives a stable list from capabilities with at least one discoverable tool.

## Security invariants

1. Application, tenant and actor identifiers are mandatory.
2. Identity is not accepted from tool input.
3. The catalog is scoped by application ID, tenant ID and selected capabilities.
4. The authorization port filters every descriptor for discovery and description.
5. Write and administrative tools cannot be described without an approval requirement.
6. The invocation path must repeat authorization and enforce tenant/resource scope in the owning application service.
7. Tool inputs and outputs must be schema validated and bounded by the transport adapter.
8. Sensitive payloads and secrets must not be placed in descriptors.
9. Capability listing is a caller-visible projection, not a complete inventory or a grant of access.

## Deliberate limitations

This milestone does not yet provide an MCP server, identity provider adapter, durable audit store, concrete runtime composition adapter, or business query endpoint. These require inspection of the actual host authentication and application composition wiring.

The accounting `TransactionPort` inspected during M1 only provides transaction lookup by ID and a reconciliation query scoped by merchant; it is not a general-purpose tenant-scoped, paginated transaction query API. The agent gateway must not bypass this boundary with direct SQL. A dedicated application query port should be added under the accounting capability after its existing reporting/query services and tenant context are audited.

## Acceptance tests

- Unauthorized descriptors are excluded.
- Discovery is deterministic.
- Missing tenant/actor context is rejected.
- Mutating/admin descriptors require approval metadata.
- Tenant-scoped application capability selection filters catalog providers.
- Description cannot reveal a tool hidden by discovery authorization.
- Capability listing contains only authorized, selected capabilities and is stable.
- Subsequent transport tests must prove cross-tenant calls fail closed and execution authorization is repeated.
