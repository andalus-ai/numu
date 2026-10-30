# Numou Architecture Blueprints

> Target architecture. Numou provides reusable modular business capabilities; downstream applications must not dictate its domain model.

## Capability boundaries

```mermaid
flowchart TB
  Farabi["Farabi"] --> Public["Public Capability Contracts"]
  Toba["Toba"] --> Public
  Apps["Other Applications"] --> Public
  Public --> Identity["Identity Capability"]
  Public --> Tenant["Tenant Capability"]
  Public --> Business["Other Business Capabilities"]
  Identity --> IdentityPorts["Identity Ports"]
  Tenant --> TenantPorts["Tenant Ports"]
  Business --> BusinessPorts["Domain Ports"]
  IdentityPorts --> Adapters["Replaceable Infrastructure Adapters"]
  TenantPorts --> Adapters
  BusinessPorts --> Adapters
```

## Tenant-aware request sequence

```mermaid
sequenceDiagram
  participant App as Consuming Application
  participant Identity as Identity Capability
  participant Tenant as Tenant Capability
  participant Domain as Business Capability
  App->>Identity: Authenticate principal
  Identity-->>App: Trusted identity
  App->>Tenant: Resolve membership and scope
  alt Membership denied
    Tenant-->>App: Deny access
  else Allowed
    Tenant-->>App: Authorized tenant context
    App->>Domain: Invoke scoped operation
    Domain-->>App: Scoped result
  end
```

## Capability evolution

```mermaid
stateDiagram-v2
  [*] --> Proposed
  Proposed --> ContractDefined
  ContractDefined --> Implemented
  Implemented --> Tested
  Tested --> Released
  Released --> Deprecated: Replacement contract available
  Deprecated --> Retired: Migration complete
```

## Architectural rules

- Capabilities own their domain invariants and explicit interfaces.
- Farabi/Toba integrations belong in adapters, not in Numou core.
- Avoid cross-capability database coupling and hidden shared state.
- Preserve versioning, migration, tenant isolation and independent testing.
- Prefer additive public API evolution with explicit deprecation policies.
