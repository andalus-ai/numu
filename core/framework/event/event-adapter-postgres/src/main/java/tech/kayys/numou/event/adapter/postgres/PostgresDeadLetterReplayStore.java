package tech.kayys.numou.event.adapter.postgres;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import tech.kayys.numou.event.spi.port.DeadLetterReplayStorePort;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * PostgreSQL lease and audit implementation. JDBC work is dispatched to
 * Mutiny's worker pool so blocking database calls never run on an event loop.
 */
public final class PostgresDeadLetterReplayStore implements DeadLetterReplayStorePort {
    private static final Duration DEFAULT_LEASE = Duration.ofMinutes(2);
    private final DataSource dataSource;
    private final Duration leaseDuration;

    public PostgresDeadLetterReplayStore(DataSource dataSource) {
        this(dataSource, DEFAULT_LEASE);
    }

    public PostgresDeadLetterReplayStore(DataSource dataSource, Duration leaseDuration) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource is required");
        this.leaseDuration = Objects.requireNonNull(leaseDuration, "leaseDuration is required");
        if (leaseDuration.isZero() || leaseDuration.isNegative())
            throw new IllegalArgumentException("leaseDuration must be positive");
    }

    @Override
    public Uni<ReplayClaim> beginReplay(ReplayRequest request) {
        Objects.requireNonNull(request, "request");
        return Uni.createFrom().item(() -> beginReplayBlocking(request))
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    private ReplayClaim beginReplayBlocking(ReplayRequest request) {
        Instant now; 
        Instant leaseUntil;
        String insert = """
                INSERT INTO numou_event_dead_letter_replay
                    (tenant_id, dead_letter_record_id, event_type, payload_sha256)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (dead_letter_record_id) DO NOTHING
                """;
        String lock = """
                SELECT event_type, payload_sha256, state, attempt, lease_until
                  FROM numou_event_dead_letter_replay
                 WHERE dead_letter_record_id = ?
                 FOR UPDATE
                """;
        String claim = """
                UPDATE numou_event_dead_letter_replay
                   SET state = 'CLAIMED', claim_token = ?, lease_until = ?,
                       attempt = attempt + 1, requested_by = ?, replay_reason = ?,
                       requested_at = ?, completed_at = NULL, last_failure_code = NULL,
                       updated_at = ?
                 WHERE tenant_id = ? AND dead_letter_record_id = ?
                """;
        String audit = """
                INSERT INTO numou_event_dead_letter_replay_audit
                    (tenant_id, dead_letter_record_id, claim_token, attempt, actor_id, action, reason, occurred_at)
                VALUES (?, ?, ?, ?, ?, 'CLAIMED', ?, ?)
                """;
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                now = databaseNow(connection);
                leaseUntil = now.plus(leaseDuration);
                try (PreparedStatement statement = connection.prepareStatement(insert)) {
                    statement.setObject(1, request.tenantId());
                    statement.setString(2, request.deadLetterRecordId());
                    statement.setString(3, request.eventType());
                    statement.setString(4, request.payloadSha256());
                    statement.executeUpdate();
                }
                String state;
                int attempt;
                Instant currentLease;
                try (PreparedStatement statement = connection.prepareStatement(lock)) {
                    statement.setString(1, request.deadLetterRecordId());
                    try (ResultSet rows = statement.executeQuery()) {
                        if (!rows.next()) throw new SQLException("Replay record disappeared while acquiring lock");
                        if (!request.tenantId().equals(rows.getObject("tenant_id", UUID.class))) {
                            throw new SecurityException("DLQ record belongs to a different tenant");
                        }
                        if (!request.eventType().equals(rows.getString("event_type"))
                                || !request.payloadSha256().equals(rows.getString("payload_sha256").trim())) {
                            throw new IllegalStateException("DLQ record identity was reused with different content");
                        }
                        state = rows.getString("state");
                        attempt = rows.getInt("attempt");
                        Timestamp lease = rows.getTimestamp("lease_until");
                        currentLease = lease == null ? null : lease.toInstant();
                    }
                }
                if ("COMPLETED".equals(state)) {
                    connection.commit();
                    return new ReplayClaim(request.deadLetterRecordId(), request.tenantId(), "completed",
                            attempt, ClaimStatus.ALREADY_COMPLETED);
                }
                if ("CLAIMED".equals(state) && currentLease != null && currentLease.isAfter(now)) {
                    connection.commit();
                    return new ReplayClaim(request.deadLetterRecordId(), request.tenantId(), "in-progress",
                            attempt, ClaimStatus.IN_PROGRESS);
                }

                UUID token = UUID.randomUUID();
                try (PreparedStatement statement = connection.prepareStatement(claim)) {
                    statement.setObject(1, token);
                    statement.setTimestamp(2, Timestamp.from(leaseUntil));
                    statement.setString(3, request.actorId());
                    statement.setString(4, request.reason());
                    statement.setTimestamp(5, Timestamp.from(now));
                    statement.setTimestamp(6, Timestamp.from(now));
                    statement.setObject(7, request.tenantId());
                    statement.setString(8, request.deadLetterRecordId());
                    if (statement.executeUpdate() != 1) throw new SQLException("Replay claim update did not affect one row");
                }
                int nextAttempt = attempt + 1;
                try (PreparedStatement statement = connection.prepareStatement(audit)) {
                    statement.setObject(1, request.tenantId());
                    statement.setString(2, request.deadLetterRecordId());
                    statement.setObject(3, token);
                    statement.setInt(4, nextAttempt);
                    statement.setString(5, request.actorId());
                    statement.setString(6, request.reason());
                    statement.setTimestamp(7, Timestamp.from(now));
                    statement.executeUpdate();
                }
                connection.commit();
                return new ReplayClaim(request.deadLetterRecordId(), request.tenantId(), token.toString(),
                        nextAttempt, ClaimStatus.ACQUIRED);
            } catch (SQLException | RuntimeException failure) {
                connection.rollback();
                throw failure;
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("Unable to acquire PostgreSQL DLQ replay lease", failure);
        }
    }

    @Override
    public Uni<Void> completeReplay(ReplayClaim claim) {
        Objects.requireNonNull(claim, "claim");
        return Uni.createFrom().item(() -> completeBlocking(claim, true, null))
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
                .chain(completed -> completed ? Uni.createFrom().voidItem()
                        : Uni.createFrom().failure(new IllegalStateException("DLQ replay claim was lost before completion")));
    }

    @Override
    public Uni<Void> failReplay(ReplayClaim claim, String failureCode) {
        Objects.requireNonNull(claim, "claim");
        String safeCode = failureCode == null ? "UnknownFailure" : failureCode.substring(0, Math.min(120, failureCode.length()));
        return Uni.createFrom().item(() -> completeBlocking(claim, false, safeCode))
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
                .chain(completed -> completed ? Uni.createFrom().voidItem()
                        : Uni.createFrom().failure(new IllegalStateException("DLQ replay claim was lost before failure audit")));
    }

    private static Instant databaseNow(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT CURRENT_TIMESTAMP");
             ResultSet rows = statement.executeQuery()) {
            if (!rows.next()) throw new SQLException("Database clock query returned no row");
            return rows.getTimestamp(1).toInstant();
        }
    }

    private boolean completeBlocking(ReplayClaim claim, boolean completed, String failureCode) {
        UUID token;
        try { token = UUID.fromString(claim.claimToken()); }
        catch (IllegalArgumentException invalid) { return false; }
        String state = completed ? "COMPLETED" : "FAILED";
        String update = """
                UPDATE numou_event_dead_letter_replay
                   SET state = ?, claim_token = NULL, lease_until = NULL,
                       completed_at = ?, last_failure_code = ?, updated_at = ?
                 WHERE tenant_id = ? AND dead_letter_record_id = ?
                   AND state = 'CLAIMED' AND claim_token = ?
                """;
        String audit = """
                INSERT INTO numou_event_dead_letter_replay_audit
                    (tenant_id, dead_letter_record_id, claim_token, attempt, actor_id, action, reason, occurred_at)
                SELECT tenant_id, dead_letter_record_id, ?, attempt, requested_by, ?, ?, ?
                  FROM numou_event_dead_letter_replay
                 WHERE tenant_id = ? AND dead_letter_record_id = ? AND state = ? AND claim_token IS NULL
                """;
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Instant now = databaseNow(connection);
                int changed;
                try (PreparedStatement statement = connection.prepareStatement(update)) {
                    statement.setString(1, state);
                    statement.setTimestamp(2, completed ? Timestamp.from(now) : null);
                    statement.setString(3, failureCode);
                    statement.setTimestamp(4, Timestamp.from(now));
                    statement.setObject(5, claim.tenantId());
                    statement.setString(6, claim.deadLetterRecordId());
                    statement.setObject(7, token);
                    changed = statement.executeUpdate();
                }
                if (changed != 1) {
                    connection.rollback();
                    return false;
                }
                try (PreparedStatement statement = connection.prepareStatement(audit)) {
                    statement.setObject(1, token);
                    statement.setString(2, state);
                    statement.setString(3, failureCode);
                    statement.setTimestamp(4, Timestamp.from(now));
                    statement.setObject(5, claim.tenantId());
                    statement.setString(6, claim.deadLetterRecordId());
                    statement.setString(7, state);
                    if (statement.executeUpdate() != 1) {
                        connection.rollback();
                        return false;
                    }
                }
                connection.commit();
                return true;
            } catch (SQLException | RuntimeException failure) {
                connection.rollback();
                throw failure;
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("Unable to finalize PostgreSQL DLQ replay", failure);
        }
    }
}
