# Vault PostgreSQL adapter

Applies the vault-owned Flyway migrations under `db/migration`. Secret versions are append-only and keyed by tenant, vault, secret and version. Every secret lookup and mutation is tenant-scoped.

Secret creation/rotation and their metadata-only audit facts share one transaction. Revocation and its audit fact also share one transaction. Secret reveal appends a durable audit fact for the exact immutable version before returning plaintext to the caller. Repeated reveal events are intentionally permitted.

The outbox relay uses bounded leases with PostgreSQL `FOR UPDATE SKIP LOCKED`, worker-owned acknowledgements, retry scheduling and a dead-letter state. Delivery is **at least once**, not exactly once: downstream consumers must deduplicate using `event_id`. A worker crash after publishing but before acknowledgement can cause a duplicate delivery. Error details persisted by the relay are restricted to exception class names to avoid leaking provider payloads or credentials.

Outbox events contain metadata only: never plaintext, ciphertext, unwrapped DEKs, or KEKs. The host supplies the publisher port implementation for its event transport. The adapter dispatches blocking JDBC work to Mutiny's worker pool.

The host must run all vault migrations before enabling the adapter and grant the runtime database role only required table privileges. Production deployments should deny UPDATE/DELETE on `vault_secret_version`; grant outbox state updates only to the dedicated relay role. Monitor pending count, oldest pending age, expired leases, retry counts and dead-letter count. Dead-letter replay should be a separately authorized operator workflow with an audit trail.
