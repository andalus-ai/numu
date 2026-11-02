package tech.kayys.numou.event.adapter.postgres;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.numou.event.spi.port.DeadLetterReplayStorePort;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

/**
 * PostgreSQL lease and audit implementation. JDBC work is dispatched to
 * Mutiny's worker pool so blocking database calls never run on an event loop.
 */
@ApplicationScoped
public final class PostgresDeadLetterReplayStore implements DeadLetterReplayStorePort {
    private static final Duration DEFAULT_LEASE = Duration.ofMinutes(2);
    private static final String COMPLETED_TOKEN = "already-completed";
    private static final String IN_PROGRESS_TOKEN = "in-progress";
    private final DataSource dataSource;
    private final Duration leaseDuration;

    @Inject
    public PostgresDeadLetterReplayStore(DataSource dataSource) {
        this(dataSource, DEFAULT_LEASE);
    }

    PostgresDeadLetterReplayStore(DataSource dataSource, Duration leaseDuration) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource is required");
        this.leaseDuration = Objects.requireNonNull(leaseDuration, "leaseDuration is required");
        if (leaseDuration.isZero() || leaseDuration.isNegative() || leaseDuration.toMillis() < 1) {
            throw new IllegalArgumentException("leaseDuration must be at least one millisecond");
        }
    }

    @Override
    public Uni<ReplayClaim> beginReplay(ReplayRequest request) {
        Objects.requireNonNull(request, "request");
        return Uni.createFrom().item(() -> beginReplayBlocking(request))
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    private ReplayClaim beginReplayBlocking(ReplayRequest request) {
        UUID token = UUID.randomUUID();
        String insert = """
            INSERT INTO numou_event_dead_letter_replay
                (dead_letter_record_id, tenant_id, event_type, payload_sha256, state, attempt_count)
            VALUES (?, ?, ?, ?, 'PENDING', 0)
            ON CONFLICT (dead_letter_record_id) DO NOTHING
            """;
        String lock = """
            SELECT tenant_id, event_type, payload_sha256, state, attempt_count,
                   (lease_until > clock_timestamp()) AS lease_active
              FROM numou_event_dead_letter_replay
             WHERE dead_letter_record_id = ? FOR UPDATE
            """;
        String acquire = """
            UPDATE numou_event_dead_letter_replay
               SET state = 'IN_PROGRESS', claim_token = ?,
                   lease_until = clock_timestamp() + (? * interval '1 millisecond'),
                   attempt_count = attempt_count + 1, actor_id = ?, replay_reason = ?,
                   requested_at = clock_timestamp(), failure_code = NULL, updated_at = clock_timestamp()
             WHERE dead_letter_record_id = ?
            RETURNING attempt_count
            """;
        try (Connection connection = dataSource.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement statement = connection.prepareStatement(insert)) {
                    statement.setString(1, request.deadLetterRecordId());
                    statement.setObject(2, request.tenantId());
                    statement.setString(3, request.eventType());
                    statement.setString(4, request.payloadSha256());
                    statement.executeUpdate();
                }

                String state;
                int attempt;
                boolean leaseActive;
                try (PreparedStatement statement = connection.prepareStatement(lock)) {
                    statement.setString(1, request.deadLetterRecordId());
                    try (ResultSet row = statement.executeQuery()) {
                        if (!row.next()) throw new SQLException("Replay record disappeared while acquiring lock");
                        if (!request.tenantId().equals(row.getObject("tenant_id", UUID.class))) {
                            throw new SecurityException("DLQ record belongs to a different tenant");
                        }
                        if (!request.eventType().equals(row.getString("event_type"))
                                || !request.payloadSha256().equals(row.getString("payload_sha256").trim())) {
                            throw new IllegalStateException("DLQ record identity was reused with different content");
                        }
                        state = row.getString("state");
                        attempt = row.getInt("attempt_count");
                        leaseActive = row.getBoolean("lease_active");
                    }
                }

                if ("COMPLETED".equals(state)) {
                    insertAudit(connection, request, null, attempt, "ALREADY_COMPLETED", request.reason());
                    connection.commit();
                    return new ReplayClaim(request.deadLetterRecordId(), request.tenantId(),
                            COMPLETED_TOKEN, attempt, ClaimStatus.ALREADY_COMPLETED);
                }
                if ("IN_PROGRESS".equals(state) && leaseActive) {
                    insertAudit(connection, request, null, attempt, "IN_PROGRESS", request.reason());
                    connection.commit();
                    return new ReplayClaim(request.deadLetterRecordId(), request.tenantId(),
                            IN_PROGRESS_TOKEN, attempt, ClaimStatus.IN_PROGRESS);
                }

                try (PreparedStatement statement = connection.prepareStatement(acquire)) {
                    statement.setObject(1, token);
                    statement.setLong(2, leaseDuration.toMillis());
                    statement.setString(3, request.actorId());
                    statement.setString(4, request.reason());
                    statement.setString(5, request.deadLetterRecordId());
                    try (ResultSet row = statement.executeQuery()) {
                        if (!row.next()) throw new SQLException("Replay claim update did not affect one row");
                        attempt = row.getInt("attempt_count");
                    }
                }
                insertAudit(connection, request, token, attempt, "CLAIMED", request.reason());
                connection.commit();
                return new ReplayClaim(request.deadLetterRecordId(), request.tenantId(),
                        token.toString(), attempt, ClaimStatus.ACQUIRED);
            } catch (SQLException | RuntimeException failure) {
                connection.rollback();
                throw failure;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("Unable to acquire PostgreSQL DLQ replay lease", failure);
        }
    }

    @Override
    public Uni<Void> completeReplay(ReplayClaim claim) {
        Objects.requireNonNull(claim, "claim");
        return Uni.createFrom().voidItem()
                .invoke(() -> finishBlocking(claim, true, null))
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    @Override
    public Uni<Void> failReplay(ReplayClaim claim, String failureCode) {
        Objects.requireNonNull(claim, "claim");
        String safeCode = failureCode == null || failureCode.isBlank()
                ? "UnknownFailure" : failureCode.substring(0, Math.min(120, failureCode.length()));
        return Uni.createFrom().voidItem()
                .invoke(() -> finishBlocking(claim, false, safeCode))
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    private void finishBlocking(ReplayClaim claim, boolean completed, String failureCode) {
        if (claim.status() != ClaimStatus.ACQUIRED) {
            throw new IllegalArgumentException("Only an acquired replay claim can be finalized");
        }
        UUID token;
        try {
            token = UUID.fromString(claim.claimToken());
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("Replay claim token is invalid", invalid);
        }
        String lock = """
            SELECT actor_id, replay_reason FROM numou_event_dead_letter_replay
             WHERE dead_letter_record_id = ? AND tenant_id = ?
               AND state = 'IN_PROGRESS' AND claim_token = ?
               AND lease_until > clock_timestamp() FOR UPDATE
            """;
        String update = """
            UPDATE numou_event_dead_letter_replay
               SET state = ?, claim_token = NULL, lease_until = NULL,
                   completed_at = CASE WHEN ? THEN clock_timestamp() ELSE NULL END,
                   failure_code = ?, updated_at = clock_timestamp()
             WHERE dead_letter_record_id = ? AND tenant_id = ?
               AND state = 'IN_PROGRESS' AND claim_token = ?
               AND lease_until > clock_timestamp()
            """;
        try (Connection connection = dataSource.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                String actor;
                String reason;
                try (PreparedStatement statement = connection.prepareStatement(lock)) {
                    statement.setString(1, claim.deadLetterRecordId());
                    statement.setObject(2, claim.tenantId());
                    statement.setObject(3, token);
                    try (ResultSet row = statement.executeQuery()) {
                        if (!row.next()) throw new IllegalStateException("Replay claim expired or was fenced by a newer attempt");
                        actor = row.getString("actor_id");
                        reason = row.getString("replay_reason");
                    }
                }
                try (PreparedStatement statement = connection.prepareStatement(update)) {
                    statement.setString(1, completed ? "COMPLETED" : "FAILED");
                    statement.setBoolean(2, completed);
                    statement.setString(3, failureCode);
                    statement.setString(4, claim.deadLetterRecordId());
                    statement.setObject(5, claim.tenantId());
                    statement.setObject(6, token);
                    if (statement.executeUpdate() != 1) {
                        throw new IllegalStateException("Replay claim expired or was fenced by a newer attempt");
                    }
                }
                insertAudit(connection, claim.deadLetterRecordId(), claim.tenantId(), token,
                        actor, completed ? "COMPLETED" : "FAILED",
                        completed ? reason : failureCode, claim.attempt());
                connection.commit();
            } catch (SQLException | RuntimeException failure) {
                connection.rollback();
                throw failure;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("Unable to finalize PostgreSQL DLQ replay", failure);
        }
    }

    private static void insertAudit(Connection connection, ReplayRequest request, UUID token,
            int attempt, String action, String detail) throws SQLException {
        insertAudit(connection, request.deadLetterRecordId(), request.tenantId(), token,
                request.actorId(), action, detail, attempt);
    }

    private static void insertAudit(Connection connection, String recordId, UUID tenantId, UUID token,
            String actor, String action, String detail, int attempt) throws SQLException {
        String sql = """
            INSERT INTO numou_event_dead_letter_replay_audit
                (dead_letter_record_id, tenant_id, claim_token, actor_id, action, detail, attempt_count, occurred_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, clock_timestamp())
            """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, recordId);
            statement.setObject(2, tenantId);
            statement.setObject(3, token);
            statement.setString(4, actor);
            statement.setString(5, action);
            statement.setString(6, detail);
            statement.setInt(7, attempt);
            statement.executeUpdate();
        }
    }
}
