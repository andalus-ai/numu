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
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** PostgreSQL-backed, tenant-bound replay leases with append-only audit. */
@ApplicationScoped
public final class PostgresDeadLetterReplayStore implements DeadLetterReplayStorePort {
    private static final Duration DEFAULT_LEASE = Duration.ofMinutes(2);
    private final DataSource dataSource;
    private final Duration leaseDuration;

    @Inject
    public PostgresDeadLetterReplayStore(DataSource dataSource) {
        this(dataSource, DEFAULT_LEASE);
    }

    public PostgresDeadLetterReplayStore(DataSource dataSource, Duration leaseDuration) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource is required");
        this.leaseDuration = Objects.requireNonNull(leaseDuration, "leaseDuration is required");
        if (leaseDuration.isZero() || leaseDuration.isNegative()) {
            throw new IllegalArgumentException("leaseDuration must be positive");
        }
    }

    @Override
    public Uni<ReplayClaim> beginReplay(ReplayRequest request) {
        Objects.requireNonNull(request, "request");
        return Uni.createFrom().item(() -> beginReplayBlocking(request))
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    private ReplayClaim beginReplayBlocking(ReplayRequest request) {
        String insert = """
                INSERT INTO numou_event_dead_letter_replay
                    (dead_letter_record_id, tenant_id, event_type, payload_sha256)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (dead_letter_record_id) DO NOTHING
                """;
        String select = """
                SELECT tenant_id, event_type, payload_sha256, state, attempt_count, lease_until
                  FROM numou_event_dead_letter_replay
                 WHERE dead_letter_record_id = ?
                 FOR UPDATE
                """;
        String claim = """
                UPDATE numou_event_dead_letter_replay
                   SET state = 'IN_PROGRESS', claim_token = ?, lease_until = ?,
                       attempt_count = attempt_count + 1, actor_id = ?, replay_reason = ?,
                       requested_at = ?, completed_at = NULL, failure_code = NULL,
                       updated_at = ?
                 WHERE dead_letter_record_id = ? AND tenant_id = ?
                """;
        String audit = """
                INSERT INTO numou_event_dead_letter_replay_audit
                    (dead_letter_record_id, tenant_id, claim_token, actor_id, action, detail,
                     attempt_count, occurred_at)
                VALUES (?, ?, ?, ?, 'CLAIMED', ?, ?, ?)
                """;

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Instant now = databaseNow(connection);
                Instant leaseUntil = now.plus(leaseDuration);
                try (PreparedStatement statement = connection.prepareStatement(insert)) {
                    statement.setString(1, request.deadLetterRecordId());
                    statement.setObject(2, request.tenantId());
                    statement.setString(3, request.eventType());
                    statement.setString(4, request.payloadSha256());
                    statement.executeUpdate();
                }

                String state;
                int attempts;
                Instant currentLease;
                try (PreparedStatement statement = connection.prepareStatement(select)) {
                    statement.setString(1, request.deadLetterRecordId());
                    try (ResultSet rows = statement.executeQuery()) {
                        if (!rows.next()) throw new SQLException("DLQ replay row disappeared while locking");
                        if (!request.tenantId().equals(rows.getObject("tenant_id", UUID.class))) {
                            throw new SecurityException("DLQ record belongs to a different tenant");
                        }
                        if (!request.eventType().equals(rows.getString("event_type"))
                                || !request.payloadSha256().equals(rows.getString("payload_sha256").trim())) {
                            throw new SecurityException("DLQ record identity was reused with different content");
                        }
                        state = rows.getString("state");
                        attempts = rows.getInt("attempt_count");
                        Timestamp lease = rows.getTimestamp("lease_until");
                        currentLease = lease == null ? null : lease.toInstant();
                    }
                }

                if ("COMPLETED".equals(state)) {
                    connection.commit();
                    return new ReplayClaim(request.deadLetterRecordId(), request.tenantId(),
                            "completed", attempts, ClaimStatus.ALREADY_COMPLETED);
                }
                if ("IN_PROGRESS".equals(state) && currentLease != null && currentLease.isAfter(now)) {
                    connection.commit();
                    return new ReplayClaim(request.deadLetterRecordId(), request.tenantId(),
                            "in-progress", attempts, ClaimStatus.IN_PROGRESS);
                }

                UUID token = UUID.randomUUID();
                try (PreparedStatement statement = connection.prepareStatement(claim)) {
                    statement.setObject(1, token);
                    statement.setTimestamp(2, Timestamp.from(leaseUntil));
                    statement.setString(3, request.actorId());
                    statement.setString(4, request.reason());
                    statement.setTimestamp(5, Timestamp.from(now));
                    statement.setTimestamp(6, Timestamp.from(now));
                    statement.setString(7, request.deadLetterRecordId());
                    statement.setObject(8, request.tenantId());
                    if (statement.executeUpdate() != 1) {
                        throw new SQLException("DLQ replay claim did not update exactly one row");
                    }
                }

                int nextAttempt = attempts + 1;
                try (PreparedStatement statement = connection.prepareStatement(audit)) {
                    statement.setString(1, request.deadLetterRecordId());
                    statement.setObject(2, request.tenantId());
                    statement.setObject(3, token);
                    statement.setString(4, request.actorId());
                    statement.setString(5, request.reason());
                    statement.setInt(6, nextAttempt);
                    statement.setTimestamp(7, Timestamp.from(now));
                    statement.executeUpdate();
                }
                connection.commit();
                return new ReplayClaim(request.deadLetterRecordId(), request.tenantId(),
                        token.toString(), nextAttempt, ClaimStatus.ACQUIRED);
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
        return finish(claim, true, null);
    }

    @Override
    public Uni<Void> failReplay(ReplayClaim claim, String failureCode) {
        Objects.requireNonNull(claim, "claim");
        String safeCode = failureCode == null ? "UnknownFailure"
                : failureCode.substring(0, Math.min(120, failureCode.length()));
        return finish(claim, false, safeCode);
    }

    private Uni<Void> finish(ReplayClaim claim, boolean completed, String failureCode) {
        return Uni.createFrom().item(() -> finishBlocking(claim, completed, failureCode))
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
                .chain(updated -> updated ? Uni.createFrom().voidItem()
                        : Uni.createFrom().failure(new IllegalStateException(
                                "DLQ replay claim was lost before its outcome was recorded")));
    }

    private boolean finishBlocking(ReplayClaim claim, boolean completed, String failureCode) {
        final UUID token;
        try {
            token = UUID.fromString(claim.claimToken());
        } catch (IllegalArgumentException invalidToken) {
            return false;
        }

        String nextState = completed ? "COMPLETED" : "FAILED";
        String update = """
                UPDATE numou_event_dead_letter_replay
                   SET state = ?, claim_token = NULL, lease_until = NULL,
                       completed_at = ?, failure_code = ?, updated_at = ?
                 WHERE dead_letter_record_id = ? AND tenant_id = ?
                   AND state = 'IN_PROGRESS' AND claim_token = ? AND lease_until > clock_timestamp()
                """;
        String audit = """
                INSERT INTO numou_event_dead_letter_replay_audit
                    (dead_letter_record_id, tenant_id, claim_token, actor_id, action, detail,
                     attempt_count, occurred_at)
                SELECT dead_letter_record_id, tenant_id, ?, actor_id, ?, ?, attempt_count, ?
                  FROM numou_event_dead_letter_replay
                 WHERE dead_letter_record_id = ? AND tenant_id = ?
                   AND state = ? AND claim_token IS NULL
                """;

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Instant now = databaseNow(connection);
                int changed;
                try (PreparedStatement statement = connection.prepareStatement(update)) {
                    statement.setString(1, nextState);
                    statement.setTimestamp(2, completed ? Timestamp.from(now) : null);
                    statement.setString(3, failureCode);
                    statement.setTimestamp(4, Timestamp.from(now));
                    statement.setString(5, claim.deadLetterRecordId());
                    statement.setObject(6, claim.tenantId());
                    statement.setObject(7, token);
                    changed = statement.executeUpdate();
                }
                if (changed != 1) {
                    connection.rollback();
                    return false;
                }

                try (PreparedStatement statement = connection.prepareStatement(audit)) {
                    statement.setObject(1, token);
                    statement.setString(2, nextState);
                    statement.setString(3, failureCode);
                    statement.setTimestamp(4, Timestamp.from(now));
                    statement.setString(5, claim.deadLetterRecordId());
                    statement.setObject(6, claim.tenantId());
                    statement.setString(7, nextState);
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

    private static Instant databaseNow(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT clock_timestamp()");
             ResultSet rows = statement.executeQuery()) {
            if (!rows.next()) throw new SQLException("Database clock query returned no row");
            return rows.getTimestamp(1).toInstant();
        }
    }
}
