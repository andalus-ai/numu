# Vault PostgreSQL adapter

Applies the vault-owned migrations under `db/migration`. Secret versions are append-only and keyed by tenant, vault, secret and version. Every secret lookup and mutation is tenant-scoped.

Secret creation/rotation and their metadata-only audit facts share one transaction. Revocation and its audit fact also share one transaction. Secret reveal appends a durable audit fact for the exact immutable version before returning plaintext to the caller. Repeated reveal events are intentionally permitted.

The outbox relay uses bounded leases with PostgreSQL `FOR UPDATE SKIP LOCKED`, worker-owned acknowledgements, retry scheduling and a dead-letter state. Delivery is **at least once**, not exactly once: downstream consumers must deduplicate using `event_id`. A worker crash after publishing but before acknowledgement can cause a duplicate delivery. Error details persisted by the relay are restricted to exception class names to avoid leaking provider payloads or credentials.

## Policy persistence and enforcement

Migration V3 creates tenant-local policy heads, immutable published revisions, revision-scoped rules, administrative publication audit and append-only decision audit. Revision zero means no policy has been published and evaluates to default deny. Publishing requires the caller's expected active revision; the adapter locks the tenant head, inserts the complete next snapshot, advances the head and writes the publication audit fact in one transaction. A stale publisher or audit failure leaves the active revision unchanged.

A policy snapshot is loaded under a repeatable-read transaction. Rules are immutable after publication; changes publish a new revision. A null vault or secret scope is an explicit wildcard, but tenant scope is never wildcarded. Rule matching is exact and the evaluator applies deny-overrides-allow within the selected snapshot.

`PostgresVaultAccessPolicyAdapter` evaluates the active snapshot and awaits `VaultPolicyDecisionAuditPort.append` for both allow and deny before returning. A policy-store or audit-store failure propagates and fails closed. The host supplies a stable principal-to-UUID mapping; it must map verified platform principals, not request-supplied identifiers. Workload identity is accepted only from the trusted resolver described by the application contract.

Decision audit records contain tenant, policy revision, subject reference, vault/secret identifiers, operation, effect, matched rule, reason, optional correlation ID and evaluation time. They never contain plaintext, ciphertext, key material, tokens or credentials. Restrict database access to policy administration and audit tables; runtime roles should not receive UPDATE/DELETE on immutable policy/audit records.

The host must run all vault migrations before enabling the adapter and grant the runtime database role only required table privileges. Monitor pending outbox count, oldest pending age, expired leases, retry counts and dead-letter count. Dead-letter replay should be a separately authorized operator workflow with an audit trail.
