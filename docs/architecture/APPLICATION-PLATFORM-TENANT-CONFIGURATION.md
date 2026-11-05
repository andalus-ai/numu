# Application Platform: tenant configuration contract

## Ownership

The Application Platform owns tenant-specific configuration for an installed application. The application package owns the schema; the host owns tenant identity, actor identity, authorization, persistence and secret resolution. Configuration is not a general-purpose replacement for each business capability's own operational data.

## Write contract

- Configuration is scoped by the verified `TenantId` and `ApplicationId`; tenant or actor identity is never accepted from the configuration payload.
- The host authorization port must authorize the actor for the target installed application before schema validation or persistence.
- The schema port validates against the schema version declared by the installed package and returns normalized JSON.
- `expectedRevision=0` creates the initial configuration. Positive values perform compare-and-set updates.
- Advancing the current snapshot and appending its immutable revision are one database transaction. A stale compare-and-set does not append history.
- The persisted payload is a JSON object, bounded to 1 MiB. Its SHA-256 digest is checked by the domain snapshot.

## Read and revision-history contract

- Reads use a separate host authorization port; permission to configure is not implicitly treated as permission to read.
- The tenant comes only from the verified host actor context. The persistence adapter also applies PostgreSQL row-level security using transaction-local `numou.tenant_id`.
- History is returned newest-first, ordered by monotonically increasing revision.
- Pagination uses an exclusive `beforeRevision` cursor. The first page omits the cursor; a subsequent page supplies the previous page's `nextBeforeRevision`.
- Page size is bounded to 1–100. The adapter fetches at most one extra row to determine whether another page exists. The cursor is the last revision returned, so changing page size between requests does not introduce offset drift.
- Revision rows are append-only and are not updated or deleted through the application role.

## Secrets and sensitive values

Configuration snapshots and revision history must never contain secret material. Persist opaque references to the host's secret manager (for example, a Vault path/version reference) and resolve the value only at runtime through a separate secret-resolution boundary. Schema implementations must reject inline credentials, tokens, private keys and other secret values; redaction after persistence is not an acceptable control.

## Composition

The domain and application contracts remain independent of HTTP, CDI and a specific secret provider. A standalone host, modular monolith or distributed control plane can supply the authorization, schema and secret-resolution adapters without changing the configuration aggregate or its revision semantics.

## Verification expectations

The PostgreSQL adapter tests cover atomic create/update, stale-write rejection, concurrent compare-and-set, append-only history, tenant row-level isolation and deterministic cursor pagination. Host composition must additionally provide tests for real authorization and schema enforcement before exposing transport endpoints.
