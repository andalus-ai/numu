# Numou Agent Integration — Application Contracts

This module contains application-layer contracts for exposing enabled Numou capabilities to authorized agents.

## Current scope

M1 first slice implements:
- Verified caller context (`AgentExecutionContext`).
- Versioned, declarative tool metadata (`AgentToolDescriptor`).
- Tool catalog and authorization ports.
- Deterministic discovery service that returns only tools enabled for an application and permitted for the caller.
- Unit tests for filtering, deterministic ordering, mandatory caller context and approval metadata.

## Ownership and security

This module does not implement an MCP transport, authentication adapter, persistence adapter, or business operation. Those belong at integration/runtime edges.

The host must construct `AgentExecutionContext` from verified authentication and authorization context. Never construct tenant or actor identity from tool arguments.

Discovery filtering is not authorization to execute. Every tool invocation must independently authorize the operation at the owning Numou application service. Mutating and administrative descriptors are required to declare approval.

Tool descriptors are metadata only. They must not contain executable code or secrets.

## Build

This is an independently buildable Maven child project using the Numou Java 21 framework parent:

```bash
cd agent-integration/numou-agent-integration-application
../../mvnw test
```

The exact Maven wrapper location and dependency resolution should be verified in the checkout used for CI.

## Next

Implement a capability-backed catalog adapter using the existing application composition and capability provider contracts, then add an authenticated read-only HTTP/MCP transport adapter. Do not expose finance transaction querying until an appropriate tenant-scoped application-service query port exists; the currently inspected accounting `TransactionPort` does not provide a general paginated transaction-list contract.
