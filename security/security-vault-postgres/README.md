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


## Policy lifecycle controls

Policy publication and rollback require two independently authenticated principals in the same tenant. The proposer must hold the platform `UPDATE` grant on `vault-policy`; the approver must separately hold the `PERFORM` grant on `vault-policy-approve` (or `vault-policy-rollback-approve`). The same principal ID cannot propose and approve. The database also rejects equal actor/approver labels and stores both identities on the immutable revision and administrative audit row. Hosts must provision these grants to distinct security-administrator roles; do not grant both permissions broadly.

Rollback never rewinds the active pointer or edits old rows. It reads a tenant-scoped historical snapshot and publishes its rules as a new monotonically increasing revision. The new revision records `change_type=ROLLBACK` and its `source_revision`, while the admin audit records `POLICY_ROLLED_BACK`. The expected active revision is checked before copying and compare-and-set again during publication, so concurrent policy changes cannot be overwritten.

`PostgresVaultPolicyMetricsAdapter` exposes tenant-scoped active revision/rule count and ALLOW/DENY decision counts over a caller-specified time window. The application service authorizes metric reads and bounds the window to seven days. These are operational counters, not a substitute for alerting on database availability, audit write failures, or relay health.


The application-level co-authorization check is not yet a persisted draft/approval workflow: the host must obtain the approver's independently authenticated confirmation for the exact policy payload before invoking publication. A future review workflow should persist a canonical proposal digest and approval state so approval can be deferred across requests without allowing payload substitution.
