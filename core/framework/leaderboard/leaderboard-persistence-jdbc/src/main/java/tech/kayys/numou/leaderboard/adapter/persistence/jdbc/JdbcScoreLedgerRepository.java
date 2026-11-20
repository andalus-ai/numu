package tech.kayys.numou.leaderboard.adapter.persistence.jdbc;

import tech.kayys.numou.leaderboard.application.port.out.ScoreLedgerRepository;
import tech.kayys.numou.leaderboard.domain.*;

import javax.sql.DataSource;
import java.sql.*;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * PostgreSQL JDBC implementation. Database uniqueness constraints are the
 * concurrency boundary; the in-memory ScoreLedger is not used for locking.
 */
public final class JdbcScoreLedgerRepository implements ScoreLedgerRepository {
    private static final String COLUMNS = """
            tenant_id, leaderboard_id, entry_id, participant_type, participant_id,
            entry_kind, score_value, source_type, source_id, idempotency_key,
            reverses_entry_id, reason, effective_at, recorded_at
            """;

    private final DataSource dataSource;

    public JdbcScoreLedgerRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public ScoreLedger.AppendResult append(ScoreEntry entry) {
        Objects.requireNonNull(entry, "entry");
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                if (entry.kind() == ScoreEntryKind.REVERSAL) validateReversal(connection, entry);
                String sql = """
                        INSERT INTO leaderboard_score_entry (
                            tenant_id, leaderboard_id, entry_id, participant_type, participant_id,
                            entry_kind, score_value, source_type, source_id, idempotency_key,
                            reverses_entry_id, reason, effective_at, recorded_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT (tenant_id, leaderboard_id, idempotency_key) DO NOTHING
                        """;
                int inserted;
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    bind(statement, entry);
                    inserted = statement.executeUpdate();
                }
                if (inserted == 1) {
                    insertOutboxEvent(connection, entry);
                    connection.commit();
                    return new ScoreLedger.AppendResult(ScoreLedger.AppendStatus.APPENDED, entry);
                }
                ScoreEntry existing = findByIdempotencyKey(connection, entry.scope(),
                        entry.leaderboardId(), entry.idempotencyKey())
                        .orElseThrow(() -> new SQLException("idempotency conflict row disappeared"));
                if (!existing.sameIdempotentPayload(entry)) {
                    throw new IllegalStateException("idempotency key was already used for a different score entry");
                }
                connection.commit();
                return new ScoreLedger.AppendResult(ScoreLedger.AppendStatus.DUPLICATE, existing);
            } catch (RuntimeException | SQLException failure) {
                rollback(connection, failure);
                if (failure instanceof RuntimeException runtime) throw runtime;
                throw new ScoreLedgerPersistenceException("Unable to append score entry", failure);
            }
        } catch (SQLException failure) {
            throw new ScoreLedgerPersistenceException("Unable to acquire score-ledger connection", failure);
        }
    }

    private void insertOutboxEvent(Connection connection, ScoreEntry entry) throws SQLException {
        String sql = """
                INSERT INTO leaderboard_score_outbox (
                    event_id, tenant_id, leaderboard_id, aggregate_id,
                    event_type, schema_version, payload, occurred_at, created_at
                ) VALUES (?, ?, ?, ?, 'leaderboard.score-entry-recorded.v1', 1,
                    jsonb_build_object(
                        'entryId', ?::text,
                        'tenantId', ?,
                        'leaderboardId', ?::text,
                        'participantType', ?,
                        'participantId', ?,
                        'entryKind', ?,
                        'scoreValue', ?,
                        'sourceType', ?,
                        'sourceId', ?,
                        'idempotencyKey', ?,
                        'reversesEntryId', ?,
                        'reason', ?,
                        'effectiveAt', ?,
                        'recordedAt', ?
                    ), ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setString(2, entry.scope().tenantId());
            statement.setObject(3, entry.leaderboardId().value());
            statement.setObject(4, entry.id().value());
            statement.setObject(5, entry.id().value());
            statement.setString(6, entry.scope().tenantId());
            statement.setObject(7, entry.leaderboardId().value());
            statement.setString(8, entry.participant().subjectType());
            statement.setString(9, entry.participant().subjectId());
            statement.setString(10, entry.kind().name());
            statement.setBigDecimal(11, entry.value().amount());
            statement.setString(12, entry.source().sourceType());
            statement.setString(13, entry.source().sourceId());
            statement.setString(14, entry.idempotencyKey().value());
            statement.setString(15, entry.reversesEntryId() == null ? null : entry.reversesEntryId().value().toString());
            statement.setString(16, entry.reason());
            statement.setString(17, entry.effectiveAt().toString());
            statement.setString(18, entry.recordedAt().toString());
            statement.setTimestamp(19, Timestamp.from(entry.recordedAt()));
            statement.setTimestamp(20, Timestamp.from(entry.recordedAt()));
            statement.executeUpdate();
        }
    }

    private void validateReversal(Connection connection, ScoreEntry reversal) throws SQLException {
        String sql = """
                SELECT participant_type, participant_id, entry_kind, score_value
                FROM leaderboard_score_entry
                WHERE tenant_id = ? AND leaderboard_id = ? AND entry_id = ?
                FOR UPDATE
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, reversal.scope().tenantId());
            statement.setObject(2, reversal.leaderboardId().value());
            statement.setObject(3, reversal.reversesEntryId().value());
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("reversed score entry does not exist in this tenant leaderboard");
                if (ScoreEntryKind.REVERSAL.name().equals(rs.getString("entry_kind"))) {
                    throw new IllegalArgumentException("a reversal cannot itself be reversed");
                }
                if (!reversal.participant().subjectType().equals(rs.getString("participant_type"))
                        || !reversal.participant().subjectId().equals(rs.getString("participant_id"))
                        || new ScoreValue(rs.getBigDecimal("score_value")).negate().compareTo(reversal.value()) != 0) {
                    throw new IllegalArgumentException("reversal must negate the original score for the same participant");
                }
            }
        }
    }

    @Override
    public Optional<ScoreEntry> findByIdempotencyKey(
            LeaderboardScope scope, LeaderboardId leaderboardId, IdempotencyKey key) {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(leaderboardId, "leaderboardId");
        Objects.requireNonNull(key, "key");
        try (Connection connection = dataSource.getConnection()) {
            return findByIdempotencyKey(connection, scope, leaderboardId, key);
        } catch (SQLException failure) {
            throw new ScoreLedgerPersistenceException("Unable to read score entry by idempotency key", failure);
        }
    }

    private Optional<ScoreEntry> findByIdempotencyKey(
            Connection connection, LeaderboardScope scope, LeaderboardId leaderboardId,
            IdempotencyKey key) throws SQLException {
        String sql = "SELECT " + COLUMNS + """
                 FROM leaderboard_score_entry
                 WHERE tenant_id = ? AND leaderboard_id = ? AND idempotency_key = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, scope.tenantId());
            statement.setObject(2, leaderboardId.value());
            statement.setString(3, key.value());
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    @Override
    public ScoreValue totalFor(LeaderboardScope scope, LeaderboardId leaderboardId,
                               ParticipantRef participant) {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(leaderboardId, "leaderboardId");
        Objects.requireNonNull(participant, "participant");
        String sql = """
                SELECT COALESCE(SUM(score_value), 0) AS total
                FROM leaderboard_score_entry
                WHERE tenant_id = ? AND leaderboard_id = ?
                  AND participant_type = ? AND participant_id = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, scope.tenantId());
            statement.setObject(2, leaderboardId.value());
            statement.setString(3, participant.subjectType());
            statement.setString(4, participant.subjectId());
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return new ScoreValue(rs.getBigDecimal("total"));
            }
        } catch (SQLException failure) {
            throw new ScoreLedgerPersistenceException("Unable to aggregate participant score", failure);
        }
    }

    private static void bind(PreparedStatement statement, ScoreEntry entry) throws SQLException {
        statement.setString(1, entry.scope().tenantId());
        statement.setObject(2, entry.leaderboardId().value());
        statement.setObject(3, entry.id().value());
        statement.setString(4, entry.participant().subjectType());
        statement.setString(5, entry.participant().subjectId());
        statement.setString(6, entry.kind().name());
        statement.setBigDecimal(7, entry.value().amount());
        statement.setString(8, entry.source().sourceType());
        statement.setString(9, entry.source().sourceId());
        statement.setString(10, entry.idempotencyKey().value());
        statement.setObject(11, entry.reversesEntryId() == null ? null : entry.reversesEntryId().value());
        statement.setString(12, entry.reason());
        statement.setTimestamp(13, Timestamp.from(entry.effectiveAt()));
        statement.setTimestamp(14, Timestamp.from(entry.recordedAt()));
    }

    private static ScoreEntry map(ResultSet rs) throws SQLException {
        UUID reversalId = rs.getObject("reverses_entry_id", UUID.class);
        return new ScoreEntry(
                ScoreEntryId.of(rs.getObject("entry_id", UUID.class)),
                LeaderboardId.of(rs.getObject("leaderboard_id", UUID.class)),
                new LeaderboardScope(rs.getString("tenant_id")),
                new ParticipantRef(rs.getString("participant_type"), rs.getString("participant_id")),
                ScoreEntryKind.valueOf(rs.getString("entry_kind")),
                new ScoreValue(rs.getBigDecimal("score_value")),
                new SourceReference(rs.getString("source_type"), rs.getString("source_id")),
                new IdempotencyKey(rs.getString("idempotency_key")),
                reversalId == null ? null : ScoreEntryId.of(reversalId),
                rs.getString("reason"),
                rs.getTimestamp("effective_at").toInstant(),
                rs.getTimestamp("recorded_at").toInstant());
    }

    private static void rollback(Connection connection, Throwable failure) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            failure.addSuppressed(rollbackFailure);
        }
    }
}
