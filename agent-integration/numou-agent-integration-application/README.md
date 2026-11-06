# Numou Agent Integration — Application Contracts

This module contains transport-neutral application contracts for exposing enabled Numou capabilities to authorized agents.

## Current scope

M1 implements:
- Verified caller context (`AgentExecutionContext`).
- Versioned, declarative tool metadata (`AgentToolDescriptor`).
- Capability-owned tool provider and application capability-selection ports.
- Capability-aware catalog adapter that filters providers by the application's selected capability set.
- Authorization-filtered, deterministic tool discovery.
- Authorized tool description and discoverable capability listing.
- Unit tests for capability filtering, authorization, deterministic ordering, caller context and approval metadata.

## Ownership and security

This module does not implement an MCP transport, authentication adapter, persistence adapter, or business operation. Those belong at integration/runtime edges.

The host must construct `AgentExecutionContext` from verified authentication and authorization context. Never construct tenant or actor identity from tool arguments.

Discovery, listing and description are visibility operations, not authorization to execute. Every tool invocation must independently authorize the operation at the owning Numou application service. Mutating and administrative descriptors are required to declare approval.

Capability listing is derived only from capabilities that have at least one enabled tool visible to the caller; it is not a complete application capability inventory.

Tool descriptors are metadata only. They must not contain executable code or secrets.

## Build

This is an independently buildable Maven child project using the Numou Java 21 framework parent:

```bash
cd agent-integration/numou-agent-integration-application
../../mvnw test
```

The exact Maven wrapper location and dependency resolution should be verified in the checkout used for CI. Tests have not yet been executed in the remote repository environment.

## Next

Bind the capability-selection port to the actual runtime application composition, then add an authenticated read-only HTTP/MCP transport adapter after inspecting the runtime's supported server and authentication APIs. Do not expose finance transaction querying until an appropriate tenant-scoped application-service query port exists; the currently inspected accounting `TransactionPort` does not provide a general paginated transaction-list contract.
