# Leaderboard M2 — Durable Score Ledger

## Ownership

The Leaderboard capability owns accepted score movements, their idempotency identity, and participant totals. Source capabilities (Mobility, POS, Workforce, Marketplace, etc.) remain authoritative for the business facts that caused a score. Participant identity remains an opaque `ParticipantRef`; this capability does not copy user, employee, driver, or team records.

## Artifacts

- `leaderboard-domain`: immutable `ScoreEntry`, exact `ScoreValue`, idempotency/source references, and in-memory invariant model.
- `leaderboard-application`: `ScoreLedgerRepository` outbound port.
- `leaderboard-persistence-jdbc`: PostgreSQL JDBC adapter and Flyway-compatible SQL migration.

## Ledger rules

1. Entries are append-only. Corrections use a new `ADJUSTMENT`; a reversal appends the exact opposite value and references the original entry.
2. A reversal must target an existing non-reversal entry in the same tenant and leaderboard, for the same participant, with the exact negative value.
3. One original entry can be reversed at most once.
4. Idempotency is scoped by `(tenant_id, leaderboard_id, idempotency_key)`. A retry with a new generated entry ID or ingestion timestamp is a duplicate only when its immutable business payload matches. Reusing the key for a different payload is a conflict.
5. Score values use exact decimal arithmetic and fit PostgreSQL `NUMERIC(18,6)`.
6. Every read and write is tenant-scoped. Cross-tenant/global boards require a separate explicit capability contract; null tenant scope is not supported.

## Transaction and outbox

A successful new score insert and its `leaderboard.score-entry-recorded.v1` outbox row are committed in one database transaction. A duplicate delivery creates no second outbox row. The outbox payload is versioned and carries opaque source and participant references, not copied source-domain objects.

The outbox table is a durable hand-off point. A later relay milestone will add leasing, retry/backoff, dead-letter handling, and publication through Numou's shared event capability. This adapter does not claim that an event has already been published.

## PostgreSQL migration

Apply `leaderboard-persistence-jdbc/src/main/resources/db/migration/V1__leaderboard_score_ledger.sql` using the product's migration runner (for example Flyway). The migration creates:

- `leaderboard_score_entry` with tenant/leaderboard-scoped primary and idempotency keys.
- A unique partial index preventing more than one reversal per original entry.
- Database triggers validating reversal content and rejecting UPDATE/DELETE operations.
- `leaderboard_score_outbox` with a pending-event index and a foreign key to the accepted score entry.

The adapter expects this schema to be installed before use. It accepts a standard `javax.sql.DataSource` and does not require Quarkus or a broker.

## Verification

The domain tests cover append, exact aggregation, retry semantics, tenant/leaderboard isolation, adjustments, reversals, and immutable history. PostgreSQL integration tests use Testcontainers and cover durable reads, idempotent retries, conflicting payloads, reversal uniqueness, append-only enforcement, tenant isolation, and transactional outbox creation.

Run the narrow reactor from the repository root after installing the framework parent and Foundation domain artifacts:

```bash
mvn -B -ntp -f core/framework/leaderboard/pom.xml verify
```

If Docker is unavailable, Testcontainers tests are skipped by their explicit `disabledWithoutDocker` setting; that is not equivalent to a successful PostgreSQL integration run.
