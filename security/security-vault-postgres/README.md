# Vault PostgreSQL adapter

Applies the vault-owned Flyway migration under `db/migration`. Secret versions are append-only and keyed by tenant, vault, secret and version. Every query includes the tenant scope.

The adapter commits the encrypted version and a metadata-only audit/outbox event in one PostgreSQL transaction. It never stores plaintext, unwrapped DEKs, or KEKs. Outbox delivery is deliberately separate from mutation; a later relay can retry publication without repeating the secret write.

The host must run migrations before enabling the adapter and grant the runtime database role only the required table privileges. Production deployments should deny UPDATE/DELETE on `vault_secret_version` and `vault_audit_outbox` except narrowly scoped outbox delivery columns/roles.
