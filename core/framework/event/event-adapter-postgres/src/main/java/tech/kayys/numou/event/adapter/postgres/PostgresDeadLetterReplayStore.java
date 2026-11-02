package tech.kayys.numou.event.adapter.postgres;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import tech.kayys.numou.event.spi.port.DeadLetterReplayStorePort;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

/** PostgreSQL replay store. Blocking JDBC work is always dispatched to a worker pool. */
@ApplicationScoped
public class PostgresDeadLetterReplayStore implements DeadLetterReplayStorePort {
    private final DataSource dataSource;
    private final Duration leaseDuration;

    @Inject
    public PostgresDeadLetterReplayStore(DataSource dataSource,
            @ConfigProperty(name = "numou.event.dead-letter-replay.lease", defaultValue = "PT2M")
            Duration leaseDuration) {
        this(dataSource, leaseDuration);
    }

    PostgresDeadLetterReplayStore(DataSource dataSource, Duration leaseDuration) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
        this.leaseDuration = Objects.requireNonNull(leaseDuration, "leaseDuration must not be null");
        if (leaseDuration.isZero() || leaseDuration.isNegative()) {
            throw new IllegalArgumentException("Replay lease duration must be positive");
        }
    }

    @Override
    public Uni<ReplayClaim> beginReplay(ReplayRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        validate(request);
        return Uni.createFrom().item(() -> beginReplayBlocking(request))
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    @Override
    public Uni<Void> completeReplay(ReplayClaim claim) {
        Objects.requireNonNull(claim, "claim must not be null");
        return Uni.createFrom().voidItem()
                .invoke(() -> finishBlocking(claim, true, null))
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    @Override
    public Uni<Void> failReplay(ReplayClaim claim, String failureCode) {
        Objects.requireNonNull(claim, "claim must not be null");
        String safeCode = bounded(failureCode, "failureCode", 120);
        return Uni.createFrom().voidItem()
                .invoke(() -> finishBlocking(claim, false, safeCode))
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
                        if (!row.next()) throw new IllegalStateException("DLQ replay record disappeared during claim");
                        UUID owner = row.getObject("tenant_id", UUID.class);
                        if (!request.tenantId().equals(owner)
                                || !request.eventType().equals(row.getString("event_type"))
                                || !request.payloadSha256().equals(row.getString("payload_sha256"))) {
                            throw new SecurityException("DLQ record identity, tenant or payload contract does not match");
                        }
                        state = row.getString("state");
                        attempt = row.getInt("attempt_count");
                        leaseActive = row.getBoolean("lease_active");
                    }
                }
                if ("COMPLETED".equals(state)) {
                    insertAudit(connection, request.deadLetterRecordId(), request.tenantId(), null,
                            request.actorId(), "ALREADY_COMPLETED", request.reason(), attempt);
                    connection.commit();
                    return new ReplayClaim(request.deadLetterRecordId(), request.tenantId(),
                            null, attempt, ClaimStatus.ALREADY_COMPLETED);
                }
                if ("IN_PROGRESS".equals(state) && leaseActive) {
                    insertAudit(connection, request.deadLetterRecordId(), request.tenantId(), null,
                            request.actorId(), "IN_PROGRESS", request.reason(), attempt);
                    connection.commit();
                    return new ReplayClaim(request.deadLetterRecordId(), request.tenantId(),
                            null, attempt, ClaimStatus.IN_PROGRESS);
                }
                String acquire = """
                    UPDATE numou_event_dead_letter_replay
                       SET state = 'IN_PROGRESS', claim_token = ?,
                           lease_until = clock_timestamp() + (? * interval '1 millisecond'),
                           attempt_count = attempt_count + 1, actor_id = ?, replay_reason = ?,
                           requested_at = clock_timestamp(), failure_code = NULL, updated_at = clock_timestamp()
                     WHERE dead_letter_record_id = ?
                    RETURNING attempt_count
                    """;
                try (PreparedStatement statement = connection.prepareStatement(acquire)) {
                    statement.setObject(1, token);
                    statement.setLong(2, leaseDuration.toMillis());
                    statement.setString(3, request.actorId());
                    statement.setString(4, request.reason());
                    statement.setString(5, request.deadLetterRecordId());
                    try (ResultSet row = statement.executeQuery()) {
                        if (!row.next()) throw new IllegalStateException("Unable to acquire DLQ replay lease");
                        attempt = row.getInt("attempt_count");
                    }
                }
                insertAudit(connection, request.deadLetterRecordId(), request.tenantId(), token,
                        request.actorId(), "CLAIMED", request.reason(), attempt);
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
            throw storageFailure("begin DLQ replay", failure);
        }
    }

    private void finishBlocking(ReplayClaim claim, boolean completed, String failureCode) {
        if (claim.status() != ClaimStatus.ACQUIRED || claim.claimToken() == null) {
            throw new IllegalArgumentException("Only an acquired replay claim can be completed");
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
            throw storageFailure("finish DLQ replay", failure);
        }
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

    private static void validate(ReplayRequest request) {
        bounded(request.deadLetterRecordId(), "deadLetterRecordId", 500);
        Objects.requireNonNull(request.tenantId(), "tenantId must not be null");
        bounded(request.actorId(), "actorId", 200);
        bounded(request.reason(), "reason", 1000);
        bounded(request.eventType(), "eventType", 200);
        String hash = bounded(request.payloadSha256(), "payloadSha256", 64);
        if (!hash.matches("[0-9a-fA-F]{64}")) throw new IllegalArgumentException("payloadSha256 must be a SHA-256 hex digest");
    }

    private static String bounded(String value, String field, int max) {
        Objects.requireNonNull(value, field + " must not be null");
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.length() > max)
            throw new IllegalArgumentException(field + " must be non-blank and at most " + max + " characters");
        return normalized;
    }

    private static RuntimeException storageFailure(String operation, SQLException cause) {
        return new IllegalStateException("Unable to " + operation + " in PostgreSQL", cause);
    }
}
